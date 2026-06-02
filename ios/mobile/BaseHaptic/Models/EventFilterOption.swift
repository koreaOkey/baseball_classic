import Foundation

enum EventFilterGate {
    static func isAllowed(eventType: String?) -> Bool {
        guard let type = eventType?.uppercased(), !type.isEmpty else { return true }
        let key: String
        switch type {
        case "HOMERUN": key = "event_filter_homerun_enabled"
        case "SCORE", "SAC_FLY_SCORE": key = "event_filter_score_enabled"
        case "HIT": key = "event_filter_hit_enabled"
        case "STEAL", "TAG_UP_ADVANCE": key = "event_filter_steal_enabled"
        case "WALK": key = "event_filter_walk_enabled"
        case "OUT": key = "event_filter_out_enabled"
        case "DOUBLE_PLAY", "TRIPLE_PLAY": key = "event_filter_double_play_enabled"
        case "PITCHER_CHANGE": key = "event_filter_pitcher_change_enabled"
        default: return true
        }
        return UserDefaults.standard.object(forKey: key) as? Bool ?? EventFilterOption.defaultEnabled(forKey: key)
    }
}

struct EventFilterOption: Identifiable {
    let id: String
    let storageKey: String
    let title: String
    let subtitle: String
    let icon: String
    let defaultEnabled: Bool

    static let all: [EventFilterOption] = [
        EventFilterOption(
            id: "homerun",
            storageKey: "event_filter_homerun_enabled",
            title: "홈런",
            subtitle: "홈런 발생 시 강한 알림",
            icon: "star.circle.fill",
            defaultEnabled: true
        ),
        EventFilterOption(
            id: "score",
            storageKey: "event_filter_score_enabled",
            title: "득점",
            subtitle: "득점 발생 시 강한 알림",
            icon: "flag.fill",
            defaultEnabled: true
        ),
        EventFilterOption(
            id: "hit",
            storageKey: "event_filter_hit_enabled",
            title: "안타",
            subtitle: "안타 발생 시 강한 알림",
            icon: "baseball.fill",
            defaultEnabled: true
        ),
        EventFilterOption(
            id: "steal",
            storageKey: "event_filter_steal_enabled",
            title: "도루",
            subtitle: "도루 발생 시 강한 알림",
            icon: "figure.run",
            defaultEnabled: false
        ),
        EventFilterOption(
            id: "walk",
            storageKey: "event_filter_walk_enabled",
            title: "볼넷",
            subtitle: "볼넷 발생 시 강한 알림",
            icon: "figure.walk",
            defaultEnabled: false
        ),
        EventFilterOption(
            id: "out",
            storageKey: "event_filter_out_enabled",
            title: "아웃",
            subtitle: "아웃 발생 시 강한 알림",
            icon: "xmark.circle.fill",
            defaultEnabled: false
        ),
        EventFilterOption(
            id: "double_play",
            storageKey: "event_filter_double_play_enabled",
            title: "병살",
            subtitle: "병살 발생 시 강한 알림",
            icon: "arrow.triangle.merge",
            defaultEnabled: false
        ),
        EventFilterOption(
            id: "pitcher_change",
            storageKey: "event_filter_pitcher_change_enabled",
            title: "투수 교체",
            subtitle: "투수 교체 시 강한 알림",
            icon: "arrow.triangle.2.circlepath",
            defaultEnabled: false
        )
    ]

    static func defaultEnabled(forKey storageKey: String) -> Bool {
        all.first { $0.storageKey == storageKey }?.defaultEnabled ?? true
    }
}
