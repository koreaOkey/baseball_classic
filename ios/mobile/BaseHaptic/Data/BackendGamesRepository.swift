import Foundation

// MARK: - Configuration
enum BackendConfig {
    #if DEBUG
    private static let defaultBaseURL = "https://baseballclassic-production-4796.up.railway.app"
    #else
    private static let defaultBaseURL = "https://baseballclassic-production-4796.up.railway.app"
    #endif

    private static func infoString(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.hasPrefix("$(") { return nil }
        return trimmed
    }

    /// 백엔드 베이스 URL - Info.plist의 BACKEND_BASE_URL 또는 기본값 사용
    static var baseURL: String {
        infoString("BACKEND_BASE_URL") ?? defaultBaseURL
    }

    static var wsBaseURL: String {
        if let configured = infoString("BACKEND_WS_URL") {
            return configured.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }
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
    let baseFirstRunner: String?
    let baseSecondRunner: String?
    let baseThirdRunner: String?
    let pitcher: String
    let batter: String
    let pitcherPitchCount: Int?
    let lastEventType: String?
    let homeLineup: [LineupSlot]
    let awayLineup: [LineupSlot]
    // 선발투수 이름. 백엔드가 GamePitcherStat(is_starter=True) 에서 채움.
    // DH 룰로 lineup 에는 빠지므로 BaseballFieldCard 마운드 자리에 따로 그린다.
    let homeStartingPitcher: String?
    let awayStartingPitcher: String?
}

struct LineupSlot {
    let battingOrder: Int
    let playerName: String
    let positionCode: String?
    let positionName: String?
    let isStarter: Bool
    let isActive: Bool
}

struct LiveEvent: Identifiable {
    let cursor: Int64
    let id: String
    let type: String
    let description: String
    let time: String
    let pitcher: String?
    let batter: String?
    let inning: String?
    // 타석(at-bat) 그룹화 키. 백엔드 source_event_id 가 "{inning:02d}-{relayNo:03d}-{seqno:04d}"
    // 형식일 때만 채워지며, 구버전 백엔드/비정형 이벤트에서는 nil — 평면 폴백.
    let atBatId: String?
    let seqno: Int?
    // 이벤트 직후 시점의 누적 스코어. 백엔드 GameEventOut.homeScoreAfter/awayScoreAfter
    // 에서 옴. 크롤러가 채운 경우에만 값이 있고, 미수집 이벤트는 nil — description 폴백.
    let homeScoreAfter: Int?
    let awayScoreAfter: Int?
    let pitchNum: Int?
    let pitchSpeed: Int?
    let pitchStuff: String?
    let ballAfter: Int?
    let strikeAfter: Int?
    let outAfter: Int?
    let batterRecord: [String: Any]?
    let homeWinProbability: Double?
    let awayWinProbability: Double?
    let wpaByPlate: Double?

