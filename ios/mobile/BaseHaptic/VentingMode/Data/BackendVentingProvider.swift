import Foundation

// MARK: - BackendVentingProvider

/// 서버 regret-top5(6.1) 응답으로 분풀이 컨텍스트를 조립하는 `RegretCandidateProviding` 구현체
/// (Android `ServerRegretProvider` 포팅).
///
/// 서버 항목(role_label·inning·reason)을 익명 후보로 매핑하고, 박스스코어 조인으로
/// 실명을 해소한다 — 실명은 `RegretCandidate.playerName` 에만 담기며 선택 화면 TargetRow
/// 에서만 노출된다(룸·완파 화면은 `VentingTarget.roleLabel`/`eventDescription` 만 읽음).
///
/// `regret.items` 가 비어 있으면 `nil` 을 반환하고, 호출자는 `LiveRegretProvider` 로 폴백한다.
struct BackendVentingProvider: RegretCandidateProviding {

    let gameId: String
    let myTeam: Team

    func fetchVentingContext() async -> VentingGameContext? {
        let repo = BackendGamesRepository.shared
        async let regretTask = repo.fetchVentingRegretTop5(gameId: gameId)
        async let stateTask = repo.fetchGameState(gameId: gameId)
        async let boxscoreTask = repo.fetchGameBoxscore(gameId: gameId)
        let regret = await regretTask
        let state = await stateTask
        let boxscore = await boxscoreTask

        guard let regret else { return nil }
        return Self.buildContext(
            gameId: gameId,
            state: state,
            boxscore: boxscore,
            myTeam: myTeam,
            regret: regret
        )
    }

    /// 이미 가져온 데이터로 동기 조립 (라이브 진입·딥링크에서 재사용).
    /// `regret.items` 가 비어 있으면 `nil` → 호출자 로컬 폴백.
    static func buildContext(
        gameId: String,
        state: LiveGameState?,
        boxscore: GameBoxscore?,
        myTeam: Team,
        regret: VentingRegretTop5
    ) -> VentingGameContext? {
        guard !regret.items.isEmpty else { return nil }

        let myTeamIsHome: Bool?
        if state?.homeTeamId == myTeam {
            myTeamIsHome = true
        } else if state?.awayTeamId == myTeam {
            myTeamIsHome = false
        } else {
            myTeamIsHome = nil
        }

        let myScore: Int
        let opponentScore: Int
        switch myTeamIsHome {
        case true:
            myScore = state?.homeScore ?? 0
            opponentScore = state?.awayScore ?? 0
        case false:
            myScore = state?.awayScore ?? 0
            opponentScore = state?.homeScore ?? 0
        default:
            myScore = 0
            opponentScore = 0
        }

        let isFinished = state?.status == .finished
        let gameResult: VentingGameResult
        if state == nil {
            gameResult = .loss
        } else if !isFinished {
            gameResult = .inProgress
        } else if myScore < opponentScore {
            gameResult = .loss
        } else if myScore > opponentScore {
            gameResult = .win
        } else {
            gameResult = .draw
        }

        let candidates: [RegretCandidate] = regret.items.prefix(5).enumerated().map { index, item in
            let roleLabel = (item.roleLabel?.isEmpty == false) ? item.roleLabel! : "선수"
            let reason = item.reason ?? ""
            let inning = item.inning ?? ""
            let eventDescription: String
            if !inning.isEmpty, !reason.isEmpty {
                eventDescription = "\(inning) \(reason)"
            } else if !reason.isEmpty {
                eventDescription = reason
            } else if !inning.isEmpty {
                eventDescription = inning
            } else {
                eventDescription = "아쉬운 순간"
            }
            return RegretCandidate(
                id: "server_\(index)",
                roleLabel: roleLabel,
                eventDescription: eventDescription,
                kind: item.kind,
                teamSide: item.teamSide,
                battingOrder: item.battingOrder,
                appearanceOrder: item.appearanceOrder,
                playerName: resolvePlayerName(item: item, boxscore: boxscore)
            )
        }

        return VentingGameContext(
            gameId: gameId,
            gameDate: todayKSTString(),
            gameResult: gameResult,
            myTeamId: myTeam.kboTeamId ?? myTeam.rawValue,
            myScore: myScore,
            opponentScore: opponentScore,
            candidates: candidates,
            managerEventDescription: regret.manager ?? "지금까지의 경기 운영 아쉬움",
            inningLabel: (state == nil || isFinished) ? nil : state?.inning
        )
    }

    // MARK: - Real-name resolution

    /// 서버 항목(kind + team_side + 타순/등판순서)을 박스스코어 명단과 조인해 실명을 해소한다.
    private static func resolvePlayerName(item: VentingRegretItem, boxscore: GameBoxscore?) -> String? {
        guard let boxscore else { return nil }
        let isHome: Bool
        switch item.teamSide?.lowercased() {
        case "home": isHome = true
        case "away": isHome = false
        default: return nil
        }
        let kind = item.kind?.lowercased()
        if kind == "pitcher" || (kind != "batter" && item.appearanceOrder != nil) {
            let pitchers = isHome ? boxscore.homePitchers : boxscore.awayPitchers
            guard let order = item.appearanceOrder else { return nil }
            return pitchers.first(where: { $0.appearanceOrder == order })?.playerName
        } else {
            let batters = isHome ? boxscore.homeBatters : boxscore.awayBatters
            guard let order = item.battingOrder else { return nil }
            return batters.first(where: { $0.battingOrder == order })?.playerName
        }
    }

    private static func todayKSTString() -> String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return fmt.string(from: Date())
    }
}
