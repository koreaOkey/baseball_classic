import Foundation
import Supabase
import AuthenticationServices
import UIKit
import SafariServices

enum AuthState: Equatable {
    case loading
    case loggedOut
    case loggedIn(userId: String, email: String?, provider: String)
}

@MainActor
class AuthManager: ObservableObject {
    static let shared = AuthManager()

    @Published var authState: AuthState = .loading

    private let client = SupabaseClientProvider.client

    func initialize() async {
        // Check existing session first
        if let session = try? await client.auth.session {
            authState = makeLoggedInState(from: session)
        } else {
            authState = .loggedOut
        }

        // Listen for auth state changes (actor isolation 우회: await로 stream 먼저 획득)
        let authClient = client.auth
        Task.detached { [weak self] in
            let stream = await authClient.authStateChanges
            for await (event, session) in stream {
                await MainActor.run { [weak self] in
                    guard let self else { return }
                    print("[Auth] authStateChange event: \(event)")
                    switch event {
                    case .signedIn:
                        if let session {
                            self.authState = self.makeLoggedInState(from: session)
                            print("[Auth] authState → loggedIn")
                        }
                    case .signedOut:
                        self.authState = .loggedOut
                        print("[Auth] authState → loggedOut")
                    default:
                        break
                    }
                }
            }
        }
    }

    func signInWithKakao() async throws {
        // Supabase 2.5.1: Provider enum에 kakao 없음 → URL 직접 구성 (implicit flow)
        let baseURL = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String
            ?? "https://snrafqoqpmtoannnnwdq.supabase.co"
        let redirectTo = "com.basehaptic.app://login-callback"
        let encodedRedirect = redirectTo.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        guard let url = URL(string: "\(baseURL)/auth/v1/authorize?provider=kakao&redirect_to=\(encodedRedirect)") else {
            throw URLError(.badURL)
        }

        let callbackURL = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<URL, Error>) in
            let session = ASWebAuthenticationSession(
                url: url,
                callbackURLScheme: "com.basehaptic.app"
            ) { callbackURL, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let callbackURL {
                    continuation.resume(returning: callbackURL)
                } else {
                    continuation.resume(throwing: URLError(.cancelled))
                }
            }
            session.prefersEphemeralWebBrowserSession = false
            session.presentationContextProvider = WebAuthContextProvider.shared
            session.start()
        }

        let session = try await client.auth.session(from: callbackURL)
        authState = makeLoggedInState(from: session)
    }

    func signInWithApple(authorization: ASAuthorization) async throws {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let idTokenData = credential.identityToken,
              let idToken = String(data: idTokenData, encoding: .utf8) else {
            throw NSError(domain: "AuthManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Apple ID token missing"])
        }

        let session = try await client.auth.signInWithIdToken(
            credentials: .init(provider: .apple, idToken: idToken)
        )
        authState = makeLoggedInState(from: session)
    }

    func signOut() async throws {
        try await client.auth.signOut()
        authState = .loggedOut
    }

    func deleteAccount() async throws {
        let session = try await client.auth.session
        let backendURL = Bundle.main.object(forInfoDictionaryKey: "BACKEND_BASE_URL") as? String
            ?? "https://baseballclassic-production.up.railway.app"
        guard let deleteURL = URL(string: "\(backendURL)/account") else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: deleteURL)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }

        // 유저가 이미 삭제되었으므로 signOut 실패해도 무시
        try? await client.auth.signOut()
        authState = .loggedOut

        // 로컬 데이터 초기화
        UserDefaults.standard.removeObject(forKey: "selected_team")
        UserDefaults.standard.removeObject(forKey: "synced_game_id")
        UserDefaults.standard.removeObject(forKey: "synced_my_team")
    }

    private func makeLoggedInState(from session: Session) -> AuthState {
        let provider = session.user.appMetadata["provider"]?.value as? String ?? "unknown"
        return .loggedIn(
            userId: session.user.id.uuidString,
            email: session.user.email,
            provider: provider
        )
    }

    func handleOpenURL(_ url: URL) {
        Task {
            do {
                try await client.auth.session(from: url)
            } catch {
                print("[Auth] handleOpenURL error: \(error)")
            }
        }
    }
}

// MARK: - ASWebAuthenticationSession Context Provider
private class WebAuthContextProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = WebAuthContextProvider()

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        guard let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
              let window = scene.windows.first(where: { $0.isKeyWindow }) else {
            return ASPresentationAnchor()
        }
        return window
    }
}


