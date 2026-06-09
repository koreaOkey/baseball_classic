import ActivityKit
import Foundation

/// Live Activity 생명주기 관리 + ActivityKit push token 관찰
final class LiveActivityManager {
    static let shared = LiveActivityManager()
    static let previewGameId = "live_score_preview"
    static let lockScreenLiveScoreEnabledKey = "lock_screen_live_score_enabled"

    private var currentActivity: Activity<BaseballGameAttributes>?
    private var pushTokenTask: Task<Void, Never>?
    private var highlightClearTask: Task<Void, Never>?

    private init() {}

    var isLockScreenCardEnabled: Bool {
        if UserDefaults.standard.object(forKey: Self.lockScreenLiveScoreEnabledKey) == nil {
            return true
        }
        return UserDefaults.standard.bool(forKey: Self.lockScreenLiveScoreEnabledKey)
    }

    // MARK: - Start

    /// 경기 관람 시작 시 Live Activity 생성
    func startActivity(
        gameId: String,
        homeTeam: String,
        awayTeam: String,
        homeScore: Int,
        awayScore: Int,
        inning: String,
        status: String,
        myTeam: String
    ) {
        guard isLockScreenCardEnabled else {
            endActivity(gameId: gameId)
            return
        }

        // 기존 액티비티 종료
        endCurrentActivity()

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("[LiveActivity] Activities not enabled")
            return
        }

