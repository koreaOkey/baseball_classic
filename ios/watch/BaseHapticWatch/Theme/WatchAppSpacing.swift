import CoreGraphics

/// 워치 전용 정적 스페이싱 토큰.
/// 디바이스 크기별 반응형 값은 `WatchUiProfile`을 사용하고,
/// 프로파일과 무관한 정적 상수는 여기서 가져온다.
/// iPhone `AppSpacing`과 동일 스케일.
enum WatchAppSpacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 24
    static let xxxl: CGFloat = 32
}
