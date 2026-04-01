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

        let body: [String: Any] = [
            "token": token,
            "game_id": gameId,
            "my_team": myTeam,
            "platform": "ios"
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
