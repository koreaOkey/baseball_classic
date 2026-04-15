import SwiftUI
import WatchKit

@main
struct BaseHapticWatchApp: App {
    @WKApplicationDelegateAdaptor(WatchAppDelegate.self) var appDelegate
    @StateObject private var connectivity = WatchConnectivityManager.shared

    var body: some Scene {
        WindowGroup {
            WatchContentView()
                .environmentObject(connectivity)
                .environment(\.watchTeamTheme, WatchTeamThemes.theme(for: connectivity.syncedTeamName))
                .onAppear {
                    connectivity.activate()
                }
        }
    }
}

// MARK: - Main Watch View
struct WatchContentView: View {
    @EnvironmentObject private var connectivity: WatchConnectivityManager
    @Environment(\.watchTeamTheme) private var watchTheme
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced
    @Environment(\.scenePhase) private var scenePhase

    @State private var isEventOverlayVisible = false
    @State private var visibleEventType: String?
    @State private var visibleEventTimestamp: Date?
    @State private var isHomeRunVisible = false
    @State private var isHitVisible = false
    @State private var isDoublePlayVisible = false
    @State private var isScoreVisible = false
    @State private var isVictoryVisible = false
    @State private var pendingEventType: String?
    @State private var pendingEventTimestamp: Date?
    // 비디오 재생 중 새 이벤트로 화면이 끊기지 않도록 하는 가드.
    // true 인 동안은 후속 이벤트를 전부 무시. 각 영상 onFinished 에서 false
    // 로 복구하고, 혹시 onFinished 가 누락돼도 watchdog 타이머로 자동 해제.
    @State private var isPlayingVideo = false

    private let eventOverlayDuration: TimeInterval = 2.2
    private let eventFreshness: TimeInterval = 5.0
    private let pendingEventFreshness: TimeInterval = 30.0
    private let homeRunDuration: TimeInterval = 4.0
    // 영상 최대 길이 여유치(홈런 5.05s + 버퍼). watchdog 자동 해제 타이머용.
    private let videoWatchdogDuration: TimeInterval = 6.5

