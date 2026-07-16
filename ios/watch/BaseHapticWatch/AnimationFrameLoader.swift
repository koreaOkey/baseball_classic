import UIKit

/// 애니메이션 프레임 JPEG 로더 — 번들 폴더에서 직접 로드하여 시스템 named-image 캐시를 우회
enum AnimationFrameLoader {
    static func frame(named name: String) -> UIImage? {
        guard let path = Bundle.main.path(forResource: name, ofType: "jpg", inDirectory: "AnimationFrames") else {
            return nil
        }
        return UIImage(contentsOfFile: path)
    }

    static func loadFrame(named name: String) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            frame(named: name)
        }.value
    }
}
