import SwiftUI
import WatchConnectivity

// MARK: - VentingFlowState

/// 분풀이 모드 화면 흐름 상태.
enum VentingFlowState {
    case selection                  // 대상 선택 화면
    case room(VentingRoomViewModel) // 분풀이 룸
    case destroyed(VentingRoomViewModel) // 완파 화면
    case watchHandoff               // 워치로 분풀이 시작 후 안내 화면 (폰 룸 미진입)
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
    /// 진입 경로 지표 (room_enter 등 6.1 metrics 의 entry_source).
    var entrySource: String = "unknown"

    // 재도전 게이트 — 경기당 첫 완파 무료, 재파괴는 Rewarded 광고
    private let gate: any VentingGateProviding = RewardedAdGate()

    @State private var flowState: VentingFlowState = .selection

    /// 워치 연동(페어링 + 워치 앱 설치) 여부. installed일 때만 "워치로 분풀이 시작하기"를 노출한다.
    private var isWatchAvailable: Bool {
        guard WCSession.isSupported() else { return false }
        let session = WCSession.default
        return session.activationState == .activated
            && session.isPaired
            && session.isWatchAppInstalled
    }

    /// 워치 트리거 페이로드 매핑. 선수는 역할 라벨(실명 금지), 감독/직접입력은 라벨 그대로.
    private func watchPayload(for target: VentingTarget) -> (label: String, description: String) {
        (target.roleLabel, target.eventDescription)
    }

    var body: some View {
        switch flowState {
        case .selection:
            VentingTargetSelectionScreen(
                context: context,
                gate: gate,
                onBack: onClose,
                backLabel: backLabel,
                onSelectTarget: { target in
                    // 6.1 지표: 룸 진입 순간 = room_enter
                    VentingEventsReporter.report(
                        eventType: "room_enter",
                        team: context.myTeamId,
                        entrySource: entrySource,
                        gameId: context.gameId
                    )
                    let vm = VentingRoomViewModel(
                        gameContext: context,
                        selectedTarget: target,
                        gate: gate
                    )
                    flowState = .room(vm)
                },
                showWatchOption: isWatchAvailable,
                onSelectTargetOnWatch: { target in
                    // 워치 룸 트리거 발송 (테스트 도구와 동일 경로). 폰은 룸으로 진입하지 않는다.
                    let payload = watchPayload(for: target)
                    WatchGameSyncManager.shared.sendVentingTrigger(
                        gameId: context.gameId,
                        targetLabel: payload.label,
                        eventDescription: payload.description
                    )
                    // 6.1 지표: 워치 진입 = watch_room_enter
                    VentingEventsReporter.report(
                        eventType: "watch_room_enter",
                        team: context.myTeamId,
                        entrySource: entrySource,
                        gameId: context.gameId
                    )
                    flowState = .watchHandoff
                }
            )

        case .room(let vm):
            VentingRoomScreen(
                viewModel: vm,
                entrySource: entrySource,
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
                entrySource: entrySource,
                onRetry: {
                    vm.reset()
                    flowState = .room(vm)
                },
                onClose: onClose
            )

        case .watchHandoff:
            VentingWatchHandoffScreen(onClose: onClose)
        }
    }
}

// MARK: - VentingWatchHandoffScreen

/// 워치로 분풀이를 시작했을 때 표시하는 안내 화면 (결정 ⓑ).
/// 폰은 룸으로 진입하지 않고, 손목의 워치 앱에서 룸이 열렸음을 안내한다.
private struct VentingWatchHandoffScreen: View {
    let onClose: () -> Void

    var body: some View {
        ZStack {
            AppColors.gray950.ignoresSafeArea()
            VStack(spacing: AppSpacing.lg) {
                Spacer()
                Image(systemName: "applewatch.radiowaves.left.and.right")
                    .font(.system(size: 56))
                    .foregroundColor(AppColors.red400)
                Text("워치에서 빠따존에 입장하세요")
                    .font(AppFont.h5Bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                Text("손목의 야구봄 워치 앱에서\n빠따존이 열렸어요. 마음껏 풀어보세요!")
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray400)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.xxl)
                Spacer()
                Button(action: onClose) {
                    Text("닫기")
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, AppSpacing.lg)
                        .background(AppColors.red500)
                        .cornerRadius(AppRadius.md)
                }
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.bottom, AppSpacing.xxxl)
            }
        }
        .navigationBarHidden(true)
    }
}

// MARK: - VentingHomeCardContainer

/// HomeScreen에 삽입되는 분풀이 카드 컨테이너.
///
/// - 오늘 완료된 마이팀 패배 경기의 실제 컨텍스트를 서버 regret-top5(6.1)로 로드하고,
///   실패·빈 items 시 로컬 규칙(LiveRegretProvider)으로 폴백한다 — loss_push 딥링크와 동일 경로.
/// - 오픈 조건 미충족 시 아무것도 렌더링하지 않는다.
/// - venting_mode_enabled 피처 플래그(기본 ON) 뒤에서만 동작한다.
struct VentingHomeCardContainer: View {

    let myTeam: Team
    /// 오늘 완료된 마이팀 패배 경기 (HomeScreen이 실제 경기 목록에서 선별). nil이면 카드 미표시.
    let finishedLossGame: Game?

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
                        onClose: { showVentingFlow = false },
                        entrySource: "home_card"
                    )
                }
            } else {
                // LazyVStack이 이 뷰를 lazy 렌더하고 .task를 실행하도록 최소 자리를 확보한다.
                // (컨텍스트가 없을 때 EmptyView면 높이 0이라 렌더·task가 영영 실행되지 않는다.)
                Color.clear.frame(height: 1)
            }
        }
        .task(id: finishedLossGame?.id) {
            guard VentingFeatureFlag.isEnabled, myTeam != .none,
                  let game = finishedLossGame else {
                context = nil
                return
            }
            let team = myTeam
            let repo = BackendGamesRepository.shared
            async let regretTask = repo.fetchVentingRegretTop5(gameId: game.id)
            async let stateTask = repo.fetchGameState(gameId: game.id)
            async let boxscoreTask = repo.fetchGameBoxscore(gameId: game.id)
            let regret = await regretTask
            let state = await stateTask
            let boxscore = await boxscoreTask

            var loaded: VentingGameContext?
            if let regret {
                loaded = BackendVentingProvider.buildContext(
                    gameId: game.id,
                    state: state,
                    boxscore: boxscore,
                    myTeam: team,
                    regret: regret
                )
            }
            if loaded == nil, let state {
                let events = await repo.fetchGameEvents(gameId: game.id, after: 0, limit: 50)?.items ?? []
                loaded = LiveRegretProvider.buildContext(
                    state: state,
                    events: events,
                    boxscore: boxscore,
                    myTeam: team
                )
            }
            guard let loaded,
                  VentingOpenConditionChecker.isOpen(context: loaded, myTeamId: team.kboTeamId ?? "") else {
                context = nil
                return
            }
            context = loaded
        }
    }
}

#if DEBUG
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
