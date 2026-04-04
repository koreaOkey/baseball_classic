import SwiftUI

struct LiveGameScreen: View {
    let activeTheme: ThemeData?
    let gameId: String?
    let syncedGameId: String?
    let onBack: () -> Void

    @Environment(\.teamTheme) private var teamTheme
    @State private var gameState: LiveGameState?
    @State private var events: [LiveEvent] = []
    @State private var loadError: String?

    private var primaryColor: Color {
        activeTheme?.colors.primary ?? teamTheme.primary
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header with scoreboard
            VStack(alignment: .leading, spacing: 0) {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                        .font(.system(size: 20))
                        .padding(12)
                }

                if gameId == nil || gameId?.isEmpty == true {
                    Text("선택한 경기가 없습니다.")
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.bottom, 16)
                } else if gameState == nil {
                    Text(loadError ?? "경기 데이터를 불러오는 중...")
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.bottom, 16)
                } else {
                    ScoreboardCard(state: gameState!, events: events)
                }
            }
            .padding(16)
            .background(
                LinearGradient(
                    colors: [primaryColor, primaryColor.opacity(0.85)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )

            // Watch sync status
            HStack(spacing: 8) {
                Image(systemName: "applewatch")
                    .foregroundColor(syncedGameId?.isEmpty ?? true ? AppColors.gray400 : AppColors.green500)
                Text(watchSyncText)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(syncedGameId?.isEmpty ?? true ? AppColors.gray400 : AppColors.green500)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppColors.gray900)
            .cornerRadius(12)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            // Events list
            ScrollView {
                LazyVStack(spacing: 0) {
                    Text("실시간 이벤트")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 8)

                    if events.isEmpty {
                        Text("아직 이벤트가 없습니다.")
                            .foregroundColor(AppColors.gray500)
                            .padding(.vertical, 12)
                    } else {
                        ForEach(events) { event in
                            EventCard(event: event)
                        }
                    }

                    Spacer().frame(height: 80)
                }
                .padding(.horizontal, 16)
            }
        }
        .background(AppColors.gray950)
        .task(id: gameId) {
            await startLiveStream()
        }
    }

    private var watchSyncText: String {
        if syncedGameId?.isEmpty ?? true {
            return "워치 동기화 꺼짐"
        } else if syncedGameId == gameId {
            return "워치가 현재 경기와 동기화 중"
        } else {
            return "워치 동기화 대상: \(syncedGameId ?? "")"
        }
    }

    // MARK: - Live Stream
    private func startLiveStream() async {
        guard let gameId = gameId, !gameId.isEmpty else { return }

        let repo = BackendGamesRepository.shared
        let reconnectDelays: [UInt64] = [1_000_000_000, 2_000_000_000, 5_000_000_000, 10_000_000_000]
        var reconnectAttempt = 0
        var cursor: Int64 = 0

        while !Task.isCancelled {
            // Recovery pull
            if let fetchedState = await repo.fetchGameState(gameId: gameId) {
                gameState = fetchedState
                loadError = nil
            } else if gameState == nil {
                loadError = "백엔드 경기 상태를 가져오지 못했습니다."
            }

            if let fetchedEvents = await repo.fetchGameEvents(gameId: gameId, after: cursor, limit: 200) {
                mergeEvents(fetchedEvents.items)
                if let nextCursor = fetchedEvents.nextCursor {
                    cursor = max(cursor, nextCursor)
                }
            }

            // WebSocket stream
            do {
                for await message in repo.streamGame(gameId: gameId) {
                    switch message {
                    case .connected:
                        reconnectAttempt = 0
                        loadError = nil
                    case .closed:
                        break
                    case .error:
                        if gameState == nil {
                            loadError = "실시간 연결이 불안정합니다. 재연결 중..."
                        }
                        break
                    case .events(let items):
                        mergeEvents(items)
                    case .state(let state):
                        gameState = state
                        loadError = nil
                    case .update(let state, let events):
                        mergeEvents(events)
                        if let state {
                            let isInningChange = state.out == 0 && (gameState?.out ?? 0) >= 1 && state.status == .live
                            if isInningChange {
                                try? await Task.sleep(nanoseconds: 1_500_000_000)
                            }
                            gameState = state
                            loadError = nil
                        }
                    case .pong:
                        break
                    }
                }
            }

            if Task.isCancelled { break }
            let delayNs = reconnectDelays[min(reconnectAttempt, reconnectDelays.count - 1)]
            reconnectAttempt = min(reconnectAttempt + 1, reconnectDelays.count - 1)
            try? await Task.sleep(nanoseconds: delayNs)
        }
    }

    private func mergeEvents(_ incoming: [LiveEvent]) {
        guard !incoming.isEmpty else { return }
        let sorted = incoming.sorted { $0.cursor > $1.cursor }
        let merged = (sorted + events)
            .reduce(into: [Int64: LiveEvent]()) { dict, event in
                if dict[event.cursor] == nil { dict[event.cursor] = event }
            }
            .values
            .sorted { $0.cursor > $1.cursor }
        events = Array(merged.prefix(80))
    }
}

