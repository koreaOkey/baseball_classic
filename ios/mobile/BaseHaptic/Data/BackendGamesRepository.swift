import Foundation

// MARK: - Configuration
enum BackendConfig {
    /// 백엔드 베이스 URL - Info.plist의 BACKEND_BASE_URL 또는 기본값 사용
    static var baseURL: String {
        Bundle.main.object(forInfoDictionaryKey: "BACKEND_BASE_URL") as? String
            ?? "http://localhost:8080"
    }

    static var wsBaseURL: String {
        let base = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if base.hasPrefix("https://") {
            return "wss://" + base.dropFirst("https://".count)
        } else if base.hasPrefix("http://") {
            return "ws://" + base.dropFirst("http://".count)
        } else if base.hasPrefix("ws://") || base.hasPrefix("wss://") {
            return base
        }
        return "ws://" + base
    }
}

// MARK: - Data Models
struct LiveGameState {
    let gameId: String
    let homeTeam: String
    let awayTeam: String
    let homeTeamId: Team
    let awayTeamId: Team
    let homeScore: Int
    let awayScore: Int
    let inning: String
    let status: GameStatus
    let ball: Int
    let strike: Int
    let out: Int
    let baseFirst: Bool
    let baseSecond: Bool
    let baseThird: Bool
    let pitcher: String
    let batter: String
    let pitcherPitchCount: Int?
    let lastEventType: String?
}

struct LiveEvent: Identifiable {
    let cursor: Int64
    let id: String
    let type: String
    let description: String
    let time: String
}

struct LiveEventsPage {
    let items: [LiveEvent]
    let nextCursor: Int64?
}

struct TeamRecordStats {
    let teamId: String
    let ranking: Int?
    let wra: Double?
    let lastFiveGames: String?
    let updatedAt: String?
}

struct UpcomingGameSchedule: Identifiable {
    var id: String { "\(gameDate):\(game.id)" }
    let gameDate: Date
    let game: Game
}

// MARK: - Live Stream Messages
enum LiveStreamMessage {
    case connected
    case state(LiveGameState)
    case events([LiveEvent])
    case update(state: LiveGameState?, events: [LiveEvent])
    case pong(String?)
    case error(Error)
    case closed
}

enum TeamRecordStreamMessage {
    case connected
    case teamRecord(TeamRecordStats)
    case pong(String?)
    case error(Error)
    case closed
}

