import SwiftUI

struct LiveGameScreen: View {
    let activeTheme: ThemeData?
    let gameId: String?
    let syncedGameId: String?
    let onSetSyncedGame: (String?) -> Void
    let onBack: () -> Void

    @State private var gameState: LiveGameState?
    @State private var events: [LiveEvent] = []
    @State private var loadError: String?
    @State private var selectedInningNumber: Int? = nil
    @State private var hasManualInningSelection: Bool = false

    private var filteredEvents: [LiveEvent] {
        guard let n = selectedInningNumber else { return events }
        return events.filter { event in
            guard let inn = event.inning else { return false }
            return inningNumber(inn) == n
        }
    }

    private var filteredAtBats: [AtBatGroup] {
        AtBatGroup.group(filteredEvents)
    }

    private var currentLineup: FieldLineup? {
        #if DEBUG
        if gameId == "debug-watch-sync-test" { return DebugDummyLiveGame.lineup }
        #endif
        if let state = gameState, let mapped = FieldLineup.from(state: state) { return mapped }
        return nil
    }

    var body: some View {
        VStack(spacing: 0) {
            DetailTopBar(
                state: gameState,
                gameId: gameId,
                syncedGameId: syncedGameId,
                onSetSyncedGame: onSetSyncedGame,
                onBack: onBack
            )

            if gameId == nil || gameId?.isEmpty == true || gameState == nil {
                VStack {
                    Spacer()
                    Text(emptyStateText)
                        .font(AppFont.bodyMedium)
                        .foregroundColor(AppColors.gray400)
                        .multilineTextAlignment(.center)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .padding(AppSpacing.xxl)
            } else if let state = gameState {
                ScrollView {
                    LazyVStack(spacing: AppSpacing.md) {
                        ScoreboardCard(state: state, latestEvent: events.first)
                        BaseballFieldCard(state: state, latestEvent: events.first, lineup: currentLineup)
                        InningTabs(
                            state: state,
                            selectedInningNumber: selectedInningNumber,
                            onSelect: { n in
                                selectedInningNumber = n
                                hasManualInningSelection = true
                            }
                        )
                        CurrentMatchupCard(state: state, latestEvent: events.first)

                        Text("실시간 중계")
                            .font(AppFont.h5Bold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, AppSpacing.sm)

                        if filteredEvents.isEmpty {
                            EmptyInningEventCard(hasAnyEvents: !events.isEmpty)
                        } else {
                            let groups = filteredAtBats
                            ForEach(Array(groups.enumerated()), id: \.element.id) { index, group in
                                if index == 0 || sectionKey(for: groups[index - 1]) != sectionKey(for: group) {
                                    AtBatSectionHeader(title: sectionTitle(for: group, state: state))
                                }
                                AtBatCard(group: group)
                            }
                        }

                        Spacer().frame(height: AppSpacing.bottomSafeSpacer)
                    }
                    .padding(.horizontal, AppSpacing.lg)
                }
            }
        }
        .background(AppColors.gray950)
        .task(id: gameId) {
            await startLiveStream()
        }
        .onChange(of: gameState?.inning) { _, newValue in
            guard !hasManualInningSelection, let inning = newValue else { return }
            let n = inningNumber(inning)
            if n > 0 { selectedInningNumber = n }
        }
    }

    private var emptyStateText: String {
        if gameId == nil || gameId?.isEmpty == true {
            return "선택한 경기가 없습니다."
        }
        return loadError ?? "경기 데이터를 불러오는 중..."
    }

    // MARK: - Live Stream
    private func startLiveStream() async {
        guard let gameId = gameId, !gameId.isEmpty else { return }

        #if DEBUG
        if gameId == "debug-watch-sync-test" {
            gameState = DebugDummyLiveGame.state
            events = DebugDummyLiveGame.events
            loadError = nil
            return
        }
        #endif

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

/// 야구장 9명 수비 라인업 + 루상 주자 이름. 백엔드 라인업 API 도입 전까지는 DEBUG 더미에서만 채움.
struct FieldLineup {
    let leftFielder: String?
    let centerFielder: String?
    let rightFielder: String?
    let shortstop: String?
    let secondBaseman: String?
    let thirdBaseman: String?
    let firstBaseman: String?
    let catcher: String?
    let firstRunner: String?
    let secondRunner: String?
    let thirdRunner: String?
}

extension FieldLineup {
    /// 백엔드 응답의 라인업을 수비팀 기준으로 매핑.
    /// 이닝 "초"=홈수비, "말"=어웨이수비. 라인업 비어있거나 이닝 파싱 실패 시 nil.
    static func from(state: LiveGameState) -> FieldLineup? {
        let defending: [LineupSlot]
        switch defendingTeamSide(forInning: state.inning) {
        case .home: defending = state.homeLineup
        case .away: defending = state.awayLineup
        case .none: return nil
        }
        if defending.isEmpty { return nil }
        var slots: [String: String] = [:]
        for slot in defending where slot.isActive {
            guard let posName = slot.positionName, !posName.isEmpty else { continue }
            if slots[posName] == nil { slots[posName] = slot.playerName }
        }
        return FieldLineup(
            leftFielder: slots["좌익수"],
            centerFielder: slots["중견수"],
            rightFielder: slots["우익수"],
            shortstop: slots["유격수"],
            secondBaseman: slots["2루수"],
            thirdBaseman: slots["3루수"],
            firstBaseman: slots["1루수"],
            catcher: slots["포수"],
            firstRunner: nil,
            secondRunner: nil,
            thirdRunner: nil
        )
    }
}

private enum DefendingTeamSide { case home, away }

private func defendingTeamSide(forInning inning: String) -> DefendingTeamSide? {
    if inning.contains("초") { return .home }
    if inning.contains("말") { return .away }
    return nil
}

#if DEBUG
private enum DebugDummyLiveGame {
    static let lineup = FieldLineup(
        leftFielder: "김재환",
        centerFielder: "박해민",
        rightFielder: "문보경",
        shortstop: "오지환",
        secondBaseman: "신민재",
        thirdBaseman: "허경민",
        firstBaseman: "오스틴",
        catcher: "박동원",
        firstRunner: "문성주",
        secondRunner: "오스틴",
        thirdRunner: "김현수"
    )

    static let state = LiveGameState(
        gameId: "debug-watch-sync-test",
        homeTeam: "두산",
        awayTeam: "LG",
        homeTeamId: .doosan,
        awayTeamId: .lg,
        homeScore: 3,
        awayScore: 5,
        inning: "7회초",
        status: .live,
        ball: 2,
        strike: 1,
        out: 1,
        baseFirst: true,
        baseSecond: true,
        baseThird: true,
        pitcher: "곽빈",
        batter: "오스틴",
        pitcherPitchCount: 87,
        lastEventType: "HIT",
        homeLineup: [],
        awayLineup: []
    )

    // 타석 그룹화 시연용 atBatId/seqno 부여:
    //   - 오스틴 7회초 타석(relayNo 003) STRIKE→BALL→HIT 3구 → 1개 카드
    //   - 박해민 7회초 타석(relayNo 002) 삼진 아웃 → 1개 카드
    //   - 신민재 6회말 득점(relayNo 001) → 1개 카드
    static let events: [LiveEvent] = [
        LiveEvent(cursor: 5, id: "dbg-5", type: "HIT", description: "오스틴 우전 안타로 1루 진루", time: "19:42", pitcher: "곽빈", batter: "오스틴", inning: "7회초", atBatId: "07-003", seqno: 3),
        LiveEvent(cursor: 4, id: "dbg-4", type: "BALL", description: "곽빈 → 오스틴 볼", time: "19:41", pitcher: "곽빈", batter: "오스틴", inning: "7회초", atBatId: "07-003", seqno: 2),
        LiveEvent(cursor: 3, id: "dbg-3", type: "STRIKE", description: "곽빈 → 오스틴 스트라이크", time: "19:40", pitcher: "곽빈", batter: "오스틴", inning: "7회초", atBatId: "07-003", seqno: 1),
        LiveEvent(cursor: 2, id: "dbg-2", type: "OUT", description: "박해민 삼진 아웃", time: "19:37", pitcher: "곽빈", batter: "박해민", inning: "7회초", atBatId: "07-002", seqno: 1),
        LiveEvent(cursor: 1, id: "dbg-1", type: "SCORE", description: "신민재 득점", time: "19:34", pitcher: "곽빈", batter: "오지환", inning: "6회말", atBatId: "06-001", seqno: 1),
    ]
}
#endif

private struct DetailTopBar: View {
    let state: LiveGameState?
    let gameId: String?
    let syncedGameId: String?
    let onSetSyncedGame: (String?) -> Void
    let onBack: () -> Void

    var body: some View {
        ZStack {
            HStack(spacing: AppSpacing.xs) {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(AppFont.h4)
                        .foregroundColor(.white)
                        .frame(width: AppSpacing.buttonHeight, height: AppSpacing.buttonHeight)
                }

                Spacer()

                WatchSyncBadge(
                    gameId: gameId,
                    syncedGameId: syncedGameId,
                    onSetSyncedGame: onSetSyncedGame
                )

                if state?.status == .live {
                    LiveBadge()
                }
            }

            Text("경기 상세")
                .font(AppFont.h5Bold)
                .foregroundColor(.white)
        }
        .padding(.horizontal, AppSpacing.sm)
        .padding(.vertical, AppSpacing.sm)
    }
}

private struct LiveBadge: View {
    var body: some View {
        HStack(spacing: AppSpacing.xs) {
            Circle()
                .fill(AppColors.red500)
                .frame(width: AppSpacing.sm, height: AppSpacing.sm)
            Text("LIVE")
                .font(AppFont.microBold)
                .foregroundColor(AppColors.red500)
        }
        .padding(.horizontal, AppSpacing.md)
        .padding(.vertical, AppSpacing.xs)
        .background(Capsule().fill(AppColors.red500.opacity(0.16)))
        .overlay(Capsule().stroke(AppColors.red500.opacity(0.44), lineWidth: 1))
    }
}

private struct ScoreboardCard: View {
    let state: LiveGameState
    let latestEvent: LiveEvent?
    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(currentAttackLabel(state))
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.yellow400)
                .frame(maxWidth: .infinity, alignment: .center)

            Spacer().frame(height: AppSpacing.md)

            HStack(alignment: .top, spacing: AppSpacing.sm) {
                ScoreTeamBlock(
                    team: state.awayTeamId,
                    teamName: state.awayTeamId.teamName,
                    score: state.awayScore,
                    showFavorite: teamTheme.team == state.awayTeamId && teamTheme.team != .none
                )

                ScoreStateBlock(state: state, latestEvent: latestEvent)
                    .frame(width: 118)

                ScoreTeamBlock(
                    team: state.homeTeamId,
                    teamName: state.homeTeamId.teamName,
                    score: state.homeScore,
                    showFavorite: teamTheme.team == state.homeTeamId && teamTheme.team != .none
                )
            }
        }
        .padding(AppSpacing.lg)
        .background(
            LinearGradient(
                colors: [
                    AppColors.gray950,
                    AppColors.gray900,
                    AppEventColors.color(for: state.lastEventType ?? "").opacity(0.12)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(AppRadius.lg)
        .overlay(RoundedRectangle(cornerRadius: AppRadius.lg).stroke(AppColors.gray800, lineWidth: 1))
    }
}

private struct ScoreTeamBlock: View {
    let team: Team
    let teamName: String
    let score: Int
    let showFavorite: Bool

    var body: some View {
        VStack(alignment: .center, spacing: AppSpacing.xs) {
            TeamLogo(team: team, size: 60)
            Text(teamName)
                .font(AppFont.h4Bold)
                .foregroundColor(.white)
                .lineLimit(1)
            Text("\(score)")
                .font(AppFont.h1)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .center)
            if showFavorite {
                FavoriteTeamBadge()
                    .padding(.top, AppSpacing.xs)
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }
}

private struct ScoreStateBlock: View {
    let state: LiveGameState
    let latestEvent: LiveEvent?

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            ScoreboardBaseDiamond(state: state)
                .frame(width: 86, height: 78)
            Spacer().frame(height: AppSpacing.sm)
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                CountDots(label: "B", value: state.ball, max: 3, activeColor: AppColors.green400)
                CountDots(label: "S", value: state.strike, max: 2, activeColor: AppColors.yellow400)
                CountDots(label: "O", value: state.out, max: 2, activeColor: AppColors.red500)
            }
            Spacer().frame(height: AppSpacing.md)
            Text("P \(displayPitcher(state: state, event: latestEvent))  |  B \(displayBatter(state: state, event: latestEvent))")
                .font(AppFont.microBold)
                .foregroundColor(AppColors.gray400)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .center)
        }
    }
}

