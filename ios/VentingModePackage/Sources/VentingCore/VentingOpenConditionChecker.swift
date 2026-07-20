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
public enum VentingOpenConditionChecker {

    private static let kstCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return cal
    }()

    private static let kstDateFormatter: DateFormatter = {
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
    ///     온톨로지 필드: `my_team_id` / `my_team_configured`(isEmpty == false)
    ///   - nowKST: 판정 시점의 KST 현재시각(기본값 = 시스템 현재). 온톨로지 필드: `now_kst`.
    ///     테스트에서 주입해 자정 경과 만료를 결정론적으로 검증한다.
    /// - Returns: 오픈 조건 충족 여부 (`is_open`)
    public static func isOpen(
        context: VentingGameContext,
        myTeamId: String,
        nowKST: Date = Date()
    ) -> Bool {
        // 마이팀 미설정(my_team_configured = false) → 미오픈
        guard !myTeamId.isEmpty else { return false }

        // 마이팀이 이 경기에 참여하지 않은 경우 → 미오픈
        guard context.myTeamId == myTeamId else { return false }

        // 무승부·취소·연기(game_result) → 미오픈 (패배만 오픈)
        guard context.gameResult == .loss else { return false }

        // 당일(KST) 판정: game_date_kst vs now_kst
        guard isSameDayKST(gameDate: context.gameDate, now: nowKST) else { return false }

        return true
    }

    /// `gameDate`(KST "yyyy-MM-dd")가 `now` 기준으로 오늘인지 판정한다.
    /// - Parameters:
    ///   - gameDate: 온톨로지 `game_date_kst` 입력값
    ///   - now: 온톨로지 `now_kst` 입력값 (테스트에서 주입 가능)
    public static func isSameDayKST(gameDate: String, now: Date = Date()) -> Bool {
        guard let gameDateParsed = kstDateFormatter.date(from: gameDate) else { return false }
        return kstCalendar.isDate(gameDateParsed, inSameDayAs: now)
    }

    /// 날짜 문자열이 오늘(KST 기준)인지 판정한다. (하위 호환 래퍼)
    @available(*, deprecated, renamed: "isSameDayKST(gameDate:now:)")
    public static func isTodayKST(dateString: String) -> Bool {
        isSameDayKST(gameDate: dateString, now: Date())
    }
}
