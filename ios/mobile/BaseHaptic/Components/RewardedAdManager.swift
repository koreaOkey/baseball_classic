import GoogleMobileAds
import UIKit

@MainActor
final class RewardedAdManager: NSObject, ObservableObject {
    static let shared = RewardedAdManager()

    @Published var isLoading = false

    private var pendingCompletion: ((Bool) -> Void)?
    private var rewardEarnedForCurrentAd = false
    private var presentingAdDelegate: AdDelegate?

    private override init() { super.init() }

    #if DEBUG
    private static let testAdUnitID = "ca-app-pub-3940256099942544/1712485313"
    #endif

    private static let themeStoreAdUnitProd = "ca-app-pub-7935544989894266/6775093261"
    private static let watchSyncAdUnitProd = "ca-app-pub-7935544989894266/6602098213"
    private static let liveActivityAdUnitProd = "ca-app-pub-7935544989894266/2584558049"

    static var themeStoreAdUnitID: String {
        #if DEBUG
        return testAdUnitID
        #else
        return themeStoreAdUnitProd
        #endif
    }

    static var watchSyncAdUnitID: String {
        #if DEBUG
        return testAdUnitID
        #else
        return watchSyncAdUnitProd
        #endif
    }

    static var liveActivityAdUnitID: String {
        #if DEBUG
        return testAdUnitID
        #else
        return liveActivityAdUnitProd
        #endif
    }

    /// 광고 로드 → 표시 → dismiss 후 콜백.
    /// `rewardEarned`: 사용자가 광고를 끝까지 시청했으면 true. 로드/표시 실패도 콜백을 호출하며 false.
    func loadAndShowAd(adUnitID: String, onComplete: @escaping (_ rewardEarned: Bool) -> Void) {
        guard !isLoading else {
            onComplete(false)
            return
        }
        isLoading = true

        RewardedAd.load(with: adUnitID, request: Request()) { [weak self] ad, error in
            Task { @MainActor in
                guard let self else {
                    onComplete(false)
                    return
                }
                self.isLoading = false

                if let error {
                    print("[RewardedAd] Load failed: \(error.localizedDescription)")
                    onComplete(false)
                    return
                }

                guard let ad,
                      let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                      let rootVC = windowScene.windows.first?.rootViewController else {
                    print("[RewardedAd] No ad or no root VC")
                    onComplete(false)
                    return
                }

                let delegate = AdDelegate { rewardEarned in
                    self.presentingAdDelegate = nil
                    onComplete(rewardEarned)
                }
                self.presentingAdDelegate = delegate
                ad.fullScreenContentDelegate = delegate

                ad.present(from: rootVC) {
                    print("[RewardedAd] User earned reward")
                    delegate.rewardEarned = true
                }
            }
        }
    }
}

private final class AdDelegate: NSObject, FullScreenContentDelegate {
    var rewardEarned = false
    private let onDismissOrFail: (Bool) -> Void

    init(onDismissOrFail: @escaping (Bool) -> Void) {
        self.onDismissOrFail = onDismissOrFail
        super.init()
    }

    func adDidDismissFullScreenContent(_ ad: any FullScreenPresentingAd) {
        onDismissOrFail(rewardEarned)
    }

    func ad(_ ad: any FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: any Error) {
        print("[RewardedAd] Present failed: \(error.localizedDescription)")
        onDismissOrFail(false)
    }
}

enum WatchSyncAdLedger {
    private static let keyPrefix = "watchSyncAdViewed."

    static func hasViewed(gameId: String) -> Bool {
        guard !gameId.isEmpty else { return false }
        return UserDefaults.standard.bool(forKey: keyPrefix + gameId)
    }

    static func markViewed(gameId: String) {
        guard !gameId.isEmpty else { return }
        UserDefaults.standard.set(true, forKey: keyPrefix + gameId)
    }
}

enum LiveActivityAdLedger {
    private static let keyPrefix = "liveActivityAdViewed."

    static func hasViewed(gameId: String) -> Bool {
        guard !gameId.isEmpty else { return false }
        return UserDefaults.standard.bool(forKey: keyPrefix + gameId)
    }

    static func markViewed(gameId: String) {
        guard !gameId.isEmpty else { return }
        UserDefaults.standard.set(true, forKey: keyPrefix + gameId)
    }
}
