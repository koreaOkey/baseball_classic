import XCTest
@testable import VentingCore

// MARK: - MockVentingHapticPlayer (테스트 전용)

/// 단위 테스트에서 `VentingHapticPlaying` 호출을 기록하는 목업.
///
/// `stageTransitionCalls` 에 stage 전이 순서가 누적되고
/// `tapFeedbackCallCount` 에 탭 경햅틱 호출 횟수가 기록된다.
final class MockVentingHapticPlayer: VentingHapticPlaying {
    private(set) var stageTransitionCalls: [DestructionStage] = []
    private(set) var tapFeedbackCallCount: Int = 0

    func playStageTransition(_ stage: DestructionStage) {
        stageTransitionCalls.append(stage)
    }

    func playTapFeedback() {
        tapFeedbackCallCount += 1
    }

    /// 기록 초기화 (재도전 테스트에서 사용)
    func resetRecords() {
        stageTransitionCalls = []
        tapFeedbackCallCount = 0
    }
}

// MARK: - VentingHapticTriggerTests

/// 분풀이 룸 단계별 햅틱 트리거 단위 테스트.
///
/// `DestructionStateMachine.tap()` 결과를 `VentingHapticPlaying` 에 연결하는
/// ViewModel 좌표계를 재현하여, 각 stage 전이 시 올바른 햅틱 메서드가 호출됨을 검증한다.
///
/// **온톨로지 매핑 검증**
/// | destruction_stage | haptic_pattern | 검증 포인트                    |
/// |------------------|----------------|-----------------------------|
/// | idle → cracked   | crack          | stageTransitionCalls == [.cracked] |
/// | cracked → burst  | burst          | stageTransitionCalls == [.cracked, .burst] |
/// | burst → destroyed| destroyed      | stageTransitionCalls.last == .destroyed |
/// | 탭 (전이 없음)    | none           | tapFeedbackCallCount 증가     |
final class VentingHapticTriggerTests: XCTestCase {

    // MARK: - Setup

    private var machine: DestructionStateMachine!
    private var hapticPlayer: MockVentingHapticPlayer!
    private var defaults: UserDefaults!
    private var suiteName: String = ""

    override func setUp() {
        super.setUp()
        suiteName = "com.basehaptic.haptictest.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!
        machine = DestructionStateMachine(gameID: "haptic-test-game", userDefaults: defaults)
        hapticPlayer = MockVentingHapticPlayer()
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        machine = nil
        hapticPlayer = nil
        super.tearDown()
    }

    // MARK: - 헬퍼: ViewModel 햅틱 좌표 로직 재현

    /// VentingRoomViewModel.recordTap() 의 햅틱 좌표 로직을 순수하게 재현한다.
    ///
    /// - 완파 이후 탭은 무시된다 (guard stage != .destroyed).
    /// - stage 전이 시 `playStageTransition`, 아닐 때 `playTapFeedback` 을 호출한다.
    private func simulateTap() {
        // 완파 이후 탭은 ViewModel에서 guard로 막힌다
        guard machine.stage != .destroyed else { return }

        let newStage = machine.tap()

        if let transitioned = newStage {
            // stage 전이 → 단계 전환 햅틱 (탭 경햅틱 대체)
            hapticPlayer.playStageTransition(transitioned)
        } else {
            // 전이 없음 → 일반 탭 경햅틱
            hapticPlayer.playTapFeedback()
        }
    }

    // MARK: - 초기 상태

    func test_initialState_noHapticsTriggered() {
        XCTAssertTrue(hapticPlayer.stageTransitionCalls.isEmpty,
                      "초기화 직후 stage 전환 햅틱 없음")
        XCTAssertEqual(hapticPlayer.tapFeedbackCallCount, 0,
                       "초기화 직후 탭 경햅틱 없음")
    }

    // MARK: - idle 단계 탭 (전이 없음)

    /// idle 구간 탭은 tapFeedback 만 발생시킨다.
    func test_idleTaps_onlyTapFeedback() {
        // 13탭 - 모두 idle 구간
        for _ in 0..<13 { simulateTap() }

        XCTAssertTrue(hapticPlayer.stageTransitionCalls.isEmpty,
                      "idle 구간 탭: stage 전환 햅틱 없음")
        XCTAssertEqual(hapticPlayer.tapFeedbackCallCount, 13,
                       "idle 구간 탭: tapFeedback 13회")
    }

    // MARK: - idle → cracked 전이

    /// 14번째 탭에서 cracked 전이 햅틱이 발생한다.
    func test_threshold33_firesCrackedHaptic() {
        // 13탭 (idle 유지)
        for _ in 0..<13 { simulateTap() }
        let tapsBefore = hapticPlayer.tapFeedbackCallCount
        XCTAssertTrue(hapticPlayer.stageTransitionCalls.isEmpty,
                      "14번째 탭 전: stage 전환 없음")

        // 14번째 탭 → cracked 전이
        simulateTap()

        XCTAssertEqual(hapticPlayer.stageTransitionCalls, [.cracked],
                       "14번째 탭(0.33 임계값): crack 햅틱 호출")
        XCTAssertEqual(hapticPlayer.tapFeedbackCallCount, tapsBefore,
                       "stage 전이 탭: tapFeedback 추가 없음")
    }

