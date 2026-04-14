import SwiftUI

/// BaseHaptic 타이포그래피 토큰.
/// 새 코드는 raw `.system(size:)` 대신 이 토큰을 사용한다.
/// 상세 기준은 openspec/specs/design-system/spec.md 참고.
enum AppFont {

    // MARK: - Display (Onboarding)
    /// 56pt — 온보딩 이모지/심볼
    static let display = Font.system(size: 56)

    // MARK: - Headings
    /// 36pt bold — 온보딩 대제목
    static let h1 = Font.system(size: 36, weight: .bold)
    /// 28pt bold — 섹션 대제목, 스코어 숫자
    static let h2 = Font.system(size: 28, weight: .bold)
    /// 24pt — 아이콘·심볼용 (weight 없음)
    static let h3 = Font.system(size: 24)
    /// 24pt bold — 섹션 제목
    static let h3Bold = Font.system(size: 24, weight: .bold)
    /// 24pt heavy — Dynamic Island leading 점수
    static let h3Heavy = Font.system(size: 24, weight: .heavy)
    /// 20pt — 아이콘 크기
    static let h4 = Font.system(size: 20)
    /// 20pt bold — 서브 제목
    static let h4Bold = Font.system(size: 20, weight: .bold)
    /// 18pt bold — 카드 제목
    static let h5Bold = Font.system(size: 18, weight: .bold)

    // MARK: - Body Large (16pt)
    /// 16pt — 아이콘·기본 본문
    static let bodyLg = Font.system(size: 16)
    /// 16pt medium — 버튼 라벨, 본문 강조
    static let bodyLgMedium = Font.system(size: 16, weight: .medium)
    /// 16pt bold — 카드 제목
    static let bodyLgBold = Font.system(size: 16, weight: .bold)

    // MARK: - Label (15pt)
    /// 15pt — 선택 가능 라벨
    static let label = Font.system(size: 15)
    /// 15pt medium
    static let labelMedium = Font.system(size: 15, weight: .medium)
    /// 15pt bold
    static let labelBold = Font.system(size: 15, weight: .bold)

    // MARK: - Body (14pt)
    /// 14pt — 본문
    static let body = Font.system(size: 14)
    /// 14pt medium — 본문 강조
    static let bodyMedium = Font.system(size: 14, weight: .medium)
    /// 14pt semibold
    static let bodySemibold = Font.system(size: 14, weight: .semibold)
    /// 14pt bold
    static let bodyBold = Font.system(size: 14, weight: .bold)

    // MARK: - Caption (13pt)
    /// 13pt — 상태 라벨
    static let caption = Font.system(size: 13)
    /// 13pt medium
    static let captionMedium = Font.system(size: 13, weight: .medium)
    /// 13pt semibold
    static let captionSemibold = Font.system(size: 13, weight: .semibold)
    /// 13pt bold
    static let captionBold = Font.system(size: 13, weight: .bold)

    // MARK: - Micro (12pt)
    /// 12pt — 캡션
    static let micro = Font.system(size: 12)
    /// 12pt medium
    static let microMedium = Font.system(size: 12, weight: .medium)
    /// 12pt bold
    static let microBold = Font.system(size: 12, weight: .bold)

    // MARK: - Tiny (11pt)
    /// 11pt — 미니 캡션
    static let tiny = Font.system(size: 11)
    /// 11pt bold
    static let tinyBold = Font.system(size: 11, weight: .bold)

    // MARK: - LiveActivity 전용
    /// 9pt — BSO 라벨
    static let liveActivity9 = Font.system(size: 9)
    /// 9pt bold
    static let liveActivity9Bold = Font.system(size: 9, weight: .bold)
    /// 10pt — 이벤트 라벨
    static let liveActivity10 = Font.system(size: 10)
    /// 10pt bold
    static let liveActivity10Bold = Font.system(size: 10, weight: .bold)
    /// 11pt — 투수/타자명
    static let liveActivity11 = Font.system(size: 11)
    /// 15pt bold — LockScreen 팀명
    static let liveActivity15Bold = Font.system(size: 15, weight: .bold)
    /// 24pt heavy — LockScreen 점수
    static let liveActivity24Heavy = Font.system(size: 24, weight: .heavy)
}
