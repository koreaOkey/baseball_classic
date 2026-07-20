import XCTest
@testable import VentingCore

/// 파괴 상태머신 단위 테스트.
///
/// 검증 항목:
/// 1. 초기 상태 (gauge = 0, stage = idle)
/// 2. 33% 임계값 전환 (idle → cracked) — 탭당 증가량 1/40 기준 14번째 탭
/// 3. 66% 임계값 전환 (cracked → burst)  — 27번째 탭
/// 4. 100% 임계값 전환 (burst → destroyed) — 40번째 탭
/// 5. 게이지 1.0 상한 초과 불가
/// 6. 완파 상태에서 탭 무시
/// 7. 첫 완파 기록 — 완파 전에는 미기록, 완파 시 기록
/// 8. reset() 후 gauge/stage 초기화, UserDefaults 기록 유지
/// 9. 게임ID별 기록 독립성
/// 10. 재도전(reset + 재완파) 시 첫 완파 기록 덮어쓰지 않음
final class DestructionStateMachineTests: XCTestCase {

    // MARK: - Setup / Teardown

    private var suiteName: String = ""
    private var defaults: UserDefaults!
    private var machine: DestructionStateMachine!

    private let testGameID = "2026-07-20-HH-LG"

    override func setUp() {
        super.setUp()
        suiteName = "com.basehaptic.test.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!
        machine = DestructionStateMachine(gameID: testGameID, userDefaults: defaults)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        machine = nil
        super.tearDown()
    }

    // MARK: - 헬퍼

    /// n회 탭 수행
    private func tap(_ count: Int) {
        for _ in 0..<count { machine.tap() }
    }

    // MARK: - 초기 상태

    func test_initialState_gaugeZero_stageIdle() {
        XCTAssertEqual(machine.gauge, 0.0, accuracy: 1e-9)
        XCTAssertEqual(machine.stage, .idle)
        XCTAssertFalse(machine.hasRecordedFirstDestruction)
    }

    // MARK: - 임계값 0.33 전환 (균열)

    /// 13번째 탭: gauge = 0.325 < 0.33 → 여전히 idle
    func test_threshold33_13taps_stayIdle() {
        tap(13)
        XCTAssertEqual(machine.stage, .idle,
                       "13탭(gauge=0.325)에서 아직 균열 단계 미진입")
        XCTAssertLessThan(machine.gauge, DestructionConstants.threshold1)
    }

    /// 14번째 탭: gauge = 0.35 ≥ 0.33 → cracked
    func test_threshold33_14taps_transitions_to_cracked() {
        tap(13)
        let changed = machine.tap()
        XCTAssertEqual(machine.stage, .cracked,
                       "14탭(gauge=0.35)에서 균열 단계 진입")
        XCTAssertEqual(changed, .cracked,
                       "tap()이 단계 전환 시 새 단계를 반환해야 함")
        XCTAssertGreaterThanOrEqual(machine.gauge, DestructionConstants.threshold1)
    }

    // MARK: - 임계값 0.66 전환 (터짐)

    /// 26번째 탭: gauge = 0.65 < 0.66 → 여전히 cracked
    func test_threshold66_26taps_stayCracked() {
        tap(26)
        XCTAssertEqual(machine.stage, .cracked,
                       "26탭(gauge=0.65)에서 아직 터짐 단계 미진입")
        XCTAssertLessThan(machine.gauge, DestructionConstants.threshold2)
    }

    /// 27번째 탭: gauge = 0.675 ≥ 0.66 → burst
    func test_threshold66_27taps_transitions_to_burst() {
        tap(26)
        let changed = machine.tap()
        XCTAssertEqual(machine.stage, .burst,
                       "27탭(gauge=0.675)에서 터짐 단계 진입")
        XCTAssertEqual(changed, .burst)
        XCTAssertGreaterThanOrEqual(machine.gauge, DestructionConstants.threshold2)
    }

    // MARK: - 임계값 1.0 전환 (완파)

    /// 39번째 탭: gauge = 0.975 < 1.0 → 여전히 burst
    func test_complete_39taps_stayBurst() {
        tap(39)
        XCTAssertEqual(machine.stage, .burst,
                       "39탭(gauge=0.975)에서 아직 완파 미달")
        XCTAssertLessThan(machine.gauge, DestructionConstants.complete)
    }

