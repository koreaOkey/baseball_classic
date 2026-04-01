import Foundation
import WatchConnectivity

/// WatchConnectivity 세션 관리 및 워치에서 오는 응답 처리
/// Android의 MobileDataLayerListenerService + WearWatchSyncBridge에 대응
final class PhoneConnectivityManager: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = PhoneConnectivityManager()

    @Published var watchSyncResponse: WatchSyncResponse?

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
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    /// 워치에서 보낸 메시지 수신 (sync response 등)
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        guard let type = message["type"] as? String else { return }

        switch type {
        case "watch_sync_response":
            let gameId = message["game_id"] as? String ?? ""
            let accepted = message["accepted"] as? Bool ?? false
            DispatchQueue.main.async {
                self.watchSyncResponse = WatchSyncResponse(gameId: gameId, accepted: accepted)
            }
        default:
            break
        }
    }

    /// 워치에서 보낸 메시지 수신 (reply 포함)
    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        self.session(session, didReceiveMessage: message)
        replyHandler(["status": "ok"])
    }

    /// 워치에서 transferUserInfo로 보낸 메시지 수신
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        guard let type = userInfo["type"] as? String else { return }

        switch type {
        case "watch_sync_response":
            let gameId = userInfo["game_id"] as? String ?? ""
            let accepted = userInfo["accepted"] as? Bool ?? false
            DispatchQueue.main.async {
                self.watchSyncResponse = WatchSyncResponse(gameId: gameId, accepted: accepted)
            }
        default:
            break
        }
    }

    func consumePendingResponse() -> WatchSyncResponse? {
        let response = watchSyncResponse
        watchSyncResponse = nil
        return response
    }
}