// MARK: - ScoreboardCard
private struct ScoreboardCard: View {
    let state: LiveGameState
    let events: [LiveEvent]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(statusLine(status: state.status, inning: state.inning))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                Spacer()
                Text("GAME \(state.gameId)")
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.8))
            }

            Spacer().frame(height: 14)
            LiveTeamScoreRow(teamName: state.awayTeamId.teamName, team: state.awayTeamId, score: state.awayScore)
            Spacer().frame(height: 10)
            LiveTeamScoreRow(teamName: state.homeTeamId.teamName, team: state.homeTeamId, score: state.homeScore)

            Spacer().frame(height: 12)
            HStack(spacing: 8) {
                CountChip(label: "B", value: state.ball)
                CountChip(label: "S", value: state.strike)
                CountChip(label: "O", value: state.out)
                Spacer()
                Text("주자 \(baseText(state))")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.9))
            }

            Spacer().frame(height: 10)
            Text("투수 \(state.pitcher.isEmpty ? "-" : state.pitcher) · 타자 \(state.batter.isEmpty ? "-" : state.batter)")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.9))

            if let latestEvent = events.first {
                Spacer().frame(height: 8)
                Text("최근 이벤트 \(latestEvent.type): \(latestEvent.description.isEmpty ? "-" : latestEvent.description)")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.85))
                    .lineLimit(1)
            }
        }
        .padding(14)
        .background(Color.white.opacity(0.12))
        .cornerRadius(14)
    }

    private func statusLine(status: GameStatus, inning: String) -> String {
        switch status {
        case .live: return "경기 중 · \(inning.isEmpty ? "진행 중" : inning)"
        case .scheduled: return "경기 전"
        case .finished: return "경기 종료"
        case .canceled: return "우천 취소"
        case .postponed: return "경기 연기"
        }
    }

    private func baseText(_ state: LiveGameState) -> String {
        var bases: [String] = []
        if state.baseFirst { bases.append("1") }
        if state.baseSecond { bases.append("2") }
        if state.baseThird { bases.append("3") }
        return bases.isEmpty ? "없음" : bases.joined(separator: ",")
    }
}

private struct LiveTeamScoreRow: View {
    let teamName: String
    let team: Team
    let score: Int

    var body: some View {
        HStack {
            HStack(spacing: 10) {
                TeamLogo(team: team, size: 52)
                Text(teamName)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
            }
            Spacer()
            Text("\(score)")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)
        }
    }
}

private struct CountChip: View {
    let label: String
    let value: Int

    var body: some View {
        Text("\(label) \(value)")
            .font(.system(size: 12))
            .foregroundColor(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(AppColors.gray900.opacity(0.55))
            .cornerRadius(999)
    }
}

// MARK: - EventCard
private struct EventCard: View {
    let event: LiveEvent

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(event.type)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(eventColor(event.type))
                Spacer()
                Text(event.time)
                    .font(.system(size: 12))
                    .foregroundColor(AppColors.gray400)
            }
            if !event.description.isEmpty {
                Text(event.description)
                    .font(.system(size: 13))
                    .foregroundColor(.white)
                    .padding(.top, 8)
            }
        }
        .padding(14)
        .background(AppColors.gray900)
        .cornerRadius(12)
        .padding(.vertical, 5)
    }

    private func eventColor(_ type: String) -> Color {
        switch type.uppercased() {
        case "HOMERUN", "SCORE", "SAC_FLY_SCORE": return AppColors.yellow500
        case "HIT", "STEAL", "WALK": return AppColors.green500
        case "DOUBLE_PLAY", "TRIPLE_PLAY": return AppColors.orange500
        case "OUT", "STRIKE": return AppColors.red500
        case "BALL": return AppColors.gray400
        default: return AppColors.gray500
        }
    }
}
