import Foundation

/// APNs 디바이스 토큰을 백엔드에 등록/해제하는 매니저
enum PushTokenManager {

    /// 경기 구독 시 디바이스 토큰을 백엔드에 등록
    static func register(gameId: String, myTeam: String) async {
        guard let token = UserDefaults.standard.string(forKey: "apns_device_token"),
              !token.isEmpty else {
            print("[PushToken] No device token available")
            return
        }

        let url = URL(string: "\(BackendConfig.baseURL)/device-tokens")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        #if DEBUG
        let isSandbox = true
        #else
        let isSandbox = false
        #endif

        let body: [String: Any] = [
            "token": token,
            "game_id": gameId,
            "my_team": myTeam,
            "platform": "ios",
            "is_sandbox": isSandbox
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("[PushToken] Registered: \(statusCode)")
        } catch {
            print("[PushToken] Register failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Watch Push Token

    /// 워치 APNs 토큰을 백엔드에 등록
    static func registerWatchToken(gameId: String, myTeam: String) async {
        guard let token = UserDefaults.standard.string(forKey: "watch_apns_device_token"),
              !token.isEmpty else {
            print("[PushToken] No watch device token available")
            return
        }

        let url = URL(string: "\(BackendConfig.baseURL)/device-tokens")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        #if DEBUG
        let isSandbox = true
        #else
        let isSandbox = false
        #endif

        let body: [String: Any] = [
            "token": token,
            "game_id": gameId,
            "my_team": myTeam,
            "platform": "watchos",
            "is_sandbox": isSandbox
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("[PushToken] Watch token registered: \(statusCode)")
        } catch {
            print("[PushToken] Watch token register failed: \(error.localizedDescription)")
        }
    }

    /// 워치 토큰 해제
    static func unregisterWatchToken(gameId: String) async {
        guard let token = UserDefaults.standard.string(forKey: "watch_apns_device_token"),
              !token.isEmpty else { return }

        let url = URL(string: "\(BackendConfig.baseURL)/device-tokens/\(token)?game_id=\(gameId)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("[PushToken] Watch token unregistered: \(statusCode)")
        } catch {
            print("[PushToken] Watch token unregister failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Live Activity Token

    /// Live Activity push token 등록
    static func registerLiveActivityToken(gameId: String, token: String, myTeam: String) async {
        let url = URL(string: "\(BackendConfig.baseURL)/live-activity-tokens")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "token": token,
            "game_id": gameId,
            "my_team": myTeam,
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("[PushToken] Live Activity token registered: \(statusCode)")
        } catch {
            print("[PushToken] Live Activity token register failed: \(error.localizedDescription)")
        }
    }

    /// Live Activity token 해제
    static func unregisterLiveActivityToken(gameId: String) async {
        guard let encoded = gameId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return }
        let url = URL(string: "\(BackendConfig.baseURL)/live-activity-tokens?game_id=\(encoded)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"

        do {
            let (_, _) = try await URLSession.shared.data(for: request)
            print("[PushToken] Live Activity token unregistered")
        } catch {
            print("[PushToken] Live Activity token unregister failed: \(error.localizedDescription)")
        }
    }

    /// 경기 구독 해제 시 디바이스 토큰 삭제
    static func unregister(gameId: String) async {
        guard let token = UserDefaults.standard.string(forKey: "apns_device_token"),
              !token.isEmpty else { return }

        let url = URL(string: "\(BackendConfig.baseURL)/device-tokens/\(token)?game_id=\(gameId)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("[PushToken] Unregistered: \(statusCode)")
        } catch {
            print("[PushToken] Unregister failed: \(error.localizedDescription)")
        }
    }
}
