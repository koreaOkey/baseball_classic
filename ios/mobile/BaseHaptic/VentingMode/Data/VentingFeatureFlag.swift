#if DEBUG
import Foundation

// MARK: - VentingFeatureFlag

/// 분풀이 모드 피처 플래그: DEBUG 빌드 + 로컬 토글의 이중 게이트.
///
/// 릴리즈 빌드에서는 전체 파일이 컴파일 제외되므로
/// `isEnabled`와 `setEnabled(_:)` 모두 존재하지 않는다.
enum VentingFeatureFlag {

    private static let userDefaultsKey = "venting_mode_enabled"

    /// 분풀이 모드 활성 여부.
    static var isEnabled: Bool {
        return UserDefaults.standard.bool(forKey: userDefaultsKey)
    }

    /// 로컬 토글 On/Off (DEBUG 전용).
    static func setEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: userDefaultsKey)
    }

    /// 토글 상태 반전 (디버그 개발 편의용).
    static func toggle() {
        setEnabled(!isEnabled)
    }
}
#endif
