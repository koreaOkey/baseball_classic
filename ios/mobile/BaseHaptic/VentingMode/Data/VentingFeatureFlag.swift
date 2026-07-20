import Foundation

// MARK: - VentingFeatureFlag

/// 분풀이 모드 피처 플래그: DEBUG 빌드 + 로컬 토글의 이중 게이트.
///
/// 릴리즈 빌드에서는 `isEnabled`가 항상 `false`를 반환하며,
/// `setEnabled(_:)` 함수 자체가 컴파일 대상에서 제외된다.
enum VentingFeatureFlag {

    private static let userDefaultsKey = "venting_mode_enabled"

    /// 분풀이 모드 활성 여부.
    /// - DEBUG: `UserDefaults.venting_mode_enabled` 값 참조
    /// - Release: 항상 `false`
    static var isEnabled: Bool {
        #if DEBUG
        return UserDefaults.standard.bool(forKey: userDefaultsKey)
        #else
        return false
        #endif
    }

    #if DEBUG
    /// 로컬 토글 On/Off (DEBUG 전용).
    static func setEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: userDefaultsKey)
    }

    /// 토글 상태 반전 (디버그 개발 편의용).
    static func toggle() {
        setEnabled(!isEnabled)
    }
    #endif
}
