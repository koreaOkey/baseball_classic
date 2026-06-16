import UIKit
import UserNotifications
import GoogleMobileAds
import WatchConnectivity

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

    func applicationWillTerminate(_ application: UIApplication) {
        LiveActivityManager.shared.endPreviewActivities()
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
                LiveActivityManager.shared.updateFromPushPayload(userInfo)
                completionHandler(.newData)
            } else {
                completionHandler(.noData)
            }
            return
        }

        // 햅틱 이벤트 워치로 전달 (Watch 채널 이벤트 필터 가드)
        if EventFilterGate.isAllowed(eventType: eventType, channel: .watch),
           isWatchSyncActive(for: userInfo["game_id"] as? String) {
            let cursor = userInfo["event_cursor"] as? Int64
            WatchGameSyncManager.shared.sendHapticEvent(eventType: eventType, cursor: cursor)
        }

        // 게임 상태도 함께 왔으면 워치 UI 업데이트
        sendGameDataToWatch(from: userInfo)
        LiveActivityManager.shared.updateFromPushPayload(userInfo)

        completionHandler(.newData)
    }

    private func sendGameDataToWatch(from userInfo: [AnyHashable: Any]) {
        guard let gameId = userInfo["game_id"] as? String,
              let homeTeam = userInfo["home_team"] as? String,
              let awayTeam = userInfo["away_team"] as? String else { return }
        guard isWatchSyncActive(for: gameId) else { return }

        let displayStyle = TeamDisplayNameStyle.fromString(
            UserDefaults.standard.string(forKey: "team_display_name_style")
        )
        let homeDisplay = Team.fromBackendName(homeTeam).displayName(style: displayStyle)
        let awayDisplay = Team.fromBackendName(awayTeam).displayName(style: displayStyle)

        // 누적 투구수: 푸시 페이로드 sentinel = -1 → nil. 키 자체가 빠진 (구버전 백엔드)
        // 페이로드는 마지막 전송값으로 폴백해 워치 UI 깜빡임을 방지.
        let pitcherPitchCount: Int? = {
            if let raw = userInfo["pitcher_pitch_count"] as? Int {
                return raw >= 0 ? raw : nil
            }
            return WatchGameSyncManager.shared.cachedPitcherPitchCount(forGameId: gameId)
        }()

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
            pitcherPitchCount: pitcherPitchCount,
            myTeam: userInfo["my_team"] as? String ?? ""
        )
    }

    private func isWatchSyncActive(for gameId: String?) -> Bool {
        guard let gameId, !gameId.isEmpty else { return false }
        return UserDefaults.standard.string(forKey: "synced_game_id") == gameId
    }

    // MARK: - Foreground에서 알림 표시 (silent push는 여기 안 옴)

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        let userInfo = notification.request.content.userInfo
        let eventType = userInfo["event_type"] as? String

        // 잠금화면 이벤트 필터: 미선택 이벤트는 폰 노티 자체를 노출 안 함
        if !EventFilterGate.isAllowed(eventType: eventType, channel: .lockScreen) {
            return []
        }

        // 워치 우선 햅틱 정책: 워치 페어링·설치 상태면 폰 소리/진동 suppress (배너만 노출)
        let session = WCSession.default
        let watchActive = session.activationState == .activated
            && session.isPaired
            && session.isWatchAppInstalled
        if watchActive {
            return [.banner, .badge]
        }
        return [.banner, .sound, .badge]
    }

    // MARK: - 알림 탭 시 홈 화면 진입

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
    /// 푸시 알림 탭 시 홈 화면으로 이동 요청
    static let openLiveGameRequested = Notification.Name("openLiveGameRequested")
}
