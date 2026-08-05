#if DEBUG
import SwiftUI
import UIKit

// MARK: - VentingRoomViewModel

/// 분풀이 룸 뷰모델.
///
/// `DestructionStateMachine` 을 SwiftUI 바인딩에 연결하고,
/// `VentingHapticPlaying` 인터페이스를 통해 단계별 햅틱을 구동한다.
///
/// **햅틱 결속 규칙** (AC 4.1)
/// | 이벤트                    | 호출                                    |
/// |--------------------------|----------------------------------------|
/// | 탭 (stage 전이 없음)      | `hapticPlayer.playTapFeedback()`        |
/// | idle → cracked 전이       | `hapticPlayer.playStageTransition(.cracked)` |
/// | cracked → burst 전이      | `hapticPlayer.playStageTransition(.burst)`   |
/// | burst → destroyed 전이    | `hapticPlayer.playStageTransition(.destroyed)` |
/// | 완파 후 탭                | (무시 — guard 로 차단)                  |
///
/// - `hapticPlayer` 는 생성자 주입: 실기기 → `UIKitVentingHapticPlayer`,
///   테스트·프리뷰 → `MockVentingHapticPlayer`.
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

    /// 햅틱 재생 추상 인터페이스.
    /// 실기기: `UIKitVentingHapticPlayer`, 테스트: `MockVentingHapticPlayer`.
    private let hapticPlayer: any VentingHapticPlaying

    // MARK: - Throttle

    /// 탭 경햅틱 최소 간격 (50ms). 스로틀은 tapFeedback 에만 적용.
    private var lastTapHapticTime: TimeInterval = 0

    // MARK: - Init

    /// - Parameters:
    ///   - gameContext: 현재 경기 컨텍스트.
    ///   - selectedTarget: 사용자가 선택한 분풀이 대상.
    ///   - gate: 재도전 허용 게이트 (Phase 1: `AlwaysAllowGate`).
    ///   - hapticPlayer: 햅틱 재생 구현체 (기본값: `UIKitVentingHapticPlayer`).
    init(
        gameContext: VentingGameContext,
        selectedTarget: VentingTarget,
        gate: any VentingGateProviding,
        hapticPlayer: (any VentingHapticPlaying)? = nil
    ) {
        self.gameContext = gameContext
        self.selectedTarget = selectedTarget
        self.gate = gate
        self.machine = DestructionStateMachine(gameID: gameContext.gameId)
        self.hapticPlayer = hapticPlayer ?? UIKitVentingHapticPlayer()
    }

    // MARK: - Actions

    /// 탭 1회 처리.
    ///
    /// 완파 상태에서는 즉시 반환하며, 어떤 햅틱도 발생하지 않는다.
    /// stage 전이 발생 시 `hapticPlayer.playStageTransition(_:)` 을 호출한다.
    /// stage 전이 없는 탭은 `hapticPlayer.playTapFeedback()` 을 호출한다(스로틀 50ms).
    func recordTap() {
        guard !isDestroyed else { return }

        let newStage = machine.tap()
        gauge = machine.gauge
        stage = machine.stage

        if let transitioned = newStage {
            // destruction_stage 전이: 단계별 햅틱 (결속 규칙 AC 4.1)
            hapticPlayer.playStageTransition(transitioned)
            if transitioned == .destroyed {
                isDestroyed = true
            }
        } else {
            // 전이 없는 일반 탭: 경햅틱 (스로틀 50ms)
            let now = Date().timeIntervalSince1970
            if now - lastTapHapticTime >= 0.05 {
                hapticPlayer.playTapFeedback()
                lastTapHapticTime = now
            }
        }

        // 흔들림 애니메이션 트리거
        triggerShake()
    }

    /// 흔들기 버스트 1회 처리.
    ///
    /// 세기에 비례한 `hits`(1~3)만큼 탭 데미지를 주고, 햅틱은 버스트당 1회만
    /// 재생한다(단계 전이 시엔 전이 햅틱이 우선). 스로틀 없음 — 감지기가 디바운스.
    func recordShake(hits: Int) {
        guard !isDestroyed else { return }

        var lastTransition: DestructionStage?
        for _ in 0..<max(1, min(hits, 3)) {
            if let transitioned = machine.tap() {
                lastTransition = transitioned
            }
        }
        gauge = machine.gauge
        stage = machine.stage

        if let transitioned = lastTransition {
            hapticPlayer.playStageTransition(transitioned)
            if transitioned == .destroyed {
                isDestroyed = true
            }
        } else {
            hapticPlayer.playTapFeedback()
        }

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

/// 선택된 분풀이 대상 (선수 후보, 감독, 또는 직접 입력).
enum VentingTarget: Equatable {
    case player(RegretCandidate)
    case manager(VentingManagerOption)
    /// 사용자가 이름을 직접 입력한 대상.
    /// 입력값은 화면 표시 전용이며 어디에도 저장·전송하지 않는다(초상권 리스크 없음).
    case custom(String)

    var roleLabel: String {
        switch self {
        case .player(let c): return c.roleLabel
        case .manager(let m): return m.label
        case .custom(let name): return name
        }
    }

    var eventDescription: String {
        switch self {
        case .player(let c): return c.eventDescription
        case .manager(let m): return m.eventDescription
        case .custom: return "직접 지목한 분풀이 대상"
        }
    }
}
#endif
