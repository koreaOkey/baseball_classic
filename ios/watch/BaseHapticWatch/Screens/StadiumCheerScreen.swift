import SwiftUI
import WatchKit

struct StadiumCheerPayload: Equatable {
    let teamCode: String
    let cheerText: String
    let primaryColorHex: String
    let hapticPatternId: String
    let fireAtUnixMs: Int64
}

final class StadiumCheerCoordinator: ObservableObject {
    static let shared = StadiumCheerCoordinator()

    @Published var current: StadiumCheerPayload?

    func dispatch(_ payload: StadiumCheerPayload) {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let delayMs = max(0, payload.fireAtUnixMs - nowMs)
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(Int(delayMs))) { [weak self] in
            self?.current = payload
            StadiumCheerHapticPlayer.play(patternId: payload.hapticPatternId)
            DispatchQueue.main.asyncAfter(deadline: .now() + 6.0) { [weak self] in
                if self?.current == payload {
                    self?.current = nil
                }
            }
        }
    }
}

enum StadiumCheerHapticPlayer {
    static func play(patternId _: String) {
        playHomeRunLikePattern()
    }

    private static func playHomeRunLikePattern() {
        let device = WKInterfaceDevice.current()
        device.play(.notification)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            device.play(.notification)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            device.play(.notification)
        }
    }
}

struct StadiumCheerScreen: View {
    let payload: StadiumCheerPayload

    var body: some View {
        let bg = Self.color(fromHex: payload.primaryColorHex).opacity(0.95)
        ZStack {
            bg.ignoresSafeArea()
            VStack(spacing: 12) {
                Text(payload.teamCode)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white.opacity(0.85))
                Text(payload.cheerText)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)
            }
        }
    }

    private static func color(fromHex hex: String) -> Color {
        let sanitized = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        let value = Int(sanitized, radix: 16) ?? 0x3B82F6
        let r = Double((value >> 16) & 0xFF) / 255.0
        let g = Double((value >> 8) & 0xFF) / 255.0
        let b = Double(value & 0xFF) / 255.0
        return Color(red: r, green: g, blue: b)
    }
}
