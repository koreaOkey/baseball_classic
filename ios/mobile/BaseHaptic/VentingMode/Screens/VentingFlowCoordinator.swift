import SwiftUI

// MARK: - VentingFlowState

/// 분풀이 모드 화면 흐름 상태.
#if DEBUG
enum VentingFlowState {
    case selection                  // 대상 선택 화면
    case room(VentingRoomViewModel) // 분풀이 룸
    case destroyed(VentingRoomViewModel) // 완파 화면
}

// MARK: - VentingFlowCoordinator

/// 분풀이 모드 화면 흐름 조율자.
///
/// VentingTargetSelectionScreen → VentingRoomScreen → VentingDestroyedScreen
/// 사이의 화면 전환을 관리한다.
struct VentingFlowCoordinator: View {

    let context: VentingGameContext
    let onClose: () -> Void

    // 의존성 — Phase 2에서 교체 가능
    private let gate: any VentingGateProviding = AlwaysAllowGate()

    @State private var flowState: VentingFlowState = .selection

    var body: some View {
        switch flowState {
        case .selection:
            VentingTargetSelectionScreen(
                context: context,
                gate: gate,
                onBack: onClose,
                onSelectTarget: { target in
                    let vm = VentingRoomViewModel(
                        gameContext: context,
                        selectedTarget: target,
                        gate: gate
                    )
                    flowState = .room(vm)
                }
            )

        case .room(let vm):
            VentingRoomScreen(
                viewModel: vm,
                onBack: {
                    flowState = .selection
                },
                onDestroyed: {
                    flowState = .destroyed(vm)
                }
            )

        case .destroyed(let vm):
            VentingDestroyedScreen(
                viewModel: vm,
                onRetry: {
                    vm.reset()
                    flowState = .room(vm)
                },
                onClose: onClose
            )
        }
    }
}

// MARK: - VentingHomeCardContainer

/// HomeScreen에 삽입되는 분풀이 카드 컨테이너.
///
/// - 비동기로 경기 컨텍스트를 로드하고, 오픈 조건을 판정한다.
/// - 조건 미충족 시 아무것도 렌더링하지 않는다.
/// - DEBUG + venting_mode_enabled 이중 게이트 뒤에서만 동작한다.
struct VentingHomeCardContainer: View {

    let myTeamId: String

    private let provider: any RegretCandidateProviding = MockRegretProvider()

    @State private var context: VentingGameContext?
    @State private var showVentingFlow = false

    var body: some View {
        Group {
            if let context {
                VentingHomeCard(
                    context: context,
                    onEnterVenting: { _ in
                        showVentingFlow = true
                    }
                )
                .fullScreenCover(isPresented: $showVentingFlow) {
                    VentingFlowCoordinator(
                        context: context,
                        onClose: { showVentingFlow = false }
                    )
                }
            }
        }
        .task {
            guard VentingFeatureFlag.isEnabled else { return }
            guard let loaded = await provider.fetchVentingContext() else { return }
            guard VentingOpenConditionChecker.isOpen(context: loaded, myTeamId: myTeamId) else { return }
            context = loaded
        }
    }
}
#endif