private struct ScoreboardBaseDiamond: View {
    let state: LiveGameState

    var body: some View {
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let second = CGPoint(x: center.x, y: center.y - 18)
            let first = CGPoint(x: center.x + 18, y: center.y)
            let third = CGPoint(x: center.x - 18, y: center.y)
            let home = CGPoint(x: center.x, y: center.y + 18)

            drawBase(context: context, center: second, occupied: state.baseSecond)
            drawBase(context: context, center: first, occupied: state.baseFirst)
            drawBase(context: context, center: third, occupied: state.baseThird)
            drawBase(context: context, center: home, occupied: false)
        }
    }

    private func drawBase(context: GraphicsContext, center: CGPoint, occupied: Bool) {
        let baseSize: CGFloat = 34
        var path = Path()
        path.move(to: CGPoint(x: center.x, y: center.y - baseSize / 2))
        path.addLine(to: CGPoint(x: center.x + baseSize / 2, y: center.y))
        path.addLine(to: CGPoint(x: center.x, y: center.y + baseSize / 2))
        path.addLine(to: CGPoint(x: center.x - baseSize / 2, y: center.y))
        path.closeSubpath()

        context.fill(path, with: .color(occupied ? AppColors.yellow500 : AppColors.gray800))
        context.stroke(path, with: .color(Color.black.opacity(0.36)), lineWidth: 1)
    }
}

