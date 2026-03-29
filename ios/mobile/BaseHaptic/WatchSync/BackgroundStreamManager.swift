import UIKit

/// 앱이 백그라운드로 진입해도 WebSocket 스트리밍을 유지하기 위한 매니저
/// beginBackgroundTask를 사용하여 iOS가 허용하는 시간 동안 스트리밍을 유지합니다.
final class BackgroundStreamManager {
    static let shared = BackgroundStreamManager()

    private var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid

    private init() {}

    /// 앱이 백그라운드로 진입할 때 호출
    func beginBackgroundStreaming() {
        guard backgroundTaskId == .invalid else { return }

        backgroundTaskId = UIApplication.shared.beginBackgroundTask(withName: "GameStreaming") { [weak self] in
            // iOS가 시간 만료를 알려주면 정리
            self?.endBackgroundStreaming()
        }
    }

    /// 앱이 포그라운드로 돌아올 때 또는 백그라운드 시간 만료 시 호출
    func endBackgroundStreaming() {
        guard backgroundTaskId != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskId)
        backgroundTaskId = .invalid
    }
}
