import GoogleMobileAds
import UIKit

@MainActor
enum RewardedAdFormat {
    case rewarded
    case rewardedInterstitial
}

/// 광고 시청 결과 — 보상 지급 정책을 호출부가 구분할 수 있도록 세분화
/// - rewardEarned: 광고를 끝까지 시청하여 보상 획득
/// - loadFailed: no-fill/네트워크 오류 등 광고 자체 실패 (사용자 잘못 아님 → 우아한 성능 저하로 통과 허용)
/// - dismissedWithoutReward: 사용자가 보상 전에 광고를 닫음 (보상 거부)
/// - busy: 이미 다른 광고가 진행 중 (중복 탭 — 아무것도 지급/소비하지 않아야 함)
enum RewardedAdOutcome {
    case rewardEarned
    case loadFailed
    case dismissedWithoutReward
    case busy
}

@MainActor
final class RewardedAdManager: NSObject, ObservableObject {
    static let shared = RewardedAdManager()

    @Published var isLoading = false

    /// 광고가 화면에 떠 있는 동안 true — 이 동안 들어온 새 요청은 busy 처리해서
    /// presentingAdDelegate 덮어쓰기(첫 요청의 종단 콜백 유실)를 막는다.
    private var isPresenting = false

    private var presentingAdDelegate: AdDelegate?

    /// 로드 세대 — 워치독과 로드 콜백 중 먼저 마감한 쪽만 유효하다.
    private var loadGeneration = 0

    /// 로드 콜백이 유실됐을 때 isLoading·스피너가 영구히 남지 않도록 하는 워치독 시한.
    private static let loadTimeoutSeconds: TimeInterval = 60

    private override init() { super.init() }

    #if DEBUG
    private static let rewardedTestAdUnitID = "ca-app-pub-3940256099942544/1712485313"
    private static let rewardedInterstitialTestAdUnitID = "ca-app-pub-3940256099942544/6978759866"
    #endif

    private static let themeStoreAdUnitProd = "ca-app-pub-7935544989894266/6775093261"
    private static let watchSyncAdUnitProd = "ca-app-pub-7935544989894266/6602098213"
    private static let liveActivityAdUnitProd = "ca-app-pub-7935544989894266/2584558049"
    private static let ventingRetryAdUnitProd = "ca-app-pub-7935544989894266/7560088484"

    static var themeStoreAdUnitID: String {
        #if DEBUG
        return rewardedTestAdUnitID
        #else
        return themeStoreAdUnitProd
        #endif
    }

    static var watchSyncAdUnitID: String {
        #if DEBUG
        return rewardedInterstitialTestAdUnitID
        #else
        return watchSyncAdUnitProd
        #endif
    }

    static var liveActivityAdUnitID: String {
        #if DEBUG
        return rewardedInterstitialTestAdUnitID
        #else
        return liveActivityAdUnitProd
        #endif
    }

    /// 빠따존 재도전 게이트 (Rewarded)
    static var ventingRetryAdUnitID: String {
        #if DEBUG
        return rewardedTestAdUnitID
        #else
        return ventingRetryAdUnitProd
        #endif
    }

    /// 광고 로드 → 표시 → dismiss 후 콜백.
    /// 결과는 `RewardedAdOutcome`으로 구분 — 호출부에서 보상 지급 정책을 판단한다.
    func loadAndShowAd(
        adUnitID: String,
        format: RewardedAdFormat = .rewarded,
        onComplete: @escaping (_ outcome: RewardedAdOutcome) -> Void
    ) {
        guard !isLoading, !isPresenting else {
            // 중복 탭 가드 — 보상도 대기 상태 소비도 하지 않도록 busy로 구분해서 알림
            onComplete(.busy)
            return
        }
        isLoading = true

        switch format {
        case .rewarded:
            loadAndShowRewardedAd(adUnitID: adUnitID, onComplete: onComplete)
        case .rewardedInterstitial:
            loadAndShowRewardedInterstitialAd(adUnitID: adUnitID, onComplete: onComplete)
        }
    }

    private func loadAndShowRewardedAd(
        adUnitID: String,
        onComplete: @escaping (_ outcome: RewardedAdOutcome) -> Void
    ) {
        let generation = beginLoadWatchdog(tag: "RewardedAd", onComplete: onComplete)
        RewardedAd.load(with: adUnitID, request: Request()) { [weak self] ad, error in
            Task { @MainActor in
                guard let self else {
                    onComplete(.loadFailed)
                    return
                }
                // 워치독이 먼저 마감했으면 늦게 도착한 로드는 폐기 (표시하면 안 됨)
                guard self.settleLoad(generation: generation) else { return }

                if let error {
                    print("[RewardedAd] Load failed: \(error.localizedDescription)")
                    onComplete(.loadFailed)
                    return
                }

                guard let ad,
                      let presenter = Self.currentAdPresenter() else {
                    print("[RewardedAd] No ad or no root VC")
                    onComplete(.loadFailed)
                    return
                }

                let delegate = AdDelegate { outcome in
                    self.isPresenting = false
                    self.presentingAdDelegate = nil
                    onComplete(outcome)
                }
                self.presentingAdDelegate = delegate
                ad.fullScreenContentDelegate = delegate
                self.isPresenting = true

                ad.present(from: presenter) {
                    print("[RewardedAd] User earned reward")
                    delegate.rewardEarned = true
                }
            }
        }
    }

