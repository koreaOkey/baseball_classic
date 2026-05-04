import Foundation

// TODO(stadium-cheer): 활성화 시 백엔드 GET /cheer-signals 또는 CDN URL로 fetch.
// 다크 머지 단계에서는 빈 배열 반환 + 캐시 키만 정의.

struct CheerSignal: Codable, Identifiable {
    let teamCode: String
    let role: String              // "home" | "away"
    let cheerText: String
    let primaryColorHex: String
    let hapticPatternId: String
    var id: String { teamCode + role }

    enum CodingKeys: String, CodingKey {
        case teamCode = "team_code"
        case role
        case cheerText = "cheer_text"
        case primaryColorHex = "primary_color_hex"
        case hapticPatternId = "haptic_pattern_id"
    }
}

struct CheerSignalEntry: Codable, Identifiable {
    let gameId: String
    let stadiumCode: String
    let fireAtIso: String
    let signals: [CheerSignal]
    var id: String { gameId }

    enum CodingKeys: String, CodingKey {
        case gameId = "game_id"
        case stadiumCode = "stadium_code"
        case fireAtIso = "fire_at_iso"
        case signals
    }
}

// TODO(stadium-cheer): 활성화 시 POST /cheer-events 호출부에서 platform="ios" 포함하여 송신.
// 백엔드 집계는 iOS+Android 합산이며, raw 이벤트에만 platform 보존.
final class CheerSignalsLoader {
    static let shared = CheerSignalsLoader()
    private static let cacheKey = "cheer_signals_v1_cache"

    private(set) var cached: [CheerSignalEntry] = []

    private init() { load() }

    /// TODO(stadium-cheer): 활성화 시 호출. 다크 상태에서는 캐시만 로드.
    func refresh() async {
        // let url = URL(string: "https://api.example.com/cheer-signals")!
        // let (data, _) = try await URLSession.shared.data(from: url)
        // cached = (try? JSONDecoder().decode([CheerSignalEntry].self, from: data)) ?? []
        // UserDefaults.standard.set(data, forKey: Self.cacheKey)
    }

    func entry(forStadium stadiumCode: String, on date: Date) -> CheerSignalEntry? {
        cached.first { $0.stadiumCode == stadiumCode }
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: Self.cacheKey) else { return }
        cached = (try? JSONDecoder().decode([CheerSignalEntry].self, from: data)) ?? []
    }
}
