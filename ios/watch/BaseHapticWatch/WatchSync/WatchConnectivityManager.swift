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

    /// 경기 데이터가 마지막으로 업데이트된 시각
    private var gameDataUpdatedAt: Date?

    struct WatchSyncPrompt {
        let gameId: String
        let homeTeam: String
        let awayTeam: String
    }

    /// 백그라운드에서도 햅틱 및 UI 업데이트를 유지하기 위한 Extended Runtime Session
    private var extendedSession: WKExtendedRuntimeSession?

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
            // 종료된 경기가 자정을 넘긴 경우 바로 정리
            DispatchQueue.main.async {
                self.clearExpiredGameData()
            }
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
        if applicationContext["type"] is String {
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
            homeTeam: Self.displayTeamName(message["home_team"] as? String ?? ""),
            awayTeam: Self.displayTeamName(message["away_team"] as? String ?? ""),
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
        if let updatedAt = message["updated_at"] as? TimeInterval {
            gameDataUpdatedAt = Date(timeIntervalSince1970: updatedAt)
        } else {
            gameDataUpdatedAt = Date()
        }

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
            // 오래된 이벤트는 햅틱 무시
            let isStale: Bool = {
                guard let ts = message["timestamp"] as? TimeInterval else { return false }
                return Date().timeIntervalSince1970 - ts > Self.staleEventThreshold
            }()
            if !isStale {
                triggerHaptic(eventType: eventType)
            }
        }

        // 경기가 LIVE이면 Extended Session 시작, 종료되면 정지
        if !isFinished {
            startExtendedSession()
        } else {
            stopExtendedSession()
        }
    }

    private func handleThemeUpdate(_ message: [String: Any]) {
        if let teamName = message["my_team"] as? String, !teamName.isEmpty {
            syncedTeamName = teamName
        }
    }

    /// 10초 이상 된 이벤트는 stale로 판정 (워치 재시작 시 이벤트 폭주 방지)
    private static let staleEventThreshold: TimeInterval = 10.0

    /// 같은 이벤트가 중복 채널(APNs + WatchConnectivity)로 올 때 이중 햅틱 방지
    private var lastTriggeredEvent: String?
    private var lastTriggeredAt: Date?
    private static let deduplicationWindow: TimeInterval = 3.0

    private func handleHapticEvent(_ message: [String: Any]) {
        guard let eventType = message["event_type"] as? String, !eventType.isEmpty else { return }

        // 오래된 이벤트는 햅틱 무시
        if let eventTimestamp = message["timestamp"] as? TimeInterval,
           Date().timeIntervalSince1970 - eventTimestamp > Self.staleEventThreshold {
            print("[WatchConnectivity] Skipping stale haptic event: \(eventType)")
            return
        }

        latestEventType = eventType.uppercased()
        latestEventTimestamp = Date()
        triggerHaptic(eventType: eventType)
    }

    private func handleWatchSyncPrompt(_ message: [String: Any]) {
        let gameId = message["game_id"] as? String ?? ""
        guard !gameId.isEmpty else { return }

        // 이미 해당 경기 데이터를 수신 중이면 팝업 무시
        if gameData?.gameId == gameId, gameData?.isLive == true { return }
        // 이미 같은 경기 팝업이 떠있으면 무시
        if watchSyncPrompt?.gameId == gameId { return }

        watchSyncPrompt = WatchSyncPrompt(
            gameId: gameId,
            homeTeam: Self.displayTeamName(message["home_team"] as? String ?? ""),
            awayTeam: Self.displayTeamName(message["away_team"] as? String ?? "")
        )

        if let myTeam = message["my_team"] as? String, !myTeam.isEmpty {
            syncedTeamName = myTeam
        }
    }

    // MARK: - Direct Push Handlers (워치 직접 APNs 수신)

    /// APNs push에서 직접 받은 햅틱 이벤트 처리
    func handleDirectPushHapticEvent(eventType: String) {
        let upper = eventType.uppercased()
        print("⌚ [WatchConn] Direct push haptic: \(upper) | extendedSession=\(extendedSession?.state.rawValue ?? -1)")
        latestEventType = upper
        latestEventTimestamp = Date()
        triggerHaptic(eventType: eventType)
    }

    /// APNs push에서 직접 받은 게임 데이터 처리
    func handleDirectPushGameData(_ message: [String: Any]) {
        print("⌚ [WatchConn] Direct push game_data")
        handleGameData(message)
    }

    // MARK: - Send Watch Push Token to iPhone

    /// 워치 APNs 토큰을 iPhone으로 전달 (iPhone이 백엔드에 등록)
    func sendWatchPushToken(_ token: String) {
        guard WCSession.default.activationState == .activated else {
            print("⌚ [WatchConn] Session not activated, deferring watch token send")
            return
        }

        let message: [String: Any] = [
            "type": "watch_push_token",
            "watch_token": token,
        ]

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(message, replyHandler: nil) { error in
                print("⌚ [WatchConn] sendMessage failed for watch token, using transferUserInfo: \(error.localizedDescription)")
                WCSession.default.transferUserInfo(message)
            }
        } else {
            WCSession.default.transferUserInfo(message)
        }
        print("⌚ [WatchConn] Watch push token sent to iPhone")
    }

    // MARK: - Send Watch Sync Response
    func sendSyncResponse(gameId: String, accepted: Bool) {
        let response: [String: Any] = [
            "type": "watch_sync_response",
            "game_id": gameId,
            "accepted": accepted
        ]

        if WCSession.default.isReachable {
            WCSession.default.sendMessage(response, replyHandler: nil) { error in
                print("[WatchConnectivity] sendMessage failed, falling back to transferUserInfo: \(error.localizedDescription)")
                WCSession.default.transferUserInfo(response)
            }
        } else {
            // 폰이 백그라운드/비활성 상태 → transferUserInfo로 전달 (큐잉됨)
            WCSession.default.transferUserInfo(response)
        }
    }

    func clearSyncPrompt() {
        watchSyncPrompt = nil
    }

    /// 종료된 경기가 자정을 넘긴 경우 gameData를 nil로 초기화
    func clearExpiredGameData() {
        guard let game = gameData, !game.isLive,
              let updatedAt = gameDataUpdatedAt else { return }
        let todayMidnight = Calendar.current.startOfDay(for: Date())
        if updatedAt < todayMidnight {
            gameData = nil
            gameDataUpdatedAt = nil
        }
    }

    // MARK: - Extended Runtime Session (백그라운드 햅틱 유지)

    private var extendedSessionRetryCount = 0
    private static let maxExtendedSessionRetries = 3

    /// 경기 동기화가 시작되면 Extended Runtime Session을 시작하여 백그라운드에서도 햅틱이 동작하도록 합니다.
    func startExtendedSession() {
        guard extendedSession == nil || extendedSession?.state == .invalid else { return }
        let session = WKExtendedRuntimeSession()
        session.delegate = self
        extendedSession = session
        session.start()
        print("[WatchConnectivity] Extended runtime session started")
    }

    /// 경기 동기화가 종료되면 Extended Runtime Session을 종료합니다.
    func stopExtendedSession() {
        extendedSessionRetryCount = 0
        guard let session = extendedSession, session.state == .running else {
            extendedSession = nil
            return
        }
        session.invalidate()
        extendedSession = nil
        print("[WatchConnectivity] Extended runtime session stopped")
    }

    // MARK: - Haptic Feedback (Apple Watch Taptic Engine)
    private func triggerHaptic(eventType: String) {
        let upper = eventType.uppercased()
        // 중복 채널(APNs + WatchConnectivity) 이중 햅틱 방지
        if let lastEvent = lastTriggeredEvent, lastEvent == upper,
           let lastTime = lastTriggeredAt,
           Date().timeIntervalSince(lastTime) < Self.deduplicationWindow {
            print("⌚ [WatchConn] Skipping duplicate haptic: \(upper)")
            return
        }
        lastTriggeredEvent = upper
        lastTriggeredAt = Date()

        let device = WKInterfaceDevice.current()

        switch eventType.uppercased() {
        case "VICTORY":
            // 4초간 반복 진동 (0.3초 간격, 13회)
            for i in 0..<13 {
                DispatchQueue.main.asyncAfter(deadline: .now() + Double(i) * 0.3) {
                    device.play(.notification)
                }
            }
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
            device.play(.click)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                device.play(.click)
            }
        case "STEAL":
            device.play(.click)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                device.play(.click)
            }
        case "OUT":
            device.play(.directionDown)
        case "DOUBLE_PLAY":
            device.play(.directionDown)
        case "TRIPLE_PLAY":
            device.play(.directionDown)
        case "SCORE":
            device.play(.notification)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                device.play(.notification)
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                device.play(.notification)
            }
        case "STRIKE":
            device.play(.click)
        case "BALL":
            device.play(.click)
        default:
            break
        }
    }
    /// 백엔드 팀명("SSG 랜더스"), 팀 코드("DOOSAN"), 마스코트("베어스") 등 어느 형식이든 canonical 마스코트로 변환.
    /// 팀 비교/표시 양쪽에서 사용 — 멱등.
    static func displayTeamName(_ name: String) -> String {
        let n = name.trimmingCharacters(in: .whitespaces).lowercased()
        if n.isEmpty { return name }
        if n.contains("doosan") || n.contains("두산") || n.contains("베어스") { return "베어스" }
        if n.contains("lg") || n.contains("엘지") || n.contains("트윈스") { return "트윈스" }
        if n.contains("kiwoom") || n.contains("키움") || n.contains("히어로즈") || n.contains("넥센") { return "히어로즈" }
        if n.contains("samsung") || n.contains("삼성") || n.contains("라이온즈") { return "라이온즈" }
        if n.contains("lotte") || n.contains("롯데") || n.contains("자이언츠") { return "자이언츠" }
        if n.contains("ssg") || n.contains("lander") || n.contains("에스에스지") || n.contains("랜더스") { return "랜더스" }
        if n.contains("kt") || n.contains("wiz") || n.contains("케이티") || n.contains("위즈") { return "위즈" }
        if n.contains("hanwha") || n.contains("한화") || n.contains("이글스") { return "이글스" }
        if n.contains("kia") || n.contains("기아") || n.contains("타이거즈") { return "타이거즈" }
        if n.contains("nc") || n.contains("dinos") || n.contains("엔씨") || n.contains("다이노스") { return "다이노스" }
        return name // 매칭 안 되면 원본
    }
}

