#if DEBUG
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
    var backLabel: String = "홈"

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
                backLabel: backLabel,
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
/// - DEBUG 빌드에서 `--venting-force-show` 런치 인자 사용 시 조건 판정 없이 목업 데이터를 즉시 표시한다.
struct VentingHomeCardContainer: View {

    let myTeamId: String

    private let provider: any RegretCandidateProviding = MockRegretProvider()

    @State private var context: VentingGameContext?
    @State private var showVentingFlow = false

    /// DEBUG 전용: `--venting-force-show` 런치 인자 존재 시 조건 판정 생략
    private var isForceShowEnabled: Bool {
        CommandLine.arguments.contains("--venting-force-show")
    }

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
            } else {
                // LazyVStack이 이 뷰를 lazy 렌더하고 .task를 실행하도록 최소 자리를 확보한다.
                // (컨텍스트가 없을 때 EmptyView면 높이 0이라 렌더·task가 영영 실행되지 않는다.)
                Color.clear.frame(height: 1)
            }
        }
        .task {
            guard VentingFeatureFlag.isEnabled else { return }
            // DEBUG: force-show 인자가 있으면 오픈 조건 검사 없이 목업 컨텍스트 직접 표시
            if isForceShowEnabled {
                context = await MockRegretProvider().fetchVentingContext()
                return
            }
            guard let loaded = await provider.fetchVentingContext() else { return }
            guard VentingOpenConditionChecker.isOpen(context: loaded, myTeamId: myTeamId) else { return }
            context = loaded
        }
    }
}

// MARK: - VentingDebugNavigator

/// DEBUG 딥링크(`com.basehaptic.app://venting-debug?screen=<name>`)로 직접
/// 각 분풀이 화면을 표시하는 독립 뷰.
///
/// 시뮬레이터 E2E 스크린샷 캡처 시 사용:
///   xcrun simctl openurl booted "com.basehaptic.app://venting-debug?screen=selection"
///
/// 지원 screen 값: home | selection | room | destroyed
struct VentingDebugNavigator: View {

    let screenName: String
    let onClose: () -> Void

    private let mockContext = VentingGameContext(
        gameId: "debug-venting-mock",
        gameDate: "2026-07-20",
        gameResult: .loss,
        myTeamId: "HH",
        myScore: 1,
        opponentScore: 7,
        candidates: [
            RegretCandidate(id: "dbg-1", roleLabel: "선발 투수", eventDescription: "2회 피홈런 3실점"),
            RegretCandidate(id: "dbg-2", roleLabel: "3번 타자", eventDescription: "8회 2사 만루 삼진"),
            RegretCandidate(id: "dbg-3", roleLabel: "4번 타자", eventDescription: "6회 병살타"),
            RegretCandidate(id: "dbg-4", roleLabel: "중견수", eventDescription: "5회 플라이 실책"),
            RegretCandidate(id: "dbg-5", roleLabel: "마무리 투수", eventDescription: "9회 동점 홈런 피허용"),
        ],
        managerEventDescription: "번트 실패 후 무리한 강공 지시"
    )

    var body: some View {
        switch screenName {
        case "home":
            homeView
        case "selection":
            VentingTargetSelectionScreen(
                context: mockContext,
                gate: AlwaysAllowGate(),
                onBack: onClose,
                onSelectTarget: { _ in }
            )
        case "room":
            VentingRoomDebugView(context: mockContext, tapCount: 15, onClose: onClose)
        case "destroyed":
            VentingRoomDebugView(context: mockContext, tapCount: 40, onClose: onClose)
        default:
            homeView
        }
    }

    private var homeView: some View {
        ZStack {
            AppColors.gray950.ignoresSafeArea()
            VStack(spacing: AppSpacing.xxl) {
                Spacer()
                VentingHomeCard(context: mockContext, onEnterVenting: { _ in })
                Spacer()
                Button("닫기", action: onClose)
                    .foregroundColor(AppColors.gray400)
                    .padding(.bottom, AppSpacing.xxxl)
            }
        }
    }
}

// MARK: - VentingRoomDebugView

/// 룸·완파 화면 디버그 전용 래퍼.
/// `tapCount`회 탭을 onAppear에 적용하여 원하는 파괴 단계를 시연한다.
private struct VentingRoomDebugView: View {

    let context: VentingGameContext
    let tapCount: Int
    let onClose: () -> Void

    @StateObject private var vm: VentingRoomViewModel

    init(context: VentingGameContext, tapCount: Int, onClose: @escaping () -> Void) {
        self.context = context
        self.tapCount = tapCount
        self.onClose = onClose
        let target = VentingTarget.player(context.candidates[0])
        _vm = StateObject(wrappedValue: VentingRoomViewModel(
            gameContext: context,
            selectedTarget: target,
            gate: AlwaysAllowGate()
        ))
    }

    var body: some View {
        Group {
            if tapCount >= 40 && vm.isDestroyed {
                VentingDestroyedScreen(viewModel: vm, onRetry: {
                    vm.reset()
                }, onClose: onClose)
            } else {
                VentingRoomScreen(viewModel: vm, onBack: onClose, onDestroyed: {})
            }
        }
        .onAppear {
            // 목업 탭 횟수 적용 (스크린샷 데모용)
            for _ in 0..<tapCount {
                vm.recordTap()
            }
        }
    }
}
#endif
#endif
