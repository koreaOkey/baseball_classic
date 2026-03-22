import Foundation

enum EventType: String, Codable {
    case ball = "BALL"
    case strike = "STRIKE"
    case walk = "WALK"
    case out = "OUT"
    case doublPlay = "DOUBLE_PLAY"
    case triplePlay = "TRIPLE_PLAY"
    case steal = "STEAL"
    case hit = "HIT"
    case homerun = "HOMERUN"
    case score = "SCORE"

    var hapticPattern: String {
        switch self {
        case .ball: return "tap"
        case .strike: return "tap-tap"
        case .walk: return "tap-soft-tap"
        case .out: return "tap-tap-tap"
        case .doublPlay: return "tap-pause-tap"
        case .triplePlay: return "tap-pause-tap-pause-tap"
        case .steal: return "quick-double-tap"
        case .hit: return "double-tap"
        case .homerun: return "triple-tap"
        case .score: return "tap-long"
        }
    }
}

struct GameEvent: Identifiable {
    let id: String
    let type: String
    let description: String
    let time: String
    let hapticPattern: String?
}
