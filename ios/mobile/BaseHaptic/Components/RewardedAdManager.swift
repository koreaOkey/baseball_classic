import GoogleMobileAds
import UIKit

#if DEBUG
private let kRewardedAdUnitID = "ca-app-pub-3940256099942544/1712485313" // Google 테스트용
#else
private let kRewardedAdUnitID = "ca-app-pub-7935544989894266/6775093261" // 프로덕션
#endif

@MainActor
final class RewardedAdManager: ObservableObject {
    static let shared = RewardedAdManager()

    @Published var isLoading = false

    private init() {}

    /// 버튼 클릭 시 호출: 광고 로드 → 표시 → 시청 완료 시 onRewardEarned 콜백
    func loadAndShowAd(onRewardEarned: @escaping () -> Void) {
        guard !isLoading else { return }
        isLoading = true

        RewardedAd.load(with: kRewardedAdUnitID, request: Request()) { [weak self] ad, error in
            Task { @MainActor in
                guard let self else { return }
                self.isLoading = false

                if let error {
                    print("[RewardedAd] Load failed: \(error.localizedDescription)")
                    return
                }

                guard let ad,
                      let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                      let rootVC = windowScene.windows.first?.rootViewController else {
                    print("[RewardedAd] No ad or no root VC")
                    return
                }

                ad.present(from: rootVC) {
                    print("[RewardedAd] User earned reward")
                    onRewardEarned()
                }
            }
        }
    }
}
