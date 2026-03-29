import SwiftUI

/// 병살 시 전체 화면 이미지 시퀀스 애니메이션
/// 398x398 JPEG, 20fps, 47프레임 (~2.35초)
struct DoublePlayTransitionScreen: View {
    let onFinished: () -> Void

    private static let frameCount = 47
    private static let frameInterval: UInt64 = 50_000_000 // 50ms = 20fps
    private static let frameNames: [String] = (1...frameCount).map { String(format: "dp_frame_%03d", $0) }

    @State private var currentFrame = 0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Image(Self.frameNames[currentFrame])
                .resizable()
                .scaledToFill()
                .ignoresSafeArea()
        }
        .task {
            for i in 1..<Self.frameCount {
                try? await Task.sleep(nanoseconds: Self.frameInterval)
                guard !Task.isCancelled else { return }
                currentFrame = i
            }

            try? await Task.sleep(nanoseconds: Self.frameInterval)
            guard !Task.isCancelled else { return }
            onFinished()
        }
    }
}
