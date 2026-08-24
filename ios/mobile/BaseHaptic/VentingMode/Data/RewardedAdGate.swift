import Foundation

// MARK: - RewardedAdGate

/// `VentingGateProviding` 운영 구현체 — 경기당 첫 완파는 무료(진입 게이트 없음),
/// 재도전(재파괴)은 매번 Rewarded 광고 1회 시청 후 허용한다.
///
/// 광고 로드 실패(no-fill·네트워크 등)는 사용자 귀책이 아니므로 재도전을 허용한다
/// (테마 스토어·워치 동기화 게이트와 동일 정책).
final class RewardedAdGate: VentingGateProviding {

    func canRetry(gameId: String) async -> Bool {
        // 완파 화면의 재도전은 항상 두 번째 이후 완파 → 광고 필요
        return false
    }

    @MainActor
    func requestRetry(gameId: String) async -> VentingRetryVerdict {
        await withCheckedContinuation { continuation in
            RewardedAdManager.shared.loadAndShowAd(
                adUnitID: RewardedAdManager.ventingRetryAdUnitID
            ) { outcome in
                switch outcome {
                case .rewardEarned:
                    continuation.resume(returning: .adRewarded)
                case .loadFailed:
                    continuation.resume(returning: .allowedFree)
                case .dismissedWithoutReward, .busy:
                    continuation.resume(returning: .denied)
                }
            }
        }
    }
}
