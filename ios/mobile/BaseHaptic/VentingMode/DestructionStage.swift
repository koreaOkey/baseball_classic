import Foundation

// MARK: - DestructionStage

/// 분풀이 룸 파괴 단계.
///
/// 게이지 임계값에 따라 자동 결정된다:
/// - idle:      0.0 ≤ gauge < 0.33  (대기)
/// - cracked:   0.33 ≤ gauge < 0.66 (균열)
/// - burst:     0.66 ≤ gauge < 1.0  (터짐)
/// - destroyed: gauge ≥ 1.0         (완파)
enum DestructionStage: String, Equatable, CaseIterable {
    case idle      = "idle"       // 대기
    case cracked   = "cracked"   // 균열
    case burst     = "burst"     // 터짐
    case destroyed = "destroyed" // 완파

    /// 게이지 값에서 파괴 단계를 결정한다.
    init(gauge: Double) {
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

    /// 단계별 펭귄 인형 스프라이트 애셋 이름 (익명 인형 — 선수 정보 없음).
    var dollImageName: String {
        switch self {
        case .idle:      return "VentingDollNormal"
        case .cracked:   return "VentingDollCrack"
        case .burst:     return "VentingDollBurst"
        case .destroyed: return "VentingDollDestroyed"
        }
    }
}