        let attributes = BaseballGameAttributes(
            gameId: gameId,
            homeTeam: homeTeam,
            awayTeam: awayTeam,
            myTeam: myTeam
        )
        let initialState = BaseballGameAttributes.ContentState(
            homeScore: homeScore,
            awayScore: awayScore,
            inning: inning,
            ball: 0, strike: 0, out: 0,
            baseFirst: false, baseSecond: false, baseThird: false,
            pitcher: "", batter: "",
            status: status,
            lastEventType: nil,
            lastEventDescription: nil,
            highlightEventType: nil,
            highlightEventText: nil
        )

        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: .init(state: initialState, staleDate: nil),
                pushType: .token
            )
            currentActivity = activity
            observePushToken(activity: activity)
            print("[LiveActivity] Started for game \(gameId)")
        } catch {
            print("[LiveActivity] Failed to start: \(error)")
        }
    }

    func startOrUpdateActivity(
        state: LiveGameState,
        myTeam: String,
        latestEvent: LiveEvent? = nil,
        alert: Bool = false
    ) {
        guard isLockScreenCardEnabled else {
            endActivity(gameId: state.gameId)
            return
        }

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("[LiveActivity] Activities not enabled")
            return
        }

        if state.status != .live {
            endCurrentActivity()
            return
        }

        let attributes = BaseballGameAttributes(
            gameId: state.gameId,
            homeTeam: state.homeTeamId.rawValue,
            awayTeam: state.awayTeamId.rawValue,
            myTeam: myTeam
        )
        let contentState = contentState(from: state, latestEvent: latestEvent, myTeam: myTeam, alert: alert)

        if let activity = activeActivity(for: state.gameId) {
            currentActivity = activity
            observePushToken(activity: activity)
            Task {
                await update(activity: activity, state: contentState, latestEvent: latestEvent, alert: alert)
            }
            return
        }

        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: .init(state: contentState, staleDate: Date.now.addingTimeInterval(60)),
                pushType: .token
            )
            currentActivity = activity
            observePushToken(activity: activity)
            print("[LiveActivity] Started for game \(state.gameId)")
        } catch {
            print("[LiveActivity] Failed to start: \(error)")
        }
    }

    func startPreviewActivity(
        gameId: String = LiveActivityManager.previewGameId,
        homeTeam: String,
        awayTeam: String,
        myTeam: String,
        state: BaseballGameAttributes.ContentState,
        alert: Bool
    ) {
        guard isLockScreenCardEnabled else {
            endActivity(gameId: gameId)
            return
        }

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("[LiveActivity] Activities not enabled")
            return
        }

        let attributes = BaseballGameAttributes(
            gameId: gameId,
            homeTeam: homeTeam,
            awayTeam: awayTeam,
            myTeam: myTeam
        )

        if let activity = activeActivity(for: gameId) {
            currentActivity = activity
            observePushToken(activity: activity)
            Task {
                await update(activity: activity, state: state, latestEvent: nil, alert: alert)
            }
            return
        }

        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: .init(state: state, staleDate: Date.now.addingTimeInterval(60)),
                pushType: .token
            )
            currentActivity = activity
            observePushToken(activity: activity)
            print("[LiveActivity] Preview started")
        } catch {
            print("[LiveActivity] Failed to start preview: \(error)")
        }
    }

    // MARK: - Update

    /// 로컬에서 상태 업데이트 (WebSocket 스트림용)
    func updateActivity(state: BaseballGameAttributes.ContentState) async {
        guard let activity = currentActivity else { return }
        await activity.update(.init(state: state, staleDate: nil))
    }

    func updateFromPushPayload(_ userInfo: [AnyHashable: Any]) {
        guard let gameId = userInfo["game_id"] as? String, !gameId.isEmpty else { return }
        guard isLockScreenCardEnabled else {
            endActivity(gameId: gameId)
            return
        }
        guard let activity = activeActivity(for: gameId) else { return }

        let eventType = userInfo["event_type"] as? String
        let eventDescription = userInfo["event_description"] as? String
            ?? userInfo["description"] as? String
            ?? highlightText(eventType: eventType, batter: userInfo["batter"] as? String)
        let myTeam = userInfo["my_team"] as? String ?? activity.attributes.myTeam
        let nextState = BaseballGameAttributes.ContentState(
            homeScore: userInfo["home_score"] as? Int ?? activity.content.state.homeScore,
            awayScore: userInfo["away_score"] as? Int ?? activity.content.state.awayScore,
            inning: userInfo["inning"] as? String ?? activity.content.state.inning,
            ball: userInfo["ball"] as? Int ?? activity.content.state.ball,
            strike: userInfo["strike"] as? Int ?? activity.content.state.strike,
            out: userInfo["out"] as? Int ?? activity.content.state.out,
            baseFirst: userInfo["base_first"] as? Bool ?? activity.content.state.baseFirst,
            baseSecond: userInfo["base_second"] as? Bool ?? activity.content.state.baseSecond,
            baseThird: userInfo["base_third"] as? Bool ?? activity.content.state.baseThird,
            pitcher: userInfo["pitcher"] as? String ?? activity.content.state.pitcher,
            batter: userInfo["batter"] as? String ?? activity.content.state.batter,
            status: userInfo["status"] as? String ?? activity.content.state.status,
            lastEventType: eventType ?? activity.content.state.lastEventType,
            lastEventDescription: eventDescription ?? activity.content.state.lastEventDescription,
            highlightEventType: shouldHighlight(eventType: eventType, inning: userInfo["inning"] as? String, homeTeam: activity.attributes.homeTeam, awayTeam: activity.attributes.awayTeam, myTeam: myTeam) ? eventType : nil,
            highlightEventText: shouldHighlight(eventType: eventType, inning: userInfo["inning"] as? String, homeTeam: activity.attributes.homeTeam, awayTeam: activity.attributes.awayTeam, myTeam: myTeam) ? eventDescription : nil
        )
        currentActivity = activity
        Task {
            await update(activity: activity, state: nextState, latestEvent: nil, alert: nextState.highlightEventText != nil)
        }
    }

    // MARK: - End

    /// 경기 종료 또는 관람 해제 시 Live Activity 종료
    func endCurrentActivity() {
        pushTokenTask?.cancel()
        pushTokenTask = nil
        highlightClearTask?.cancel()
        highlightClearTask = nil

        guard let activity = currentActivity else { return }

        let lastState = activity.content.state
        Task {
            await activity.end(
                .init(state: lastState, staleDate: nil),
                dismissalPolicy: .immediate
            )
        }
        currentActivity = nil
        print("[LiveActivity] Ended")
    }

    func endActivity(gameId: String) {
        if currentActivity?.attributes.gameId == gameId {
            endCurrentActivity()
            return
        }
        if let activity = Activity<BaseballGameAttributes>.activities.first(where: { $0.attributes.gameId == gameId }) {
            Task {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }

    func endPreviewActivities() {
        endActivity(gameId: Self.previewGameId)
    }

    func endAllActivities() {
        pushTokenTask?.cancel()
        pushTokenTask = nil
        highlightClearTask?.cancel()
        highlightClearTask = nil

        for activity in Activity<BaseballGameAttributes>.activities {
            guard activity.activityState != .ended && activity.activityState != .dismissed else { continue }
            Task {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
        currentActivity = nil
    }

    // MARK: - Push Token Observation

    /// ActivityKit push token이 갱신될 때마다 백엔드에 등록
    private func observePushToken(activity: Activity<BaseballGameAttributes>) {
        pushTokenTask?.cancel()
        pushTokenTask = Task {
            for await token in activity.pushTokenUpdates {
                let tokenString = token.map { String(format: "%02x", $0) }.joined()
                print("[LiveActivity] Push token updated: \(tokenString.prefix(16))...")
                await PushTokenManager.registerLiveActivityToken(
                    gameId: activity.attributes.gameId,
                    token: tokenString,
                    myTeam: activity.attributes.myTeam
                )
            }
        }
    }

    // MARK: - Cleanup

    /// 앱 재시작 시 stale 액티비티 정리
    func cleanupStaleActivities() {
        for activity in Activity<BaseballGameAttributes>.activities {
            if activity.activityState == .ended || activity.activityState == .dismissed {
                continue
            }

            if activity.attributes.gameId == Self.previewGameId || activity.content.state.status != GameStatus.live.rawValue {
                Task {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
        }
    }

    private func activeActivity(for gameId: String) -> Activity<BaseballGameAttributes>? {
        if let currentActivity, currentActivity.attributes.gameId == gameId {
            return currentActivity
        }
        return Activity<BaseballGameAttributes>.activities.first { activity in
            activity.attributes.gameId == gameId && activity.activityState != .ended && activity.activityState != .dismissed
        }
    }

    private func update(
        activity: Activity<BaseballGameAttributes>,
        state: BaseballGameAttributes.ContentState,
        latestEvent: LiveEvent?,
        alert: Bool
    ) async {
        let alertConfiguration: AlertConfiguration? = {
            guard alert, let text = state.highlightEventText, !text.isEmpty else { return nil }
            return AlertConfiguration(
                title: "\(eventLabel(state.highlightEventType ?? latestEvent?.type ?? ""))",
                body: "\(text)",
                sound: .default
            )
        }()

        await activity.update(
            .init(state: state, staleDate: Date.now.addingTimeInterval(60), relevanceScore: alert ? 100 : 50),
            alertConfiguration: alertConfiguration
        )

        guard alert, state.highlightEventText != nil else { return }
        highlightClearTask?.cancel()
        highlightClearTask = Task { [weak self, weak activity] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard let self, let activity, !Task.isCancelled else { return }
            var latest = activity.content.state
            latest = BaseballGameAttributes.ContentState(
                homeScore: latest.homeScore,
                awayScore: latest.awayScore,
                inning: latest.inning,
                ball: latest.ball,
                strike: latest.strike,
                out: latest.out,
                baseFirst: latest.baseFirst,
                baseSecond: latest.baseSecond,
                baseThird: latest.baseThird,
                pitcher: latest.pitcher,
                batter: latest.batter,
                status: latest.status,
                lastEventType: latest.lastEventType,
                lastEventDescription: latest.lastEventDescription,
                highlightEventType: nil,
                highlightEventText: nil
            )
            await activity.update(.init(state: latest, staleDate: Date.now.addingTimeInterval(60), relevanceScore: 50))
            if self.currentActivity?.id == activity.id {
                self.highlightClearTask = nil
            }
        }
    }

    private func contentState(
        from state: LiveGameState,
        latestEvent: LiveEvent?,
        myTeam: String,
        alert: Bool
    ) -> BaseballGameAttributes.ContentState {
        let eventType = latestEvent?.type ?? state.lastEventType
        let eventDescription = latestEvent?.description
        let shouldHighlight = alert && shouldHighlight(
            eventType: eventType,
            inning: state.inning,
            homeTeam: state.homeTeamId.rawValue,
            awayTeam: state.awayTeamId.rawValue,
            myTeam: myTeam
        )
        let highlight = shouldHighlight
            ? (eventDescription ?? highlightText(eventType: eventType, batter: latestEvent?.batter ?? state.batter))
            : nil

        return BaseballGameAttributes.ContentState(
            homeScore: state.homeScore,
            awayScore: state.awayScore,
            inning: state.inning,
            ball: state.ball,
            strike: state.strike,
            out: state.out,
            baseFirst: state.baseFirst,
            baseSecond: state.baseSecond,
            baseThird: state.baseThird,
            pitcher: state.pitcher,
            batter: state.batter,
            status: state.status.rawValue,
            lastEventType: eventType,
            lastEventDescription: eventDescription,
            highlightEventType: shouldHighlight ? eventType : nil,
            highlightEventText: highlight
        )
    }

    private func shouldHighlight(
        eventType: String?,
        inning: String?,
        homeTeam: String,
        awayTeam: String,
        myTeam: String
    ) -> Bool {
        guard let type = eventType?.uppercased() else { return false }
        guard ["SCORE", "HOMERUN", "HIT", "WALK", "STEAL"].contains(type) else { return false }
        guard !myTeam.isEmpty, myTeam != Team.none.rawValue else { return true }
        guard let inning else { return true }
        if inning.contains("초") {
            return awayTeam == myTeam
        }
        if inning.contains("말") {
            return homeTeam == myTeam
        }
        return true
    }

    private func highlightText(eventType: String?, batter: String?) -> String? {
        guard let type = eventType?.uppercased() else { return nil }
        let name = (batter?.isEmpty == false ? batter! : "우리 팀")
        switch type {
        case "SCORE": return "\(name) 득점!"
        case "HOMERUN": return "\(name) 홈런!"
        case "HIT": return "\(name) 안타"
        case "WALK": return "\(name) 볼넷 출루"
        case "STEAL": return "\(name) 도루 성공"
        default: return nil
        }
    }

    private func eventLabel(_ type: String) -> String {
        switch type.uppercased() {
        case "SCORE": return "득점"
        case "HOMERUN": return "홈런"
        case "HIT": return "안타"
        case "WALK": return "볼넷"
        case "STEAL": return "도루"
        default: return "경기 업데이트"
        }
    }
}
