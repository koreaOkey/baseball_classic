import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        registerForPushNotifications()
        return true
    }

    // MARK: - Push Registration

    private func registerForPushNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        print("[APNs] Device token: \(token)")
        // 토큰을 UserDefaults에 저장 (백엔드 등록은 경기 구독 시 수행)
        UserDefaults.standard.set(token, forKey: "apns_device_token")
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[APNs] Failed to register: \(error.localizedDescription)")
    }

    // MARK: - Background Push 수신 → 워치 전달

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        let appState = UIApplication.shared.applicationState
        let stateStr = appState == .active ? "active" : appState == .background ? "background" : "inactive"
        print("📱 [APNs] Push received | appState=\(stateStr) | payload=\(userInfo)")

        guard let eventType = userInfo["event_type"] as? String, !eventType.isEmpty else {
            // 이벤트가 아닌 경우: 게임 상태 업데이트만 시도
            if let gameId = userInfo["game_id"] as? String, !gameId.isEmpty {
                print("📱 [APNs] No event_type, forwarding game_data only (gameId=\(gameId))")
                sendGameDataToWatch(from: userInfo)
                completionHandler(.newData)
            } else {
                print("📱 [APNs] No event_type, no game_id → noData")
                completionHandler(.noData)
            }
            return
        }

        // 햅틱 이벤트 워치로 전달
        print("📱 [APNs] Forwarding haptic event to watch: \(eventType)")
        let cursor = userInfo["event_cursor"] as? Int64
        WatchGameSyncManager.shared.sendHapticEvent(eventType: eventType, cursor: cursor)

        // 게임 상태도 함께 왔으면 워치 UI 업데이트
        sendGameDataToWatch(from: userInfo)

        completionHandler(.newData)
    }

    private func sendGameDataToWatch(from userInfo: [AnyHashable: Any]) {
        guard let gameId = userInfo["game_id"] as? String,
              let homeTeam = userInfo["home_team"] as? String,
              let awayTeam = userInfo["away_team"] as? String else { return }

        WatchGameSyncManager.shared.sendGameData(
            gameId: gameId,
            homeTeam: homeTeam,
            awayTeam: awayTeam,
            homeScore: userInfo["home_score"] as? Int ?? 0,
            awayScore: userInfo["away_score"] as? Int ?? 0,
            status: userInfo["status"] as? String ?? "LIVE",
            inning: userInfo["inning"] as? String ?? "",
            ball: userInfo["ball"] as? Int ?? 0,
            strike: userInfo["strike"] as? Int ?? 0,
            out: userInfo["out"] as? Int ?? 0,
            baseFirst: userInfo["base_first"] as? Bool ?? false,
            baseSecond: userInfo["base_second"] as? Bool ?? false,
            baseThird: userInfo["base_third"] as? Bool ?? false,
            pitcher: userInfo["pitcher"] as? String ?? "",
            batter: userInfo["batter"] as? String ?? "",
            myTeam: userInfo["my_team"] as? String ?? ""
        )
    }

    // MARK: - Foreground에서 알림 표시 (silent push는 여기 안 옴)

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        return []
    }
}
