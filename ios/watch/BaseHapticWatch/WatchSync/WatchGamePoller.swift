import Foundation

/// 워치에서 독립적으로 백엔드를 폴링하여 내 팀 경기가 LIVE 상태가 되면 감지
/// 1) 앱 시작 시 한 번 경기 목록 조회 → 내 팀 경기 시작 시간 파악
/// 2) 시작 시간 5분 전부터 30초 간격 폴링
/// 3) 이미 LIVE인 경기가 있으면 즉시 팝업
final class WatchGamePoller: ObservableObject {
    static let shared = WatchGamePoller()

    private var pollingTask: Task<Void, Never>?
    private var promptedGameIds: Set<String> = []

    private var baseURL: String {
        Bundle.main.object(forInfoDictionaryKey: "BACKEND_BASE_URL") as? String
            ?? "http://localhost:8080"
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

            // 1) 경기 목록 조회하여 내 팀 경기 시작 시간 확인
            let games = await self.fetchTodayGames()
            let myGames = games.filter { Self.isMyTeamGame($0, myTeam: myTeam) }

            // 이미 LIVE인 경기가 있으면 즉시 처리
            for game in myGames {
                let status = (game["status"] as? String ?? "").uppercased()
                if status == "LIVE" || status == "IN_PROGRESS" {
                    let gameId = game["id"] as? String ?? ""
                    guard !gameId.isEmpty, !promptedGameIds.contains(gameId) else { continue }
                    promptedGameIds.insert(gameId)
                    await MainActor.run {
                        onGameLive(gameId, game["homeTeam"] as? String ?? "", game["awayTeam"] as? String ?? "")
                    }
                    return // 이미 LIVE → 폴링 불필요
                }
            }

            // 예정된 경기 시작 시간 중 가장 빠른 것 찾기
            let earliestStart = myGames.compactMap { Self.parseStartTime($0) }.min()

            if let startTime = earliestStart {
                // 시작 5분 전까지 대기
                let waitUntil = startTime.addingTimeInterval(-5 * 60)
                let waitSeconds = waitUntil.timeIntervalSinceNow
                if waitSeconds > 0 {
                    print("⌚ [WatchPoller] 내 팀 경기 \(startTime)까지 대기 중 (\(Int(waitSeconds))초 후 폴링 시작)")
                    try? await Task.sleep(nanoseconds: UInt64(waitSeconds * 1_000_000_000))
                }
            } else if myGames.isEmpty {
                // 내 팀 경기 없음 → 폴링 불필요
                print("⌚ [WatchPoller] 오늘 내 팀 경기 없음, 폴링 중단")
                return
            }
            // 시작 시간을 모르면 (startTime 파싱 실패) 바로 폴링 시작

            // 2) 30초 간격 폴링
            print("⌚ [WatchPoller] 폴링 시작")
            while !Task.isCancelled {
                await self.pollOnce(myTeam: myTeam, onGameLive: onGameLive)
                try? await Task.sleep(nanoseconds: 30_000_000_000)
            }
        }
    }

    func stopPolling() {
        pollingTask?.cancel()
        pollingTask = nil
    }

    // MARK: - Network

    private func fetchTodayGames() async -> [[String: Any]] {
        let dateStr = Self.todayDateString()
        let endpoint = "\(baseURL.trimmingSuffix("/"))/games?date=\(dateStr)&limit=100"

        guard let url = URL(string: endpoint) else { return [] }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10

        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let games = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        return games
    }

    private func pollOnce(myTeam: String, onGameLive: @escaping (String, String, String) -> Void) async {
        let games = await fetchTodayGames()

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

    private static func todayDateString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = kst
        return formatter.string(from: Date())
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