    var body: some View {
        ZStack {
            if isLuminanceReduced, let gameData = connectivity.gameData, gameData.isLive {
                // Always On Display: 경기 중일 때만 화면 유지 (경기 종료 시 시계 화면으로 자연스럽게 전환)
                WatchLiveGameScreen(gameData: gameData)
                    .opacity(0.6)
            } else if isVictoryVisible {
                // 승리 애니메이션 전체 화면
                VictoryTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isVictoryVisible = false
                    }
                    isPlayingVideo = false
                })
                .transition(.opacity)
            } else if isHomeRunVisible {
                // 홈런 비디오 전체 화면
                HomeRunTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isHomeRunVisible = false
                    }
                    isPlayingVideo = false
                })
                .transition(.opacity)
            } else if isHitVisible {
                // 안타 애니메이션 전체 화면
                HitTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isHitVisible = false
                    }
                    isPlayingVideo = false
                })
                .transition(.opacity)
            } else if isDoublePlayVisible {
                // 병살 애니메이션 전체 화면
                DoublePlayTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isDoublePlayVisible = false
                    }
                    isPlayingVideo = false
                })
                .transition(.opacity)
            } else if isScoreVisible {
                // 득점 애니메이션 전체 화면
                ScoreTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isScoreVisible = false
                    }
                    isPlayingVideo = false
                })
                .transition(.opacity)
            } else {
                // Main content
                if let gameData = connectivity.gameData {
                    WatchLiveGameScreen(gameData: gameData)
                } else {
                    WatchNoGameScreen()
                }

                // Event overlay (홈런 제외)
                if let eventType = visibleEventType, isEventOverlayVisible,
                   !["HOMERUN", "HIT", "DOUBLE_PLAY", "VICTORY"].contains(eventType.uppercased()) {
                    WatchEventOverlay(eventType: eventType)
                }

                // Watch sync prompt
                if let prompt = connectivity.watchSyncPrompt {
                    WatchSyncPromptView(
                        prompt: prompt,
                        onAccept: {
                            connectivity.sendSyncResponse(gameId: prompt.gameId, accepted: true)
                            connectivity.clearSyncPrompt()
                            // 워치에서 직접 백엔드에 APNs 토큰 등록 → 폰 앱 없이도 push 수신
                            Task {
                                await WatchTokenRegistrar.register(
                                    gameId: prompt.gameId,
                                    myTeam: connectivity.syncedTeamName
                                )
                            }
                        },
                        onDecline: {
                            connectivity.sendSyncResponse(gameId: prompt.gameId, accepted: false)
                            connectivity.clearSyncPrompt()
                        }
                    )
                }
            }
        }
        .onAppear {
            startGamePollerIfNeeded()
        }
        .onChange(of: connectivity.syncedTeamName) { _, _ in
            startGamePollerIfNeeded()
        }
        .onChange(of: isLuminanceReduced) { _, reduced in
            if reduced {
                // AOD 진입 시 진행 중인 전환 애니메이션 해제
                isVictoryVisible = false
                isHomeRunVisible = false
                isHitVisible = false
                isDoublePlayVisible = false
                isScoreVisible = false
                isEventOverlayVisible = false
                isPlayingVideo = false
            } else {
                // AOD 해제(손목 올림) 시 대기 중인 이벤트 재생
                if let eventType = pendingEventType,
                   let timestamp = pendingEventTimestamp,
                   Date().timeIntervalSince(timestamp) < pendingEventFreshness {
                    pendingEventType = nil
                    pendingEventTimestamp = nil
                    showTransition(for: eventType)
                } else {
                    pendingEventType = nil
                    pendingEventTimestamp = nil
                }
            }
        }
        .onChange(of: connectivity.gameData?.isLive) { oldValue, newValue in
            // 경기가 live → finished로 전환될 때 내 팀 승리 확인
            if oldValue == true, newValue == false,
               let game = connectivity.gameData {
                let myTeam = game.myTeamName.uppercased()
                let isMyTeamHome = myTeam == game.homeTeam.uppercased()
                let isMyTeamAway = myTeam == game.awayTeam.uppercased()
                let myTeamWon = (isMyTeamHome && game.homeScore > game.awayScore) ||
                                (isMyTeamAway && game.awayScore > game.homeScore)
                if myTeamWon {
                    beginVideoPlayback()
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isVictoryVisible = true
                    }
                }
            }
        }
        .onChange(of: connectivity.latestEventTimestamp) { _, timestamp in
            guard let timestamp = timestamp,
                  let eventType = connectivity.latestEventType,
                  Date().timeIntervalSince(timestamp) < eventFreshness else { return }

            let upper = eventType.uppercased()

            // AOD 모드에서는 영상 이벤트를 대기열에 저장 (햅틱은 WatchConnectivityManager에서 직접 실행됨)
            if isLuminanceReduced {
                if ["HOMERUN", "HIT", "DOUBLE_PLAY", "SCORE", "VICTORY"].contains(upper) {
                    pendingEventType = upper
                    pendingEventTimestamp = Date()
                }
                return
            }

            // 비디오 재생 중에는 후속 이벤트 무시 (사용자가 영상을 끝까지 보도록 보호)
            if isPlayingVideo {
                return
            }

            showTransition(for: upper)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                connectivity.clearExpiredGameData()
            }
        }
    }

    private func startGamePollerIfNeeded() {
        let team = connectivity.syncedTeamName
        guard !team.isEmpty, team != "DEFAULT" else { return }
        // 이미 동기화 중이면 폴링 불필요
        guard connectivity.gameData == nil || connectivity.gameData?.isLive != true else { return }

        WatchGamePoller.shared.startPolling(myTeam: team) { [self] gameId, homeTeam, awayTeam in
            // 이미 동기화 중이거나 팝업이 표시 중이면 무시
            guard connectivity.gameData?.isLive != true,
                  connectivity.watchSyncPrompt == nil else { return }

            connectivity.watchSyncPrompt = WatchConnectivityManager.WatchSyncPrompt(
                gameId: gameId,
                homeTeam: homeTeam,
                awayTeam: awayTeam
            )
        }
    }

    private func showTransition(for eventType: String) {
        let game = connectivity.gameData
        let isTestGame = game?.gameId.hasPrefix("test_") == true

        // 팀 코드("DOOSAN")·백엔드 전체명("두산 베어스")·마스코트("베어스") 어느 형식이든
        // 같은 canonical 마스코트로 정규화해서 비교 (displayTeamName은 멱등).
        let myTeam = WatchConnectivityManager.displayTeamName(game?.myTeamName ?? "")
        let homeTeamNorm = WatchConnectivityManager.displayTeamName(game?.homeTeam ?? "")
        let awayTeamNorm = WatchConnectivityManager.displayTeamName(game?.awayTeam ?? "")
        let isMyTeamHome = !myTeam.isEmpty && myTeam == homeTeamNorm
        let isMyTeamAway = !myTeam.isEmpty && myTeam == awayTeamNorm
        let inning = game?.inning ?? ""
        let isMyTeamBatting = isTestGame || (isMyTeamHome && inning.contains("말")) ||
                              (isMyTeamAway && inning.contains("초"))
        let isMyTeamFielding = isTestGame || (!isMyTeamBatting && (isMyTeamHome || isMyTeamAway))

        if eventType == "VICTORY" {
            beginVideoPlayback()
            withAnimation(.easeInOut(duration: 0.3)) {
                isVictoryVisible = true
            }
        } else if eventType == "HOMERUN", isMyTeamBatting {
            beginVideoPlayback()
            withAnimation(.easeInOut(duration: 0.3)) {
                isHomeRunVisible = true
            }
        } else if eventType == "HIT", isMyTeamBatting {
            beginVideoPlayback()
            withAnimation(.easeInOut(duration: 0.3)) {
                isHitVisible = true
            }
        } else if eventType == "DOUBLE_PLAY", isMyTeamFielding {
            beginVideoPlayback()
            withAnimation(.easeInOut(duration: 0.3)) {
                isDoublePlayVisible = true
            }
        } else if eventType == "SCORE", isMyTeamBatting {
            beginVideoPlayback()
            withAnimation(.easeInOut(duration: 0.3)) {
                isScoreVisible = true
            }
        } else {
            // 기타 이벤트 또는 상대팀 이벤트: 오버레이 (비디오 아님)
            visibleEventType = eventType
            visibleEventTimestamp = Date()
            isEventOverlayVisible = true

            let ts = visibleEventTimestamp
            DispatchQueue.main.asyncAfter(deadline: .now() + eventOverlayDuration) {
                if visibleEventTimestamp == ts {
                    isEventOverlayVisible = false
                    visibleEventType = nil
                }
            }
        }
    }

    /// 비디오 재생 시작 훅. isPlayingVideo 가드를 켜고,
    /// onFinished 누락 대비 watchdog 타이머를 설정.
    private func beginVideoPlayback() {
        isPlayingVideo = true
        DispatchQueue.main.asyncAfter(deadline: .now() + videoWatchdogDuration) {
            if isPlayingVideo {
                isPlayingVideo = false
            }
        }
    }
}

