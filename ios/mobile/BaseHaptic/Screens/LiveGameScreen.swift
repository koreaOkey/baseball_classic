import Foundation
import SwiftUI

struct LiveGameScreen: View {
    let activeTheme: ThemeData?
    let gameId: String?
    let onBack: () -> Void

    @State private var gameState: LiveGameState?
    @State private var events: [LiveEvent] = []
    @State private var loadError: String?
    @State private var selectedInningNumber: Int? = nil
    @State private var hasManualInningSelection: Bool = false
    @State private var isScoreFilterActive: Bool = false
    @State private var loadedInningNumbers: Set<Int> = []
    @State private var loadingInningNumbers: Set<Int> = []
    @State private var isScoreEventsLoaded: Bool = false
    @State private var isScoreEventsLoading: Bool = false
    @AppStorage("team_display_name_style") private var teamDisplayNameStyleRaw = TeamDisplayNameStyle.team.rawValue

    private var teamDisplayNameStyle: TeamDisplayNameStyle {
        TeamDisplayNameStyle.fromString(teamDisplayNameStyleRaw)
    }

    private var filteredEvents: [LiveEvent] {
        if isScoreFilterActive {
            return events.filter { event in
                let t = event.type.uppercased()
                return t == "SCORE" || t == "SAC_FLY_SCORE"
            }
        }
        guard let n = selectedInningNumber else { return events }
        return events.filter { event in
            guard let inn = event.inning else { return false }
            return inningNumber(inn) == n
        }
    }

    private var filteredAtBats: [AtBatGroup] {
        AtBatGroup.group(filteredEvents)
    }

