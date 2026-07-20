import XCTest
@testable import VentingCore

/// 분풀이 모드 오픈 조건 판정 단위 테스트.
///
/// **온톨로지 입력 필드 주입 대응표**
/// | 온톨로지 필드          | 테스트 주입 경로                            |
/// |----------------------|------------------------------------------|
/// | `my_team_configured`  | `myTeamId` 빈 문자열 vs "HH"              |
/// | `my_team_id`          | `isOpen(myTeamId:)` 파라미터               |
/// | `game_result`         | `VentingGameContext.gameResult`          |
/// | `game_date_kst`       | `VentingGameContext.gameDate` ("yyyy-MM-dd") |
/// | `now_kst`             | `isOpen(nowKST:)` 파라미터 — 결정론적 주입 |
///
/// 검증 케이스 (AC 필수 4케이스 + 보조):
/// 1. 마이팀 패배 + 당일 KST → isOpen = true
/// 2. 무승부 → isOpen = false
/// 3. 자정 경과(now_kst가 다음 날) → isOpen = false
/// 4. 마이팀 미설정(빈 문자열) → isOpen = false
/// 5. 취소 → isOpen = false
/// 6. 연기 → isOpen = false
/// 7. 승리 → isOpen = false
/// 8. 마이팀 불일치(다른 팀 ID) → isOpen = false
final class VentingOpenConditionCheckerTests: XCTestCase {

    // MARK: - 상수

    private let myTeamId = "HH"

    /// 테스트 기준 경기 날짜 (KST "yyyy-MM-dd") — 고정값, 시스템 시계 미사용
    private let fixedGameDate = "2026-07-20"

    // MARK: - 헬퍼

    private static let kstFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd HH:mm"
        fmt.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return fmt
    }()

    /// KST 날짜+시각 문자열("yyyy-MM-dd HH:mm")로 Date 생성.
    /// `now_kst` 온톨로지 입력을 결정론적으로 주입한다.
    private func makeNowKST(_ kstString: String) -> Date {
        guard let date = Self.kstFormatter.date(from: kstString) else {
            fatalError("Invalid KST date string: \(kstString)")
        }
        return date
    }

    private func makeContext(
        result: VentingGameResult,
        date: String,
        teamId: String = "HH"
    ) -> VentingGameContext {
        VentingGameContext(
            gameId: "test-game-001",
            gameDate: date,
            gameResult: result,
            myTeamId: teamId,
            myScore: 2,
            opponentScore: 5,
            candidates: [],
            managerEventDescription: "작전 실패"
        )
    }

    // MARK: - AC 필수 케이스 1: 마이팀 패배 당일 오픈

    /// AC케이스 1: 마이팀 패배 + game_date_kst == now_kst 날짜 → isOpen = true
    /// 입력: my_team_configured=true, my_team_id="HH", game_result=.loss,
    ///       game_date_kst="2026-07-20", now_kst="2026-07-20 20:00"
    func test_AC1_loss_today_isOpen() {
        let context = makeContext(result: .loss, date: fixedGameDate)
        let nowKST = makeNowKST("2026-07-20 20:00")   // now_kst 주입

        XCTAssertTrue(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId, nowKST: nowKST),
            "마이팀 패배 + 당일 KST → 오픈"
        )
    }

    // MARK: - AC 필수 케이스 2: 무승부 미오픈

    /// AC케이스 2: game_result=.draw → isOpen = false
    /// 입력: my_team_configured=true, my_team_id="HH", game_result=.draw,
    ///       game_date_kst="2026-07-20", now_kst="2026-07-20 21:00"
    func test_AC2_draw_today_isNotOpen() {
        let context = makeContext(result: .draw, date: fixedGameDate)
        let nowKST = makeNowKST("2026-07-20 21:00")   // now_kst 주입

        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId, nowKST: nowKST),
            "무승부 → 미오픈"
        )
    }

    // MARK: - AC 필수 케이스 3: 자정 경과 만료 미오픈

    /// AC케이스 3: game_date_kst="2026-07-20", now_kst="2026-07-21 01:00" (자정 경과)
    /// → isOpen = false
    /// 입력: my_team_configured=true, my_team_id="HH", game_result=.loss,
    ///       game_date_kst="2026-07-20", now_kst="2026-07-21 01:00"
    func test_AC3_midnight_expired_isNotOpen() {
        let context = makeContext(result: .loss, date: fixedGameDate)
        let nowKST = makeNowKST("2026-07-21 01:00")   // now_kst 주입 (자정 경과)

        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId, nowKST: nowKST),
            "자정 경과(now_kst > game_date_kst) → 미오픈"
        )
    }

    // MARK: - AC 필수 케이스 4: 마이팀 미설정 미오픈

    /// AC케이스 4: my_team_configured=false (myTeamId 빈 문자열) → isOpen = false
    /// 입력: my_team_configured=false, my_team_id="", game_result=.loss,
    ///       game_date_kst="2026-07-20", now_kst="2026-07-20 20:00"
    func test_AC4_emptyMyTeamId_isNotOpen() {
        let context = makeContext(result: .loss, date: fixedGameDate)
        let nowKST = makeNowKST("2026-07-20 20:00")   // now_kst 주입

        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: "", nowKST: nowKST),
            "마이팀 미설정(my_team_configured=false) → 미오픈"
        )
    }

    // MARK: - 보조 케이스: 취소·연기

    func test_canceled_today_isNotOpen() {
        let context = makeContext(result: .canceled, date: fixedGameDate)
        let nowKST = makeNowKST("2026-07-20 20:00")
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId, nowKST: nowKST),
            "취소 → 미오픈"
        )
    }

    func test_postponed_today_isNotOpen() {
        let context = makeContext(result: .postponed, date: fixedGameDate)
        let nowKST = makeNowKST("2026-07-20 20:00")
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId, nowKST: nowKST),
            "연기 → 미오픈"
        )
    }

    // MARK: - 보조 케이스: 승리

    func test_win_today_isNotOpen() {
        let context = makeContext(result: .win, date: fixedGameDate)
        let nowKST = makeNowKST("2026-07-20 20:00")
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId, nowKST: nowKST),
            "승리 → 미오픈"
        )
    }

    // MARK: - 보조 케이스: 마이팀 불일치

    func test_differentTeamId_isNotOpen() {
        let context = makeContext(result: .loss, date: fixedGameDate, teamId: "LG")
        let nowKST = makeNowKST("2026-07-20 20:00")
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId, nowKST: nowKST),
            "마이팀과 경기 팀 코드 불일치 → 미오픈"
        )
    }

    // MARK: - isSameDayKST 경계 검증

    /// 자정 직전(23:59 KST)은 당일
    func test_isSameDayKST_justBeforeMidnight_isToday() {
        let nowKST = makeNowKST("2026-07-20 23:59")
        XCTAssertTrue(
            VentingOpenConditionChecker.isSameDayKST(gameDate: "2026-07-20", now: nowKST),
            "23:59 KST은 같은 날"
        )
    }

    /// 자정 직후(00:01 KST 다음 날)는 어제
    func test_isSameDayKST_justAfterMidnight_isNotToday() {
        let nowKST = makeNowKST("2026-07-21 00:01")
        XCTAssertFalse(
            VentingOpenConditionChecker.isSameDayKST(gameDate: "2026-07-20", now: nowKST),
            "00:01 KST 다음날은 다른 날"
        )
    }
}
