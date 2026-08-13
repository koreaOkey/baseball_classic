#if DEBUG
import SwiftUI

// MARK: - VentingLiveRequest

/// fullScreenCover(item:) 용 분풀이 플로우 요청.
struct VentingLiveRequest: Identifiable {
    let id = UUID()
    let context: VentingGameContext
    /// 진입 경로 지표 (live / loss_push 등). VentingFlowCoordinator 로 전달된다.
    var entrySource: String = "unknown"
}

// MARK: - VentingLiveEntryOverlay

/// 경기 상세 화면의 분풀이 라이브 진입 오버레이 (Android VentingLiveEntryOverlay 포팅).
///
/// - 왼쪽 하단 플로팅 💢 버튼 — 경기전·라이브·종료, 스코어와 무관하게 언제든지 진입.
/// - 종료 시: 마이팀 패배면 "분풀이 모드로 진입하시겠습니까?" 알림 (경기당 1회).
/// - 마이팀이 참가하지 않은 경기·피처 플래그 OFF에서는 아무것도 렌더링하지 않는다.
struct VentingLiveEntryOverlay: View {

    let gameState: LiveGameState?
    let events: [LiveEvent]
    let boxscore: GameBoxscore?

    @AppStorage("selected_team") private var selectedTeamRaw = Team.none.rawValue
    @State private var flowRequest: VentingLiveRequest?
    @State private var showLossPrompt = false

    private static let lossPromptedKey = "venting_loss_prompted_game_ids"

    private var myTeam: Team { Team(rawValue: selectedTeamRaw) ?? Team.none }

    private var isMyTeamGame: Bool {
        guard let state = gameState else { return false }
        return myTeam != Team.none && (state.homeTeamId == myTeam || state.awayTeamId == myTeam)
    }

    var body: some View {
        Group {
            if VentingFeatureFlag.isEnabled, isMyTeamGame {
                // 경기전·라이브·종료 상태 구분 없이 상시 노출 — 언제든지 진입 가능 (2026-08-05 사용자 결정)
                Button {
                    openFlow()
                } label: {
                    Text("💢")
                        .font(.system(size: 22))
                        .frame(width: 52, height: 52)
                        .background(AppColors.gray950.opacity(0.92))
                        .clipShape(Circle())
                        .overlay(
                            Circle().stroke(AppColors.red500.opacity(0.6), lineWidth: 1.5)
                        )
                        .shadow(radius: 6)
                }
                .buttonStyle(.plain)
                .padding(.leading, 16)
                .padding(.bottom, 28)
            }
        }
        .onChange(of: gameState?.status) { _ in
            maybePromptLoss()
        }
        .onAppear { maybePromptLoss() }
        .alert("오늘은 아쉽게 졌어요 💢", isPresented: $showLossPrompt) {
            Button("분풀이 하러 가기") { openFlow() }
            Button("다음에", role: .cancel) {}
        } message: {
            Text("분풀이 모드로 진입하시겠습니까?")
        }
        .fullScreenCover(item: $flowRequest) { request in
            VentingFlowCoordinator(
                context: request.context,
                onClose: { flowRequest = nil },
                backLabel: "경기",
                entrySource: request.entrySource
            )
        }
    }

    /// 서버 regret-top5(6.1) 우선 → 실패·빈 items 시 로컬 규칙(LiveRegretProvider) 폴백.
    /// entrySource="live".
    private func openFlow() {
        guard let state = gameState else { return }
        let team = myTeam
        let capturedEvents = events
        let capturedBoxscore = boxscore
        Task {
            var context: VentingGameContext?
            if let regret = await BackendGamesRepository.shared.fetchVentingRegretTop5(gameId: state.gameId) {
                context = BackendVentingProvider.buildContext(
                    gameId: state.gameId,
                    state: state,
                    boxscore: capturedBoxscore,
                    myTeam: team,
                    regret: regret
                )
            }
            if context == nil {
                context = LiveRegretProvider.buildContext(
                    state: state,
                    events: capturedEvents,
                    boxscore: capturedBoxscore,
                    myTeam: team
                )
            }
            guard let context else { return }
            await MainActor.run {
                flowRequest = VentingLiveRequest(context: context, entrySource: "live")
            }
        }
    }

    /// 종료 감지 → 마이팀 패배면 경기당 1회 알림 ("다음에"를 눌러도 재노출하지 않는다)
    private func maybePromptLoss() {
        guard VentingFeatureFlag.isEnabled,
              let state = gameState,
              state.status == .finished,
              isMyTeamGame,
              let context = LiveRegretProvider.buildContext(
                  state: state,
                  events: events,
                  boxscore: boxscore,
                  myTeam: myTeam
              ),
              context.gameResult == .loss else { return }

        var prompted = UserDefaults.standard.stringArray(forKey: Self.lossPromptedKey) ?? []
        guard !prompted.contains(state.gameId) else { return }
        prompted.append(state.gameId)
        UserDefaults.standard.set(prompted, forKey: Self.lossPromptedKey)
        showLossPrompt = true
    }
}
#endif
