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
            assertionFailure("[VentingMode] \(bundleFileName).json 번들 파일 미발견")
            return nil
        }

        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            return try decoder.decode(VentingGameContext.self, from: data)
        } catch {
            assertionFailure("[VentingMode] mock_regret_candidates.json 파싱 실패: \(error)")
            return nil
        }
    }
}
#endif
