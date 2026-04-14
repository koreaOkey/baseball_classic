import CoreGraphics

/// BaseHaptic 스페이싱 토큰 (패딩·마진·spacing 공통).
/// 4의 배수 기반. 새 코드는 raw 숫자 대신 이 토큰을 사용한다.
/// 상세 기준은 openspec/specs/design-system/spec.md 참고.
enum AppSpacing {
    /// 2pt — 미세 조정 (점수 VStack 내부 등)
    static let xxs: CGFloat = 2
    /// 4pt — 아이콘 gap, 라벨 간격
    static let xs: CGFloat = 4
    /// 8pt — 기본 요소 간격, 작은 버튼
    static let sm: CGFloat = 8
    /// 12pt — 카드 내부 보조 여백
    static let md: CGFloat = 12
    /// 16pt — 카드 내부 기본 여백
    static let lg: CGFloat = 16
    /// 20pt — 큰 카드 여백
    static let xl: CGFloat = 20
    /// 24pt — 화면 가장자리 여백
    static let xxl: CGFloat = 24
    /// 32pt — 섹션 간격
    static let xxxl: CGFloat = 32
    /// 48pt — 버튼 높이, 큰 아이콘 프레임
    static let buttonHeight: CGFloat = 48
    /// 80pt — ScrollView 하단 안전 영역
    static let bottomSafeSpacer: CGFloat = 80
}