    private var isCurrentEventFilterLoading: Bool {
        if isScoreFilterActive {
            return isScoreEventsLoading
        }
        guard let selectedInningNumber else { return false }
        return loadingInningNumbers.contains(selectedInningNumber)
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
                        BaseballFieldCard(state: state, latestEvent: events.first, recentEvents: events, lineup: currentLineup)
                        InningTabs(
                            state: state,
                            selectedInningNumber: selectedInningNumber,
                            isScoreFilterActive: isScoreFilterActive,
                            onSelectInning: { n in
                                isScoreFilterActive = false
                                selectedInningNumber = n
                                hasManualInningSelection = true
                                Task { await loadInningEvents(n) }
                            },
                            onSelectScore: {
                                isScoreFilterActive = true
                                selectedInningNumber = nil
                                hasManualInningSelection = true
                                Task { await loadScoreEvents() }
                            }
                        )
                        CurrentMatchupCard(state: state, latestEvent: events.first)

                        Text("실시간 중계")
                            .font(AppFont.h5Bold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, AppSpacing.sm)

                        if filteredEvents.isEmpty {
                            EmptyInningEventCard(isLoading: isCurrentEventFilterLoading)
                        } else {
                            let groups = filteredAtBats
                            ForEach(Array(groups.enumerated()), id: \.element.id) { index, group in
                                if index == 0 || sectionKey(for: groups[index - 1]) != sectionKey(for: group) {
                                    AtBatSectionHeader(title: sectionTitle(for: group, state: state, style: teamDisplayNameStyle))
                                }
                                AtBatCard(
                                    group: group,
                                    awayTeamName: state.awayTeamId.displayName(style: teamDisplayNameStyle),
                                    homeTeamName: state.homeTeamId.displayName(style: teamDisplayNameStyle),
                                    highlightScoreOutcome: isScoreFilterActive
                                )
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
            if n > 0 {
                selectedInningNumber = n
                Task { await loadInningEvents(n) }
            }
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
                let n = inningNumber(fetchedState.inning)
                if !hasManualInningSelection, n > 0 {
                    selectedInningNumber = n
                    await loadInningEvents(n)
                    cursor = max(cursor, events.map(\.cursor).max() ?? 0)
                }
                loadError = nil
            } else if gameState == nil {
                loadError = "백엔드 경기 상태를 가져오지 못했습니다."
            }

            if cursor > 0, let fetchedEvents = await repo.fetchGameEvents(gameId: gameId, after: cursor, limit: 200) {
                mergeEvents(fetchedEvents.items)
                if let nextCursor = fetchedEvents.nextCursor {
                    cursor = max(cursor, nextCursor)
                } else {
                    cursor = max(cursor, fetchedEvents.items.map(\.cursor).max() ?? 0)
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
                        cursor = max(cursor, items.map(\.cursor).max() ?? 0)
                    case .state(let state):
                        gameState = state
                        loadError = nil
                    case .update(let state, let events):
                        mergeEvents(events)
                        cursor = max(cursor, events.map(\.cursor).max() ?? 0)
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

    private func loadInningEvents(_ inningNumber: Int) async {
        guard let gameId = gameId, !gameId.isEmpty else { return }
        guard !loadedInningNumbers.contains(inningNumber),
              !loadingInningNumbers.contains(inningNumber) else { return }

        loadingInningNumbers.insert(inningNumber)
        defer { loadingInningNumbers.remove(inningNumber) }

        let fetched = await fetchEventPages(
            gameId: gameId,
            inningNumber: inningNumber,
            scoringOnly: false
        )
        if let fetched {
            mergeEvents(fetched)
            loadedInningNumbers.insert(inningNumber)
            loadError = nil
        } else if filteredEvents.isEmpty {
            loadError = "중계 데이터를 가져오지 못했습니다."
        }
    }

    private func loadScoreEvents() async {
        guard let gameId = gameId, !gameId.isEmpty else { return }
        guard !isScoreEventsLoaded, !isScoreEventsLoading else { return }

        isScoreEventsLoading = true
        defer { isScoreEventsLoading = false }

        let fetched = await fetchEventPages(
            gameId: gameId,
            inningNumber: nil,
            scoringOnly: true
        )
        if let fetched {
            mergeEvents(fetched)
            isScoreEventsLoaded = true
            loadError = nil
        } else if filteredEvents.isEmpty {
            loadError = "중계 데이터를 가져오지 못했습니다."
        }
    }

    private func fetchEventPages(
        gameId: String,
        inningNumber: Int?,
        scoringOnly: Bool
    ) async -> [LiveEvent]? {
        let repo = BackendGamesRepository.shared
        var after: Int64 = 0
        var collected: [LiveEvent] = []

        while !Task.isCancelled {
            guard let page = await repo.fetchGameEvents(
                gameId: gameId,
                after: after,
                limit: 200,
                inningNumber: inningNumber,
                scoringOnly: scoringOnly
            ) else {
                return nil
            }

            collected.append(contentsOf: page.items)
            guard let nextCursor = page.nextCursor, nextCursor > after else { break }
            after = nextCursor
        }

        return collected
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
        events = merged
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
    /// 이닝 "초"=홈수비, "말"=어웨이수비. 라이브 외(SCHEDULED "경기전", FINISHED "경기 종료" 등)
    /// 에서는 1회초가 시작될 예정이므로 home 수비를 가정 — 라인업이 30분 전 노출되는 시점부터
    /// BaseballFieldCard 가 비지 않는다. 선택된 수비팀 라인업이 비어있으면 반대편으로 폴백.
    static func from(state: LiveGameState) -> FieldLineup? {
        let preferHome: Bool = !state.inning.contains("말")
        let primary = preferHome ? state.homeLineup : state.awayLineup
        let fallback = preferHome ? state.awayLineup : state.homeLineup
        let defending: [LineupSlot] = primary.isEmpty ? fallback : primary
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
        leftFielder: "채현우",
        centerFielder: "김성욱",
        rightFielder: "오태곤",
        shortstop: "안상현",
        secondBaseman: "홍대인",
        thirdBaseman: "최윤석",
        firstBaseman: "전의산",
        catcher: "신범수",
        firstRunner: "박해민",
        secondRunner: "홍창기",
        thirdRunner: "신민재"
    )

    static let state = LiveGameState(
        gameId: "debug-watch-sync-test",
        homeTeam: "LG",
        awayTeam: "SSG",
        homeTeamId: .lg,
        awayTeamId: .ssg,
        homeScore: 10,
        awayScore: 1,
        inning: "4회말",
        status: .live,
        ball: 0,
        strike: 0,
        out: 2,
        baseFirst: true,
        baseSecond: true,
        baseThird: true,
        baseFirstRunner: "박해민",
        baseSecondRunner: "홍창기",
        baseThirdRunner: "신민재",
        pitcher: "최용준",
        batter: "오스틴",
        pitcherPitchCount: 18,
        lastEventType: "SCORE",
        homeLineup: [],
        awayLineup: [],
        homeStartingPitcher: "김윤식",
        awayStartingPitcher: "김건우"
    )

    private static let austinRecord: [String: Any] = [
        "name": "오스틴",
        "batOrder": 3,
        "seasonHra": 0.349,
        "todayHra": 0.75,
        "pa": 3,
        "ab": 3,
        "hit": 2,
        "run": 3,
        "rbi": 3,
        "hr": 0,
        "bb": 0,
        "so": 1
    ]
    private static let parkRecord: [String: Any] = [
        "name": "박해민",
        "batOrder": 2,
        "seasonHra": 0.28,
        "todayHra": 0.333,
        "pa": 3,
        "ab": 2,
        "hit": 1,
        "run": 2,
        "rbi": 0,
        "hr": 0,
        "bb": 1,
        "so": 0
    ]
    private static let hongRecord: [String: Any] = [
        "name": "홍창기",
        "batOrder": 1,
        "seasonHra": 0.236,
        "todayHra": 0.333,
        "pa": 3,
        "ab": 3,
        "hit": 1,
        "run": 1,
        "rbi": 1,
        "hr": 0,
        "bb": 0,
        "so": 0
    ]
    // 2026-06-11 SSG 1 : 15 LG, 네이버 relay 4회말 일부.
    // 실제 relayNo/seqno 흐름으로 타석 그룹, 선수별 당일 기록, 주자명, 득점 테두리를 확인한다.
    static let events: [LiveEvent] = [
        LiveEvent(cursor: 13, id: "04-045-0283", type: "SCORE", description: "3루주자 신민재 : 홈인", time: "20:12", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 283, homeScoreAfter: 9, awayScoreAfter: 1, batterRecord: austinRecord, homeWinProbability: 98.7, awayWinProbability: 1.3, wpaByPlate: -0.2),
        LiveEvent(cursor: 12, id: "04-045-0282", type: "SCORE", description: "2루주자 홍창기 : 홈인", time: "20:12", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 282, homeScoreAfter: 8, awayScoreAfter: 1, batterRecord: austinRecord),
        LiveEvent(cursor: 11, id: "04-045-0281", type: "SCORE", description: "1루주자 박해민 : 홈인", time: "20:12", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 281, homeScoreAfter: 7, awayScoreAfter: 1, batterRecord: austinRecord),
        LiveEvent(cursor: 10, id: "04-045-0280", type: "HIT", description: "오스틴 : 좌중간 2루타", time: "20:12", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 280, batterRecord: austinRecord),
        LiveEvent(cursor: 9, id: "04-045-0279", type: "OTHER", description: "5구 타격", time: "20:11", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 279, pitchNum: 5, pitchSpeed: 146, pitchStuff: "직구", ballAfter: 3, strikeAfter: 1, outAfter: 0, batterRecord: austinRecord),
        LiveEvent(cursor: 8, id: "04-045-0278", type: "BALL", description: "4구 볼", time: "20:11", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 278, pitchNum: 4, pitchSpeed: 132, pitchStuff: "체인지업", ballAfter: 3, strikeAfter: 1, outAfter: 0, batterRecord: austinRecord),
        LiveEvent(cursor: 7, id: "04-045-0277", type: "BALL", description: "3구 볼", time: "20:10", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 277, pitchNum: 3, pitchSpeed: 147, pitchStuff: "직구", ballAfter: 2, strikeAfter: 1, outAfter: 0, batterRecord: austinRecord),
        LiveEvent(cursor: 6, id: "04-045-0276", type: "STRIKE", description: "2구 파울", time: "20:10", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 276, pitchNum: 2, pitchSpeed: 133, pitchStuff: "체인지업", ballAfter: 1, strikeAfter: 1, outAfter: 0, batterRecord: austinRecord),
        LiveEvent(cursor: 5, id: "04-045-0275", type: "BALL", description: "1구 볼", time: "20:09", pitcher: "최용준", batter: "오스틴", inning: "4회말", atBatId: "04-045", seqno: 275, pitchNum: 1, pitchSpeed: 146, pitchStuff: "직구", ballAfter: 1, strikeAfter: 0, outAfter: 0, batterRecord: austinRecord),
        LiveEvent(cursor: 4, id: "04-044-0270", type: "HIT", description: "박해민 : 우익수 앞 1루타", time: "20:07", pitcher: "김건우", batter: "박해민", inning: "4회말", atBatId: "04-044", seqno: 270, batterRecord: parkRecord),
        LiveEvent(cursor: 3, id: "04-043-0262", type: "SCORE", description: "2루주자 이주헌 : 홈인", time: "20:05", pitcher: "김건우", batter: "홍창기", inning: "4회말", atBatId: "04-043", seqno: 262, homeScoreAfter: 6, awayScoreAfter: 1, batterRecord: hongRecord),
        LiveEvent(cursor: 2, id: "04-043-0260", type: "HIT", description: "홍창기 : 중견수 앞 1루타", time: "20:04", pitcher: "김건우", batter: "홍창기", inning: "4회말", atBatId: "04-043", seqno: 260, batterRecord: hongRecord),
        LiveEvent(cursor: 1, id: "04-042-0254", type: "WALK", description: "신민재 : 볼넷", time: "20:01", pitcher: "김건우", batter: "신민재", inning: "4회말", atBatId: "04-042", seqno: 254),
    ]
}
#endif

private struct DetailTopBar: View {
    let state: LiveGameState?
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
            }

            HStack(spacing: AppSpacing.sm) {
                if state?.status == .live {
                    LiveBadge()
                }

                Text("경기 상세")
                    .font(AppFont.h5Bold)
                    .foregroundColor(.white)
            }
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
    @AppStorage("team_display_name_style") private var teamDisplayNameStyleRaw = TeamDisplayNameStyle.team.rawValue
    private var teamDisplayNameStyle: TeamDisplayNameStyle {
        TeamDisplayNameStyle.fromString(teamDisplayNameStyleRaw)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(currentAttackLabel(state, style: teamDisplayNameStyle))
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.yellow400)
                .frame(maxWidth: .infinity, alignment: .center)

            Spacer().frame(height: AppSpacing.md)

            HStack(alignment: .top, spacing: AppSpacing.sm) {
                ScoreTeamBlock(
                    team: state.awayTeamId,
                    teamName: state.awayTeamId.displayName(style: teamDisplayNameStyle),
                    score: state.awayScore,
                    showFavorite: teamTheme.team == state.awayTeamId && teamTheme.team != .none
                )

                ScoreStateBlock(state: state, latestEvent: latestEvent)
                    .frame(width: 118)

                ScoreTeamBlock(
                    team: state.homeTeamId,
                    teamName: state.homeTeamId.displayName(style: teamDisplayNameStyle),
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
    let recentEvents: [LiveEvent]
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

                let inferredRunners = FieldRunners.from(state: state, events: recentEvents, lineup: lineup)
                if state.baseFirst {
                    BaseRunnerMarker(name: runnerDisplayName(cleanPlayerName(state.baseFirstRunner) ?? inferredRunners.first))
                        .atFieldPosition(FieldPositions.firstBase, in: geometry.size)
                }
                if state.baseSecond {
                    BaseRunnerMarker(name: runnerDisplayName(cleanPlayerName(state.baseSecondRunner) ?? inferredRunners.second))
                        .atFieldPosition(FieldPositions.secondBase, in: geometry.size)
                }
                if state.baseThird {
                    BaseRunnerMarker(name: runnerDisplayName(cleanPlayerName(state.baseThirdRunner) ?? inferredRunners.third))
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

private struct FieldRunners {
    let first: String?
    let second: String?
    let third: String?

    static func from(state: LiveGameState, events: [LiveEvent], lineup: FieldLineup?) -> FieldRunners {
        var bases: [Int: String] = [:]
        let scopedEvents = events
            .filter { $0.inning == nil || $0.inning == state.inning }
            .sorted { lhs, rhs in
                if lhs.cursor != rhs.cursor { return lhs.cursor < rhs.cursor }
                return (lhs.seqno ?? 0) < (rhs.seqno ?? 0)
            }
        let groups = Dictionary(grouping: scopedEvents, by: { $0.atBatId ?? $0.id })
            .values
            .sorted { ($0.first?.cursor ?? 0) < ($1.first?.cursor ?? 0) }

        for group in groups {
            var batterPlacement: (base: Int, name: String)?

            for event in group.sorted(by: { ($0.seqno ?? 0) < ($1.seqno ?? 0) }) {
                if event.type.uppercased() == "HALF_INNING_CHANGE" {
                    bases.removeAll()
                    continue
                }
                if let movement = runnerMovement(from: event.description) {
                    bases = bases.filter { $0.value != movement.name }
                    if let targetBase = movement.targetBase {
                        bases[targetBase] = movement.name
                    }
                }
                if let placement = batterRunnerPlacement(from: event) {
                    batterPlacement = placement
                }
            }

            if let batterPlacement {
                bases = bases.filter { $0.value != batterPlacement.name }
                bases[batterPlacement.base] = batterPlacement.name
            }
        }

        return FieldRunners(
            first: lineup?.firstRunner ?? (state.baseFirst ? bases[1] : nil),
            second: lineup?.secondRunner ?? (state.baseSecond ? bases[2] : nil),
            third: lineup?.thirdRunner ?? (state.baseThird ? bases[3] : nil)
        )
    }
}

private func runnerDisplayName(_ name: String?) -> String {
    cleanPlayerName(name) ?? "주자"
}

private func runnerMovement(from description: String) -> (name: String, targetBase: Int?)? {
    guard let match = firstMatch(#"([123])루주자\s+([^:]+)\s*:\s*(.+)"#, in: description),
          let name = cleanPlayerName(match[1]) else {
        return nil
    }
    let action = match[2]
    if action.contains("홈인") || action.contains("아웃") {
        return (name, nil)
    }
    if action.contains("3루") { return (name, 3) }
    if action.contains("2루") { return (name, 2) }
    if action.contains("1루") { return (name, 1) }
    return nil
}

private func batterRunnerPlacement(from event: LiveEvent) -> (base: Int, name: String)? {
    guard let name = cleanPlayerName(event.batter) else { return nil }
    let type = event.type.uppercased()
    let desc = event.description
    if desc.contains("홈런") { return nil }
    if desc.contains("3루타") { return (3, name) }
    if desc.contains("2루타") { return (2, name) }
    if type == "WALK" || type == "HIT_BY_PITCH" || type == "HIT" || desc.contains("출루") {
        return (1, name)
    }
    return nil
}

private func firstMatch(_ pattern: String, in text: String) -> [String]? {
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
    let range = NSRange(text.startIndex..<text.endIndex, in: text)
    guard let match = regex.firstMatch(in: text, range: range) else { return nil }
    return (1..<match.numberOfRanges).compactMap { index in
        guard let swiftRange = Range(match.range(at: index), in: text) else { return nil }
        return String(text[swiftRange]).trimmingCharacters(in: .whitespacesAndNewlines)
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
    let isScoreFilterActive: Bool
    let onSelectInning: (Int) -> Void
    let onSelectScore: () -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppSpacing.sm) {
                ForEach(tabs, id: \.self) { tab in
                    let isScore = tab == "득점"
                    let tabNumber: Int? = isScore ? nil : Int(tab.replacingOccurrences(of: "회", with: ""))
                    let selected: Bool = isScore
                        ? isScoreFilterActive
                        : (!isScoreFilterActive && tabNumber == selectedInningNumber)
                    Button {
                        if isScore {
                            onSelectScore()
                        } else if let n = tabNumber {
                            onSelectInning(n)
                        }
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
    let isLoading: Bool

    var body: some View {
        Text(isLoading ? "중계 데이터를 불러오는 중..." : "해당 회 이벤트가 없습니다")
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
        let isHomerun = type.uppercased() == "HOMERUN"
        return Group {
            if isHomerun {
                Text(eventLabel(type))
                    .font(AppFont.microBold)
                    .foregroundStyle(homerunRainbowGradient)
                    .padding(.horizontal, AppSpacing.sm)
                    .padding(.vertical, AppSpacing.xs)
                    .background(Capsule().fill(homerunRainbowGradientSoft))
            } else {
                Text(eventLabel(type))
                    .font(AppFont.microBold)
                    .foregroundColor(AppEventColors.color(for: type))
                    .padding(.horizontal, AppSpacing.sm)
                    .padding(.vertical, AppSpacing.xs)
                    .background(Capsule().fill(AppEventColors.color(for: type).opacity(0.14)))
            }
        }
    }
}

// MARK: - At-Bat Card (네이버 릴레이 스타일 타석 단위 카드)
private struct AtBatCard: View {
    let group: AtBatGroup
    let awayTeamName: String
    let homeTeamName: String
    let highlightScoreOutcome: Bool

    /// SCORE outcome 그룹의 정확한 시점 누적 스코어 라인 ("LG 1 : 3 두산" 형태).
    /// 백엔드 GameEventOut.homeScoreAfter/awayScoreAfter 가 노출된 경우에만 만들어짐.
    private var scoreLineText: String? {
        guard isScoreOutcome,
              let away = group.outcome?.awayScoreAfter,
              let home = group.outcome?.homeScoreAfter else { return nil }
        return "\(awayTeamName) \(away) : \(home) \(homeTeamName)"
    }

    private var highlighted: Bool {
        highlightScoreOutcome && isScoreOutcome
    }

    /// SCORE/SAC_FLY_SCORE outcome 그룹은 "득점 탭" 에서 description 자체가 정보의 핵심
    /// ("{타자} 적시타로 X점", "{주자} 홈인" 등)이라, 헤더에 description 을 강조하고
    /// 푸터 중복을 생략한다.
    private var isScoreOutcome: Bool {
        let t = group.outcome?.type.uppercased() ?? ""
        return t == "SCORE" || t == "SAC_FLY_SCORE"
    }

    private var headerText: String {
        if isScoreOutcome, let desc = group.outcome?.description, !desc.isEmpty {
            return desc
        }
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

    private var batterRecord: [String: Any]? {
        group.pitches.reversed().compactMap { $0.batterRecord }.first
    }

    private var pitchDetailEvents: [LiveEvent] {
        group.pitches
            .filter { event in
                event.pitchNum != nil ||
                    event.pitchSpeed != nil ||
                    event.pitchStuff != nil
            }
            .sorted {
                ($0.pitchNum ?? $0.seqno ?? Int($0.cursor)) >
                    ($1.pitchNum ?? $1.seqno ?? Int($1.cursor))
            }
    }

    private var showsNaverStyleDetails: Bool {
        !isScoreOutcome && (batterRecord != nil || !pitchDetailEvents.isEmpty)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            if showsNaverStyleDetails {
                AtBatBatterHeader(
                    batterName: batterRecordString(batterRecord, keys: ["name"]) ?? group.batter ?? headerText,
                    pitcherName: group.pitcher,
                    inning: group.inning,
                    record: batterRecord
                )
            } else {
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
            }

            if pitchDetailEvents.isEmpty {
                let chipTypes = normalizePitchTypes(events: group.pitches)
                if chipTypes.count > 1 {
                    FlowingPitchChips(types: chipTypes)
                }
            }

            // Footer: 최종 결과 텍스트
            if let outcome = group.outcome, !outcome.description.isEmpty {
                HStack(alignment: .top, spacing: AppSpacing.sm) {
                    EventTypePill(type: outcome.type)
                    if !isScoreOutcome {
                        Text(outcome.description)
                            .font(AppFont.caption)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        // SCORE 그룹: description 은 헤더로 옮겼고, 푸터엔 타석 타자 + 정확 스코어 노출
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            if let batter = group.batter, !batter.isEmpty {
                                Text("타석: \(batter)")
                                    .font(AppFont.micro)
                                    .foregroundColor(AppColors.gray400)
                            }
                            if let scoreLine = scoreLineText {
                                Text(scoreLine)
                                    .font(AppFont.microBold)
                                    .foregroundColor(AppColors.yellow500)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
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

            if !pitchDetailEvents.isEmpty {
                PitchDetailRows(events: pitchDetailEvents)
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

private struct AtBatBatterHeader: View {
    let batterName: String
    let pitcherName: String?
    let inning: String?
    let record: [String: Any]?

    private var metaText: String {
        var parts: [String] = []
        if let order = batterRecordInt(record, keys: ["batOrder", "battingOrder"]) {
            parts.append("\(order)번타자")
        }
        if let average = batterRecordAverageText(record) {
            parts.append("타율 \(average)")
        }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            HStack(alignment: .top, spacing: AppSpacing.md) {
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    HStack(alignment: .firstTextBaseline, spacing: AppSpacing.sm) {
                        Text(batterName)
                            .font(AppFont.h5Bold)
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)

                        if !metaText.isEmpty {
                            Text(metaText)
                                .font(AppFont.micro)
                                .foregroundColor(AppColors.gray400)
                                .lineLimit(1)
                                .minimumScaleFactor(0.82)
                        }
                    }

                    if let inning, !inning.isEmpty {
                        Text(inning)
                            .font(AppFont.micro)
                            .foregroundColor(AppColors.gray500)
                    }
                }

                Spacer(minLength: AppSpacing.sm)

                if let pitcherName, !pitcherName.isEmpty {
                    VStack(alignment: .trailing, spacing: AppSpacing.xxs) {
                        Text("상대투수")
                            .font(AppFont.micro)
                            .foregroundColor(AppColors.gray500)
                        Text(pitcherName)
                            .font(AppFont.captionBold)
                            .foregroundColor(AppColors.gray200)
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)
                    }
                }
            }

            if let record {
                BatterStatGrid(record: record)
            }
        }
    }
}

private struct BatterStatGrid: View {
    let record: [String: Any]

    private var rows: [[BatterStatItem]] {
        let stats = [
            BatterStatItem(label: "타석", value: batterRecordDisplayInt(record, keys: ["pa", "plateAppearance", "plateAppearances"])),
            BatterStatItem(label: "타수", value: batterRecordDisplayInt(record, keys: ["ab", "atBat", "atBats"])),
            BatterStatItem(label: "안타", value: batterRecordDisplayInt(record, keys: ["hit", "hits"])),
            BatterStatItem(label: "득점", value: batterRecordDisplayInt(record, keys: ["run", "runs", "score"])),
            BatterStatItem(label: "타점", value: batterRecordDisplayInt(record, keys: ["rbi"])),
            BatterStatItem(label: "홈런", value: batterRecordDisplayInt(record, keys: ["hr", "homeRuns"])),
            BatterStatItem(label: "볼넷", value: batterRecordDisplayInt(record, keys: ["bb", "walk", "walks", "baseOnBalls"])),
            BatterStatItem(label: "삼진", value: batterRecordDisplayInt(record, keys: ["so", "strikeOuts", "strikeouts"]))
        ]
        return [Array(stats.prefix(4)), Array(stats.dropFirst(4))]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.xs) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: AppSpacing.sm) {
                    ForEach(row) { item in
                        HStack(spacing: AppSpacing.xxs) {
                            Text(item.label)
                                .foregroundColor(AppColors.gray500)
                            Text(item.value)
                                .foregroundColor(AppColors.gray300)
                        }
                        .font(AppFont.micro)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                    }
                    Spacer(minLength: 0)
                }
            }
        }
    }
}

private struct BatterStatItem: Identifiable {
    let label: String
    let value: String

    var id: String { label }
}

private struct PitchDetailRows: View {
    let events: [LiveEvent]

    var body: some View {
        VStack(spacing: AppSpacing.sm) {
            ForEach(events) { event in
                PitchDetailRow(event: event)
            }
        }
        .padding(AppSpacing.md)
        .background(AppColors.gray800.opacity(0.42))
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.sm, style: .continuous))
    }
}

private struct PitchDetailRow: View {
    let event: LiveEvent

    private var displayType: String {
        pitchDisplayType(event)
    }

    private var pitchMetricText: String {
        switch (event.pitchSpeed, event.pitchStuff) {
        case let (speed?, stuff?) where !stuff.isEmpty:
            return "\(speed)km/h | \(stuff)"
        case let (speed?, _):
            return "\(speed)km/h"
        case let (_, stuff?) where !stuff.isEmpty:
            return stuff
        default:
            return "-"
        }
    }

    private var countText: String {
        guard let ball = event.ballAfter, let strike = event.strikeAfter else { return "-" }
        return "\(ball)-\(strike)"
    }

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            Text("\(event.pitchNum ?? 0)")
                .font(AppFont.microBold)
                .foregroundColor(AppColors.gray950)
                .frame(width: 22, height: 22)
                .background(Circle().fill(AppEventColors.color(for: displayType)))

            Text(pitchOutcomeText(event))
                .font(AppFont.captionMedium)
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.86)

            Spacer(minLength: AppSpacing.sm)

            Text(pitchMetricText)
                .font(AppFont.micro)
                .foregroundColor(AppColors.gray400)
                .lineLimit(1)
                .minimumScaleFactor(0.78)

            Text(countText)
                .font(AppFont.microMedium)
                .foregroundColor(AppColors.gray400)
                .frame(width: 34, alignment: .trailing)
                .lineLimit(1)
        }
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

private let homerunRainbowColors: [Color] = [
    Color(red: 1.0, green: 0.30, blue: 0.30),   // red
    Color(red: 1.0, green: 0.62, blue: 0.20),   // orange
    Color(red: 1.0, green: 0.85, blue: 0.20),   // yellow
    Color(red: 0.35, green: 0.85, blue: 0.40),  // green
    Color(red: 0.30, green: 0.60, blue: 1.0),   // blue
    Color(red: 0.70, green: 0.40, blue: 0.95),  // violet
]

private var homerunRainbowGradient: LinearGradient {
    LinearGradient(colors: homerunRainbowColors, startPoint: .leading, endPoint: .trailing)
}

private var homerunRainbowGradientSoft: LinearGradient {
    LinearGradient(
        colors: homerunRainbowColors.map { $0.opacity(0.18) },
        startPoint: .leading, endPoint: .trailing
    )
}

private struct PitchChip: View {
    let type: String

    var body: some View {
        let isHomerun = type.uppercased() == "HOMERUN"
        let color = AppEventColors.color(for: type)
        return Group {
            if isHomerun {
                Text(pitchShortLabel(type))
                    .font(AppFont.microBold)
                    .foregroundStyle(homerunRainbowGradient)
                    .frame(minWidth: 24)
                    .padding(.horizontal, AppSpacing.xs)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(homerunRainbowGradientSoft))
                    .overlay(Capsule().stroke(homerunRainbowGradient, lineWidth: 0.8))
            } else {
                Text(pitchShortLabel(type))
                    .font(AppFont.microBold)
                    .foregroundColor(color)
                    .frame(minWidth: 24)
                    .padding(.horizontal, AppSpacing.xs)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(color.opacity(0.14)))
                    .overlay(Capsule().stroke(color.opacity(0.32), lineWidth: 0.5))
            }
        }
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
private func sectionTitle(for group: AtBatGroup, state: LiveGameState, style: TeamDisplayNameStyle) -> String {
    guard let inning = group.inning, !inning.isEmpty else { return "타석" }
    let teamName: String
    if inning.contains("초") {
        teamName = state.awayTeamId.displayName(style: style)
    } else if inning.contains("말") {
        teamName = state.homeTeamId.displayName(style: style)
    } else {
        teamName = ""
    }
    return teamName.isEmpty ? inning : "\(inning) \(teamName) 공격"
}

private func pitchShortLabel(_ type: String) -> String {
    switch type.uppercased() {
    case "BALL": return "B"
    case "STRIKE": return "S"
    case "FOUL": return "F"
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

/// 타석 카드 PitchChip 시퀀스에 들어갈 타입을 정제한다.
/// - 타석 시작 안내성 OTHER ("1번타자 ...", "X회 ... 공격") → 칩 제외
/// - 파울/타격 OTHER → 클라이언트 가상 타입 "FOUL" 로 통합 (Orange500, F 라벨)
/// - 그 외 OTHER (분류 불가) → 칩 제외
/// - 그 외 타입은 그대로 보존
private func normalizePitchTypes(events: [LiveEvent]) -> [String] {
    events.compactMap { event in
        let upper = pitchDisplayType(event)
        if upper != "OTHER" { return upper }
        // 타석/이닝 안내성 OTHER 는 칩에서 제외
        return nil
    }
}

private func pitchDisplayType(_ event: LiveEvent) -> String {
    let upper = event.type.uppercased()
    let desc = event.description
    if desc.contains("파울") || desc.contains("타격") { return "FOUL" }
    return upper
}

private func pitchOutcomeText(_ event: LiveEvent) -> String {
    var text = event.description.trimmingCharacters(in: .whitespacesAndNewlines)
    if let range = text.range(of: #"^\d+구\s*"#, options: .regularExpression) {
        text.removeSubrange(range)
    }
    if !text.isEmpty { return text }
    return eventLabel(pitchDisplayType(event))
}

private func batterRecordString(_ record: [String: Any]?, keys: [String]) -> String? {
    guard let record else { return nil }
    for key in keys {
        guard let raw = record[key] else { continue }
        if let value = raw as? String {
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { return trimmed }
        } else {
            let value = "\(raw)".trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty { return value }
        }
    }
    return nil
}

private func batterRecordInt(_ record: [String: Any]?, keys: [String]) -> Int? {
    guard let record else { return nil }
    for key in keys {
        guard let raw = record[key] else { continue }
        if let value = raw as? Int { return value }
        if let value = raw as? Double { return Int(value) }
        if let value = raw as? String, let parsed = Int(value.trimmingCharacters(in: .whitespacesAndNewlines)) {
            return parsed
        }
    }
    return nil
}

private func batterRecordDouble(_ record: [String: Any]?, keys: [String]) -> Double? {
    guard let record else { return nil }
    for key in keys {
        guard let raw = record[key] else { continue }
        if let value = raw as? Double { return value }
        if let value = raw as? Int { return Double(value) }
        if let value = raw as? String, let parsed = Double(value.trimmingCharacters(in: .whitespacesAndNewlines)) {
            return parsed
        }
    }
    return nil
}

private func batterRecordAverageText(_ record: [String: Any]?) -> String? {
    guard let average = batterRecordDouble(record, keys: ["seasonHra", "avg", "average", "battingAverage"]) else {
        return nil
    }
    return String(format: "%.3f", average)
}

private func batterRecordDisplayInt(_ record: [String: Any]?, keys: [String]) -> String {
    "\(batterRecordInt(record, keys: keys) ?? 0)"
}

private func displayPitcher(state: LiveGameState, event: LiveEvent?, placeholder: String = "-") -> String {
    if let direct = cleanPlayerName(state.pitcher) { return direct }
    if let eventName = cleanPlayerName(event?.pitcher) {
        return eventName
    }
    // 라인업 공개 후 라이브 진입 전: 수비팀 선발투수로 폴백.
    // FieldLineup.from(state:) 과 동일 규칙(`말` 이 아니면 home 수비).
    let preferHome = !state.inning.contains("말")
    if preferHome, let starter = cleanPlayerName(state.homeStartingPitcher) {
        return starter
    }
    if !preferHome, let starter = cleanPlayerName(state.awayStartingPitcher) {
        return starter
    }
    return placeholder
}

private func displayBatter(state: LiveGameState, event: LiveEvent?, placeholder: String = "-") -> String {
    if let direct = cleanPlayerName(state.batter) { return direct }
    if let eventName = cleanPlayerName(event?.batter) {
        return eventName
    }
    return placeholder
}

private func cleanPlayerName(_ name: String?) -> String? {
    let value = name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    let lowercased = value.lowercased()
    guard !value.isEmpty, lowercased != "null", lowercased != "<null>" else { return nil }
    return value
}

private func baseText(_ state: LiveGameState) -> String {
    var bases: [String] = []
    if state.baseFirst { bases.append("1") }
    if state.baseSecond { bases.append("2") }
    if state.baseThird { bases.append("3") }
    return bases.isEmpty ? "없음" : bases.joined(separator: ",")
}

private func currentAttackLabel(_ state: LiveGameState, style: TeamDisplayNameStyle) -> String {
    let battingTeam: String
    if state.inning.contains("초") {
        battingTeam = state.awayTeamId.displayName(style: style)
    } else if state.inning.contains("말") {
        battingTeam = state.homeTeamId.displayName(style: style)
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
