import Foundation
import WatchConnectivity

/// iOS → Watch 테마 동기화 매니저
/// Android의 WearThemeSyncManager에 대응
final class WatchThemeSyncManager {
    static func syncThemeToWatch(team: Team) {
        guard WCSession.default.activationState == .activated else { return }

        let context: [String: Any] = [
            "type": "theme_update",
            "my_team": team.rawValue,
            "updated_at": Date().timeIntervalSince1970
        ]

        // applicationContext는 워치가 연결될 때 자동으로 전달됨
        do {
            try WCSession.default.updateApplicationContext(context)
            print("[WatchThemeSync] Theme synced: \(team.rawValue)")
        } catch {
            print("[WatchThemeSync] Failed to sync theme: \(error.localizedDescription)")
        }

        // 워치가 현재 reachable이면 즉시 전달도 시도
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(context, replyHandler: nil) { _ in }
        }
    }
}
