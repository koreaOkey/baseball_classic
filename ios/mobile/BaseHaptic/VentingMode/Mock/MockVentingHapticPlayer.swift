#if DEBUG
import Foundation

// MARK: - MockVentingHapticPlayer

/// `VentingHapticPlaying` 의 목업 구현체.
///
/// SwiftUI 프리뷰 및 UI 테스트 시 `UIKitVentingHapticPlayer` 대신 주입한다.
/// 실제 UIKit 햅틱을 발생시키지 않고 호출 기록만 남긴다.
///
/// **사용 예시**
/// ```swift
/// let mock = MockVentingHapticPlayer()
/// let vm = VentingRoomViewModel(
///     gameContext: context,
///     selectedTarget: target,
///     gate: AlwaysAllowGate(),
///     hapticPlayer: mock
/// )
/// vm.recordTap()
/// // mock.stageTransitionCalls, mock.tapFeedbackCallCount 으로 검증
/// ```
final class MockVentingHapticPlayer: VentingHapticPlaying {

    // MARK: - Recorded Calls

    /// 호출된 stage 전환 햅틱 순서대로 기록 (cracked / burst / destroyed).
    private(set) var stageTransitionCalls: [DestructionStage] = []

    /// 탭 경햅틱 호출 횟수.
    private(set) var tapFeedbackCallCount: Int = 0

    // MARK: - VentingHapticPlaying

    func playStageTransition(_ stage: DestructionStage) {
        stageTransitionCalls.append(stage)
    }

    func playTapFeedback() {
        tapFeedbackCallCount += 1
    }

    // MARK: - Test Helpers

    /// 기록을 초기화한다 (재도전 테스트에서 사용).
    func resetRecords() {
        stageTransitionCalls = []
        tapFeedbackCallCount = 0
    }
}
#endif
