import Foundation

// MARK: - DestructionStage

/// 분풀이 룸 파괴 단계.
///
/// 게이지 임계값에 따라 자동 결정된다:
/// - idle:      0.0 ≤ gauge < 0.33  (대기)
/// - cracked:   0.33 ≤ gauge < 0.66 (균열)
/// - burst:     0.66 ≤ gauge < 1.0  (터짐)
/// - destroyed: gauge ≥ 1.0         (완파)
public enum DestructionStage: String, Equatable, CaseIterable, Sendable {
    case idle      = "idle"       // 대기
    case cracked   = "cracked"   // 균열
    case burst     = "burst"     // 터짐
    case destroyed = "destroyed" // 완파

    /// 게이지 값에서 파괴 단계를 결정한다.
    public init(gauge: Double) {
        if gauge >= DestructionConstants.complete {
            self = .destroyed
        } else if gauge >= DestructionConstants.threshold2 {
            self = .burst
        } else if gauge >= DestructionConstants.threshold1 {
            self = .cracked
        } else {
            self = .idle
        }
    }
}
