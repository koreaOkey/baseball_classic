import Foundation

/// APNs 디바이스 토큰을 백엔드에 등록/해제하는 매니저
enum PushTokenManager {

    /// embedded.mobileprovision에서 aps-environment를 읽어 sandbox 여부 판단
    static func isApnsSandbox() -> Bool {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let str = String(data: data, encoding: .ascii) else {
            // mobileprovision이 없으면 (시뮬레이터 등) DEBUG로 폴백
            #if DEBUG
            return true
            #else
            return false
            #endif
        }
        // plist 부분 추출
        if let start = str.range(of: "<plist"),
           let end = str.range(of: "</plist>") {
            let plistStr = String(str[start.lowerBound...end.upperBound])
            if let plistData = plistStr.data(using: .ascii),
               let plist = try? PropertyListSerialization.propertyList(from: plistData, format: nil) as? [String: Any],
               let entitlements = plist["Entitlements"] as? [String: Any],
               let apsEnv = entitlements["aps-environment"] as? String {
                let isSandbox = apsEnv == "development"
                print("[PushToken] aps-environment=\(apsEnv) → isSandbox=\(isSandbox)")
                return isSandbox
            }
        }
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

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

        let isSandbox = Self.isApnsSandbox()

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

        let isSandbox = Self.isApnsSandbox()

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

/// 응원팀 단위 글로벌 푸시 구독을 백엔드와 동기화한다.
/// 트리거: APNs 토큰 수신, 응원팀 변경, 앱 시작.
enum TeamSubscriptionManager {
    private static let lastTokenKey = "team_subscription_last_token"
    private static let lastTeamKey = "team_subscription_last_team"

    /// 현재 token + selected_team 을 백엔드에 동기화. 직전과 동일하면 생략.
    static func syncIfNeeded() async {
        guard let token = UserDefaults.standard.string(forKey: "apns_device_token"),
              !token.isEmpty else { return }

        let raw = UserDefaults.standard.string(forKey: "selected_team") ?? ""
        let myTeam = (raw.lowercased() == "none" || raw.isEmpty) ? "" : raw

        let lastToken = UserDefaults.standard.string(forKey: lastTokenKey)
        let lastTeam = UserDefaults.standard.string(forKey: lastTeamKey)

        if myTeam.isEmpty {
            if let prev = lastToken, !prev.isEmpty {
                await unregister(token: prev)
            }
            return
        }

        if token == lastToken && myTeam == lastTeam { return }

        await register(token: token, myTeam: myTeam)
    }

    private static func register(token: String, myTeam: String) async {
        let url = URL(string: "\(BackendConfig.baseURL)/team-subscriptions")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = [
            "token": token,
            "my_team": myTeam,
            "platform": "ios",
            "is_sandbox": PushTokenManager.isApnsSandbox()
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            if (200..<300).contains(statusCode) {
                UserDefaults.standard.set(token, forKey: lastTokenKey)
                UserDefaults.standard.set(myTeam, forKey: lastTeamKey)
                print("[TeamSubscription] Registered team=\(myTeam) status=\(statusCode)")
            } else {
                print("[TeamSubscription] Register failed status=\(statusCode)")
            }
        } catch {
            print("[TeamSubscription] Register error: \(error.localizedDescription)")
        }
    }

    private static func unregister(token: String) async {
        let url = URL(string: "\(BackendConfig.baseURL)/team-subscriptions/\(token)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            if (200..<300).contains(statusCode) {
                UserDefaults.standard.removeObject(forKey: lastTokenKey)
                UserDefaults.standard.removeObject(forKey: lastTeamKey)
                print("[TeamSubscription] Unregistered status=\(statusCode)")
            }
        } catch {
            print("[TeamSubscription] Unregister error: \(error.localizedDescription)")
        }
    }
}
