import XCTest
@testable import VentingCore

/// 분풀이 모드 오픈 조건 판정 단위 테스트.
///
/// 검증 항목:
/// 1. 마이팀 패배 + 당일 KST → isOpen = true
/// 2. 무승부 → isOpen = false
/// 3. 취소 → isOpen = false
/// 4. 연기 → isOpen = false
/// 5. 승리 → isOpen = false
/// 6. 자정 경과(어제 날짜) → isOpen = false
/// 7. 마이팀 미설정(빈 문자열) → isOpen = false
/// 8. 마이팀 불일치(다른 팀 ID) → isOpen = false
final class VentingOpenConditionCheckerTests: XCTestCase {

    // MARK: - 헬퍼

    private let myTeamId = "HH"

    /// 오늘(KST) 날짜 문자열 반환
    private var todayKST: String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return fmt.string(from: Date())
    }

    /// 어제(KST) 날짜 문자열 반환
    private var yesterdayKST: String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        var components = DateComponents()
        components.day = -1
        let yesterday = Calendar.current.date(byAdding: components, to: Date())!
        return fmt.string(from: yesterday)
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

    // MARK: - 패배 당일 오픈

    func test_loss_today_isOpen() {
        let context = makeContext(result: .loss, date: todayKST)
        XCTAssertTrue(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId),
            "마이팀 패배 + 당일 KST → 오픈"
        )
    }

    // MARK: - 무승부·취소·연기 미오픈

    func test_draw_today_isNotOpen() {
        let context = makeContext(result: .draw, date: todayKST)
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId),
            "무승부 → 미오픈"
        )
    }

    func test_canceled_today_isNotOpen() {
        let context = makeContext(result: .canceled, date: todayKST)
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId),
            "취소 → 미오픈"
        )
    }

    func test_postponed_today_isNotOpen() {
        let context = makeContext(result: .postponed, date: todayKST)
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId),
            "연기 → 미오픈"
        )
    }

    func test_win_today_isNotOpen() {
        let context = makeContext(result: .win, date: todayKST)
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId),
            "승리 → 미오픈"
        )
    }

    // MARK: - 자정 경과 (어제 날짜)

    func test_loss_yesterday_isNotOpen() {
        let context = makeContext(result: .loss, date: yesterdayKST)
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId),
            "어제 날짜(자정 경과) → 미오픈"
        )
    }

    // MARK: - 마이팀 미설정

    func test_emptyMyTeamId_isNotOpen() {
        let context = makeContext(result: .loss, date: todayKST)
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: ""),
            "마이팀 미설정(빈 문자열) → 미오픈"
        )
    }

    // MARK: - 마이팀 불일치

    func test_differentTeamId_isNotOpen() {
        let context = makeContext(result: .loss, date: todayKST, teamId: "LG")
        XCTAssertFalse(
            VentingOpenConditionChecker.isOpen(context: context, myTeamId: myTeamId),
            "마이팀과 경기 팀 코드 불일치 → 미오픈"
        )
    }
}