    // 신규 필드들에 default nil 을 부여하기 위한 명시적 init.
    // (Swift 의 let + default value 는 memberwise init 에서 인자를 받지 못하므로,
    //  외부 호출자 무영향 + parser 에서 값 전달 가능이라는 두 조건을 같이 만족시키려면
    //  명시적 init 이 필요.)
    init(
        cursor: Int64,
        id: String,
        type: String,
        description: String,
        time: String,
        pitcher: String? = nil,
        batter: String? = nil,
        inning: String? = nil,
        atBatId: String? = nil,
        seqno: Int? = nil,
        homeScoreAfter: Int? = nil,
        awayScoreAfter: Int? = nil,
        pitchNum: Int? = nil,
        pitchSpeed: Int? = nil,
        pitchStuff: String? = nil,
        ballAfter: Int? = nil,
        strikeAfter: Int? = nil,
        outAfter: Int? = nil,
        batterRecord: [String: Any]? = nil,
        homeWinProbability: Double? = nil,
        awayWinProbability: Double? = nil,
        wpaByPlate: Double? = nil
    ) {
        self.cursor = cursor
        self.id = id
        self.type = type
        self.description = description
        self.time = time
        self.pitcher = pitcher
        self.batter = batter
        self.inning = inning
        self.atBatId = atBatId
        self.seqno = seqno
        self.homeScoreAfter = homeScoreAfter
        self.awayScoreAfter = awayScoreAfter
        self.pitchNum = pitchNum
        self.pitchSpeed = pitchSpeed
        self.pitchStuff = pitchStuff
        self.ballAfter = ballAfter
        self.strikeAfter = strikeAfter
        self.outAfter = outAfter
        self.batterRecord = batterRecord
        self.homeWinProbability = homeWinProbability
        self.awayWinProbability = awayWinProbability
        self.wpaByPlate = wpaByPlate
    }
}

struct LiveEventsPage {
    let items: [LiveEvent]
    let nextCursor: Int64?
}

struct AppNotice {
    let enabled: Bool
    let title: String
    let message: String
}

struct AppConfig {
    let platform: String
    let minSupportedVersion: String
    let latestVersion: String
    let forceUpdate: Bool
    let updateTitle: String
    let updateMessage: String
    let storeUrl: String
    let notice: AppNotice
}

struct TeamRecordStats {
    let teamId: String
    let ranking: Int?
    let wra: Double?
    let lastFiveGames: String?
    let updatedAt: String?
}

struct TeamRecordStanding: Identifiable {
    var id: String { teamId }
    let teamId: String
    let teamName: String
    let ranking: Int?
    let wra: Double?
    let gameCount: Int?
    let winGameCount: Int?
    let drawnGameCount: Int?
    let loseGameCount: Int?
    let gameBehind: Double?
    let continuousGameResult: String?
    let updatedAt: String?
}

struct UpcomingGameSchedule: Identifiable {
    var id: String { "\(gameDate):\(game.id)" }
    let gameDate: Date
    let game: Game
}

struct GameWeatherHourly {
    let gameId: String
    let stadiumCode: String
    let stadiumName: String
    let stadiumShortName: String
    let gameStartTime: String?
    let items: [GameWeatherHourlyItem]
}

struct GameWeatherHourlyItem: Identifiable {
    var id: String { "\(forecastDate):\(forecastTime)" }
    let forecastDate: String
    let forecastTime: String
    let timeLabel: String
    let condition: String
    let temperatureC: Int?
    let precipitationProbability: Int?
    let precipitationType: String?
    let windSpeedMps: Double?
    let isGameStartForecast: Bool
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
    private let webSocketSession: URLSession
    private let timeoutInterval: TimeInterval = 5.0
    private let scheduleCacheTTL: TimeInterval = 6 * 60 * 60
    private let scheduleCacheVersion = 2
    private let cache = NSCache<NSString, CacheEntry>()

