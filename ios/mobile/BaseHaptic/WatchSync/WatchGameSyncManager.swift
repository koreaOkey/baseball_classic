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
        guard WCSession.default.isReachable else { return }

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

        WCSession.default.sendMessage(message, replyHandler: nil) { error in
            print("[WatchGameSync] Failed to send game data: \(error.localizedDescription)")
        }
    }

    // MARK: - Send Haptic Event
    func sendHapticEvent(eventType: String, cursor: Int64? = nil) {
        guard WCSession.default.isReachable else { return }

        var message: [String: Any] = [
            "type": "haptic_event",
            "event_type": eventType,
            "timestamp": Date().timeIntervalSince1970
        ]
        if let cursor = cursor {
            message["event_cursor"] = cursor
        }

        WCSession.default.sendMessage(message, replyHandler: nil) { error in
            print("[WatchGameSync] Failed to send haptic event: \(error.localizedDescription)")
        }
    }

    // MARK: - Send Watch Sync Prompt
    func sendWatchSyncPrompt(gameId: String, homeTeam: String, awayTeam: String, myTeam: String) {
        guard WCSession.default.isReachable else { return }

        let message: [String: Any] = [
            "type": "watch_sync_prompt",
            "game_id": gameId,
            "home_team": homeTeam,
            "away_team": awayTeam,
            "my_team": myTeam,
            "updated_at": Date().timeIntervalSince1970
        ]

        WCSession.default.sendMessage(message, replyHandler: nil) { error in
            print("[WatchGameSync] Failed to send watch sync prompt: \(error.localizedDescription)")
        }
    }
}
