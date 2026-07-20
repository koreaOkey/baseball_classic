import Foundation

// MARK: - DestructionConstants

/// 분풀이 룸 파괴 상태머신의 상수 모음.
/// Phase 2에서 밸런스 조정 시 이 struct 안의 값만 수정한다.
///
/// - threshold1:   균열 단계 진입 임계값 (33%)
/// - threshold2:   터짐 단계 진입 임계값 (66%)
/// - complete:     완파 게이지 값 (100%)
/// - tapIncrement: 탭 1회당 증가량 (1/40)
public struct DestructionConstants {
    public static let threshold1: Double    = 0.33
    public static let threshold2: Double    = 0.66
    public static let complete: Double      = 1.0
    /// 탭 1회당 게이지 증가량. 40탭에 완파.
    public static let tapIncrement: Double  = 1.0 / 40.0

    private init() {}
}