    private init() {
        // REST 전용 세션 — 짧은 타임아웃으로 빠른 실패
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = timeoutInterval
        config.timeoutIntervalForResource = timeoutInterval
        session = URLSession(configuration: config)

        // WebSocket 전용 세션 — REST용 5초 타임아웃을 공유하면
        // timeoutIntervalForResource(5초)에 걸려 스트림이 연결 직후 강제 종료된다.
        let wsConfig = URLSessionConfiguration.default
        wsConfig.timeoutIntervalForRequest = 60
        wsConfig.timeoutIntervalForResource = 7 * 24 * 60 * 60 // 사실상 무제한 (기본값과 동일)
        webSocketSession = URLSession(configuration: wsConfig)
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

    func fetchAppConfig(platform: String, version: String) async -> AppConfig? {
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/app-config?platform=\(platform)&version=\(version)"
        return await getJSON(endpoint: endpoint) { data in
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            let notice = json["notice"] as? [String: Any] ?? [:]
            return AppConfig(
                platform: json["platform"] as? String ?? platform,
                minSupportedVersion: json["minSupportedVersion"] as? String ?? "",
                latestVersion: json["latestVersion"] as? String ?? "",
                forceUpdate: json["forceUpdate"] as? Bool ?? false,
                updateTitle: json["updateTitle"] as? String ?? "업데이트가 필요합니다",
                updateMessage: json["updateMessage"] as? String ?? "안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요.",
                storeUrl: json["storeUrl"] as? String ?? "",
                notice: AppNotice(
                    enabled: notice["enabled"] as? Bool ?? false,
                    title: notice["title"] as? String ?? "",
                    message: notice["message"] as? String ?? ""
                )
            )
        }
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
    func fetchGameEvents(
        gameId: String,
        after: Int64,
        limit: Int = 50,
        inningNumber: Int? = nil,
        scoringOnly: Bool = false
    ) async -> LiveEventsPage? {
        var query = "after=\(after)&limit=\(limit)"
        if let inningNumber {
            query += "&inningNumber=\(inningNumber)"
        }
        if scoringOnly {
            query += "&scoringOnly=true"
        }
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/games/\(gameId)/events?\(query)"
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

    func fetchTeamRecordStandings() async -> [TeamRecordStanding]? {
        let year = Calendar.current.component(.year, from: Date())
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/team-records?categoryId=kbo&seasonCode=\(year)"
        return await getJSON(endpoint: endpoint) { data in
            guard let items = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return nil }
            return items.map { self.parseTeamRecordStanding($0) }
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
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        guard let fromDate = calendar.date(byAdding: .day, value: 1, to: today),
              let toDate = calendar.date(byAdding: .day, value: max(daysAhead, 1), to: today) else {
            return nil
        }
        let schedules = await fetchMyTeamScheduleRangeCached(
            selectedTeam: selectedTeam,
            fromDate: fromDate,
            toDate: toDate,
            cacheScope: "upcoming" // 홈 카드(30일)와 일정 시트(시즌 전체)가 같은 캐시를 덮어쓰지 않도록 분리
        ) ?? []
        return schedules
            .filter { $0.gameDate > today && $0.game.status == .scheduled }
            .prefix(max(maxItems, 1))
            .map { $0 }
    }

    func fetchGameHourlyWeather(gameId: String, targetDate: Date = Date()) async -> GameWeatherHourly? {
        let dateString = dateFormatter.string(from: targetDate)
        let endpoint = "\(BackendConfig.baseURL.trimmingSuffix("/"))/games/\(gameId)/weather?date=\(dateString)"
        return await getJSON(endpoint: endpoint) { data in
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            return self.parseGameWeatherHourly(json)
        }
    }

    func fetchMyTeamScheduleGames(selectedTeam: Team, daysAhead: Int = 30) async -> [UpcomingGameSchedule]? {
        guard selectedTeam != .none else { return [] }
        let normalizedDaysAhead = max(daysAhead, 0)
        let calendar = Calendar.current
        var items: [UpcomingGameSchedule] = []

        for offset in 0...normalizedDaysAhead {
            guard let targetDate = calendar.date(byAdding: .day, value: offset, to: Date()) else { continue }
            guard let dayGames = await fetchGamesByDate(selectedTeam: selectedTeam, targetDate: targetDate) else {
                return nil
            }
            let myTeamGames = dayGames
                .filter { $0.isMyTeam }
                .map { UpcomingGameSchedule(gameDate: targetDate, game: $0) }
            items.append(contentsOf: myTeamGames)
        }

        return items.sorted {
            let dateOrder = calendar.compare($0.gameDate, to: $1.gameDate, toGranularity: .day)
            if dateOrder != .orderedSame {
                return dateOrder == .orderedAscending
            }
            let lhsTime = parseGameTimeToSortKey($0.game.time)
            let rhsTime = parseGameTimeToSortKey($1.game.time)
            if lhsTime != rhsTime {
                return lhsTime < rhsTime
            }
            return $0.game.id < $1.game.id
        }
    }

    /// TTL을 무시하고 조건(팀/기간/버전)이 일치하는 일정 캐시를 즉시 읽는다.
    /// 시트를 열 때 오래된 데이터라도 먼저 그려주고(stale-while-revalidate),
    /// 신선 여부는 `isStale` 플래그로 알려 호출 측이 백그라운드 갱신을 결정하게 한다.
    func peekMyTeamScheduleRangeCache(
        selectedTeam: Team,
        fromDate: Date,
        toDate: Date,
        cacheScope: String? = nil
    ) -> (items: [UpcomingGameSchedule], isStale: Bool)? {
        guard selectedTeam != .none else { return nil }
        let calendar = Calendar.current
        let normalizedFrom = calendar.startOfDay(for: fromDate)
        let normalizedTo = max(calendar.startOfDay(for: toDate), normalizedFrom)
        let fromString = dateFormatter.string(from: normalizedFrom)
        let toString = dateFormatter.string(from: normalizedTo)
        let defaults = UserDefaults.standard
        let keyPrefix = scheduleRangeCacheKeyPrefix(scope: cacheScope)

        guard defaults.string(forKey: "\(keyPrefix)_team") == selectedTeam.rawValue,
              defaults.string(forKey: "\(keyPrefix)_from") == fromString,
              defaults.string(forKey: "\(keyPrefix)_to") == toString,
              defaults.integer(forKey: "\(keyPrefix)_version") == scheduleCacheVersion,
              let payload = defaults.string(forKey: "\(keyPrefix)_payload"),
              !payload.isEmpty,
              let items = parseScheduleRangePayload(payload, selectedTeam: selectedTeam),
              !items.isEmpty else {
            return nil
        }
        let cachedAt = defaults.double(forKey: "\(keyPrefix)_cached_at")
        return (items: items, isStale: !isFreshScheduleCache(cachedAt))
    }

    private func scheduleRangeCacheKeyPrefix(scope: String?) -> String {
        guard let scope, !scope.isEmpty else { return "schedule_range" }
        return "schedule_range_\(scope)"
    }

    func fetchMyTeamScheduleRangeCached(
        selectedTeam: Team,
        fromDate: Date,
        toDate: Date,
        forceRefresh: Bool = false,
        cacheScope: String? = nil
    ) async -> [UpcomingGameSchedule]? {
        guard selectedTeam != .none else { return [] }
        let calendar = Calendar.current
        let normalizedFrom = calendar.startOfDay(for: fromDate)
        let normalizedTo = max(calendar.startOfDay(for: toDate), normalizedFrom)
        let fromString = dateFormatter.string(from: normalizedFrom)
        let toString = dateFormatter.string(from: normalizedTo)
        let defaults = UserDefaults.standard
        let keyPrefix = scheduleRangeCacheKeyPrefix(scope: cacheScope)
        let cachedTeam = defaults.string(forKey: "\(keyPrefix)_team")
        let cachedFrom = defaults.string(forKey: "\(keyPrefix)_from")
        let cachedTo = defaults.string(forKey: "\(keyPrefix)_to")
        let cachedVersion = defaults.integer(forKey: "\(keyPrefix)_version")
        let cachedPayload = defaults.string(forKey: "\(keyPrefix)_payload")
        let cachedAt = defaults.double(forKey: "\(keyPrefix)_cached_at")

        if !forceRefresh,
           cachedTeam == selectedTeam.rawValue,
           cachedFrom == fromString,
           cachedTo == toString,
           cachedVersion == scheduleCacheVersion,
           isFreshScheduleCache(cachedAt),
           let cachedPayload,
           !cachedPayload.isEmpty,
           let cached = parseScheduleRangePayload(cachedPayload, selectedTeam: selectedTeam) {
            if !cached.isEmpty {
                return cached
            }
        }

        if let freshPayload = await fetchGamesByDateRangePayload(selectedTeam: selectedTeam, fromDate: normalizedFrom, toDate: normalizedTo),
           let fresh = parseScheduleRangePayload(freshPayload, selectedTeam: selectedTeam) {
            if fresh.isEmpty {
                defaults.removeObject(forKey: "\(keyPrefix)_payload")
                defaults.removeObject(forKey: "\(keyPrefix)_cached_at")
            } else {
                defaults.set(selectedTeam.rawValue, forKey: "\(keyPrefix)_team")
                defaults.set(fromString, forKey: "\(keyPrefix)_from")
                defaults.set(toString, forKey: "\(keyPrefix)_to")
                defaults.set(scheduleCacheVersion, forKey: "\(keyPrefix)_version")
                defaults.set(freshPayload, forKey: "\(keyPrefix)_payload")
                defaults.set(Date().timeIntervalSince1970, forKey: "\(keyPrefix)_cached_at")
            }
            return fresh
        }

        if cachedTeam == selectedTeam.rawValue,
           cachedFrom == fromString,
           cachedTo == toString,
           cachedVersion == scheduleCacheVersion,
           let cachedPayload,
           !cachedPayload.isEmpty {
            if let cached = parseScheduleRangePayload(cachedPayload, selectedTeam: selectedTeam), !cached.isEmpty {
                return cached
            }
        }

        return nil
    }

    private func isFreshScheduleCache(_ cachedAt: TimeInterval) -> Bool {
        cachedAt > 0 && Date().timeIntervalSince1970 - cachedAt <= scheduleCacheTTL
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

    private func fetchGamesByDateRangeRaw(fromDate: Date, toDate: Date) async -> String? {
        let fromString = dateFormatter.string(from: fromDate)
        let toString = dateFormatter.string(from: toDate)
        let baseURL = BackendConfig.baseURL.trimmingSuffix("/")
        let fullRangeEndpoint = "\(baseURL)/games?from=\(fromString)&to=\(toString)&limit=500"
        if let payload = await getJSON(endpoint: fullRangeEndpoint, parser: { data in
            String(data: data, encoding: .utf8)
        }) {
            return payload
        }
        let compatibleEndpoint = "\(baseURL)/games?from=\(fromString)&to=\(toString)&limit=100"
        return await getJSON(endpoint: compatibleEndpoint) { data in
            String(data: data, encoding: .utf8)
        }
    }

    /// 팀 필터를 지원하는 신규 백엔드용 단일 요청.
    /// 팀당 시즌 전체 경기(~144)는 limit=500 안에 모두 들어오므로 요청 1번으로 끝난다.
    /// 실패(구버전 400 아님 — team 파라미터는 무시됨, 네트워크 오류 등) 시 nil을 반환해 월 단위 폴백을 태운다.
    private func fetchTeamGamesRangePayload(selectedTeam: Team, fromDate: Date, toDate: Date) async -> String? {
        guard selectedTeam != .none else { return nil }
        let fromString = dateFormatter.string(from: fromDate)
        let toString = dateFormatter.string(from: toDate)
        let baseURL = BackendConfig.baseURL.trimmingSuffix("/")
        // 팀 코드는 Team enum rawValue(DOOSAN/LG/SSG/...)와 백엔드 코드가 동일하다.
        let endpoint = "\(baseURL)/games?team=\(selectedTeam.rawValue)&from=\(fromString)&to=\(toString)&limit=500"
        guard let payload = await getJSON(endpoint: endpoint, parser: { data in
            String(data: data, encoding: .utf8)
        }) else { return nil }

        // 구버전 백엔드는 team 파라미터를 무시하고 리그 전체를 반환한다.
        // 그 경우 시즌 전체 요청은 limit=500에 잘려 일정이 누락될 수 있으므로,
        // 응원팀 외 경기가 섞여 있으면 서버 필터 미지원으로 판단하고 월 단위 폴백을 사용한다.
        guard let data = payload.data(using: .utf8),
              let items = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }
        let serverFiltered = items.allSatisfy { item in
            let home = Team.fromBackendName(item["homeTeam"] as? String ?? "")
            let away = Team.fromBackendName(item["awayTeam"] as? String ?? "")
            return home == selectedTeam || away == selectedTeam
        }
        return serverFiltered ? payload : nil
    }

    private func fetchGamesByDateRangePayload(selectedTeam: Team, fromDate: Date, toDate: Date) async -> String? {
        // 1) 신규 백엔드: 팀 필터 단일 요청 (시즌 전체도 1회 왕복)
        if let teamPayload = await fetchTeamGamesRangePayload(selectedTeam: selectedTeam, fromDate: fromDate, toDate: toDate) {
            return teamPayload
        }

        // 2) 폴백: 기존 리그 전체 월 단위 순차 요청 (클라이언트 측 응원팀 필터는 파싱 단계에서 유지)
        let calendar = Calendar.current
        if calendar.isDate(fromDate, equalTo: toDate, toGranularity: .month) {
            return await fetchGamesByDateRangeRaw(fromDate: fromDate, toDate: toDate)
        }

        var mergedItems: [[String: Any]] = []
        var seenIds = Set<String>()
        let normalizedFrom = calendar.startOfDay(for: fromDate)
        let normalizedTo = max(calendar.startOfDay(for: toDate), normalizedFrom)
        var cursor = monthStartDate(for: normalizedFrom, calendar: calendar)

        while cursor <= normalizedTo {
            guard let nextMonth = calendar.date(byAdding: .month, value: 1, to: cursor) else { return nil }
            let monthEnd = calendar.date(byAdding: .day, value: -1, to: nextMonth) ?? cursor
            let chunkStart = max(normalizedFrom, cursor)
            let chunkEnd = min(normalizedTo, monthEnd)

            guard let payload = await fetchGamesByDateRangeRaw(fromDate: chunkStart, toDate: chunkEnd),
                  let data = payload.data(using: .utf8),
                  let items = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                return nil
            }

            for item in items {
                let gameId = item["id"] as? String ?? ""
                if !gameId.isEmpty {
                    guard seenIds.insert(gameId).inserted else { continue }
                }
                mergedItems.append(item)
            }

            cursor = nextMonth
        }

        guard let data = try? JSONSerialization.data(withJSONObject: mergedItems) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func monthStartDate(for date: Date, calendar: Calendar) -> Date {
        let components = calendar.dateComponents([.year, .month], from: date)
        return calendar.date(from: components) ?? calendar.startOfDay(for: date)
    }

    private func parseGamesPayload(_ payload: String, selectedTeam: Team) -> [Game]? {
        guard let data = payload.data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return nil }
        return array.compactMap { parseGame($0, selectedTeam: selectedTeam) }
    }

    private func parseScheduleRangePayload(_ payload: String, selectedTeam: Team) -> [UpcomingGameSchedule]? {
        guard let data = payload.data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return nil }
        let calendar = Calendar.current
        let items = array.compactMap { item -> UpcomingGameSchedule? in
            guard let game = parseGame(item, selectedTeam: selectedTeam), game.isMyTeam else { return nil }
            let gameDate = (item["gameDate"] as? String).flatMap { dateFormatter.date(from: $0) }
                ?? gameDateFromId(game.id)
            guard let gameDate else { return nil }
            return UpcomingGameSchedule(gameDate: calendar.startOfDay(for: gameDate), game: game)
        }
        return items.sorted {
            let dateOrder = calendar.compare($0.gameDate, to: $1.gameDate, toGranularity: .day)
            if dateOrder != .orderedSame {
                return dateOrder == .orderedAscending
            }
            let lhsTime = parseGameTimeToSortKey($0.game.time)
            let rhsTime = parseGameTimeToSortKey($1.game.time)
            if lhsTime != rhsTime {
                return lhsTime < rhsTime
            }
            return $0.game.id < $1.game.id
        }
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
            isMyTeam: selectedTeam != .none && (homeTeamId == selectedTeam || awayTeamId == selectedTeam),
            weather: (json["weather"] as? [String: Any]).flatMap(parseGameWeatherSummary)
        )
    }

