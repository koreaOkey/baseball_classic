import Foundation

// MARK: - VentingEventsReporter

/// 분풀이 6.1 지표 리포터 — POST /venting/events (LiveViewSessionManager POST 패턴).
///
/// 완전 베스트에포트: UI 를 절대 블로킹하지 않으며(자체 detached Task, fire-and-forget),
/// 실패는 조용히 삼킨다. 서버가 비활성이면 {ok:false} 를 돌려주지만 클라이언트는 무시한다.
///
/// event_type: room_enter · watch_room_enter · destroy_complete · retry_prompt_shown · retry_ad_start · retry_ad_complete.
enum VentingEventsReporter {

    /// fire-and-forget: 반환 즉시 종료하며 결과를 기다리지 않는다.
    /// - Parameters:
    ///   - team: `VentingGameContext.myTeamId`.
    ///   - entrySource: 진입 경로 (live / home_card / loss_push / unknown).
    static func report(
        eventType: String,
        team: String,
        entrySource: String,
        gameId: String?
    ) {
        guard let url = URL(string: "\(BackendConfig.baseURL)/venting/events") else { return }

        var body: [String: Any] = [
            "event_type": eventType,
            "team": team,
            "entry_source": entrySource,
            "platform": "ios"
        ]
        if let gameId, !gameId.isEmpty {
            body["game_id"] = gameId
        }

        Task.detached(priority: .utility) {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
            // 로그인 상태면 토큰 첨부 → 서버가 user_id 를 추출해 순 사용자 집계 가능.
            // 비로그인/실패 시 익명 이벤트로 전송(비차단).
            if let token = try? await SupabaseClientProvider.client.auth.session.accessToken {
                request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            }
            do {
                let (_, response) = try await URLSession.shared.data(for: request)
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                if !(200..<300).contains(status) {
                    print("[VentingEventsReporter] report failed eventType=\(eventType) status=\(status)")
                }
            } catch {
                // best-effort — 절대 UI 를 블로킹하지 않고, 실패는 조용히 삼킨다.
            }
        }
    }
}
