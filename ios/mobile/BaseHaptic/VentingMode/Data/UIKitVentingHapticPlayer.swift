#if DEBUG
import UIKit

// MARK: - UIKitVentingHapticPlayer

/// `VentingHapticPlaying` 의 UIKit 기반 실 구현체.
///
/// `destruction_stage` 전이별 햅틱 패턴:
/// - cracked  (균열): 중강도 임팩트 (intensity 0.8)
/// - burst    (터짐): 강강도 임팩트 (intensity 0.9)
/// - destroyed(완파): 성공 노티피케이션 + 최대 임팩트 (intensity 1.0)
/// - 일반 탭        : 경강도 임팩트 (intensity 0.6)
///
/// Phase 2에서 패턴·강도 조정 시 이 파일만 수정한다.
final class UIKitVentingHapticPlayer: VentingHapticPlaying {

    // MARK: - Generators

    private let lightFeedback = UIImpactFeedbackGenerator(style: .light)
    private let mediumFeedback = UIImpactFeedbackGenerator(style: .medium)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    // MARK: - Init

    init() {
        prepare()
    }

    // MARK: - Prepare

    /// 제너레이터를 시스템에 사전 등록한다.
    /// `VentingRoomViewModel.init()` 시점에 호출하면 첫 탭 지연을 줄인다.
    func prepare() {
        lightFeedback.prepare()
        mediumFeedback.prepare()
        notificationFeedback.prepare()
    }

    // MARK: - VentingHapticPlaying

    /// destruction_stage 전이 햅틱.
    ///
    /// - idle: 이 메서드를 호출하지 말 것 (`playTapFeedback()` 으로 처리).
    /// - cracked: 중강도 임팩트 → "균열이 생겼다" 느낌.
    /// - burst: 강강도 임팩트 → "곧 터진다" 긴장감.
    /// - destroyed: 성공 노티피케이션 + 최대 임팩트 → "완파" 카타르시스.
    func playStageTransition(_ stage: DestructionStage) {
        switch stage {
        case .idle:
            // idle 전이 햅틱은 없다 (대기 상태는 전이 대상 아님)
            break
        case .cracked:
            mediumFeedback.impactOccurred(intensity: 0.8)
        case .burst:
            mediumFeedback.impactOccurred(intensity: 0.9)
        case .destroyed:
            notificationFeedback.notificationOccurred(.success)
            mediumFeedback.impactOccurred(intensity: 1.0)
        }
    }

    /// 단계 전이 없는 일반 탭 경햅틱.
    func playTapFeedback() {
        lightFeedback.impactOccurred(intensity: 0.6)
    }
}
#endif