    private func loadAndShowRewardedInterstitialAd(
        adUnitID: String,
        onComplete: @escaping (_ outcome: RewardedAdOutcome) -> Void
    ) {
        let generation = beginLoadWatchdog(tag: "RewardedInterstitialAd", onComplete: onComplete)
        RewardedInterstitialAd.load(with: adUnitID, request: Request()) { [weak self] ad, error in
            Task { @MainActor in
                guard let self else {
                    onComplete(.loadFailed)
                    return
                }
                // 워치독이 먼저 마감했으면 늦게 도착한 로드는 폐기 (표시하면 안 됨)
                guard self.settleLoad(generation: generation) else { return }

                if let error {
                    print("[RewardedInterstitialAd] Load failed: \(error.localizedDescription)")
                    onComplete(.loadFailed)
                    return
                }

                guard let ad,
                      let presenter = Self.currentAdPresenter() else {
                    print("[RewardedInterstitialAd] No ad or no root VC")
                    onComplete(.loadFailed)
                    return
                }

                let delegate = AdDelegate { outcome in
                    self.isPresenting = false
                    self.presentingAdDelegate = nil
                    onComplete(outcome)
                }
                self.presentingAdDelegate = delegate
                ad.fullScreenContentDelegate = delegate
                self.isPresenting = true

                ad.present(from: presenter) {
                    print("[RewardedInterstitialAd] User earned reward")
                    delegate.rewardEarned = true
                }
            }
        }
    }

    /// 로드 워치독 시작. 시한 내에 로드 콜백이 오지 않으면 loadFailed로 마감해
    /// isLoading·호출부 스피너가 영구히 남는 것을 막는다. 반환된 세대 값으로
    /// 로드 콜백과 워치독 중 먼저 도착한 쪽만 유효 처리한다.
    private func beginLoadWatchdog(
        tag: String,
        onComplete: @escaping (_ outcome: RewardedAdOutcome) -> Void
    ) -> Int {
        loadGeneration += 1
        let generation = loadGeneration
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.loadTimeoutSeconds) { [weak self] in
            Task { @MainActor in
                guard let self, self.settleLoad(generation: generation) else { return }
                print("[\(tag)] Load watchdog fired — treating as loadFailed")
                onComplete(.loadFailed)
            }
        }
        return generation
    }

    /// 해당 세대의 로드를 마감 처리. 이미 다른 쪽(워치독/로드 콜백)이 마감했으면 false.
    private func settleLoad(generation: Int) -> Bool {
        guard loadGeneration == generation else { return false }
        loadGeneration += 1
        isLoading = false
        return true
    }

    private static func currentAdPresenter() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        let rootVC = scene?.windows.first { $0.isKeyWindow }?.rootViewController
            ?? scene?.windows.first?.rootViewController
        return rootVC?.topMostPresentedViewController()
    }
}

private extension UIViewController {
    func topMostPresentedViewController() -> UIViewController {
        if let presentedViewController, !presentedViewController.isBeingDismissed {
            return presentedViewController.topMostPresentedViewController()
        }
        if let navigationController = self as? UINavigationController,
           let visibleViewController = navigationController.visibleViewController {
            return visibleViewController.topMostPresentedViewController()
        }
        if let tabBarController = self as? UITabBarController,
           let selectedViewController = tabBarController.selectedViewController {
            return selectedViewController.topMostPresentedViewController()
        }
        return self
    }
}

private final class AdDelegate: NSObject, FullScreenContentDelegate {
    var rewardEarned = false
    private var hasFired = false
    private let onDismissOrFail: (RewardedAdOutcome) -> Void

    init(onDismissOrFail: @escaping (RewardedAdOutcome) -> Void) {
        self.onDismissOrFail = onDismissOrFail
        super.init()
    }

    /// present 실패와 dismiss가 겹쳐 들어와도 종단 콜백은 한 번만 전달한다
    /// (게이트가 continuation을 resume하므로 두 번째 호출은 크래시).
    private func fire(_ outcome: RewardedAdOutcome) {
        guard !hasFired else { return }
        hasFired = true
        onDismissOrFail(outcome)
    }

    func adDidDismissFullScreenContent(_ ad: any FullScreenPresentingAd) {
        // 보상 없이 닫힘 = 사용자가 광고를 중간에 종료한 것
        fire(rewardEarned ? .rewardEarned : .dismissedWithoutReward)
    }

    func ad(_ ad: any FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: any Error) {
        print("[RewardedAd] Present failed: \(error.localizedDescription)")
        // 표시 실패는 광고 측 문제 → loadFailed로 취급 (사용자 잘못 아님)
        fire(.loadFailed)
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