private struct FavoriteTeamBadge: View {
    var body: some View {
        HStack(spacing: AppSpacing.xs) {
            Text("응원팀")
                .font(AppFont.microBold)
            Image(systemName: "star.fill")
                .font(AppFont.microBold)
        }
        .foregroundColor(AppColors.yellow500)
        .padding(.horizontal, AppSpacing.sm)
        .padding(.vertical, AppSpacing.xs)
        .overlay(Capsule().stroke(AppColors.yellow500, lineWidth: 1))
    }
}

private struct BaseballFieldCard: View {
    let state: LiveGameState
    let latestEvent: LiveEvent?
    let lineup: FieldLineup?

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                Image("BaseballFieldBackground")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .clipped()

                lineupLabel(lineup?.leftFielder, at: FieldPositions.leftField, in: geometry.size)
                lineupLabel(lineup?.centerFielder, at: FieldPositions.centerField, in: geometry.size)
                lineupLabel(lineup?.rightFielder, at: FieldPositions.rightField, in: geometry.size)
                lineupLabel(lineup?.shortstop, at: FieldPositions.shortstop, in: geometry.size)
                lineupLabel(lineup?.secondBaseman, at: FieldPositions.secondBaseman, in: geometry.size)
                lineupLabel(lineup?.thirdBaseman, at: FieldPositions.thirdBaseman, in: geometry.size)
                lineupLabel(lineup?.firstBaseman, at: FieldPositions.firstBaseman, in: geometry.size)
                lineupLabel(lineup?.catcher, at: FieldPositions.catcher, in: geometry.size)

