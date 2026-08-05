import SwiftUI
import WatchKit

// MARK: - WatchVentingRequest

/// 폰 → 워치 분풀이 트리거 요청 (테스트 도구 발송 payload).
struct WatchVentingRequest: Equatable {
    let gameId: String
    let targetLabel: String
    let eventDescription: String
}

// MARK: - WatchVentingCoordinator

/// 분풀이 룸 표시 조율자 (StadiumCheerCoordinator 패턴).
/// WatchConnectivityManager가 "venting_trigger" 메시지를 받으면 dispatch 한다.
final class WatchVentingCoordinator: ObservableObject {
    static let shared = WatchVentingCoordinator()

    @Published var current: WatchVentingRequest?

    func dispatch(_ request: WatchVentingRequest) {
        DispatchQueue.main.async {
            self.current = request
        }
    }

    func dismiss() {
        current = nil
    }
}

// MARK: - WatchVentingStage

/// 파괴 단계 (폰 DestructionStage 축소 포팅). 40히트 완파, 33%/66% 전환.
enum WatchVentingStage {
    case idle, cracked, burst, destroyed

    init(gauge: Double) {
        switch gauge {
        case 1.0...: self = .destroyed
        case 0.66...: self = .burst
        case 0.33...: self = .cracked
        default: self = .idle
        }
    }

    var imageName: String {
        switch self {
        case .idle: return "VentingDollNormal"
        case .cracked: return "VentingDollCrack"
        case .burst: return "VentingDollBurst"
        case .destroyed: return "VentingDollDestroyed"
        }
    }
}

// MARK: - WatchVentingState

/// 워치 분풀이 상태 홀더. 탭·크라운 입력을 히트 수로 통일해 받고,
/// 워치 배터리·모터 특성에 맞춰 경햅틱은 히트 3회당 1회만 재생한다.
final class WatchVentingState: ObservableObject {

    @Published private(set) var gauge: Double = 0
    @Published private(set) var stage: WatchVentingStage = .idle
    @Published private(set) var isDestroyed = false

    private let gameId: String
    private var hitsSinceLightHaptic = 0

    init(gameId: String) {
        self.gameId = gameId
    }

    func recordHits(_ hits: Int) {
        guard !isDestroyed else { return }

        var transition: WatchVentingStage?
        for _ in 0..<max(1, min(hits, 5)) {
            guard gauge < 1.0 else { break }
            let oldStage = stage
            gauge = min(gauge + 1.0 / 40.0, 1.0)
            stage = WatchVentingStage(gauge: gauge)
            if stage != oldStage { transition = stage }
        }

        let device = WKInterfaceDevice.current()
        if let transitioned = transition {
            hitsSinceLightHaptic = 0
            switch transitioned {
            case .idle:
                break
            case .cracked:
                device.play(.directionUp)
            case .burst:
                device.play(.retry)
            case .destroyed:
                device.play(.success)
                isDestroyed = true
                recordFirstDestructionIfNeeded()
            }
        } else {
            hitsSinceLightHaptic += hits
            if hitsSinceLightHaptic >= 3 {
                hitsSinceLightHaptic = 0
                device.play(.click)
            }
        }
    }

    private func recordFirstDestructionIfNeeded() {
        let key = "venting_first_destruction_\(gameId)"
        guard !UserDefaults.standard.bool(forKey: key) else { return }
        UserDefaults.standard.set(true, forKey: key)
    }
}

// MARK: - WatchVentingScreen

/// 워치 분풀이 룸 (lite).
///
/// - 화면 전체가 탭 영역 (인형만 히트박스로 하면 손가락에 가려 답답함).
/// - 디지털 크라운 회전도 히트로 환산 — 드르륵 감아서 게이지를 올릴 수 있다.
/// - 게이지는 화면 테두리 원형 링 (워치 네이티브 문법, 손가락에 안 가림).
/// - 도구 트레이·대상 선택 없음: 폰 테스트 도구가 보낸 대상 1개 고정.
struct WatchVentingScreen: View {

    let request: WatchVentingRequest
    let onClose: () -> Void

    @StateObject private var state: WatchVentingState
    @State private var crownValue: Double = 0
    @State private var lastCrownValue: Double = 0
    @State private var crownAccum: Double = 0
    @State private var dollScale: CGFloat = 1

