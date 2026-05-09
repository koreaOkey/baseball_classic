import UIKit
import UserNotifications
import GoogleMobileAds

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        registerNotificationCategories()
        registerForPushNotifications()
        MobileAds.shared.start()
        return true
    }

    /// 경기 시작 알림에 "관람하기" 액션 버튼을 노출하기 위한 카테고리 등록.
    /// APNs payload 의 aps.category="OPEN_LIVE_GAME" 일 때 적용된다.
    private func registerNotificationCategories() {
        let watchAction = UNNotificationAction(
            identifier: "OPEN_LIVE_GAME_ACTION",
            title: "관람하기",
            options: [.foreground]
        )
        let category = UNNotificationCategory(
            identifier: "OPEN_LIVE_GAME",
            actions: [watchAction],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
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
        Task { await TeamSubscriptionManager.syncIfNeeded() }
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
        guard let eventType = userInfo["event_type"] as? String, !eventType.isEmpty else {
            // 이벤트가 아닌 경우: 게임 상태 업데이트만 시도
            if let gameId = userInfo["game_id"] as? String, !gameId.isEmpty {
                sendGameDataToWatch(from: userInfo)
                completionHandler(.newData)
            } else {
                completionHandler(.noData)
            }
            return
        }

        // 햅틱 이벤트 워치로 전달 (마스터 스위치 OFF 시 차단)
        let liveHapticEnabled = UserDefaults.standard.bool(forKey: "live_haptic_enabled")
        if liveHapticEnabled {
            let cursor = userInfo["event_cursor"] as? Int64
            WatchGameSyncManager.shared.sendHapticEvent(eventType: eventType, cursor: cursor)
        }

        // 게임 상태도 함께 왔으면 워치 UI 업데이트
        sendGameDataToWatch(from: userInfo)

        completionHandler(.newData)
    }

    private func sendGameDataToWatch(from userInfo: [AnyHashable: Any]) {
        guard let gameId = userInfo["game_id"] as? String,
              let homeTeam = userInfo["home_team"] as? String,
              let awayTeam = userInfo["away_team"] as? String else { return }

        let homeDisplay = Team.fromBackendName(homeTeam).teamName
        let awayDisplay = Team.fromBackendName(awayTeam).teamName

        WatchGameSyncManager.shared.sendGameData(
            gameId: gameId,
            homeTeam: homeDisplay,
            awayTeam: awayDisplay,
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
            pitcherPitchCount: userInfo["pitcher_pitch_count"] as? Int,
            myTeam: userInfo["my_team"] as? String ?? ""
        )
    }

    // MARK: - Foreground에서 알림 표시 (silent push는 여기 안 옴)

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        return [.banner, .sound, .badge]
    }

    // MARK: - 알림 탭 시 라이브 화면 진입

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let gameId = userInfo["game_id"] as? String, !gameId.isEmpty {
            var forwardInfo: [String: Any] = ["game_id": gameId]
            if let home = userInfo["home_team"] as? String { forwardInfo["home_team"] = home }
            if let away = userInfo["away_team"] as? String { forwardInfo["away_team"] = away }
            NotificationCenter.default.post(
                name: .openLiveGameRequested,
                object: nil,
                userInfo: forwardInfo
            )
        }
        completionHandler()
    }
}

extension Notification.Name {
    /// 푸시 알림 탭 시 라이브 화면으로 이동 요청
    static let openLiveGameRequested = Notification.Name("openLiveGameRequested")
}
