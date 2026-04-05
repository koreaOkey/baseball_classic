import Foundation

/// 워치에서 독립적으로 백엔드를 폴링하여 내 팀 경기가 LIVE 상태가 되면 감지
final class WatchGamePoller: ObservableObject {
    static let shared = WatchGamePoller()

    private var pollingTask: Task<Void, Never>?
    private var promptedGameIds: Set<String> = []

    private var baseURL: String {
        Bundle.main.object(forInfoDictionaryKey: "BACKEND_BASE_URL") as? String
            ?? "http://localhost:8080"
    }

    private init() {}

    /// 폴링 시작 — syncedTeamName이 설정된 후 호출
    func startPolling(myTeam: String, onGameLive: @escaping (String, String, String) -> Void) {
        stopPolling()
        guard !myTeam.isEmpty, myTeam != "DEFAULT" else { return }
        promptedGameIds = []

        pollingTask = Task { [weak self] in
            while !Task.isCancelled {
                await self?.pollOnce(myTeam: myTeam, onGameLive: onGameLive)

                // 30초 간격 폴링
                try? await Task.sleep(nanoseconds: 30_000_000_000)
            }
        }
    }

    func stopPolling() {
        pollingTask?.cancel()
        pollingTask = nil
    }

    private func pollOnce(myTeam: String, onGameLive: @escaping (String, String, String) -> Void) async {
        let dateStr = Self.todayDateString()
        let endpoint = "\(baseURL.trimmingSuffix("/"))/games?date=\(dateStr)&limit=100"

        guard let url = URL(string: endpoint) else { return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10

        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let games = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return
        }

        for game in games {
            let gameId = game["id"] as? String ?? ""
            let homeTeam = game["homeTeam"] as? String ?? ""
            let awayTeam = game["awayTeam"] as? String ?? ""
            let statusStr = (game["status"] as? String ?? "").uppercased()

            guard !gameId.isEmpty else { continue }

            // 내 팀 경기인지 확인
            let isMyTeam = homeTeam.contains(myTeam) || awayTeam.contains(myTeam)
                || myTeam.contains(homeTeam) || myTeam.contains(awayTeam)
                || Self.normalizeTeamName(homeTeam) == Self.normalizeTeamName(myTeam)
                || Self.normalizeTeamName(awayTeam) == Self.normalizeTeamName(myTeam)
            guard isMyTeam else { continue }

            let isLive = statusStr == "LIVE" || statusStr == "IN_PROGRESS"
            guard isLive else { continue }

            // 이미 팝업을 띄운 경기는 건너뜀
            guard !promptedGameIds.contains(gameId) else { continue }
            promptedGameIds.insert(gameId)

            await MainActor.run {
                onGameLive(gameId, homeTeam, awayTeam)
            }
        }
    }

    private static func todayDateString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        return formatter.string(from: Date())
    }

    /// 팀 이름 정규화 (예: "SSG 랜더스" → "SSG", "KIA 타이거즈" → "KIA")
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