    private func parseGameWeatherSummary(_ json: [String: Any]) -> GameWeatherSummary {
        GameWeatherSummary(
            stadiumCode: json["stadiumCode"] as? String ?? "",
            stadiumName: json["stadiumName"] as? String ?? "",
            stadiumShortName: cleanOptionalString(json["stadiumShortName"]) ?? (json["stadiumName"] as? String ?? ""),
            forecastDate: cleanOptionalString(json["forecastDate"]),
            forecastTime: cleanOptionalString(json["forecastTime"]),
            forecastTimeLabel: cleanOptionalString(json["forecastTimeLabel"]),
            condition: json["condition"] as? String ?? "",
            temperatureC: jsonInt(json["temperatureC"]),
            precipitationProbability: jsonInt(json["precipitationProbability"]),
            precipitationType: cleanOptionalString(json["precipitationType"]),
            windSpeedMps: jsonDouble(json["windSpeedMps"]),
            isIndoor: json["isIndoor"] as? Bool ?? false,
            displayText: json["displayText"] as? String ?? ""
        )
    }

    private func parseGameWeatherHourly(_ json: [String: Any]) -> GameWeatherHourly {
        let items = (json["items"] as? [[String: Any]] ?? []).map { item in
            GameWeatherHourlyItem(
                forecastDate: item["forecastDate"] as? String ?? "",
                forecastTime: item["forecastTime"] as? String ?? "",
                timeLabel: item["timeLabel"] as? String ?? "",
                condition: item["condition"] as? String ?? "",
                temperatureC: jsonInt(item["temperatureC"]),
                precipitationProbability: jsonInt(item["precipitationProbability"]),
                precipitationType: cleanOptionalString(item["precipitationType"]),
                windSpeedMps: jsonDouble(item["windSpeedMps"]),
                isGameStartForecast: item["isGameStartForecast"] as? Bool ?? false
            )
        }
        return GameWeatherHourly(
            gameId: json["gameId"] as? String ?? "",
            stadiumCode: json["stadiumCode"] as? String ?? "",
            stadiumName: json["stadiumName"] as? String ?? "",
            stadiumShortName: cleanOptionalString(json["stadiumShortName"]) ?? (json["stadiumName"] as? String ?? ""),
            gameStartTime: cleanOptionalString(json["gameStartTime"]),
            items: items
        )
    }

