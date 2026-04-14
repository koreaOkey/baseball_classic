import CoreGraphics

/// 워치 전용 모서리 반경 토큰. iPhone `AppRadius`와 동일 스케일.
enum WatchAppRadius {
    /// 2pt — 베이스 셀처럼 매우 작은 요소
    static let xxs: CGFloat = 2
    /// 8pt — 작은 버튼, 태그
    static let sm: CGFloat = 8
    /// 10pt — 스코어 카드 내부
    static let md10: CGFloat = 10
    /// 12pt — 카드 기본
    static let md: CGFloat = 12
    /// 14pt — 다이얼로그
    static let lg: CGFloat = 14
    /// 999pt — 완전히 둥근 배지
    static let pill: CGFloat = 999
}
