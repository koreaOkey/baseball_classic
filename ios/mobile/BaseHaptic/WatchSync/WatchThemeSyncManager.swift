import Foundation
import WatchConnectivity

/// iOS → Watch 테마 동기화 매니저
/// Android의 WearThemeSyncManager에 대응
final class WatchThemeSyncManager {
    static func syncThemeToWatch(team: Team) {
        syncToWatch(context: [
            "type": "theme_update",
            "my_team": team.rawValue,
            "updated_at": Date().timeIntervalSince1970
        ])
    }

    static func syncStoreThemeToWatch(themeId: String) {
        syncToWatch(context: [
            "type": "store_theme_update",
            "theme_id": themeId,
            "updated_at": Date().timeIntervalSince1970
        ])
    }

    static func syncEventVideoEnabledToWatch(enabled: Bool) {
        syncToWatch(context: [
            "type": "settings_update",
            "event_video_enabled": enabled,
            "updated_at": Date().timeIntervalSince1970
        ])
    }

    static func syncLiveHapticEnabledToWatch(enabled: Bool) {
        syncToWatch(context: [
            "type": "settings_update",
            "live_haptic_enabled": enabled,
            "updated_at": Date().timeIntervalSince1970
        ])
    }

    /// 응원 시각 도래 시 iPhone에서 Watch로 현장 응원 페이로드를 전달한다.
    /// - Parameters:
    ///   - teamCode: 사용자 응원팀 코드 (예: "DOOSAN")
    ///   - stadiumCode: 구장 코드 (예: "JAMSIL")
    ///   - cheerText: 응원 문구
    ///   - primaryColorHex: 팀 컬러 헥스 (예: "#13274F")
    ///   - hapticPatternId: 워치측 햅틱 패턴 식별자
    ///   - fireAtUnixMs: 정시 발화 시각 (워치 측 NTP 보정용)
    static func sendCheerTrigger(
        teamCode: String,
        stadiumCode: String,
        cheerText: String,
        primaryColorHex: String,
        hapticPatternId: String,
        fireAtUnixMs: Int64
    ) {
        let payload: [String: Any] = [
            "type": "stadium_cheer_trigger",
            "team_code": teamCode,
            "stadium_code": stadiumCode,
            "cheer_text": cheerText,
            "primary_color_hex": primaryColorHex,
            "haptic_pattern_id": hapticPatternId,
            "fire_at_unix_ms": fireAtUnixMs,
            "sent_at": Date().timeIntervalSince1970
        ]
        guard WCSession.default.activationState == .activated else { return }
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(payload, replyHandler: nil) { _ in }
        } else {
            WCSession.default.transferUserInfo(payload)
        }
    }

    private static func syncToWatch(context: [String: Any]) {
        guard WCSession.default.activationState == .activated else { return }

        do {
            try WCSession.default.updateApplicationContext(context)
            print("[WatchThemeSync] Synced: \(context)")
        } catch {
            print("[WatchThemeSync] Failed to sync: \(error.localizedDescription)")
        }

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(context, replyHandler: nil) { _ in }
        }
    }
}
