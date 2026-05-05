import Foundation
import Supabase

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

// 백엔드 집계는 iOS+Android 합산이며, raw 이벤트에만 platform 보존.
final class CheerSignalsLoader {
    static let shared = CheerSignalsLoader()
    private static let cacheKey = "cheer_signals_v1_cache"

    private(set) var cached: [CheerSignalEntry] = []

    private init() { load() }

    func refresh() async {
        let dateString = Self.dateFormatter.string(from: Date())
        guard let url = URL(string: "\(Self.backendBaseURL)/cheer-signals?date=\(dateString)") else { return }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { return }
            let envelope = try JSONDecoder().decode(CheerSignalsEnvelope.self, from: data)
            cached = envelope.items
            UserDefaults.standard.set(try JSONEncoder().encode(envelope.items), forKey: Self.cacheKey)
        } catch {
            print("[CheerSignals] refresh failed: \(error)")
        }
    }

    func entry(forStadium stadiumCode: String, on date: Date) -> CheerSignalEntry? {
        cached.first { $0.stadiumCode == stadiumCode }
    }

    func signal(forStadium stadiumCode: String, team: Team) -> (entry: CheerSignalEntry, signal: CheerSignal)? {
        guard let entry = entry(forStadium: stadiumCode, on: Date()),
              let signal = entry.signals.first(where: { $0.teamCode == team.rawValue }) else { return nil }
        return (entry, signal)
    }

    func fetchTeamRankings(period: String) async -> [TeamCheckinRank]? {
        guard let url = URL(string: "\(Self.backendBaseURL)/rankings/teams?period=\(period)") else { return nil }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { return nil }
            let envelope = try JSONDecoder().decode(TeamCheckinRankingEnvelope.self, from: data)
            return envelope.items.compactMap { item in
                guard let team = Team(rawValue: item.teamCode) else { return nil }
                return TeamCheckinRank(rank: item.rank, team: team, count: item.count)
            }
        } catch {
            print("[CheerRankings] fetch failed: \(error)")
            return nil
        }
    }

    func postCheckin(stadium: Stadium, selectedTeam: Team, game: Game?) async throws {
        let session = try await SupabaseClientProvider.client.auth.session
        guard let url = URL(string: "\(Self.backendBaseURL)/cheer-events") else { throw URLError(.badURL) }

        var payload: [String: Any] = [
            "team_code": selectedTeam.rawValue,
            "stadium_code": stadium.code,
            "client_ts": ISO8601DateFormatter().string(from: Date()),
            "lat": stadium.latitude,
            "lng": stadium.longitude,
            "accuracy_m": stadium.radiusMeters,
            "mock_location": false,
            "platform": "ios",
            "app_version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
            "is_home_team": game?.homeTeamId == selectedTeam,
            "opponent_team_code": opponentCode(for: game, selectedTeam: selectedTeam) ?? ""
        ]
        if let game {
            payload["game_id"] = game.id
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: Self.cacheKey) else { return }
        cached = (try? JSONDecoder().decode([CheerSignalEntry].self, from: data)) ?? []
    }

    private func opponentCode(for game: Game?, selectedTeam: Team) -> String? {
        guard let game else { return nil }
        if game.homeTeamId == selectedTeam { return game.awayTeamId.rawValue }
        if game.awayTeamId == selectedTeam { return game.homeTeamId.rawValue }
        return nil
    }

    private static var backendBaseURL: String {
        let base = BackendConfig.baseURL
        return base.hasSuffix("/") ? String(base.dropLast()) : base
    }

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
}

struct TeamCheckinRank: Identifiable {
    let rank: Int
    let team: Team
    let count: Int
    var id: String { team.rawValue }
}

private struct CheerSignalsEnvelope: Decodable {
    let items: [CheerSignalEntry]
}

private struct TeamCheckinRankingEnvelope: Decodable {
    let items: [TeamCheckinRankingItem]
}

private struct TeamCheckinRankingItem: Decodable {
    let teamCode: String
    let count: Int
    let rank: Int

    enum CodingKeys: String, CodingKey {
        case teamCode = "team_code"
        case count
        case rank
    }
}
