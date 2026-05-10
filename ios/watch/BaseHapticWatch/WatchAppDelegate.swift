import WatchKit
import UserNotifications

class WatchAppDelegate: NSObject, WKApplicationDelegate, UNUserNotificationCenterDelegate {

    func applicationDidFinishLaunching() {
        UNUserNotificationCenter.current().delegate = self
        registerForPushNotifications()
    }

    // MARK: - Push Registration

    private func registerForPushNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else {
                print("⌚ [APNs] Push authorization denied")
                return
            }
            DispatchQueue.main.async {
                WKApplication.shared().registerForRemoteNotifications()
            }
        }
    }

    func didRegisterForRemoteNotifications(withDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        print("⌚ [APNs] Watch device token: \(token)")
        UserDefaults.standard.set(token, forKey: "watch_apns_device_token")

        // iPhone에 토큰 전달 → iPhone이 백엔드에 등록
        WatchConnectivityManager.shared.sendWatchPushToken(token)
    }

    func didFailToRegisterForRemoteNotificationsWithError(_ error: Error) {
        print("⌚ [APNs] Watch push registration failed: \(error.localizedDescription)")
    }

    // MARK: - Push 수신

    func didReceiveRemoteNotification(_ userInfo: [AnyHashable: Any],
                                       fetchCompletionHandler completionHandler: @escaping (WKBackgroundFetchResult) -> Void) {
        print("⌚ [APNs] Push received: \(userInfo)")

        guard let eventType = userInfo["event_type"] as? String, !eventType.isEmpty else {
            // 이벤트 없이 게임 상태만 온 경우
            if let gameData = parseGameData(from: userInfo) {
                DispatchQueue.main.async {
                    WatchConnectivityManager.shared.handleDirectPushGameData(gameData)
                }
                completionHandler(.newData)
            } else {
                completionHandler(.noData)
            }
            return
        }

        // 햅틱 이벤트 처리
        print("⌚ [APNs] Haptic event from push: \(eventType)")
        DispatchQueue.main.async {
            WatchConnectivityManager.shared.handleDirectPushHapticEvent(eventType: eventType)
            // 게임 상태도 함께 왔으면 UI 업데이트
            if let gameData = self.parseGameData(from: userInfo) {
                WatchConnectivityManager.shared.handleDirectPushGameData(gameData)
            }
        }

        completionHandler(.newData)
    }

    // MARK: - Foreground 알림

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        // silent push이므로 표시 안 함
        return []
    }

    // MARK: - Parse

    private func parseGameData(from userInfo: [AnyHashable: Any]) -> [String: Any]? {
        guard let gameId = userInfo["game_id"] as? String, !gameId.isEmpty else { return nil }

        // 누적 투구수: push 에 키 자체가 빠진 (구버전 백엔드) 페이로드에서는 워치 메모리상의
        // 마지막 값을 유지해 폰 백그라운드 동안 투구수가 사라지는 회귀를 방지.
        // 키가 있으면 sentinel(-1)→nil 규칙은 디코더가 처리.
        let pitchCountValue: Int = {
            if let raw = userInfo["pitcher_pitch_count"] as? Int { return raw }
            if let cached = WatchConnectivityManager.shared.gameData,
               cached.gameId == gameId,
               let cachedCount = cached.pitcherPitchCount {
                return cachedCount
            }
            return -1
        }()

        return [
            "type": "game_data",
            "game_id": gameId,
            "home_team": userInfo["home_team"] as? String ?? "",
            "away_team": userInfo["away_team"] as? String ?? "",
            "home_score": userInfo["home_score"] as? Int ?? 0,
            "away_score": userInfo["away_score"] as? Int ?? 0,
            "status": userInfo["status"] as? String ?? "LIVE",
            "inning": userInfo["inning"] as? String ?? "",
            "ball": userInfo["ball"] as? Int ?? 0,
            "strike": userInfo["strike"] as? Int ?? 0,
            "out": userInfo["out"] as? Int ?? 0,
            "base_first": userInfo["base_first"] as? Bool ?? false,
            "base_second": userInfo["base_second"] as? Bool ?? false,
            "base_third": userInfo["base_third"] as? Bool ?? false,
            "pitcher": userInfo["pitcher"] as? String ?? "",
            "batter": userInfo["batter"] as? String ?? "",
            "pitcher_pitch_count": pitchCountValue,
            "my_team": userInfo["my_team"] as? String ?? "",
            "updated_at": Date().timeIntervalSince1970,
        ]
    }
}
