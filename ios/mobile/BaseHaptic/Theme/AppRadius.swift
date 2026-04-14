import CoreGraphics

/// BaseHaptic 모서리 반경 토큰.
/// 상세 기준은 openspec/specs/design-system/spec.md 참고.
enum AppRadius {
    /// 8pt — 작은 버튼, 태그, 칩
    static let sm: CGFloat = 8
    /// 12pt — 카드 기본
    static let md: CGFloat = 12
    /// 16pt — 큰 카드, 모달
    static let lg: CGFloat = 16
    /// 20pt — 강조 컨테이너
    static let xl: CGFloat = 20
    /// 999pt — 완전히 둥근 배지 (pill)
    static let pill: CGFloat = 999
}
