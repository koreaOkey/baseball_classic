import Foundation

// MARK: - VentingGateProviding

/// 분풀이 재도전 허용 여부를 판정하는 게이트 프로토콜.
///
/// Phase 1: `AlwaysAllowGate` — 광고 없이 항상 허용.
/// Phase 2: `RewardedAdGate` — 경기당 첫 완파 무료, 이후 광고 시청 필요.
/// 화면 코드는 이 프로토콜에만 의존한다.
protocol VentingGateProviding {
    /// 재도전 가능 여부를 확인한다. 광고 시청 등 선행 UI가 필요하면 `false`.
    func canRetry(gameId: String) async -> Bool

    /// 재도전을 요청한다. 광고 시청 등 선행 작업을 수행하고 허용 여부를 반환한다.
    func requestRetry(gameId: String) async -> Bool
}
