import Foundation
import Supabase
import AuthenticationServices

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

        // Listen for auth state changes
        for await (event, session) in client.auth.authStateChanges {
            switch event {
            case .signedIn:
                if let session {
                    authState = makeLoggedInState(from: session)
                }
            case .signedOut:
                authState = .loggedOut
            default:
                break
            }
        }
    }

    func signInWithKakao() async throws {
        try await client.auth.signInWithOAuth(provider: .kakao, redirectTo: URL(string: "com.basehaptic.app://login-callback"))
    }

    func signInWithApple() async throws {
        let helper = AppleSignInHelper()
        let credential = try await helper.performSignIn()

        guard let idTokenData = credential.identityToken,
              let idToken = String(data: idTokenData, encoding: .utf8) else {
            throw NSError(domain: "AuthManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Apple ID token missing"])
        }

        try await client.auth.signInWithIdToken(
            credentials: .init(provider: .apple, idToken: idToken)
        )
    }

    func signOut() async throws {
        try await client.auth.signOut()
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

// MARK: - Apple Sign In Helper
private class AppleSignInHelper: NSObject, ASAuthorizationControllerDelegate {
    private var continuation: CheckedContinuation<ASAuthorizationAppleIDCredential, Error>?

    func performSignIn() async throws -> ASAuthorizationAppleIDCredential {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation

            let provider = ASAuthorizationAppleIDProvider()
            let request = provider.createRequest()
            request.requestedScopes = [.email]

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        if let credential = authorization.credential as? ASAuthorizationAppleIDCredential {
            continuation?.resume(returning: credential)
        } else {
            continuation?.resume(throwing: NSError(domain: "AppleSignIn", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid credential type"]))
        }
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }
}