                let pitcherName = displayPitcher(state: state, event: latestEvent, placeholder: "")
                if !pitcherName.isEmpty {
                    PositionPill(text: pitcherName, highlighted: false)
                        .atFieldPosition(FieldPositions.pitcher, in: geometry.size)
                }

                let batterName = displayBatter(state: state, event: latestEvent, placeholder: "")
                if !batterName.isEmpty {
                    PositionPill(text: batterName, highlighted: true)
                        .atFieldPosition(FieldPositions.batter, in: geometry.size)
                }

                if state.baseFirst {
                    BaseRunnerMarker(name: lineup?.firstRunner)
                        .atFieldPosition(FieldPositions.firstBase, in: geometry.size)
                }
                if state.baseSecond {
                    BaseRunnerMarker(name: lineup?.secondRunner)
                        .atFieldPosition(FieldPositions.secondBase, in: geometry.size)
                }
                if state.baseThird {
                    BaseRunnerMarker(name: lineup?.thirdRunner)
                        .atFieldPosition(FieldPositions.thirdBase, in: geometry.size)
                }
            }
        }
        .aspectRatio(4.0 / 3.0, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .cornerRadius(AppRadius.lg)
        .clipped()
    }

    @ViewBuilder
    private func lineupLabel(_ name: String?, at point: CGPoint, in size: CGSize) -> some View {
        if let name, !name.isEmpty {
            PositionPill(text: name, highlighted: false)
                .atFieldPosition(point, in: size)
        }
    }
}

/// 야구장 배경 이미지 위의 정규화 좌표(0.0~1.0). 양 플랫폼 공통 값.
/// 외야 3 + 내야 4 + 투수 + 포수 + 타자 10개 포지션. P/B 만 1단계에서 사용.
private enum FieldPositions {
    static let leftField   = CGPoint(x: 0.17, y: 0.17)
    static let centerField = CGPoint(x: 0.50, y: 0.06)
    static let rightField  = CGPoint(x: 0.83, y: 0.17)
    static let shortstop   = CGPoint(x: 0.35, y: 0.28)
    static let secondBaseman = CGPoint(x: 0.65, y: 0.28)
    static let thirdBaseman  = CGPoint(x: 0.21, y: 0.40)
    static let firstBaseman  = CGPoint(x: 0.79, y: 0.40)
    static let pitcher       = CGPoint(x: 0.50, y: 0.52)
    static let catcher       = CGPoint(x: 0.50, y: 0.91)
    static let batter        = CGPoint(x: 0.45, y: 0.83)

    static let firstBase  = CGPoint(x: 0.74, y: 0.50)
    static let secondBase = CGPoint(x: 0.50, y: 0.21)
    static let thirdBase  = CGPoint(x: 0.26, y: 0.51)
}

