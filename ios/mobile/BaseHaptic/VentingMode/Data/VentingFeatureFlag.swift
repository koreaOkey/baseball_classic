import Foundation

// MARK: - VentingFeatureFlag

/// 분풀이 모드 피처 플래그: 릴리즈 포함 기본 ON.
///
/// 토글은 설정의 DEBUG 섹션에서만 노출되는 로컬 킬스위치 —
/// 릴리즈 사용자는 항상 ON이다.
enum VentingFeatureFlag {

    private static let userDefaultsKey = "venting_mode_enabled"

    /// 분풀이 모드 활성 여부 (저장값이 없으면 기본 ON).
    static var isEnabled: Bool {
        guard UserDefaults.standard.object(forKey: userDefaultsKey) != nil else { return true }
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
