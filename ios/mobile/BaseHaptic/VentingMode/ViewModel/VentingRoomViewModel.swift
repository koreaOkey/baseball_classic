import SwiftUI
import UIKit

// MARK: - VentingRoomViewModel

/// 분풀이 룸 뷰모델.
/// `DestructionStateMachine`을 SwiftUI 바인딩에 연결하고 햅틱을 구동한다.
///
/// - 탭 처리: `recordTap()` → 게이지 증가 → 단계 전환 시 자동 중햅틱
/// - 완파 감지: `stage == .destroyed` 시 `isDestroyed` 가 true
/// - 재도전: `reset()` 으로 게이지를 초기화한다 (UserDefaults 기록은 유지)
@MainActor
final class VentingRoomViewModel: ObservableObject {

    // MARK: - Published State

    @Published private(set) var gauge: Double = 0.0
    @Published private(set) var stage: DestructionStage = .idle
    @Published private(set) var isDestroyed: Bool = false
    @Published private(set) var tapShakeOffset: CGFloat = 0

    // MARK: - Dependencies

    private let machine: DestructionStateMachine
    let gameContext: VentingGameContext
    let selectedTarget: VentingTarget
    let gate: any VentingGateProviding

    // MARK: - Haptics

    private let lightFeedback = UIImpactFeedbackGenerator(style: .light)
    private let mediumFeedback = UIImpactFeedbackGenerator(style: .medium)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    // Throttle: 탭 햅틱은 최소 50ms 간격
    private var lastTapHapticTime: TimeInterval = 0

    // MARK: - Init

    init(
        gameContext: VentingGameContext,
        selectedTarget: VentingTarget,
        gate: any VentingGateProviding
    ) {
        self.gameContext = gameContext
        self.selectedTarget = selectedTarget
        self.gate = gate
        self.machine = DestructionStateMachine(gameID: gameContext.gameId)

        lightFeedback.prepare()
        mediumFeedback.prepare()
        notificationFeedback.prepare()
    }

    // MARK: - Actions

    /// 탭 1회 처리.
    func recordTap() {
        guard !isDestroyed else { return }

        let newStage = machine.tap()
        gauge = machine.gauge
        stage = machine.stage

        // 탭 경햅틱 (스로틀 50ms)
        let now = Date().timeIntervalSince1970
        if now - lastTapHapticTime >= 0.05 {
            lightFeedback.impactOccurred(intensity: 0.6)
            lastTapHapticTime = now
        }

        // 단계 전환
        if let newStage {
            if newStage == .destroyed {
                // 완파: 성공 패턴
                notificationFeedback.notificationOccurred(.success)
                mediumFeedback.impactOccurred(intensity: 1.0)
                isDestroyed = true
            } else {
                // 단계 전환 중햅틱
                mediumFeedback.impactOccurred(intensity: 0.8)
            }
        }

        // 흔들림 애니메이션 트리거
        triggerShake()
    }

    /// 게이지를 0으로 초기화한다. UserDefaults 기록은 유지된다.
    func reset() {
        machine.reset()
        gauge = 0.0
        stage = .idle
        isDestroyed = false
        tapShakeOffset = 0
    }

    /// 재도전 허용 여부 확인
    func canRetry() async -> Bool {
        await gate.canRetry(gameId: gameContext.gameId)
    }

    /// 재도전 요청 (Phase 1: AlwaysAllowGate → 항상 true)
    func requestRetry() async -> Bool {
        await gate.requestRetry(gameId: gameContext.gameId)
    }

    // MARK: - Shake Animation

    private func triggerShake() {
        let distance: CGFloat = stage == .idle ? 4 : (stage == .cracked ? 7 : 10)
        withAnimation(.easeOut(duration: 0.05)) {
            tapShakeOffset = distance * (gauge > 0.5 ? -1 : 1)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            withAnimation(.easeOut(duration: 0.05)) {
                self.tapShakeOffset = -self.tapShakeOffset
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            withAnimation(.easeOut(duration: 0.05)) {
                self.tapShakeOffset = 0
            }
        }
    }
}

// MARK: - VentingTarget

/// 선택된 분풀이 대상 (선수 후보 또는 감독).
enum VentingTarget: Equatable {
    case player(RegretCandidate)
    case manager(VentingManagerOption)

    var roleLabel: String {
        switch self {
        case .player(let c): return c.roleLabel
        case .manager(let m): return m.label
        }
    }

    var eventDescription: String {
        switch self {
        case .player(let c): return c.eventDescription
        case .manager(let m): return m.eventDescription
        }
    }
}
