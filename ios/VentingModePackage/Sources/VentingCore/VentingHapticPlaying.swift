import Foundation

// MARK: - VentingHapticPlaying

/// 분풀이 룸 햅틱 재생 추상 인터페이스.
///
/// destruction_stage 전이(대기→균열→터짐→완파)와 일반 탭에 각각 다른 패턴을 제공한다.
///
/// - Phase 1: `UIKitVentingHapticPlayer` (실기기) / `MockVentingHapticPlayer` (단위 테스트)
/// - Phase 2: 패턴·강도 조정 시 구현체만 교체, 호출부 무수정
///
/// **온톨로지 매핑**
/// | destruction_stage | haptic_pattern         | 메서드                    |
/// |------------------|------------------------|--------------------------|
/// | idle (대기)       | none (탭 경햅틱만)       | `playTapFeedback()`       |
/// | cracked (균열)    | crack                  | `playStageTransition(_:)` |
/// | burst (터짐)      | burst                  | `playStageTransition(_:)` |
/// | destroyed (완파)  | destroyed              | `playStageTransition(_:)` |
public protocol VentingHapticPlaying: AnyObject {

    /// destruction_stage 전이 시 호출된다.
    ///
    /// - 대기→균열: 중강도 임팩트
    /// - 균열→터짐: 강강도 임팩트
    /// - 터짐→완파: 성공 노티피케이션 + 최대 임팩트
    ///
    /// - Parameter stage: 새로 진입한 파괴 단계 (cracked / burst / destroyed 중 하나).
    ///                    idle 단계는 전이 햅틱 대신 `playTapFeedback()`으로 처리.
    func playStageTransition(_ stage: DestructionStage)

    /// 단계 전이 없는 일반 탭 경햅틱.
    ///
    /// `destruction_stage` 가 변경되지 않은 탭마다 호출된다.
    /// 스로틀은 호출 측(ViewModel 등)에서 적용한다.
    func playTapFeedback()
}
