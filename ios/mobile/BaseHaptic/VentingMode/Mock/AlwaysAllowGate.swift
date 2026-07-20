#if DEBUG
import Foundation

// MARK: - AlwaysAllowGate

/// `VentingGateProviding` Phase 1 구현체.
/// 광고 없이 항상 재도전을 허용한다.
///
/// Phase 2에서 `RewardedAdGate` (AdMob Rewarded 광고)로 교체 시
/// 이 파일만 교체하면 되며, 화면/뷰모델 코드는 무수정이다.
final class AlwaysAllowGate: VentingGateProviding {

    func canRetry(gameId: String) async -> Bool {
        return true
    }

    func requestRetry(gameId: String) async -> Bool {
        return true
    }
}
#endif
