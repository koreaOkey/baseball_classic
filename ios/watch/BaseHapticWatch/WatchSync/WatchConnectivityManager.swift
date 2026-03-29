import Foundation
import WatchConnectivity
import WatchKit

/// 워치 측 WatchConnectivity 관리자
/// Android의 DataLayerListenerService에 대응
final class WatchConnectivityManager: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = WatchConnectivityManager()

    @Published var gameData: GameData?
    @Published var syncedTeamName: String = "DEFAULT"
    @Published var latestEventType: String?
    @Published var latestEventTimestamp: Date?
    @Published var watchSyncPrompt: WatchSyncPrompt?

    struct WatchSyncPrompt {
        let gameId: String
        let homeTeam: String
        let awayTeam: String
    }

    private override init() {
        super.init()
    }

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // MARK: - WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if let error = error {
            print("[WatchConnectivity] Activation failed: \(error.localizedDescription)")
        } else {
            print("[WatchConnectivity] Activated: \(activationState.rawValue)")
        }

        let ctx = session.receivedApplicationContext

        // applicationContext에서 게임 데이터 복원
        if let type = ctx["type"] as? String, type == "game_data" {
            handleMessage(ctx)
        }

        // applicationContext에서 테마 복원
        if let teamName = ctx["my_team"] as? String, !teamName.isEmpty {
            DispatchQueue.main.async {
                self.syncedTeamName = teamName
            }
        }
    }

    /// iPhone에서 보낸 메시지 수신
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        handleMessage(message)
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        handleMessage(message)
        replyHandler(["status": "ok"])
    }

    /// applicationContext 업데이트 수신 (테마 + 게임 데이터 폴백)
    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        if let type = applicationContext["type"] as? String {
            // game_data가 applicationContext로 온 경우 (폴백)
            handleMessage(applicationContext)
        } else if let teamName = applicationContext["my_team"] as? String, !teamName.isEmpty {
            DispatchQueue.main.async {
                self.syncedTeamName = teamName
            }
        }
    }

    /// transferUserInfo로 수신된 메시지 처리
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        handleMessage(userInfo)
    }

    // MARK: - Message Handler
    private func handleMessage(_ message: [String: Any]) {
        guard let type = message["type"] as? String else { return }

        DispatchQueue.main.async {
            switch type {
            case "game_data":
                self.handleGameData(message)
            case "theme_update":
                self.handleThemeUpdate(message)
            case "haptic_event":
                self.handleHapticEvent(message)
            case "watch_sync_prompt":
                self.handleWatchSyncPrompt(message)
            default:
                break
            }
        }
    }

    private func handleGameData(_ message: [String: Any]) {
        let rawStatus = message["status"] as? String ?? ""
        let inning = message["inning"] as? String ?? ""
        let isFinished = rawStatus.uppercased() == "FINISHED" || inning.contains("경기 종료")

        let rawOut = max(message["out"] as? Int ?? 0, 0)
        let normalizedOut = isFinished ? 0 : rawOut
        let normalizedBall = (isFinished || rawOut >= 3) ? 0 : max(message["ball"] as? Int ?? 0, 0)
        let normalizedStrike = (isFinished || rawOut >= 3) ? 0 : max(message["strike"] as? Int ?? 0, 0)
        let normalizedInning = isFinished ? "경기 종료" : inning

        gameData = GameData(
            gameId: message["game_id"] as? String ?? "",
            homeTeam: message["home_team"] as? String ?? "",
            awayTeam: message["away_team"] as? String ?? "",
            homeScore: message["home_score"] as? Int ?? 0,
            awayScore: message["away_score"] as? Int ?? 0,
            inning: normalizedInning,
            isLive: !isFinished,
            ballCount: normalizedBall,
            strikeCount: normalizedStrike,
            outCount: normalizedOut,
            bases: BaseStatus(
                first: message["base_first"] as? Bool ?? false,
                second: message["base_second"] as? Bool ?? false,
                third: message["base_third"] as? Bool ?? false
            ),
            pitcher: message["pitcher"] as? String ?? "",
            batter: message["batter"] as? String ?? "",
            scoreDiff: 0,
            myTeamName: message["my_team"] as? String ?? ""
        )

        // 모바일에서 이미 경기 관람을 시작한 경우 → 워치 팝업 자동 수락
        if let prompt = watchSyncPrompt,
           prompt.gameId == (message["game_id"] as? String ?? "") {
            sendSyncResponse(gameId: prompt.gameId, accepted: true)
            watchSyncPrompt = nil
        }

        // 테마 동기화
        if let myTeam = message["my_team"] as? String, !myTeam.isEmpty {
            syncedTeamName = myTeam
        }

        // 인라인 이벤트 처리
        if let eventType = message["event_type"] as? String, !eventType.isEmpty {
            latestEventType = eventType.uppercased()
            latestEventTimestamp = Date()
            triggerHaptic(eventType: eventType)
        }
    }

    private func handleThemeUpdate(_ message: [String: Any]) {
        if let teamName = message["my_team"] as? String, !teamName.isEmpty {
            syncedTeamName = teamName
        }
    }

    private func handleHapticEvent(_ message: [String: Any]) {
        guard let eventType = message["event_type"] as? String, !eventType.isEmpty else { return }

        latestEventType = eventType.uppercased()
        latestEventTimestamp = Date()
        triggerHaptic(eventType: eventType)
    }

    private func handleWatchSyncPrompt(_ message: [String: Any]) {
        let gameId = message["game_id"] as? String ?? ""
        guard !gameId.isEmpty else { return }

        watchSyncPrompt = WatchSyncPrompt(
            gameId: gameId,
            homeTeam: message["home_team"] as? String ?? "",
            awayTeam: message["away_team"] as? String ?? ""
        )

        if let myTeam = message["my_team"] as? String, !myTeam.isEmpty {
            syncedTeamName = myTeam
        }
    }

    // MARK: - Send Watch Sync Response
    func sendSyncResponse(gameId: String, accepted: Bool) {
        guard WCSession.default.isReachable else { return }

        let response: [String: Any] = [
            "type": "watch_sync_response",
            "game_id": gameId,
            "accepted": accepted
        ]

        WCSession.default.sendMessage(response, replyHandler: nil) { error in
            print("[WatchConnectivity] Failed to send sync response: \(error.localizedDescription)")
        }
    }

    func clearSyncPrompt() {
        watchSyncPrompt = nil
    }

    // MARK: - Haptic Feedback (Apple Watch Taptic Engine)
    private func triggerHaptic(eventType: String) {
        let device = WKInterfaceDevice.current()

        switch eventType.uppercased() {
        case "HOMERUN":
            // 3번 강한 진동
            device.play(.notification)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                device.play(.notification)
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                device.play(.notification)
            }
        case "HIT":
            device.play(.click)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                device.play(.click)
            }
        case "WALK":
            device.play(.directionUp)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                device.play(.directionUp)
            }
        case "STEAL":
            device.play(.start)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                device.play(.start)
            }
        case "OUT":
            device.play(.directionDown)
        case "DOUBLE_PLAY":
            device.play(.failure)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                device.play(.failure)
            }
        case "TRIPLE_PLAY":
            device.play(.failure)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                device.play(.failure)
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                device.play(.failure)
            }
        case "SCORE":
            device.play(.success)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                device.play(.success)
            }
        case "STRIKE":
            device.play(.retry)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                device.play(.retry)
            }
        case "BALL":
            device.play(.click)
        default:
            break
        }
    }
}
