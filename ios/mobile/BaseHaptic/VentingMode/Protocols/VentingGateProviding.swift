import Foundation

// MARK: - VentingRetryVerdict

/// 재도전 게이트 판정 결과.
enum VentingRetryVerdict {
    /// Rewarded 광고 보상 획득 → 재도전 허용 (지표: retry_ad_complete)
    case adRewarded
    /// 광고 없이 허용 (무료 정책 또는 광고 로드 실패 폴백 — 사용자 귀책 아님)
    case allowedFree
    /// 광고 중도 이탈·중복 요청 → 재도전 거부
    case denied
}

// MARK: - VentingGateProviding

/// 분풀이 재도전 허용 여부를 판정하는 게이트 프로토콜.
///
/// 운영: `RewardedAdGate` — 경기당 첫 완파 무료, 재도전(재파괴)은 광고 시청 필요.
/// 디버그/프리뷰: `AlwaysAllowGate` — 광고 없이 항상 허용.
/// 화면 코드는 이 프로토콜에만 의존한다.
protocol VentingGateProviding {
    /// 광고 없이 바로 재도전 가능한지. `false`면 `requestRetry`(광고 등 선행 UI)가 필요하다.
    func canRetry(gameId: String) async -> Bool

    /// 재도전을 요청한다. 광고 시청 등 선행 작업을 수행하고 판정을 반환한다.
    func requestRetry(gameId: String) async -> VentingRetryVerdict
}
