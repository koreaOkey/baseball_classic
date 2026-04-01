import ActivityKit
import Foundation

/// Live Activity 생명주기 관리 + ActivityKit push token 관찰
final class LiveActivityManager {
    static let shared = LiveActivityManager()

    private var currentActivity: Activity<BaseballGameAttributes>?
    private var pushTokenTask: Task<Void, Never>?

    private init() {}

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
            lastEventType: nil
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

    // MARK: - Update

    /// 로컬에서 상태 업데이트 (WebSocket 스트림용)
    func updateActivity(state: BaseballGameAttributes.ContentState) async {
        guard let activity = currentActivity else { return }
        await activity.update(.init(state: state, staleDate: nil))
    }

    // MARK: - End

    /// 경기 종료 또는 관람 해제 시 Live Activity 종료
    func endCurrentActivity() {
        pushTokenTask?.cancel()
        pushTokenTask = nil

        guard let activity = currentActivity else { return }

        let lastState = activity.content.state
        Task {
            await activity.end(
                .init(state: lastState, staleDate: nil),
                dismissalPolicy: .default
            )
        }
        currentActivity = nil
        print("[LiveActivity] Ended")
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
            // 현재 추적 중이 아닌 액티비티 종료
            if currentActivity?.id != activity.id {
                Task {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
        }
    }
}
