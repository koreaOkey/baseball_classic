import Foundation
import UIKit
import UserNotifications
import WatchConnectivity

/// WatchConnectivity 세션 관리 및 워치에서 오는 응답 처리
/// Android의 MobileDataLayerListenerService + WearWatchSyncBridge에 대응
final class PhoneConnectivityManager: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = PhoneConnectivityManager()

    @Published var watchSyncResponse: WatchSyncResponse?
    @Published var watchCompanionStatus: WatchCompanionStatus = .loading

    struct WatchSyncResponse {
        let gameId: String
        let accepted: Bool
    }

    private override init() {
        super.init()
    }

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // MARK: - WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if let error = error {
            print("[PhoneConnectivity] Activation failed: \(error.localizedDescription)")
        } else {
            print("[PhoneConnectivity] Activated: \(activationState.rawValue)")
        }
        refreshCompanionStatus()
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        refreshCompanionStatus()
    }

    func refreshCompanionStatus() {
        guard WCSession.isSupported() else {
            DispatchQueue.main.async { self.watchCompanionStatus = .pairedNone }
            return
        }
        let session = WCSession.default
        let next: WatchCompanionStatus
        if session.activationState != .activated {
            next = .loading
        } else if !session.isPaired {
            next = .pairedNone
        } else if !session.isWatchAppInstalled {
            next = .pairedNoApp
        } else {
            next = .installed
        }
        DispatchQueue.main.async {
            if self.watchCompanionStatus != next {
                self.watchCompanionStatus = next
            }
        }
    }

    /// 워치에서 보낸 메시지 수신 (sync response 등)
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        handleWatchMessage(message)
    }

    /// 워치에서 보낸 메시지 수신 (reply 포함)
    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        self.session(session, didReceiveMessage: message)
        replyHandler(["status": "ok"])
    }

    /// 워치에서 transferUserInfo로 보낸 메시지 수신
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        handleWatchMessage(userInfo)
    }

    private func handleWatchMessage(_ message: [String: Any]) {
        guard let type = message["type"] as? String else { return }

        switch type {
        case "watch_sync_response":
            let gameId = message["game_id"] as? String ?? ""
            let accepted = message["accepted"] as? Bool ?? false
            DispatchQueue.main.async {
                self.watchSyncResponse = WatchSyncResponse(gameId: gameId, accepted: accepted)
                // 워치에서 수락 시에도 보상형 광고 게이트를 통과해야 한다.
                // iOS 는 워치가 폰 앱을 포그라운드로 띄울 수 없으므로, 앱이 비활성이면
                // 로컬 알림을 게시하고 사용자가 탭해 진입할 때 광고 플로우로 잇는다.
                // (Android 의 MobileDataLayerListenerService.notifyPhoneAdRequired 대응)
                if accepted, UIApplication.shared.applicationState != .active {
                    Self.postAdRequiredNotification()
                }
            }
        case "watch_push_token":
            if let token = message["watch_token"] as? String, !token.isEmpty {
                print("[PhoneConnectivity] Received watch push token: \(token.prefix(16))...")
                UserDefaults.standard.set(token, forKey: "watch_apns_device_token")
                // 현재 구독 중인 경기가 있으면 즉시 백엔드에 등록
                if let gameId = UserDefaults.standard.string(forKey: "synced_game_id"),
                   !gameId.isEmpty {
                    let myTeam = UserDefaults.standard.string(forKey: "synced_my_team") ?? ""
                    Task {
                        await PushTokenManager.registerWatchToken(gameId: gameId, myTeam: myTeam)
                    }
                }
            }
        default:
            break
        }
    }

    func consumePendingResponse() -> WatchSyncResponse? {
        let response = watchSyncResponse
        watchSyncResponse = nil
        if response != nil {
            Self.removeAdRequiredNotification()
        }
        return response
    }

    // MARK: - 광고 확인 로컬 알림

    static let adRequiredNotificationId = "watch-sync-ad-required"

    private static func postAdRequiredNotification() {
        let content = UNMutableNotificationContent()
        content.title = "워치 관람 광고 확인"
        content.body = "탭하여 광고 관람 후 워치 관람이 시작됩니다."
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: adRequiredNotificationId,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    private static func removeAdRequiredNotification() {
        let center = UNUserNotificationCenter.current()
        center.removeDeliveredNotifications(withIdentifiers: [adRequiredNotificationId])
        center.removePendingNotificationRequests(withIdentifiers: [adRequiredNotificationId])
    }
}
