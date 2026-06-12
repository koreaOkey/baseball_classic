import Foundation

enum EventNotificationChannel {
    case watch
    case lockScreen
}

enum EventFilterGate {
    static func isAllowed(eventType: String?, channel: EventNotificationChannel = .watch) -> Bool {
        guard let type = eventType?.uppercased(), !type.isEmpty else { return true }
        guard let option = EventFilterOption.option(forEventType: type) else {
            if ["OUT", "DOUBLE_PLAY", "TRIPLE_PLAY"].contains(type) { return false }
            return true
        }
        let key = option.storageKey(for: channel)
        return UserDefaults.standard.object(forKey: key) as? Bool ?? option.defaultEnabled(for: channel)
    }
}

struct EventFilterOption: Identifiable {
    let id: String
    let watchStorageKey: String
    let lockScreenStorageKey: String
    let title: String
    let icon: String
    let watchDefaultEnabled: Bool
    let lockScreenDefaultEnabled: Bool

    static let all: [EventFilterOption] = [
        EventFilterOption(
            id: "score",
            watchStorageKey: "event_filter_score_enabled",
            lockScreenStorageKey: "lock_screen_event_filter_score_enabled",
            title: "득점",
            icon: "flag.fill",
            watchDefaultEnabled: true,
            lockScreenDefaultEnabled: true
        ),
        EventFilterOption(
            id: "homerun",
            watchStorageKey: "event_filter_homerun_enabled",
            lockScreenStorageKey: "lock_screen_event_filter_homerun_enabled",
            title: "홈런",
            icon: "star.circle.fill",
            watchDefaultEnabled: true,
            lockScreenDefaultEnabled: true
        ),
        EventFilterOption(
            id: "hit",
            watchStorageKey: "event_filter_hit_enabled",
            lockScreenStorageKey: "lock_screen_event_filter_hit_enabled",
            title: "안타",
            icon: "baseball.fill",
            watchDefaultEnabled: true,
            lockScreenDefaultEnabled: true
        ),
        EventFilterOption(
            id: "walk",
            watchStorageKey: "event_filter_walk_enabled",
            lockScreenStorageKey: "lock_screen_event_filter_walk_enabled",
            title: "볼넷·출루",
            icon: "figure.walk",
            watchDefaultEnabled: false,
            lockScreenDefaultEnabled: false
        ),
        EventFilterOption(
            id: "steal",
            watchStorageKey: "event_filter_steal_enabled",
            lockScreenStorageKey: "lock_screen_event_filter_steal_enabled",
            title: "도루·주루",
            icon: "figure.run",
            watchDefaultEnabled: false,
            lockScreenDefaultEnabled: false
        ),
        EventFilterOption(
            id: "pitch_count",
            watchStorageKey: "event_filter_pitch_count_enabled",
            lockScreenStorageKey: "lock_screen_event_filter_pitch_count_enabled",
            title: "스트라이크·볼",
            icon: "circle.grid.2x2.fill",
            watchDefaultEnabled: false,
            lockScreenDefaultEnabled: false
        ),
        EventFilterOption(
            id: "pitcher_change",
            watchStorageKey: "event_filter_pitcher_change_enabled",
            lockScreenStorageKey: "lock_screen_event_filter_pitcher_change_enabled",
            title: "투수 교체",
            icon: "arrow.triangle.2.circlepath",
            watchDefaultEnabled: false,
            lockScreenDefaultEnabled: false
        )
    ]

    func storageKey(for channel: EventNotificationChannel) -> String {
        switch channel {
        case .watch: return watchStorageKey
        case .lockScreen: return lockScreenStorageKey
        }
    }

    func defaultEnabled(for channel: EventNotificationChannel) -> Bool {
        switch channel {
        case .watch: return watchDefaultEnabled
        case .lockScreen: return lockScreenDefaultEnabled
        }
    }

    func subtitle(for channel: EventNotificationChannel) -> String {
        switch channel {
        case .watch:
            return "\(title) 발생 시 워치에서 알림"
        case .lockScreen:
            return "\(title) 발생 시 잠금화면에서 강조"
        }
    }

    static func option(forEventType eventType: String) -> EventFilterOption? {
        switch eventType.uppercased() {
        case "SCORE", "SAC_FLY_SCORE":
            return all.first { $0.id == "score" }
        case "HOMERUN":
            return all.first { $0.id == "homerun" }
        case "HIT":
            return all.first { $0.id == "hit" }
        case "WALK", "HIT_BY_PITCH":
            return all.first { $0.id == "walk" }
        case "STEAL", "TAG_UP_ADVANCE":
            return all.first { $0.id == "steal" }
        case "BALL", "STRIKE":
            return all.first { $0.id == "pitch_count" }
        case "PITCHER_CHANGE":
            return all.first { $0.id == "pitcher_change" }
        default:
            return nil
        }
    }
}