// MARK: - Event Overlay
struct WatchEventOverlay: View {
    let eventType: String

    var body: some View {
        if let ui = WatchAppEventColors.overlayStyle(for: eventType) {
            VStack(spacing: WatchAppSpacing.xs) {
                // Reason: 워치 오버레이 아이콘 고정 크기
                Image(systemName: ui.icon)
                    .foregroundColor(ui.color)
                    .font(.system(size: 20))
                // Reason: 워치 오버레이 라벨 고정 사이즈 (spec 허용)
                Text(ui.label)
                    .foregroundColor(.white)
                    .font(.system(size: 12, weight: .medium))
            }
            .padding(.horizontal, WatchAppSpacing.lg)
            // Reason: 워치 오버레이 세로 여백은 md(12)보다 살짝 작게 유지 (10pt)
            .padding(.vertical, 10)
            .background(Color.black.opacity(0.8))
            .cornerRadius(WatchAppRadius.lg)
        }
    }
}

// MARK: - Watch Sync Prompt
struct WatchSyncPromptView: View {
    let prompt: WatchConnectivityManager.WatchSyncPrompt
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.78)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Reason: 워치 프롬프트 고정 사이즈 (spec 허용)
                Text("경기를 관람하겠습니까?")
                    .foregroundColor(.white)
                    .font(.system(size: 13))
                    .multilineTextAlignment(.center)

                let matchup = [prompt.awayTeam, prompt.homeTeam]
                    .filter { !$0.isEmpty }
                    .joined(separator: " vs ")
                if !matchup.isEmpty {
                    // Reason: 워치 프롬프트 서브 고정 사이즈
                    Text(matchup)
                        .foregroundColor(.white.opacity(0.72))
                        .font(.system(size: 12))
                }

                HStack(spacing: WatchAppSpacing.sm) {
                    Button("예") { onAccept() }
                        .buttonStyle(.borderedProminent)
                    Button("아니오") { onDecline() }
                        .buttonStyle(.bordered)
                }
                // Reason: 버튼 상단 간격 미세 조정 (10pt)
                .padding(.top, 10)
            }
            .padding(.horizontal, WatchAppSpacing.md)
            // Reason: 다이얼로그 세로 여백은 md(12)와 lg(16) 사이 (14pt)
            .padding(.vertical, 14)
            .background(WatchColors.gray900)
            .cornerRadius(WatchAppRadius.lg)
            .padding(.horizontal, WatchAppSpacing.md)
        }
    }
}
