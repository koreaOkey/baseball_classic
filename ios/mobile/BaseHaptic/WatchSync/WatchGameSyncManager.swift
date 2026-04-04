import Foundation
import WatchConnectivity

/// iOS → Watch 게임 데이터 전송 매니저
/// Android의 WearGameSyncManager에 대응
final class WatchGameSyncManager: NSObject, ObservableObject {
    static let shared = WatchGameSyncManager()

    private override init() {
        super.init()
    }

    // MARK: - Send Game Data
    func sendGameData(
        gameId: String,
        homeTeam: String,
        awayTeam: String,
        homeScore: Int,
        awayScore: Int,
        status: String = "LIVE",
        inning: String,
        ball: Int,
        strike: Int,
        out: Int,
        baseFirst: Bool,
        baseSecond: Bool,
        baseThird: Bool,
        pitcher: String,
        batter: String,
        myTeam: String,
        eventType: String? = nil
    ) {
        guard WCSession.default.activationState == .activated else {
            print("[WatchGameSync] Session not activated")
            return
        }

        var message: [String: Any] = [
            "type": "game_data",
            "game_id": gameId,
            "home_team": homeTeam,
            "away_team": awayTeam,
            "home_score": homeScore,
            "away_score": awayScore,
            "status": status,
            "inning": inning,
            "ball": ball,
            "strike": strike,
            "out": out,
            "base_first": baseFirst,
            "base_second": baseSecond,
            "base_third": baseThird,
            "pitcher": pitcher,
            "batter": batter,
            "my_team": myTeam,
            "updated_at": Date().timeIntervalSince1970
        ]

        if let eventType = eventType {
            message["event_type"] = eventType
        }

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(message, replyHandler: nil) { error in
                print("[WatchGameSync] sendMessage failed: \(error.localizedDescription)")
                // sendMessage 실패 시 applicationContext로 폴백
                self.updateApplicationContextWithGameData(message)
            }
        } else {
            print("[WatchGameSync] Watch not reachable, using applicationContext fallback")
            updateApplicationContextWithGameData(message)
        }
    }

    private func updateApplicationContextWithGameData(_ message: [String: Any]) {
        do {
            try WCSession.default.updateApplicationContext(message)
        } catch {
            print("[WatchGameSync] applicationContext update failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Send Haptic Event
    func sendHapticEvent(eventType: String, cursor: Int64? = nil) {
        let activationState = WCSession.default.activationState
        guard activationState == .activated else {
            print("📱 [WatchGameSync] sendHapticEvent SKIP - session not activated (state=\(activationState.rawValue))")
            return
        }

        let isReachable = WCSession.default.isReachable
        print("📱 [WatchGameSync] sendHapticEvent: \(eventType) | isReachable=\(isReachable) | isPaired=\(WCSession.default.isPaired) | isWatchAppInstalled=\(WCSession.default.isWatchAppInstalled)")

        var message: [String: Any] = [
            "type": "haptic_event",
            "event_type": eventType,
            "timestamp": Date().timeIntervalSince1970
        ]
        if let cursor = cursor {
            message["event_cursor"] = cursor
        }

        if isReachable {
            WCSession.default.sendMessage(message, replyHandler: nil) { error in
                print("📱 [WatchGameSync] sendMessage FAILED for haptic: \(error.localizedDescription)")
            }
            print("📱 [WatchGameSync] sendMessage sent for haptic: \(eventType)")
        } else {
            WCSession.default.transferUserInfo(message)
            print("📱 [WatchGameSync] transferUserInfo queued for haptic: \(eventType) (watch not reachable)")
        }
    }

    // MARK: - Send Watch Sync Prompt
    func sendWatchSyncPrompt(gameId: String, homeTeam: String, awayTeam: String, myTeam: String) {
        guard WCSession.default.activationState == .activated else { return }

        let message: [String: Any] = [
            "type": "watch_sync_prompt",
            "game_id": gameId,
            "home_team": homeTeam,
            "away_team": awayTeam,
            "my_team": myTeam,
            "updated_at": Date().timeIntervalSince1970
        ]

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(message, replyHandler: nil) { error in
                print("[WatchGameSync] Failed to send watch sync prompt: \(error.localizedDescription)")
            }
        } else {
            // 워치가 비활성 상태여도 프롬프트 전달
            WCSession.default.transferUserInfo(message)
        }
    }
}
