import Foundation

// MARK: - RegretCandidateProviding

/// 분풀이 모드에 필요한 경기 컨텍스트·후보 목록을 반환하는 데이터 경계 프로토콜.
///
/// Phase 1: `MockRegretProvider` (번들 JSON) 주입.
/// Phase 2: `BackendVentingProvider` (실제 API)로 교체 — 화면 코드는 무수정.
protocol RegretCandidateProviding {
    /// 현재 분풀이 모드에 사용할 경기 컨텍스트를 반환한다.
    /// 오픈 조건 미충족이거나 데이터 없는 경우 `nil` 반환.
    func fetchVentingContext() async -> VentingGameContext?
}