    // MARK: - cracked → burst 전이

    /// 27번째 탭에서 burst 전이 햅틱이 발생한다.
    func test_threshold66_firesBurstHaptic() {
        // 26탭 (cracked 구간 포함)
        for _ in 0..<26 { simulateTap() }
        XCTAssertEqual(hapticPlayer.stageTransitionCalls, [.cracked],
                       "26탭 시점: crack 햅틱 1회 발생")

        // 27번째 탭 → burst 전이
        simulateTap()

        XCTAssertEqual(hapticPlayer.stageTransitionCalls, [.cracked, .burst],
                       "27번째 탭(0.66 임계값): burst 햅틱 호출")
    }

    // MARK: - burst → destroyed 전이

    /// 40번째 탭에서 destroyed 전이 햅틱이 발생한다.
    func test_complete_firesDestroyedHaptic() {
        // 39탭 (burst 구간 포함)
        for _ in 0..<39 { simulateTap() }
        XCTAssertFalse(hapticPlayer.stageTransitionCalls.contains(.destroyed),
                       "39탭 시점: 완파 햅틱 미발생")

        // 40번째 탭 → destroyed 전이
        simulateTap()

        XCTAssertTrue(hapticPlayer.stageTransitionCalls.contains(.destroyed),
                      "40번째 탭(1.0): destroyed 햅틱 호출")
        XCTAssertEqual(hapticPlayer.stageTransitionCalls.last, .destroyed,
                       "마지막 stage 전환 햅틱은 destroyed")
    }

    // MARK: - 전체 시나리오: 3단계 전이 순서 검증

    /// 40번 연속 탭 시 cracked → burst → destroyed 순서로 햅틱이 발생한다.
    func test_fullScenario_allThreeTransitionsInOrder() {
        for _ in 0..<40 { simulateTap() }

        XCTAssertEqual(
            hapticPlayer.stageTransitionCalls,
            [.cracked, .burst, .destroyed],
            "40탭 완주: crack→burst→destroyed 순서"
        )
    }

    // MARK: - 완파 이후 탭 무시

    /// 완파 이후 탭에서는 어떤 햅틱도 발생하지 않는다.
    func test_afterDestroyed_noAdditionalHaptics() {
        // 40탭으로 완파
        for _ in 0..<40 { simulateTap() }

        let callsAfterDestroyed = hapticPlayer.stageTransitionCalls.count
        let tapCountAfterDestroyed = hapticPlayer.tapFeedbackCallCount

        // 완파 후 추가 탭
        simulateTap()
        simulateTap()
        simulateTap()

        XCTAssertEqual(hapticPlayer.stageTransitionCalls.count, callsAfterDestroyed,
                       "완파 후 stage 전환 햅틱 추가 없음")
        XCTAssertEqual(hapticPlayer.tapFeedbackCallCount, tapCountAfterDestroyed,
                       "완파 후 tapFeedback 추가 없음")
    }

    // MARK: - 탭 피드백 누적

    /// idle 구간에서의 탭은 매탭마다 tapFeedback 이 호출된다.
    func test_tapFeedback_accumulatesPerTap() {
        for i in 1...5 {
            simulateTap()
            if machine.stage == .idle {
                XCTAssertEqual(hapticPlayer.tapFeedbackCallCount, i,
                               "\(i)번째 idle 탭: tapFeedback \(i)회")
            }
        }
    }

    // MARK: - 재도전 시나리오: reset 후 햅틱 재발생

    /// reset 후 재도전 시 cracked/burst/destroyed 햅틱이 다시 발생한다.
    func test_retry_hapticsFiredAgain() {
        // 1회차: 40탭 완파
        for _ in 0..<40 { simulateTap() }
        XCTAssertEqual(
            hapticPlayer.stageTransitionCalls,
            [.cracked, .burst, .destroyed]
        )

        // 재도전: reset
        machine.reset()
        hapticPlayer.resetRecords()

        // 2회차: 다시 40탭
        for _ in 0..<40 { simulateTap() }

        XCTAssertEqual(
            hapticPlayer.stageTransitionCalls,
            [.cracked, .burst, .destroyed],
            "재도전 시 동일한 순서로 햅틱 재발생"
        )
    }

    // MARK: - idle 전이 햅틱 없음

    /// idle 단계는 `playStageTransition(_:)` 대상이 아니다.
    func test_idleStage_neverTriggersStageTransitionHaptic() {
        // 초기 탭들이 idle을 거쳐 cracked로 가더라도, idle 자체의 stageTransition 호출은 없다
        simulateTap() // gauge = 0.025, stage = idle → tapFeedback only
        XCTAssertFalse(hapticPlayer.stageTransitionCalls.contains(.idle),
                       "idle 단계는 stageTransition 햅틱 대상 아님")
    }
}