    /// 크라운 델타 누적 → 1히트 환산 단위
    private static let crownUnitsPerHit: Double = 1.0

    init(request: WatchVentingRequest, onClose: @escaping () -> Void) {
        self.request = request
        self.onClose = onClose
        _state = StateObject(wrappedValue: WatchVentingState(gameId: request.gameId))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // 테두리 게이지 링
            Circle()
                .stroke(Color(red: 0.15, green: 0.15, blue: 0.16), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                .padding(2)
            Circle()
                .trim(from: 0, to: CGFloat(state.gauge))
                .stroke(ringColor, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(2)
                .animation(.easeOut(duration: 0.15), value: state.gauge)

            VStack(spacing: 4) {
                Text(request.targetLabel)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(red: 0.97, green: 0.44, blue: 0.44))

                ZStack {
                    // 채움광 — 인형(92pt)의 56% 크기·가슴 위치로 실루엣 뒤에 거의 숨긴다.
                    // 투명 유니폼 몸판이 흰옷으로 읽히게 하는 목적 (조명 연출 아님)
                    Circle()
                        .fill(
                            RadialGradient(
                                stops: [
                                    .init(color: Color(red: 0.98, green: 0.965, blue: 0.933), location: 0),
                                    .init(color: Color(red: 0.98, green: 0.965, blue: 0.933).opacity(0.95), location: 0.55),
                                    .init(color: Color(red: 0.98, green: 0.965, blue: 0.933).opacity(0), location: 1)
                                ],
                                center: .center,
                                startRadius: 0,
                                endRadius: 26
                            )
                        )
                        .frame(width: 52, height: 52)
                        .offset(y: 10)

                    Image(state.stage.imageName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 92, height: 92)
                        .scaleEffect(dollScale)
                }

                Text(stageText)
                    .font(.system(size: 12))
                    .foregroundColor(state.isDestroyed ? Color(red: 0.97, green: 0.44, blue: 0.44) : .gray)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 16)

            if state.isDestroyed {
                VStack {
                    Spacer()
                    Button(action: onClose) {
                        Text("닫기")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 6)
                            .background(Color(red: 0.6, green: 0.11, blue: 0.11))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .padding(.bottom, 8)
                }
            } else {
                VStack {
                    HStack {
                        Button(action: onClose) {
                            Text("✕")
                                .font(.system(size: 11))
                                .foregroundColor(.gray)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color(red: 0.15, green: 0.15, blue: 0.16))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        Spacer()
                    }
                    .padding(.leading, 8)
                    .padding(.top, 2)
                    Spacer()
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { hit(1) }
        .focusable(true)
        .digitalCrownRotation(
            $crownValue,
            from: -100_000,
            through: 100_000,
            by: 0.5,
            sensitivity: .high,
            isContinuous: false,
            isHapticFeedbackEnabled: false
        )
        .onChange(of: crownValue) { _, newValue in
            guard !state.isDestroyed else { return }
            crownAccum += abs(newValue - lastCrownValue)
            lastCrownValue = newValue
            let hits = Int(crownAccum / Self.crownUnitsPerHit)
            if hits > 0 {
                crownAccum -= Double(hits) * Self.crownUnitsPerHit
                hit(hits)
            }
        }
    }

    private var ringColor: Color {
        switch state.stage {
        case .idle: return Color(red: 0.61, green: 0.64, blue: 0.69)
        case .cracked: return Color(red: 0.98, green: 0.8, blue: 0.08)
        case .burst: return Color(red: 0.98, green: 0.45, blue: 0.09)
        case .destroyed: return Color(red: 0.94, green: 0.27, blue: 0.27)
        }
    }

    private var stageText: String {
        switch state.stage {
        case .idle: return "탭·크라운으로 때리세요!"
        case .cracked: return "💢 균열!"
        case .burst: return "🔥 터지기 직전!"
        case .destroyed: return "💥 완파!"
        }
    }

    private func hit(_ count: Int) {
        guard !state.isDestroyed else { return }
        state.recordHits(count)
        dollScale = 0.9
        withAnimation(.spring(response: 0.2, dampingFraction: 0.5)) {
            dollScale = 1
        }
    }
}
