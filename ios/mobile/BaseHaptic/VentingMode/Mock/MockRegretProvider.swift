#if DEBUG
import Foundation

// MARK: - MockRegretProvider

/// `RegretCandidateProviding` Phase 1 구현체.
/// 번들에 포함된 `mock_regret_candidates.json`에서 경기 컨텍스트를 반환한다.
///
/// Phase 2에서 `BackendVentingProvider` (실제 API)로 교체 시
/// 이 파일만 교체하면 되며, 화면/뷰모델 코드는 무수정이다.
final class MockRegretProvider: RegretCandidateProviding {

    private let bundleFileName = "mock_regret_candidates"

    func fetchVentingContext() async -> VentingGameContext? {
        guard let url = Bundle.main.url(forResource: bundleFileName, withExtension: "json") else {
            // 번들에 목업 JSON이 없어도 앱을 죽이지 않는다 — 분풀이 카드만 미표시.
            print("[VentingMode] \(bundleFileName).json 번들 파일 미발견 — 분풀이 모드 비활성")
            return nil
        }

        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            let base = try decoder.decode(VentingGameContext.self, from: data)
            // 목업이 특정 팀·특정 날짜에 묶이지 않도록, 현재 설정된 마이팀과
            // 오늘(KST)을 주입한다. 이렇게 하면 어떤 팀 팬이든·어느 날이든
            // 실기기에서 분풀이 모드 오픈 조건이 충족된다.
            return VentingGameContext(
                gameId: base.gameId,
                gameDate: Self.todayKSTString(),
                gameResult: base.gameResult,
                myTeamId: Self.currentMyTeamId() ?? base.myTeamId,
                myScore: base.myScore,
                opponentScore: base.opponentScore,
                candidates: base.candidates,
                managerEventDescription: base.managerEventDescription
            )
        } catch {
            print("[VentingMode] mock_regret_candidates.json 파싱 실패: \(error) — 분풀이 모드 비활성")
            return nil
        }
    }

    /// 현재 설정된 마이팀의 KBO 팀 코드 (예: "HH"). 미설정이면 nil.
    private static func currentMyTeamId() -> String? {
        guard let raw = UserDefaults.standard.string(forKey: "selected_team"), !raw.isEmpty else {
            return nil
        }
        return Team.fromString(raw).kboTeamId
    }

    /// 오늘 날짜(KST, "yyyy-MM-dd").
    private static func todayKSTString() -> String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return fmt.string(from: Date())
    }
}
#endif
