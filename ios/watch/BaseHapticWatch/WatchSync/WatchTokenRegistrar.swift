import Foundation

/// 워치에서 직접 백엔드에 APNs 토큰을 등록/해제하는 유틸리티
/// 폰 앱을 거치지 않고 워치가 독립적으로 push 수신을 시작할 수 있도록 합니다.
enum WatchTokenRegistrar {

    private static var baseURL: String {
        Bundle.main.object(forInfoDictionaryKey: "BACKEND_BASE_URL") as? String
            ?? "http://localhost:8080"
    }

    /// embedded.mobileprovision에서 aps-environment를 읽어 sandbox 여부 판단
    private static func isApnsSandbox() -> Bool {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let str = String(data: data, encoding: .ascii) else {
            #if DEBUG
            return true
            #else
            return false
            #endif
        }
        if let start = str.range(of: "<plist"),
           let end = str.range(of: "</plist>") {
            let plistStr = String(str[start.lowerBound...end.upperBound])
            if let plistData = plistStr.data(using: .ascii),
               let plist = try? PropertyListSerialization.propertyList(from: plistData, format: nil) as? [String: Any],
               let entitlements = plist["Entitlements"] as? [String: Any],
               let apsEnv = entitlements["aps-environment"] as? String {
                return apsEnv == "development"
            }
        }
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    /// 워치 APNs 토큰을 백엔드에 직접 등록 (폰 앱 없이 push 수신 시작)
    static func register(gameId: String, myTeam: String) async {
        guard let token = UserDefaults.standard.string(forKey: "watch_apns_device_token"),
              !token.isEmpty else {
            print("⌚ [WatchTokenRegistrar] No watch APNs token available")
            return
        }

        guard let url = URL(string: "\(baseURL)/device-tokens") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "token": token,
            "game_id": gameId,
            "my_team": myTeam,
            "platform": "watchos",
            "is_sandbox": isApnsSandbox()
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("⌚ [WatchTokenRegistrar] Registered: \(statusCode)")
        } catch {
            print("⌚ [WatchTokenRegistrar] Register failed: \(error.localizedDescription)")
        }
    }

    /// 워치 APNs 토큰 해제
    static func unregister(gameId: String) async {
        guard let token = UserDefaults.standard.string(forKey: "watch_apns_device_token"),
              !token.isEmpty else { return }

        guard let url = URL(string: "\(baseURL)/device-tokens/\(token)?game_id=\(gameId)") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("⌚ [WatchTokenRegistrar] Unregistered: \(statusCode)")
        } catch {
            print("⌚ [WatchTokenRegistrar] Unregister failed: \(error.localizedDescription)")
        }
    }
}
