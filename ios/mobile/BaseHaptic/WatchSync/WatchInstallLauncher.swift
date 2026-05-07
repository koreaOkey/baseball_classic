import UIKit

enum WatchInstallLauncher {
    /// 시스템 Apple Watch 앱(App Store 탭)을 호출하여 사용 가능한 앱 목록으로 사용자를 보냄.
    /// 동작 가능 여부를 반환 — false 면 호출자가 fallback 안내를 표시해야 한다.
    @discardableResult
    static func openWatchAppStore() -> Bool {
        guard let url = URL(string: "itms-watch://") else { return false }
        guard UIApplication.shared.canOpenURL(url) else { return false }
        UIApplication.shared.open(url)
        return true
    }

    /// 시스템 Apple Watch 앱을 일반적으로 호출. 현재는 itms-watch:// 와 동일한 진입점을 사용한다
    /// (Apple 공식 일반 Watch 앱 호출용 URL scheme이 없음).
    @discardableResult
    static func openCompanionWatchApp() -> Bool {
        return openWatchAppStore()
    }
}