    private func parseLiveGameState(_ json: [String: Any]) -> LiveGameState? {
        let homeTeamName = json["homeTeam"] as? String ?? ""
        let awayTeamName = json["awayTeam"] as? String ?? ""
        let bases = json["bases"] as? [String: Any] ?? [:]
        let baseRunners = json["baseRunners"] as? [String: Any] ?? [:]
        let rawStatus = json["status"] as? String ?? ""
        let status = statusFromBackend(rawStatus)
        let inning = (json["inning"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? defaultInning(for: status)
        let homeLineup = (json["homeLineup"] as? [[String: Any]] ?? []).compactMap(parseLineupSlot)
        let awayLineup = (json["awayLineup"] as? [[String: Any]] ?? []).compactMap(parseLineupSlot)

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
            baseFirstRunner: cleanOptionalString(baseRunners["first"]),
            baseSecondRunner: cleanOptionalString(baseRunners["second"]),
            baseThirdRunner: cleanOptionalString(baseRunners["third"]),
            pitcher: json["pitcher"] as? String ?? "",
            batter: json["batter"] as? String ?? "",
            pitcherPitchCount: json["pitcherPitchCount"] as? Int,
            lastEventType: (json["lastEventType"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            homeLineup: homeLineup,
            awayLineup: awayLineup,
            homeStartingPitcher: (json["homeStartingPitcher"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            awayStartingPitcher: (json["awayStartingPitcher"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        )
    }

    private func parseLineupSlot(_ json: [String: Any]) -> LineupSlot? {
        guard let name = json["playerName"] as? String, !name.isEmpty else { return nil }
        return LineupSlot(
            battingOrder: json["battingOrder"] as? Int ?? 0,
            playerName: name,
            positionCode: (json["positionCode"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            positionName: (json["positionName"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            isStarter: json["isStarter"] as? Bool ?? false,
            isActive: json["isActive"] as? Bool ?? true
        )
    }

    private func parseLiveEvent(_ json: [String: Any]) -> LiveEvent? {
        let cursor = json["cursor"] as? Int64 ?? 0
        return LiveEvent(
            cursor: cursor,
            id: (json["id"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "\(cursor)",
            type: (json["type"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "OTHER",
            description: json["description"] as? String ?? "",
            time: formatBackendTime(json["time"] as? String ?? ""),
            pitcher: (json["pitcher"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            batter: (json["batter"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            inning: (json["inning"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            atBatId: (json["atBatId"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            seqno: json["seqno"] as? Int,
            homeScoreAfter: json["homeScoreAfter"] as? Int,
            awayScoreAfter: json["awayScoreAfter"] as? Int,
            pitchNum: json["pitchNum"] as? Int,
            pitchSpeed: json["pitchSpeed"] as? Int,
            pitchStuff: (json["pitchStuff"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            ballAfter: json["ballAfter"] as? Int,
            strikeAfter: json["strikeAfter"] as? Int,
            outAfter: json["outAfter"] as? Int,
            batterRecord: json["batterRecord"] as? [String: Any],
            homeWinProbability: json["homeWinProbability"] as? Double,
            awayWinProbability: json["awayWinProbability"] as? Double,
            wpaByPlate: json["wpaByPlate"] as? Double
        )
    }

    private func parseTeamRecordStats(_ json: [String: Any]) -> TeamRecordStats {
        TeamRecordStats(
            teamId: json["teamId"] as? String ?? "",
            ranking: json["ranking"] as? Int,
            wra: jsonDouble(json["wra"]),
            lastFiveGames: json["lastFiveGames"] as? String,
            updatedAt: json["updatedAt"] as? String
        )
    }

    private func parseTeamRecordStanding(_ json: [String: Any]) -> TeamRecordStanding {
        let teamShortName = json["teamShortName"] as? String
        let teamName = (teamShortName?.isEmpty == false ? teamShortName : json["teamName"] as? String) ?? ""
        return TeamRecordStanding(
            teamId: json["teamId"] as? String ?? "",
            teamName: teamName,
            ranking: jsonInt(json["ranking"]),
            wra: jsonDouble(json["wra"]),
            gameCount: jsonInt(json["gameCount"]),
            winGameCount: jsonInt(json["winGameCount"]),
            drawnGameCount: jsonInt(json["drawnGameCount"]),
            loseGameCount: jsonInt(json["loseGameCount"]),
            gameBehind: jsonDouble(json["gameBehind"]),
            continuousGameResult: json["continuousGameResult"] as? String,
            updatedAt: json["updatedAt"] as? String
        )
    }

    private func jsonInt(_ value: Any?) -> Int? {
        if let intValue = value as? Int { return intValue }
        if let doubleValue = value as? Double { return Int(doubleValue) }
        if let stringValue = value as? String { return Int(stringValue) }
        return nil
    }

    private func jsonDouble(_ value: Any?) -> Double? {
        if let doubleValue = value as? Double { return doubleValue }
        if let intValue = value as? Int { return Double(intValue) }
        if let stringValue = value as? String { return Double(stringValue) }
        return nil
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

            let task = webSocketSession.webSocketTask(with: url)

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

    private func cleanOptionalString(_ raw: Any?) -> String? {
        guard let raw else { return nil }
        if raw is NSNull { return nil }
        let value = "\(raw)".trimmingCharacters(in: .whitespacesAndNewlines)
        let lowercased = value.lowercased()
        guard !value.isEmpty, lowercased != "null", lowercased != "<null>" else { return nil }
        return value
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
        // 고정 포맷 파싱/출력은 en_US_POSIX + KST 고정 (비그레고리력 기기·해외 시간대 대응)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "HH:mm"
        f.timeZone = TimeZone(identifier: "Asia/Seoul")
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

    private func gameDateFromId(_ gameId: String) -> Date? {
        guard gameId.count >= 8 else { return nil }
        let prefix = String(gameId.prefix(8))
        guard prefix.allSatisfy(\.isNumber) else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd"
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        return formatter.date(from: prefix)
    }

    private func todayString() -> String {
        dateFormatter.string(from: Date())
    }

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        // 백엔드 날짜(KST 기준)를 파싱/생성 — 기기 캘린더/시간대 영향 차단
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone(identifier: "Asia/Seoul")
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