private extension View {
    func atFieldPosition(_ normalized: CGPoint, in size: CGSize) -> some View {
        position(x: normalized.x * size.width, y: normalized.y * size.height)
    }
}

private struct BaseRunnerMarker: View {
    let name: String?

    var body: some View {
        if let name, !name.isEmpty {
            PositionPill(text: name, highlighted: true)
        }
    }
}

private struct PositionPill: View {
    let text: String
    let highlighted: Bool

    var body: some View {
        Text(text)
            .font(AppFont.microBold)
            .foregroundColor(highlighted ? AppColors.gray950 : .white)
            .lineLimit(1)
            .padding(.horizontal, AppSpacing.md)
            .padding(.vertical, AppSpacing.xs)
            .background(Capsule().fill(highlighted ? AppColors.yellow500 : Color.black.opacity(0.46)))
    }
}

private struct InningTabs: View {
    let state: LiveGameState
    let selectedInningNumber: Int?
    let onSelect: (Int?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppSpacing.sm) {
                ForEach(tabs, id: \.self) { tab in
                    let isScore = tab == "득점"
                    let tabNumber: Int? = isScore ? nil : Int(tab.replacingOccurrences(of: "회", with: ""))
                    let selected: Bool = isScore ? false : (tabNumber == selectedInningNumber)
                    Button {
                        if !isScore, let n = tabNumber { onSelect(n) }
                    } label: {
                        Text(tab)
                            .font(AppFont.captionBold)
                            .foregroundColor(selected ? AppColors.gray950 : AppColors.gray400)
                            .padding(.horizontal, AppSpacing.md)
                            .padding(.vertical, AppSpacing.sm)
                            .background(Capsule().fill(selected ? AppColors.yellow500 : AppColors.gray900))
                            .overlay(Capsule().stroke(selected ? AppColors.yellow500 : AppColors.gray800, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var tabs: [String] {
        ["득점"] + (1...9).map { "\($0)회" }
    }
}

private struct EmptyInningEventCard: View {
    let hasAnyEvents: Bool

    var body: some View {
        Text("해당 회 이벤트가 없습니다")
            .font(AppFont.bodyMedium)
            .foregroundColor(AppColors.gray400)
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.xxxl)
            .background(AppColors.gray900)
            .cornerRadius(AppRadius.lg)
    }
}

private struct CurrentMatchupCard: View {
    let state: LiveGameState
    let latestEvent: LiveEvent?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text("현재 승부")
                        .font(AppFont.captionBold)
                        .foregroundColor(AppColors.yellow400)
                    Text("\(displayPitcher(state: state, event: latestEvent)) vs \(displayBatter(state: state, event: latestEvent))")
                        .font(AppFont.bodyLgBold)
                        .foregroundColor(.white)
                        .lineLimit(1)
                }

                Spacer()

                Text(state.pitcherPitchCount.map { "투구수 \($0)" } ?? "투구수 -")
                    .font(AppFont.captionBold)
                    .foregroundColor(AppColors.gray100)
                    .padding(.horizontal, AppSpacing.md)
                    .padding(.vertical, AppSpacing.xs)
                    .background(Capsule().fill(AppColors.gray800))
            }

            if let latestEvent {
                Spacer().frame(height: AppSpacing.md)
                EventSummaryLine(event: latestEvent)
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.lg)
    }
}

private struct EventSummaryLine: View {
    let event: LiveEvent

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            EventTypePill(type: event.type)
            Text(event.description.isEmpty ? "최근 이벤트 없음" : event.description)
                .font(AppFont.caption)
                .foregroundColor(AppColors.gray100)
                .lineLimit(1)
        }
    }
}

private struct WatchSyncBadge: View {
    let gameId: String?
    let syncedGameId: String?
    let onSetSyncedGame: (String?) -> Void

    @State private var visualOn: Bool = false
    @State private var ignoreNextChange: Bool = false
    @State private var showEnableAlert: Bool = false
    @State private var showDisableAlert: Bool = false
    @State private var isAdLoading: Bool = false

    private var isSyncedToCurrent: Bool {
        guard let gameId, !gameId.isEmpty else { return false }
        return syncedGameId == gameId
    }

    private var isInteractive: Bool {
        guard let gameId else { return false }
        return !gameId.isEmpty
    }

    private var accent: Color {
        if isSyncedToCurrent { return AppColors.green500 }
        if syncedGameId?.isEmpty ?? true { return AppColors.gray500 }
        return AppColors.yellow400
    }

