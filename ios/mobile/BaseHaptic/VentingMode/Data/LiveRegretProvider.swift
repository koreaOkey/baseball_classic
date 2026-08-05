#if DEBUG
import Foundation

// MARK: - LiveRegretProvider

/// 라이브 경기 화면에서 분풀이 진입 시 사용하는 컨텍스트 빌더 (Android LiveRegretProvider 포팅).
///
/// 경기 상세 화면이 이미 들고 있는 중계 이벤트·박스스코어에서 "지금까지 이슈가 있던
/// 마이팀 선수" 후보를 클라이언트 규칙으로 추린다 — 백엔드는 수정하지 않는다.
///
/// 후보 규칙(인터뷰 동결 TOP5 규칙의 라이브 축소판):
/// - 마이팀 공격 중: 병살·삼진 타자
/// - 마이팀 수비 중: 실점(득점·희생플라이·홈런 허용) 시점의 투수
/// - 표기는 역할 레이블만 사용(실명·등번호 금지 — 홈카드 TOP5와 동일 원칙),
///   실명 지목은 선택 화면의 직접 입력으로만 가능하다.
enum LiveRegretProvider {

    /// 마이팀이 참가한 경기가 아니면 nil.
    static func buildContext(
        state: LiveGameState,
        events: [LiveEvent],
        boxscore: GameBoxscore?,
        myTeam: Team
    ) -> VentingGameContext? {
        let myTeamIsHome: Bool
        if state.homeTeamId == myTeam {
            myTeamIsHome = true
        } else if state.awayTeamId == myTeam {
            myTeamIsHome = false
        } else {
            return nil
        }

        let myScore = myTeamIsHome ? state.homeScore : state.awayScore
        let opponentScore = myTeamIsHome ? state.awayScore : state.homeScore

        let isFinished = state.status == .finished
        let gameResult: VentingGameResult
        if !isFinished {
            gameResult = .inProgress
        } else if myScore < opponentScore {
            gameResult = .loss
        } else if myScore > opponentScore {
            gameResult = .win
        } else {
            gameResult = .draw
        }

        return VentingGameContext(
            gameId: state.gameId,
            gameDate: todayKSTString(),
            gameResult: gameResult,
            myTeamId: myTeam.kboTeamId ?? myTeam.rawValue,
            myScore: myScore,
            opponentScore: opponentScore,
            candidates: buildCandidates(
                events: events,
                boxscore: boxscore,
                myTeamIsHome: myTeamIsHome
            ),
            managerEventDescription: "지금까지의 경기 운영 아쉬움",
            inningLabel: isFinished ? nil : state.inning
        )
    }

    // MARK: - Candidates

    private static func buildCandidates(
        events: [LiveEvent],
        boxscore: GameBoxscore?,
        myTeamIsHome: Bool
    ) -> [RegretCandidate] {
        let myBatters = myTeamIsHome ? boxscore?.homeBatters : boxscore?.awayBatters
        let myPitchers = myTeamIsHome ? boxscore?.homePitchers : boxscore?.awayPitchers

        // events 는 최신순(cursor 내림차순) — 최근 이슈가 먼저 후보에 오른다.
        var candidates: [RegretCandidate] = []
        for event in events {
            guard let inning = event.inning else { continue }
            let homeBatting: Bool
            if inning.contains("말") {
                homeBatting = true
            } else if inning.contains("초") {
                homeBatting = false
            } else {
                continue
            }
            let myTeamBatting = homeBatting == myTeamIsHome
            let type = event.type.uppercased()

            if myTeamBatting {
                let isBatterRegret = type == "DOUBLE_PLAY" || type == "TRIPLE_PLAY" ||
                    type == "STRIKEOUT" || (type == "OUT" && event.description.contains("삼진"))
                guard isBatterRegret else { continue }
                candidates.append(RegretCandidate(
                    id: "live_\(event.cursor)",
                    roleLabel: batterRoleLabel(event.batter, batters: myBatters),
                    eventDescription: "\(inning) \(stripNamePrefix(event.description))"
                ))
            } else {
                let isPitcherRegret = type == "SCORE" || type == "SAC_FLY_SCORE" || type == "HOMERUN"
                guard isPitcherRegret else { continue }
                candidates.append(RegretCandidate(
                    id: "live_\(event.cursor)",
                    roleLabel: pitcherRoleLabel(event.pitcher, pitchers: myPitchers),
                    eventDescription: "\(inning) \(stripNamePrefix(event.description)) 허용"
                ))
            }
        }

        // 같은 역할(예: "3번 타자")은 가장 최근 사건 1건만 남긴다.
        var seenRoles = Set<String>()
        var deduped: [RegretCandidate] = []
        for candidate in candidates {
            guard !seenRoles.contains(candidate.roleLabel) else { continue }
            seenRoles.insert(candidate.roleLabel)
            deduped.append(candidate)
            if deduped.count >= 5 { break }
        }
        return deduped
    }

    /// 박스스코어 타순으로 "N번 타자" 레이블 생성. 매칭 실패 시 "타자".
    private static func batterRoleLabel(
        _ batterName: String?,
        batters: [BoxscoreBatterLine]?
    ) -> String {
        guard let batterName, !batterName.isEmpty, let batters else { return "타자" }
        guard let order = batters.first(where: { $0.playerName == batterName })?.battingOrder,
              (1...9).contains(order) else { return "타자" }
        return "\(order)번 타자"
    }

    /// 박스스코어 등판 정보로 "선발/구원 투수" 레이블 생성. 매칭 실패 시 "투수".
    private static func pitcherRoleLabel(
        _ pitcherName: String?,
        pitchers: [BoxscorePitcherLine]?
    ) -> String {
        guard let pitcherName, !pitcherName.isEmpty, let pitchers,
              let pitcher = pitchers.first(where: { $0.playerName == pitcherName }) else { return "투수" }
        return pitcher.isStarter ? "선발 투수" : "구원 투수"
    }

    /// 중계 문구의 "선수명 : 내용" 앞부분을 제거해 실명 노출을 막는다.
    private static func stripNamePrefix(_ description: String) -> String {
        guard let range = description.range(of: " : ") else { return description }
        return String(description[range.upperBound...]).trimmingCharacters(in: .whitespaces)
    }

    private static func todayKSTString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        return formatter.string(from: Date())
    }
}
#endif
