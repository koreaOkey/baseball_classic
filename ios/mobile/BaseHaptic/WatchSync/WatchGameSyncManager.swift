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
    private static let lastGameDataKey = "last_watch_game_data"

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
        pitcherPitchCount: Int? = nil,
        myTeam: String,
        eventType: String? = nil
    ) {
        var payload: [String: Any] = [
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
            "pitcher_pitch_count": pitcherPitchCount ?? -1,
            "my_team": myTeam
        ]
        if let eventType = eventType {
            payload["event_type"] = eventType
        }

        // 마스터 OFF여도 phone-side 캐시는 항상 갱신 (ON 복원 시 즉시 push 용)
        cacheLastGameData(payload)

        guard WCSession.default.activationState == .activated else {
            print("[WatchGameSync] Session not activated")
            return
        }

        let liveHapticEnabled = (UserDefaults.standard.object(forKey: "live_haptic_enabled") as? Bool) ?? true
        guard liveHapticEnabled else {
            print("[WatchGameSync] live_haptic_enabled=false, skipping game_data send")
            return
        }

        deliverGameData(payload)
    }

    /// 토글 OFF→ON 복원 시 마지막 캐시된 game_data를 즉시 워치에 push
    func resyncLastGameDataToWatch() {
        guard let cached = UserDefaults.standard.dictionary(forKey: Self.lastGameDataKey) else {
            print("[WatchGameSync] No cached game_data to resync")
            return
        }
        guard WCSession.default.activationState == .activated else { return }
        deliverGameData(cached)
    }

    private func cacheLastGameData(_ payload: [String: Any]) {
        UserDefaults.standard.set(payload, forKey: Self.lastGameDataKey)
    }

    /// 동일 game_id 의 마지막으로 워치에 전송한 누적 투구수.
    /// 백엔드 silent push 가 `pitcher_pitch_count` 를 누락한 채 도착하는 경우의 폴백.
    /// 캐시가 없거나 game_id 가 다르면 nil.
    func cachedPitcherPitchCount(forGameId gameId: String) -> Int? {
        guard !gameId.isEmpty,
              let cached = UserDefaults.standard.dictionary(forKey: Self.lastGameDataKey),
              cached["game_id"] as? String == gameId,
              let raw = cached["pitcher_pitch_count"] as? Int,
              raw >= 0 else { return nil }
        return raw
    }

    private func deliverGameData(_ basePayload: [String: Any]) {
        var message = basePayload
        message["updated_at"] = Date().timeIntervalSince1970

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(message, replyHandler: nil) { error in
                print("[WatchGameSync] sendMessage failed: \(error.localizedDescription)")
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
        guard WCSession.default.activationState == .activated else { return }

        var message: [String: Any] = [
            "type": "haptic_event",
            "event_type": eventType,
            "timestamp": Date().timeIntervalSince1970
        ]
        if let cursor = cursor {
            message["event_cursor"] = cursor
        }

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(message, replyHandler: nil) { error in
                print("[WatchGameSync] Failed to send haptic event: \(error.localizedDescription)")
            }
        } else {
            // 워치가 unreachable이어도 워치 직접 APNs로 전달되므로 transferUserInfo는 폴백용
            WCSession.default.transferUserInfo(message)
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