    var body: some View {
        HStack(spacing: 0) {
            Image(systemName: "applewatch")
                .font(AppFont.microBold)
                .foregroundColor(accent)

            if isAdLoading {
                ProgressView()
                    .scaleEffect(0.6)
                    .padding(.leading, 2)
                    .padding(.trailing, AppSpacing.xs)
            } else if isInteractive {
                Toggle("", isOn: $visualOn)
                    .labelsHidden()
                    .tint(AppColors.green500)
                    .scaleEffect(0.6)
                Text(visualOn ? "ON" : "OFF")
                    .font(AppFont.microBold)
                    .foregroundColor(accent)
                    .padding(.trailing, 2)
            } else {
                Circle()
                    .fill(accent)
                    .frame(width: AppSpacing.sm, height: AppSpacing.sm)
                    .padding(.leading, AppSpacing.xs)
                    .padding(.trailing, AppSpacing.xs)
            }
        }
        .padding(.leading, AppSpacing.sm)
        .onAppear { visualOn = isSyncedToCurrent }
        .onChange(of: syncedGameId) { _, _ in
            ignoreNextChange = true
            visualOn = isSyncedToCurrent
        }
        .onChange(of: visualOn) { _, newValue in
            if ignoreNextChange {
                ignoreNextChange = false
                return
            }
            if newValue {
                showEnableAlert = true
            } else {
                showDisableAlert = true
            }
        }
        .alert("워치로 보시겠습니까?", isPresented: $showEnableAlert) {
            Button("확인") { handleEnableConfirm() }
            Button("취소", role: .cancel) { revertVisual() }
        } message: {
            Text("광고 관람 후 동기화됩니다.")
        }
        .alert("워치 동기화를 끄시겠습니까?", isPresented: $showDisableAlert) {
            Button("확인") { handleDisableConfirm() }
            Button("취소", role: .cancel) { revertVisual() }
        }
    }

    private func handleEnableConfirm() {
        guard let gameId, !gameId.isEmpty else { return }
        if WatchSyncAdLedger.hasViewed(gameId: gameId) {
            onSetSyncedGame(gameId)
            return
        }
        isAdLoading = true
        RewardedAdManager.shared.loadAndShowAd(
            adUnitID: RewardedAdManager.watchSyncAdUnitID
        ) { rewardEarned in
            isAdLoading = false
            if rewardEarned {
                WatchSyncAdLedger.markViewed(gameId: gameId)
            }
            onSetSyncedGame(gameId)
        }
    }

    private func handleDisableConfirm() {
        onSetSyncedGame(nil)
    }

    private func revertVisual() {
        ignoreNextChange = true
        visualOn = isSyncedToCurrent
    }
}

private struct CountDots: View {
    let label: String
    let value: Int
    let max: Int
    let activeColor: Color

    var body: some View {
        HStack(spacing: AppSpacing.xxs) {
            Text(label)
                .font(AppFont.microBold)
                .foregroundColor(AppColors.gray400)
                .padding(.trailing, AppSpacing.xs)
            ForEach(0..<max, id: \.self) { index in
                Circle()
                    .fill(index < value ? activeColor : AppColors.gray700)
                    .frame(width: AppSpacing.sm, height: AppSpacing.sm)
            }
        }
    }
}

private struct RunnerSummary: View {
    let state: LiveGameState

    var body: some View {
        HStack(spacing: AppSpacing.xxs) {
            MiniBase(occupied: state.baseThird)
            MiniBase(occupied: state.baseSecond)
            MiniBase(occupied: state.baseFirst)
            Spacer().frame(width: AppSpacing.xs)
            Text(baseText(state))
                .font(AppFont.microBold)
                .foregroundColor(AppColors.gray100)
        }
        .padding(.horizontal, AppSpacing.sm)
        .padding(.vertical, AppSpacing.xs)
        .background(Capsule().fill(AppColors.gray800))
    }
}

private struct MiniBase: View {
    let occupied: Bool

    var body: some View {
        Circle()
            .fill(occupied ? AppColors.yellow500 : AppColors.gray600)
            .frame(width: AppSpacing.sm, height: AppSpacing.sm)
    }
}

private struct EmptyEventCard: View {
    var body: some View {
        Text("아직 이벤트가 없습니다.")
            .font(AppFont.bodyMedium)
            .foregroundColor(AppColors.gray500)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(AppSpacing.lg)
            .background(AppColors.gray900)
            .cornerRadius(AppRadius.lg)
    }
}

private struct EventCard: View {
    let event: LiveEvent

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                HStack(spacing: AppSpacing.sm) {
                    EventTypePill(type: event.type)
                    if !matchupText.isEmpty {
                        Text(matchupText)
                            .font(AppFont.micro)
                            .foregroundColor(AppColors.gray400)
                            .lineLimit(1)
                    }
                }
                Spacer()
                Text(event.time)
                    .font(AppFont.micro)
                    .foregroundColor(AppColors.gray400)
            }

            if !event.description.isEmpty {
                Text(event.description)
                    .font(AppFont.caption)
                    .foregroundColor(.white)
                    .padding(.top, AppSpacing.sm)
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }

    private var matchupText: String {
        [event.pitcher, event.batter]
            .compactMap { value in
                guard let value, !value.isEmpty else { return nil }
                return value
            }
            .joined(separator: " → ")
    }
}

