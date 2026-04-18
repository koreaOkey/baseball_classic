import Foundation
import WatchConnectivity

/// iOS → Watch 테마 동기화 매니저
/// Android의 WearThemeSyncManager에 대응
final class WatchThemeSyncManager {
    static func syncThemeToWatch(team: Team) {
        syncToWatch(context: [
            "type": "theme_update",
            "my_team": team.rawValue,
            "updated_at": Date().timeIntervalSince1970
        ])
    }

    static func syncStoreThemeToWatch(themeId: String) {
        syncToWatch(context: [
            "type": "store_theme_update",
            "theme_id": themeId,
            "updated_at": Date().timeIntervalSince1970
        ])
    }

    private static func syncToWatch(context: [String: Any]) {
        guard WCSession.default.activationState == .activated else { return }

        do {
            try WCSession.default.updateApplicationContext(context)
            print("[WatchThemeSync] Synced: \(context)")
        } catch {
            print("[WatchThemeSync] Failed to sync: \(error.localizedDescription)")
        }

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(context, replyHandler: nil) { _ in }
        }
    }
}
