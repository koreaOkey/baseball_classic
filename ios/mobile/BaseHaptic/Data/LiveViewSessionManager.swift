import Foundation

enum LiveViewSurface: String {
    case ios
    case watchos
}

enum LiveViewSessionManager {
    private static let installIdKey = "live_view_install_id"

    static var installId: String {
        if let existing = UserDefaults.standard.string(forKey: installIdKey), !existing.isEmpty {
            return existing
        }
        let created = UUID().uuidString
        UserDefaults.standard.set(created, forKey: installIdKey)
        return created
    }

    static func setActive(
        gameId: String,
        surface: LiveViewSurface,
        active: Bool,
        myTeam: String,
        tokenKey: String? = nil
    ) async {
        guard !gameId.isEmpty,
              let url = URL(string: "\(BackendConfig.baseURL)/live-view-sessions") else {
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any?] = [
            "game_id": gameId,
            "user_key": installId,
            "surface": surface.rawValue,
            "token_key": tokenKey,
            "my_team": myTeam,
            "active": active
        ]
        request.httpBody = try? JSONSerialization.data(
            withJSONObject: body.compactMapValues { $0 }
        )

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            if !(200..<300).contains(statusCode) {
                print("[LiveViewSession] update failed surface=\(surface.rawValue) active=\(active) status=\(statusCode)")
            }
        } catch {
            print("[LiveViewSession] update error: \(error.localizedDescription)")
        }
    }
}