private struct EventTypePill: View {
    let type: String

    var body: some View {
        Text(eventLabel(type))
            .font(AppFont.microBold)
            .foregroundColor(AppEventColors.color(for: type))
            .padding(.horizontal, AppSpacing.sm)
            .padding(.vertical, AppSpacing.xs)
            .background(Capsule().fill(AppEventColors.color(for: type).opacity(0.14)))
    }
}

// MARK: - At-Bat Card (네이버 릴레이 스타일 타석 단위 카드)
private struct AtBatCard: View {
    let group: AtBatGroup

    private var highlighted: Bool {
        guard let outcomeType = group.outcome?.type else { return false }
        return EventFilterGate.isAllowed(eventType: outcomeType)
    }

    private var headerText: String {
        if let batter = group.batter, !batter.isEmpty {
            return batter
        }
        // 타자 정보 폴백 — 단일 그룹(atBatId nil) 의 경우 대표 이벤트 description 일부.
        return group.outcome?.description ?? group.pitches.last?.description ?? "타석"
    }

    private var subHeaderText: String {
        var parts: [String] = []
        if let inning = group.inning, !inning.isEmpty { parts.append(inning) }
        if let pitcher = group.pitcher, !pitcher.isEmpty { parts.append("vs \(pitcher)") }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            // Header: 타자(또는 폴백) + 시간
            HStack(alignment: .firstTextBaseline) {
                Text(headerText)
                    .font(AppFont.bodyBold)
                    .foregroundColor(.white)
                    .lineLimit(1)
                Spacer()
                Text(group.time)
                    .font(AppFont.micro)
                    .foregroundColor(AppColors.gray400)
            }

            if !subHeaderText.isEmpty {
                Text(subHeaderText)
                    .font(AppFont.micro)
                    .foregroundColor(AppColors.gray500)
            }

            // 투구·진행 칩 시퀀스 (BALL/STRIKE 등). outcome 자체도 마지막 칩으로 포함.
            if group.pitches.count > 1 {
                FlowingPitchChips(types: group.pitches.map { $0.type })
            }

            // Footer: 최종 결과 텍스트
            if let outcome = group.outcome, !outcome.description.isEmpty {
                HStack(alignment: .top, spacing: AppSpacing.sm) {
                    EventTypePill(type: outcome.type)
                    Text(outcome.description)
                        .font(AppFont.caption)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else if group.pitches.count == 1,
                      let only = group.pitches.first,
                      !only.description.isEmpty {
                // 폴백 단일 그룹 — 기존 EventCard 와 유사한 정보 밀도 유지.
                HStack(alignment: .top, spacing: AppSpacing.sm) {
                    EventTypePill(type: only.type)
                    Text(only.description)
                        .font(AppFont.caption)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.md)
                .stroke(highlighted ? AppColors.yellow500 : Color.clear, lineWidth: highlighted ? 1.5 : 0)
        )
        .cornerRadius(AppRadius.md)
    }
}

/// 가로로 흘러가는 투구 칩 모음. 4개 초과 시 자동 줄바꿈.
private struct FlowingPitchChips: View {
    let types: [String]

    var body: some View {
        // LazyVGrid 대신 단순 HStack 으로도 보통 4~6 칩이면 한 줄에 들어감.
        // 더 많아질 때만 줄바꿈이 필요하므로 가벼운 wrapping HStack 흉내.
        let rows = Self.chunk(types, perRow: 6)
        VStack(alignment: .leading, spacing: AppSpacing.xs) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: AppSpacing.xs) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, type in
                        PitchChip(type: type)
                    }
                    Spacer(minLength: 0)
                }
            }
        }
    }

    private static func chunk(_ array: [String], perRow: Int) -> [[String]] {
        guard !array.isEmpty else { return [] }
        return stride(from: 0, to: array.count, by: perRow).map {
            Array(array[$0..<min($0 + perRow, array.count)])
        }
    }
}

private struct PitchChip: View {
    let type: String

    var body: some View {
        Text(pitchShortLabel(type))
            .font(AppFont.microBold)
            .foregroundColor(AppEventColors.color(for: type))
            .frame(minWidth: 24)
            .padding(.horizontal, AppSpacing.xs)
            .padding(.vertical, 2)
            .background(Capsule().fill(AppEventColors.color(for: type).opacity(0.14)))
            .overlay(Capsule().stroke(AppEventColors.color(for: type).opacity(0.32), lineWidth: 0.5))
    }
}

