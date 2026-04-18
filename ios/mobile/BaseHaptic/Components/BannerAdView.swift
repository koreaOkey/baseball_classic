import SwiftUI
import GoogleMobileAds

#if DEBUG
private let kBannerAdUnitID = "ca-app-pub-3940256099942544/2934735716" // Google 테스트용
#else
private let kBannerAdUnitID = "ca-app-pub-7935544989894266/6856563322" // 프로덕션
#endif

struct BannerAdView: UIViewRepresentable {
    func makeUIView(context: Context) -> BannerView {
        let banner = BannerView(adSize: AdSizeBanner)
        banner.adUnitID = kBannerAdUnitID
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first?.rootViewController {
            banner.rootViewController = rootVC
        }
        banner.load(Request())
        return banner
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}
}
