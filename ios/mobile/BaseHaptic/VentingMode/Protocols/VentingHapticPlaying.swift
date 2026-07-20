#if DEBUG
import Foundation

// MARK: - VentingHapticPlaying

/// 분풀이 룸 햅틱 재생 추상 인터페이스.
///
/// `destruction_stage` 전이(대기→균열→터짐→완파)에 결속된 햅틱과
/// 단계 전이 없는 탭 경햅틱을 분리한다.
///
/// - Phase 1: `UIKitVentingHapticPlayer` (실기기) / `MockVentingHapticPlayer` (테스트·프리뷰)
/// - Phase 2: 패턴·강도 조정 시 구현체만 교체, VentingRoomViewModel 무수정
///
/// **온톨로지 `haptic_pattern` 매핑**
/// | destruction_stage | haptic_pattern | 메서드                        |
/// |------------------|----------------|------------------------------|
/// | idle   (대기)    | none           | `playTapFeedback()` 만 호출   |
/// | cracked (균열)   | crack          | `playStageTransition(.cracked)` |
/// | burst  (터짐)    | burst          | `playStageTransition(.burst)`   |
/// | destroyed (완파) | destroyed      | `playStageTransition(.destroyed)` |
protocol VentingHapticPlaying: AnyObject {

    /// destruction_stage 전이 시 호출된다 (cracked / burst / destroyed 중 하나).
    ///
    /// - idle 단계는 전이 햅틱 대상이 아니다. 일반 탭 경햅틱은 `playTapFeedback()` 을 사용한다.
    /// - Parameter stage: 새로 진입한 파괴 단계.
    func playStageTransition(_ stage: DestructionStage)

    /// 단계 전이 없는 일반 탭 경햅틱.
    ///
    /// 스로틀(50ms) 은 호출 측(VentingRoomViewModel)에서 적용한다.
    func playTapFeedback()
}
#endif