// MARK: - WKExtendedRuntimeSessionDelegate
extension WatchConnectivityManager: WKExtendedRuntimeSessionDelegate {
    func extendedRuntimeSessionDidStart(_ extendedRuntimeSession: WKExtendedRuntimeSession) {
        print("[WatchConnectivity] Extended session running")
        extendedSessionRetryCount = 0
    }

    func extendedRuntimeSessionWillExpire(_ extendedRuntimeSession: WKExtendedRuntimeSession) {
        // 세션 만료 임박 시 새 세션 시작
        print("[WatchConnectivity] Extended session expiring, restarting...")
        extendedSession = nil
        extendedSessionRetryCount = 0
        if gameData?.isLive == true {
            startExtendedSession()
        }
    }

    func extendedRuntimeSession(_ extendedRuntimeSession: WKExtendedRuntimeSession, didInvalidateWith reason: WKExtendedRuntimeSessionInvalidationReason, error: (any Error)?) {
        print("[WatchConnectivity] Extended session invalidated: \(reason.rawValue), error: \(error?.localizedDescription ?? "none")")
        extendedSession = nil
        extendedSessionRetryCount += 1
        // 재시도 횟수 초과 시 포기 (APNs push가 앱을 깨워줌)
        guard extendedSessionRetryCount <= Self.maxExtendedSessionRetries else {
            print("[WatchConnectivity] Extended session retry limit reached, relying on APNs push")
            return
        }
        // 경기가 진행 중이면 세션 재시작
        if gameData?.isLive == true {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
                self?.startExtendedSession()
            }
        }
    }
}