    /// 40번째 탭: gauge = 1.0 → destroyed
    func test_complete_40taps_transitions_to_destroyed() {
        tap(39)
        let changed = machine.tap()
        XCTAssertEqual(machine.stage, .destroyed,
                       "40탭(gauge=1.0)에서 완파 단계 진입")
        XCTAssertEqual(changed, .destroyed)
        XCTAssertEqual(machine.gauge, DestructionConstants.complete, accuracy: 1e-9)
    }

    // MARK: - 게이지 상한

    func test_gauge_neverExceedsOne() {
        tap(60)
        XCTAssertEqual(machine.gauge, 1.0, accuracy: 1e-9,
                       "게이지는 1.0을 초과할 수 없다")
    }

    // MARK: - 완파 후 탭 무시

    func test_tapAfterDestroyed_noEffect() {
        tap(40)
        XCTAssertEqual(machine.stage, .destroyed)

        let result = machine.tap()
        XCTAssertNil(result, "완파 후 탭은 nil을 반환해야 함")
        XCTAssertEqual(machine.gauge, 1.0, accuracy: 1e-9)
        XCTAssertEqual(machine.stage, .destroyed)
    }

    // MARK: - 첫 완파 기록

    func test_firstDestruction_notRecordedBeforeComplete() {
        tap(39)
        XCTAssertFalse(machine.hasRecordedFirstDestruction,
                       "39탭 시점에는 첫 완파 기록이 없어야 함")
    }

    func test_firstDestruction_recordedOnComplete() {
        tap(40)
        XCTAssertTrue(machine.hasRecordedFirstDestruction,
                      "40탭(완파) 시점에 첫 완파가 기록돼야 함")
    }

    // MARK: - reset() 후 상태

    func test_reset_clearsGaugeAndStage() {
        tap(20)
        machine.reset()
        XCTAssertEqual(machine.gauge, 0.0, accuracy: 1e-9)
        XCTAssertEqual(machine.stage, .idle)
    }

    func test_reset_preservesFirstDestructionRecord() {
        tap(40)
        XCTAssertTrue(machine.hasRecordedFirstDestruction)

        machine.reset()

        XCTAssertEqual(machine.gauge, 0.0, accuracy: 1e-9,
                       "reset() 후 게이지 초기화")
        XCTAssertEqual(machine.stage, .idle,
                       "reset() 후 단계 초기화")
        XCTAssertTrue(machine.hasRecordedFirstDestruction,
                      "reset() 후에도 UserDefaults 기록은 유지")
    }

    // MARK: - 게임ID별 기록 독립성

    func test_firstDestruction_isolatedByGameID() {
        // machine(testGameID) 완파
        tap(40)
        XCTAssertTrue(machine.hasRecordedFirstDestruction)

        // 다른 게임 ID로 생성한 머신은 기록 없음
        let machine2 = DestructionStateMachine(
            gameID: "2026-07-20-HH-SSG",
            userDefaults: defaults
        )
        XCTAssertFalse(machine2.hasRecordedFirstDestruction,
                       "다른 경기 ID의 머신에는 기록이 없어야 함")
    }

    // MARK: - 재도전 (reset + 재완파)

    func test_retryDestruction_doesNotClobberFirstRecord() {
        // 첫 완파
        tap(40)
        XCTAssertTrue(machine.hasRecordedFirstDestruction)

        // 재도전: reset 후 다시 완파
        machine.reset()
        tap(40)

        XCTAssertTrue(machine.hasRecordedFirstDestruction,
                      "재도전 완파 후에도 첫 완파 기록이 유지돼야 함")
    }

    // MARK: - tap() 반환값

    func test_tap_returnsNil_whenStageNotChanged() {
        // 1번 탭 (gauge = 0.025, 여전히 idle)
        let result = machine.tap()
        XCTAssertNil(result, "단계 미전환 시 nil 반환")
        XCTAssertEqual(machine.stage, .idle)
    }

    func test_tap_returnsNewStage_whenStageChanged() {
        tap(13)
        let result = machine.tap()  // 14번째 탭 → cracked
        XCTAssertEqual(result, .cracked, "단계 전환 시 새 단계 반환")
    }
}
