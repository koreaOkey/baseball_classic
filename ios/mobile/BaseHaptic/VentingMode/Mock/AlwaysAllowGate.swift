import Foundation

// MARK: - AlwaysAllowGate

/// `VentingGateProviding` 디버그/프리뷰 구현체.
/// 광고 없이 항상 재도전을 허용한다 (운영은 `RewardedAdGate`).
final class AlwaysAllowGate: VentingGateProviding {

    func canRetry(gameId: String) async -> Bool {
        return true
    }

    func requestRetry(gameId: String) async -> VentingRetryVerdict {
        return .allowedFree
    }
}