// MARK: - BackendGamesRepository
final class BackendGamesRepository {
    static let shared = BackendGamesRepository()
    private let session: URLSession
    private let timeoutInterval: TimeInterval = 5.0
    private let cache = NSCache<NSString, CacheEntry>()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = timeoutInterval
        config.timeoutIntervalForResource = timeoutInterval
        session = URLSession(configuration: config)
    }

    // MARK: - Cache
    private class CacheEntry {
        let date: String
        let payload: String
        let team: String?
        init(date: String, payload: String, team: String? = nil) {
            self.date = date
            self.payload = payload
            self.team = team
        }
    }

    // MARK: - Fetch Games
    func fetchGames(selectedTeam: Team) async -> [Game]? {
        await fetchGamesByDate(selectedTeam: selectedTeam, targetDate: Date())
    }

    func fetchTodayGamesCached(selectedTeam: Team, forceRefresh: Bool = false) async -> [Game]? {
        let today = todayString()
        let cacheKey: NSString = "today_games"

        if !forceRefresh, let entry = cache.object(forKey: cacheKey),
           entry.date == today, !entry.payload.isEmpty {
            if let games = parseGamesPayload(entry.payload, selectedTeam: selectedTeam), !games.isEmpty {
                return games
            }
        }

        guard let payload = await fetchGamesByDateRaw(targetDate: Date()) else {
            if let entry = cache.object(forKey: cacheKey), !entry.payload.isEmpty {
                return parseGamesPayload(entry.payload, selectedTeam: selectedTeam)
            }
            return nil
        }

        if let games = parseGamesPayload(payload, selectedTeam: selectedTeam) {
            if !games.isEmpty {
                cache.setObject(CacheEntry(date: today, payload: payload), forKey: cacheKey)
            }
            return games
        }
        return nil
    }

    func peekTodayGamesCache(selectedTeam: Team) -> [Game]? {
        let today = todayString()
        let cacheKey: NSString = "today_games"
        guard let entry = cache.object(forKey: cacheKey),
              entry.date == today, !entry.payload.isEmpty else { return nil }
        return parseGamesPayload(entry.payload, selectedTeam: selectedTeam)
    }

    // MARK: - Game State
    func fetchGameState(gameId: String) async -> LiveGameState? {
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/games/\(gameId)/state"
        return await getJSON(endpoint: endpoint) { data in
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            return self.parseLiveGameState(json)
        }
    }

    // MARK: - Game Events
    func fetchGameEvents(gameId: String, after: Int64, limit: Int = 50) async -> LiveEventsPage? {
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/games/\(gameId)/events?after=\(after)&limit=\(limit)"
        return await getJSON(endpoint: endpoint) { data in
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            let itemsArray = json["items"] as? [[String: Any]] ?? []
            let items = itemsArray.compactMap { self.parseLiveEvent($0) }
            let nextCursor = json["nextCursor"] as? Int64
            return LiveEventsPage(items: items, nextCursor: nextCursor)
        }
    }

    // MARK: - Team Record
    func fetchTeamRecord(selectedTeam: Team) async -> TeamRecordStats? {
        guard let teamId = selectedTeam.kboTeamId else { return nil }
        let year = Calendar.current.component(.year, from: Date())
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/team-records/\(teamId)?categoryId=kbo&seasonCode=\(year)"
        return await getJSON(endpoint: endpoint) { data in
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            return self.parseTeamRecordStats(json)
        }
    }

    func fetchTodayTeamRecordCached(selectedTeam: Team, forceRefresh: Bool = false) async -> TeamRecordStats? {
        guard selectedTeam != .none else { return nil }
        let today = todayString()
        let cacheKey: NSString = "team_record_\(selectedTeam.rawValue)" as NSString

        if !forceRefresh, let entry = cache.object(forKey: cacheKey),
           entry.date == today, !entry.payload.isEmpty {
            if let stats = parseTeamRecordPayload(entry.payload) {
                return stats
            }
        }

        guard let stats = await fetchTeamRecord(selectedTeam: selectedTeam) else {
            if let entry = cache.object(forKey: cacheKey), !entry.payload.isEmpty {
                return parseTeamRecordPayload(entry.payload)
            }
            return nil
        }

        if let payload = toTeamRecordPayload(stats) {
            cache.setObject(CacheEntry(date: today, payload: payload, team: selectedTeam.rawValue), forKey: cacheKey)
        }
        return stats
    }

    // MARK: - Upcoming Games
    func fetchUpcomingMyTeamGames(selectedTeam: Team, maxItems: Int = 3, daysAhead: Int = 30) async -> [UpcomingGameSchedule]? {
        guard selectedTeam != .none else { return [] }
        var items: [UpcomingGameSchedule] = []
        let calendar = Calendar.current

        for offset in 1...daysAhead {
            guard let targetDate = calendar.date(byAdding: .day, value: offset, to: Date()) else { continue }
            let dayGames = await fetchGamesByDate(selectedTeam: selectedTeam, targetDate: targetDate) ?? []
            if dayGames.isEmpty { continue }

            let upcoming = dayGames
                .filter { $0.isMyTeam && $0.status == .scheduled }
                .sorted { parseGameTimeToSortKey($0.time) < parseGameTimeToSortKey($1.time) }
                .map { UpcomingGameSchedule(gameDate: targetDate, game: $0) }

            for entry in upcoming {
                items.append(entry)
                if items.count >= maxItems { return items }
            }
        }
        return items
    }

    // MARK: - WebSocket Stream
    func streamGame(gameId: String) -> AsyncStream<LiveStreamMessage> {
        let endpoint = "\(BackendConfig.wsBaseURL.trimmingSuffix("/"))/ws/games/\(gameId)"
        return createWebSocketStream(endpoint: endpoint) { [weak self] text in
            self?.parseLiveStreamMessage(text)
        }
    }

    func streamTeamRecord(selectedTeam: Team) -> AsyncStream<TeamRecordStreamMessage>? {
        guard let teamId = selectedTeam.kboTeamId else { return nil }
        let year = Calendar.current.component(.year, from: Date())
        let endpoint = "\(BackendConfig.wsBaseURL.trimmingSuffix("/"))/ws/team-records/\(teamId)?categoryId=kbo&seasonCode=\(year)"
        return createWebSocketStream(endpoint: endpoint) { [weak self] text in
            self?.parseTeamRecordStreamMessage(text)
        }
    }

    // MARK: - Private Helpers

    private func fetchGamesByDate(selectedTeam: Team, targetDate: Date) async -> [Game]? {
        guard let payload = await fetchGamesByDateRaw(targetDate: targetDate) else { return nil }
        return parseGamesPayload(payload, selectedTeam: selectedTeam)
    }

    private func fetchGamesByDateRaw(targetDate: Date) async -> String? {
        let dateStr = dateFormatter.string(from: targetDate)
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/games?date=\(dateStr)&limit=100"
        return await getJSON(endpoint: endpoint) { data in
            String(data: data, encoding: .utf8)
        }
    }

    private func parseGamesPayload(_ payload: String, selectedTeam: Team) -> [Game]? {
        guard let data = payload.data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return nil }
        return array.compactMap { parseGame($0, selectedTeam: selectedTeam) }
    }

    private func parseGame(_ json: [String: Any], selectedTeam: Team) -> Game? {
        let homeTeamName = json["homeTeam"] as? String ?? ""
        let awayTeamName = json["awayTeam"] as? String ?? ""
        let homeTeamId = Team.fromBackendName(homeTeamName)
        let awayTeamId = Team.fromBackendName(awayTeamName)
        let status = statusFromBackend(json["status"] as? String ?? "")
        let inning = (json["inning"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? defaultInning(for: status)
        let startTime = (json["startTime"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? extractTime(inning)
            ?? formatBackendTimeOrNull(json["observedAt"] as? String)

        return Game(
            id: json["id"] as? String ?? "",
            homeTeam: homeTeamName,
            awayTeam: awayTeamName,
            homeTeamId: homeTeamId,
            awayTeamId: awayTeamId,
            homeScore: json["homeScore"] as? Int ?? 0,
            awayScore: json["awayScore"] as? Int ?? 0,
            inning: inning,
            status: status,
            time: startTime,
            isMyTeam: selectedTeam != .none && (homeTeamId == selectedTeam || awayTeamId == selectedTeam)
        )
    }

    private func parseLiveGameState(_ json: [String: Any]) -> LiveGameState? {
        let homeTeamName = json["homeTeam"] as? String ?? ""
        let awayTeamName = json["awayTeam"] as? String ?? ""
        let bases = json["bases"] as? [String: Any] ?? [:]
        let rawStatus = json["status"] as? String ?? ""
        let status = statusFromBackend(rawStatus)
        let inning = (json["inning"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? defaultInning(for: status)

        return LiveGameState(
            gameId: json["gameId"] as? String ?? "",
            homeTeam: homeTeamName,
            awayTeam: awayTeamName,
            homeTeamId: Team.fromBackendName(homeTeamName),
            awayTeamId: Team.fromBackendName(awayTeamName),
            homeScore: json["homeScore"] as? Int ?? 0,
            awayScore: json["awayScore"] as? Int ?? 0,
            inning: inning,
            status: status,
            ball: json["ball"] as? Int ?? 0,
            strike: json["strike"] as? Int ?? 0,
            out: json["out"] as? Int ?? 0,
            baseFirst: bases["first"] as? Bool ?? false,
            baseSecond: bases["second"] as? Bool ?? false,
            baseThird: bases["third"] as? Bool ?? false,
            pitcher: json["pitcher"] as? String ?? "",
            batter: json["batter"] as? String ?? "",
            pitcherPitchCount: json["pitcherPitchCount"] as? Int,
            lastEventType: (json["lastEventType"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        )
    }

    private func parseLiveEvent(_ json: [String: Any]) -> LiveEvent? {
        let cursor = json["cursor"] as? Int64 ?? 0
        return LiveEvent(
            cursor: cursor,
            id: (json["id"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "\(cursor)",
            type: (json["type"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "OTHER",
            description: json["description"] as? String ?? "",
            time: formatBackendTime(json["time"] as? String ?? "")
        )
    }

    private func parseTeamRecordStats(_ json: [String: Any]) -> TeamRecordStats {
        TeamRecordStats(
            teamId: json["teamId"] as? String ?? "",
            ranking: json["ranking"] as? Int,
            wra: json["wra"] as? Double,
            lastFiveGames: json["lastFiveGames"] as? String,
            updatedAt: json["updatedAt"] as? String
        )
    }

    private func parseLiveStreamMessage(_ text: String) -> LiveStreamMessage? {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return nil }

        switch type {
        case "state":
            guard let payload = json["payload"] as? [String: Any],
                  let state = parseLiveGameState(payload) else { return nil }
            return .state(state)
        case "events":
            guard let payload = json["payload"] as? [String: Any],
                  let itemsArray = payload["items"] as? [[String: Any]] else { return nil }
            let items = itemsArray.compactMap { parseLiveEvent($0) }
            return .events(items)
        case "update":
            guard let payload = json["payload"] as? [String: Any] else { return nil }
            let state = (payload["state"] as? [String: Any]).flatMap { parseLiveGameState($0) }
            let events = (payload["events"] as? [[String: Any]])?.compactMap { parseLiveEvent($0) } ?? []
            return .update(state: state, events: events)
        case "pong":
            let at = (json["payload"] as? [String: Any])?["at"] as? String
            return .pong(at)
        default:
            return nil
        }
    }

    private func parseTeamRecordStreamMessage(_ text: String) -> TeamRecordStreamMessage? {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return nil }

        switch type {
        case "team_record":
            guard let payload = json["payload"] as? [String: Any] else { return nil }
            return .teamRecord(parseTeamRecordStats(payload))
        case "pong":
            let at = (json["payload"] as? [String: Any])?["at"] as? String
            return .pong(at)
        default:
            return nil
        }
    }

    private func toTeamRecordPayload(_ stats: TeamRecordStats) -> String? {
        var dict: [String: Any] = ["teamId": stats.teamId]
        if let ranking = stats.ranking { dict["ranking"] = ranking }
        if let wra = stats.wra { dict["wra"] = wra }
        if let lastFiveGames = stats.lastFiveGames { dict["lastFiveGames"] = lastFiveGames }
        if let updatedAt = stats.updatedAt { dict["updatedAt"] = updatedAt }
        guard let data = try? JSONSerialization.data(withJSONObject: dict) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func parseTeamRecordPayload(_ payload: String) -> TeamRecordStats? {
        guard let data = payload.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return parseTeamRecordStats(json)
    }

    // MARK: - Network
    private func getJSON<T>(endpoint: String, parser: @escaping (Data) -> T?) async -> T? {
        guard let url = URL(string: endpoint) else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode) else { return nil }
            return parser(data)
        } catch {
            return nil
        }
    }

    private func createWebSocketStream<T>(endpoint: String, parser: @escaping (String) -> T?) -> AsyncStream<T> {
        AsyncStream { continuation in
            guard let url = URL(string: endpoint) else {
                continuation.finish()
                return
            }

            let task = session.webSocketTask(with: url)

            func receiveMessage() {
                task.receive { result in
                    switch result {
                    case .success(let message):
                        switch message {
                        case .string(let text):
                            if let parsed = parser(text) {
                                continuation.yield(parsed)
                            }
                        default:
                            break
                        }
                        receiveMessage()
                    case .failure:
                        continuation.finish()
                    }
                }
            }

            // Heartbeat
            let heartbeatTask = Task {
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 15_000_000_000)
                    task.send(.string("ping")) { _ in }
                }
            }

            task.resume()
            task.send(.string("ping")) { _ in }
            receiveMessage()

            continuation.onTermination = { _ in
                heartbeatTask.cancel()
                task.cancel(with: .goingAway, reason: nil)
            }
        }
    }

    // MARK: - Utilities
    private func statusFromBackend(_ raw: String) -> GameStatus {
        switch raw.uppercased() {
        case "LIVE": return .live
        case "FINISHED": return .finished
        case "CANCELED", "CANCELLED", "CANCEL", "RAIN_CANCEL", "NO_GAME": return .canceled
        case "POSTPONED", "PPD", "SUSPENDED", "DELAYED": return .postponed
        default: return .scheduled
        }
    }

    private func defaultInning(for status: GameStatus) -> String {
        switch status {
        case .live: return "LIVE"
        case .finished: return "FINAL"
        case .scheduled: return "SCHEDULED"
        case .canceled: return "CANCELED"
        case .postponed: return "POSTPONED"
        }
    }

    private func extractTime(_ value: String) -> String? {
        let pattern = #"^\d{2}:\d{2}$"#
        return value.range(of: pattern, options: .regularExpression) != nil ? value : nil
    }

    private func formatBackendTimeOrNull(_ raw: String?) -> String? {
        guard let raw = raw, !raw.isEmpty else { return nil }
        let formatted = formatBackendTime(raw)
        return formatted == "--:--" ? nil : formatted
    }

    private func formatBackendTime(_ raw: String) -> String {
        if raw.isEmpty { return "--:--" }
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fmt.date(from: raw) {
            return Self.kstHourMinute.string(from: date)
        }
        fmt.formatOptions = [.withInternetDateTime]
        if let date = fmt.date(from: raw) {
            return Self.kstHourMinute.string(from: date)
        }
        return "--:--"
    }

    private static let kstHourMinute: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        f.timeZone = .current
        return f
    }()

    private func parseGameTimeToSortKey(_ raw: String?) -> Int {
        guard let raw = raw, !raw.isEmpty else { return Int.max }
        let parts = raw.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]) else { return Int.max }
        return hour * 60 + minute
    }

    private func todayString() -> String {
        dateFormatter.string(from: Date())
    }

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
}

// MARK: - String Extension
private extension String {
    func trimmingSuffix(_ suffix: String) -> String {
        if hasSuffix(suffix) {
            return String(dropLast(suffix.count))
        }
        return self
    }
}
