import Foundation

/// 워치에서 독립적으로 백엔드를 폴링하여 내 팀 경기가 LIVE 상태가 되면 감지
/// 1) 앱 시작 시 한 번 경기 목록 조회 → 내 팀 경기 시작 시간 파악
/// 2) 시작 시간 5분 전부터 30초 간격 폴링
/// 3) 이미 LIVE인 경기가 있으면 즉시 팝업
final class WatchGamePoller: ObservableObject {
    static let shared = WatchGamePoller()
    #if DEBUG
    private static let defaultBaseURL = "https://baseballclassic-production-4796.up.railway.app"
    #else
    private static let defaultBaseURL = "https://baseballclassic-production-4796.up.railway.app"
    #endif

    private var pollingTask: Task<Void, Never>?
    private var promptedGameIds: Set<String> = []

    private var baseURL: String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "BACKEND_BASE_URL") as? String else {
            return Self.defaultBaseURL
        }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.hasPrefix("$(") {
            return Self.defaultBaseURL
        }
        return trimmed
    }

    private static let kst = TimeZone(identifier: "Asia/Seoul")!

    private init() {}

    /// 폴링 시작 — syncedTeamName이 설정된 후 호출
    func startPolling(myTeam: String, onGameLive: @escaping (String, String, String) -> Void) {
        stopPolling()
        guard !myTeam.isEmpty, myTeam != "DEFAULT" else { return }
        promptedGameIds = []

        pollingTask = Task { [weak self] in
            guard let self else { return }

            // 순연/편성 변경 대비: 일정을 한 번만 조회하지 않고 주기적으로 재조회
            let scheduleRefreshInterval: TimeInterval = 20 * 60

            while !Task.isCancelled {
                // 1) 경기 목록 조회하여 내 팀 경기 시작 시간 확인
                //    조회 실패(네트워크 오류 등)는 빈 일정과 구분하여 백오프 재시도
                guard let games = await self.fetchTodayGamesWithRetry() else { return } // 취소된 경우에만 nil
                let myGames = games.filter { Self.isMyTeamGame($0, myTeam: myTeam) }

                // 이미 LIVE인 경기가 있으면 즉시 처리
                for game in myGames {
                    let status = (game["status"] as? String ?? "").uppercased()
                    if status == "LIVE" || status == "IN_PROGRESS" {
                        let gameId = game["id"] as? String ?? ""
                        guard !gameId.isEmpty, !self.promptedGameIds.contains(gameId) else { continue }
                        self.promptedGameIds.insert(gameId)
                        await MainActor.run {
                            onGameLive(gameId, game["homeTeam"] as? String ?? "", game["awayTeam"] as? String ?? "")
                        }
                        return // 이미 LIVE → 폴링 불필요
                    }
                }

                if myGames.isEmpty {
                    // 내 팀 경기 없음 → 편성 변경 가능성이 있으니 주기적으로 일정 재확인
                    wlog("⌚ [WatchPoller] 오늘 내 팀 경기 없음, \(Int(scheduleRefreshInterval / 60))분 후 일정 재확인")
                    try? await Task.sleep(nanoseconds: UInt64(scheduleRefreshInterval * 1_000_000_000))
                    continue
                }

                // 예정된 경기 시작 시간 중 가장 빠른 것 찾기
                if let startTime = myGames.compactMap({ Self.parseStartTime($0) }).min() {
                    // 시작 5분 전까지 대기 — 단, 일정 갱신 주기를 넘기면 재조회
                    let waitUntil = startTime.addingTimeInterval(-5 * 60)
                    let waitSeconds = waitUntil.timeIntervalSinceNow
                    if waitSeconds > 0 {
                        wlog("⌚ [WatchPoller] 내 팀 경기 \(startTime)까지 대기 중 (\(Int(waitSeconds))초 후 폴링 시작)")
                        try? await Task.sleep(nanoseconds: UInt64(min(waitSeconds, scheduleRefreshInterval) * 1_000_000_000))
                        if waitSeconds > scheduleRefreshInterval { continue } // 아직 멀었음 → 일정 재조회
                    }
                }
                // 시작 시간을 모르면 (startTime 파싱 실패) 바로 폴링 시작

                // 2) 30초 간격 폴링 (일정 갱신 주기마다 일정 재조회로 복귀)
                // 초기 일정 조회에서 파악한 gameId만 단건 조회 — 전체 목록 재조회는 일정 갱신 시에만
                let myGameIds = myGames.compactMap { $0["id"] as? String }.filter { !$0.isEmpty }
                wlog("⌚ [WatchPoller] 폴링 시작")
                let pollingStartedAt = Date()
                while !Task.isCancelled {
                    if myGameIds.isEmpty {
                        await self.pollOnce(myTeam: myTeam, onGameLive: onGameLive)
                    } else {
                        await self.pollGames(gameIds: myGameIds, onGameLive: onGameLive)
                    }
                    try? await Task.sleep(nanoseconds: 30_000_000_000)
                    if Date().timeIntervalSince(pollingStartedAt) > scheduleRefreshInterval { break }
                }
            }
        }
    }

    func stopPolling() {
        pollingTask?.cancel()
        pollingTask = nil
    }

    // MARK: - Network

    /// 경기 목록 조회 — 실패(네트워크 오류/파싱 실패)는 nil, 진짜 빈 일정은 []로 구분
    private func fetchTodayGames() async -> [[String: Any]]? {
        let dateStr = Self.todayDateString()
        let endpoint = "\(baseURL.trimmingSuffix("/"))/games?date=\(dateStr)&limit=100"

        guard let url = URL(string: endpoint) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10

        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let games = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }
        return games
    }

    /// 조회 실패 시 포기하지 않고 백오프(15초 → 30초 → 60초 상한)로 재시도. 취소된 경우에만 nil 반환.
    private func fetchTodayGamesWithRetry() async -> [[String: Any]]? {
        var delay: TimeInterval = 15
        while !Task.isCancelled {
            if let games = await fetchTodayGames() { return games }
            wlog("⌚ [WatchPoller] 경기 목록 조회 실패, \(Int(delay))초 후 재시도")
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            delay = min(delay * 2, 60)
        }
        return nil
    }

    /// 단일 경기 조회 — /games 목록 요소와 동일한 GameSummaryOut 형태
    private func fetchGame(gameId: String) async -> [String: Any]? {
        let endpoint = "\(baseURL.trimmingSuffix("/"))/games/\(gameId)"

        guard let url = URL(string: endpoint) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10

        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let game = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return game
    }

    private func pollGames(gameIds: [String], onGameLive: @escaping (String, String, String) -> Void) async {
        for gameId in gameIds {
            guard !promptedGameIds.contains(gameId) else { continue }
            // 일시적 조회 실패면 이번 tick만 건너뛰고 다음 폴링에서 재시도
            guard let game = await fetchGame(gameId: gameId) else { continue }

            let statusStr = (game["status"] as? String ?? "").uppercased()
            guard statusStr == "LIVE" || statusStr == "IN_PROGRESS" else { continue }
            promptedGameIds.insert(gameId)

            await MainActor.run {
                onGameLive(gameId, game["homeTeam"] as? String ?? "", game["awayTeam"] as? String ?? "")
            }
        }
    }

    private func pollOnce(myTeam: String, onGameLive: @escaping (String, String, String) -> Void) async {
        // 일시적 조회 실패면 이번 tick만 건너뛰고 다음 폴링에서 재시도
        guard let games = await fetchTodayGames() else { return }

        for game in games {
            let gameId = game["id"] as? String ?? ""
            guard !gameId.isEmpty else { continue }
            guard Self.isMyTeamGame(game, myTeam: myTeam) else { continue }

            let statusStr = (game["status"] as? String ?? "").uppercased()
            guard statusStr == "LIVE" || statusStr == "IN_PROGRESS" else { continue }
            guard !promptedGameIds.contains(gameId) else { continue }
            promptedGameIds.insert(gameId)

            await MainActor.run {
                onGameLive(gameId, game["homeTeam"] as? String ?? "", game["awayTeam"] as? String ?? "")
            }
        }
    }

    // MARK: - Helpers

    private static func isMyTeamGame(_ game: [String: Any], myTeam: String) -> Bool {
        let homeTeam = game["homeTeam"] as? String ?? ""
        let awayTeam = game["awayTeam"] as? String ?? ""
        return normalizeTeamName(homeTeam) == normalizeTeamName(myTeam)
            || normalizeTeamName(awayTeam) == normalizeTeamName(myTeam)
            || homeTeam.contains(myTeam) || awayTeam.contains(myTeam)
            || myTeam.contains(homeTeam) || myTeam.contains(awayTeam)
    }

    /// "14:00" 형태의 startTime을 오늘 날짜의 KST Date로 변환
    private static func parseStartTime(_ game: [String: Any]) -> Date? {
        guard let timeStr = game["startTime"] as? String, !timeStr.isEmpty else { return nil }

        // "14:00" or "14:30" 형태
        let parts = timeStr.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }

        var calendar = Calendar.current
        calendar.timeZone = kst
        var components = calendar.dateComponents([.year, .month, .day], from: Date())
        components.hour = parts[0]
        components.minute = parts[1]
        components.second = 0
        components.timeZone = kst
        return calendar.date(from: components)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = kst
        return formatter
    }()

    private static func todayDateString() -> String {
        dateFormatter.string(from: Date())
    }

    private static func normalizeTeamName(_ name: String) -> String {
        let first = name.split(separator: " ").first.map(String.init) ?? name
        return first.uppercased()
    }
}

private extension String {
    func trimmingSuffix(_ suffix: String) -> String {
        if hasSuffix(suffix) {
            return String(dropLast(suffix.count))
        }
        return self
    }
}