// MARK: - At-Bat Section Header (이닝·공격팀 단위 구분)
private struct AtBatSectionHeader: View {
    let title: String

    var body: some View {
        Text(title)
            .font(AppFont.captionBold)
            .foregroundColor(AppColors.gray400)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, AppSpacing.sm)
    }
}

/// 두 그룹이 같은 섹션에 속하는지 판정하는 키 — 같은 이닝 문자열이면 같은 섹션.
private func sectionKey(for group: AtBatGroup) -> String {
    group.inning ?? "?"
}

/// "9회초 트윈스 공격" 형태의 섹션 헤더 문자열.
/// 이닝 표기가 비어있으면 폴백으로 "타석"을 쓰고, 공격팀 판별이 안 되면 이닝만 보여준다.
private func sectionTitle(for group: AtBatGroup, state: LiveGameState) -> String {
    guard let inning = group.inning, !inning.isEmpty else { return "타석" }
    let teamName: String
    if inning.contains("초") {
        teamName = state.awayTeamId.teamName
    } else if inning.contains("말") {
        teamName = state.homeTeamId.teamName
    } else {
        teamName = ""
    }
    return teamName.isEmpty ? inning : "\(inning) \(teamName) 공격"
}

private func pitchShortLabel(_ type: String) -> String {
    switch type.uppercased() {
    case "BALL": return "B"
    case "STRIKE": return "S"
    case "HIT": return "안"
    case "HOMERUN": return "홈"
    case "OUT": return "O"
    case "WALK": return "BB"
    case "DOUBLE_PLAY": return "DP"
    case "TRIPLE_PLAY": return "TP"
    case "SCORE", "SAC_FLY_SCORE": return "득"
    case "STEAL": return "도"
    case "TAG_UP_ADVANCE": return "태"
    case "PITCHER_CHANGE": return "교"
    case "HALF_INNING_CHANGE": return "교대"
    default: return "·"
    }
}

private func displayPitcher(state: LiveGameState, event: LiveEvent?, placeholder: String = "-") -> String {
    let direct = state.pitcher.trimmingCharacters(in: .whitespacesAndNewlines)
    if !direct.isEmpty { return direct }
    if let eventName = event?.pitcher?.trimmingCharacters(in: .whitespacesAndNewlines), !eventName.isEmpty {
        return eventName
    }
    return placeholder
}

private func displayBatter(state: LiveGameState, event: LiveEvent?, placeholder: String = "-") -> String {
    let direct = state.batter.trimmingCharacters(in: .whitespacesAndNewlines)
    if !direct.isEmpty { return direct }
    if let eventName = event?.batter?.trimmingCharacters(in: .whitespacesAndNewlines), !eventName.isEmpty {
        return eventName
    }
    return placeholder
}

private func baseText(_ state: LiveGameState) -> String {
    var bases: [String] = []
    if state.baseFirst { bases.append("1") }
    if state.baseSecond { bases.append("2") }
    if state.baseThird { bases.append("3") }
    return bases.isEmpty ? "없음" : bases.joined(separator: ",")
}

private func currentAttackLabel(_ state: LiveGameState) -> String {
    let battingTeam: String
    if state.inning.contains("초") {
        battingTeam = state.awayTeamId.teamName
    } else if state.inning.contains("말") {
        battingTeam = state.homeTeamId.teamName
    } else {
        battingTeam = "공격"
    }
    return "\(state.inning.isEmpty ? "경기 중" : state.inning) · \(battingTeam) 공격"
}

private func inningNumber(_ inning: String) -> Int {
    let pattern = #"(\d+)회"#
    guard let range = inning.range(of: pattern, options: .regularExpression) else { return 1 }
    let matched = String(inning[range]).replacingOccurrences(of: "회", with: "")
    return min(max(Int(matched) ?? 1, 1), 9)
}

private func eventLabel(_ type: String) -> String {
    switch type.uppercased() {
    case "HOMERUN": return "홈런"
    case "SCORE": return "득점"
    case "SAC_FLY_SCORE": return "희생플라이"
    case "TAG_UP_ADVANCE": return "태그업"
    case "HIT": return "안타"
    case "WALK": return "볼넷"
    case "STEAL": return "도루"
    case "OUT": return "아웃"
    case "STRIKE": return "스트라이크"
    case "BALL": return "볼"
    case "DOUBLE_PLAY": return "병살"
    case "TRIPLE_PLAY": return "삼중살"
    case "PITCHER_CHANGE": return "투수교체"
    case "HALF_INNING_CHANGE": return "공수교대"
    default: return type
    }
}

