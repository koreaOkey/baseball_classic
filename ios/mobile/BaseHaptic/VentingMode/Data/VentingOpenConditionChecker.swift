#if DEBUG
import Foundation

// MARK: - VentingOpenConditionChecker

/// 분풀이 모드 오픈 조건 판정 로직.
/// **실제 코드**로 구현되며, 입력(경기 결과·날짜·마이팀 설정)만 목업 데이터로 주입한다.
///
/// 오픈 조건:
/// - 마이팀이 설정된 사용자
/// - 마이팀이 패배한 경기
/// - 경기 날짜가 오늘(KST 자정 전)
/// - 무승부·취소·연기 경기는 미오픈
enum VentingOpenConditionChecker {

    private static var kstCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return cal
    }()

    private static var kstDateFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return fmt
    }()

    /// 분풀이 모드 오픈 여부를 반환한다.
    /// - Parameters:
    ///   - context: 경기 컨텍스트 (날짜·결과·마이팀 코드)
    ///   - myTeamId: 현재 설정된 마이팀 코드. 빈 문자열이면 미설정으로 판단.
    /// - Returns: 오픈 조건 충족 여부
    static func isOpen(context: VentingGameContext, myTeamId: String) -> Bool {
        // 마이팀 미설정 → 미오픈
        guard !myTeamId.isEmpty else { return false }

        // 마이팀이 이 경기에 참여하지 않은 경우 → 미오픈
        guard context.myTeamId == myTeamId else { return false }

        // 무승부·취소·연기 → 미오픈
        guard context.gameResult == .loss else { return false }

        // 당일(KST) 판정
        guard isTodayKST(dateString: context.gameDate) else { return false }

        return true
    }

    /// 날짜 문자열이 오늘(KST 기준)인지 판정한다.
    private static func isTodayKST(dateString: String) -> Bool {
        guard let gameDate = kstDateFormatter.date(from: dateString) else { return false }
        return kstCalendar.isDateInToday(gameDate)
    }
}
#endif
