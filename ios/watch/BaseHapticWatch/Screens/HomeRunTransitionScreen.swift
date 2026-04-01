import SwiftUI
import WatchKit

/// 홈런 시 전체 화면 이미지 시퀀스 애니메이션 (Android의 HomeRunTransitionScreen에 대응)
/// 398x398 JPEG, 20fps, 101프레임 (5.05초)
struct HomeRunTransitionScreen: View {
    let onFinished: () -> Void

    private static let frameCount = 101
    private static let frameInterval: UInt64 = 50_000_000 // 50ms = 20fps
    private static let frameNames: [String] = (1...frameCount).map { String(format: "penguin_hr_frame_%03d", $0) }
    private static let prefetchAhead = 3

    @State private var currentFrame = 0
    @State private var cache: [Int: UIImage] = [:]

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let img = cache[currentFrame] {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
                    .offset(x: -20)
                    .ignoresSafeArea()
            }
        }
        .task {
            // 초기 prefetch
            for i in 0...min(Self.prefetchAhead, Self.frameCount - 1) {
                cache[i] = UIImage(named: Self.frameNames[i])
            }

            for i in 1..<Self.frameCount {
                try? await Task.sleep(nanoseconds: Self.frameInterval)
                guard !Task.isCancelled else { return }
                currentFrame = i

                // 다음 프레임 prefetch
                let next = i + Self.prefetchAhead
                if next < Self.frameCount {
                    cache[next] = UIImage(named: Self.frameNames[next])
                }
                // 사용 끝난 프레임 해제
                cache.removeValue(forKey: i - 1)
            }

            try? await Task.sleep(nanoseconds: Self.frameInterval)
            guard !Task.isCancelled else { return }
            onFinished()
        }
    }
}
