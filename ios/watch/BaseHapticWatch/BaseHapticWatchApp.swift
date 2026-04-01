import SwiftUI
import WatchKit

@main
struct BaseHapticWatchApp: App {
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
    @State private var isVictoryVisible = false
    @State private var pendingEventType: String?
    @State private var pendingEventTimestamp: Date?

    private let eventOverlayDuration: TimeInterval = 2.2
    private let eventFreshness: TimeInterval = 5.0
    private let pendingEventFreshness: TimeInterval = 30.0
    private let homeRunDuration: TimeInterval = 4.0

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
                })
                .transition(.opacity)
            } else if isHomeRunVisible {
                // 홈런 비디오 전체 화면
                HomeRunTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isHomeRunVisible = false
                    }
                })
                .transition(.opacity)
            } else if isHitVisible {
                // 안타 애니메이션 전체 화면
                HitTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isHitVisible = false
                    }
                })
                .transition(.opacity)
            } else if isDoublePlayVisible {
                // 병살 애니메이션 전체 화면
                DoublePlayTransitionScreen(onFinished: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isDoublePlayVisible = false
                    }
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
                        },
                        onDecline: {
                            connectivity.sendSyncResponse(gameId: prompt.gameId, accepted: false)
                            connectivity.clearSyncPrompt()
                        }
                    )
                }
            }
        }
        .onChange(of: isLuminanceReduced) { _, reduced in
            if reduced {
                // AOD 진입 시 진행 중인 전환 애니메이션 해제
                isVictoryVisible = false
                isHomeRunVisible = false
                isHitVisible = false
                isDoublePlayVisible = false
                isEventOverlayVisible = false
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
                if ["HOMERUN", "HIT", "DOUBLE_PLAY", "VICTORY"].contains(upper) {
                    pendingEventType = upper
                    pendingEventTimestamp = Date()
                }
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

    private func showTransition(for eventType: String) {
        let game = connectivity.gameData
        let myTeam = game?.myTeamName.uppercased() ?? ""
        let isMyTeamHome = myTeam == game?.homeTeam.uppercased()
        let isMyTeamAway = myTeam == game?.awayTeam.uppercased()
        let inning = game?.inning ?? ""
        let isMyTeamBatting = (isMyTeamHome && inning.contains("말")) ||
                              (isMyTeamAway && inning.contains("초"))
        let isMyTeamFielding = !isMyTeamBatting && (isMyTeamHome || isMyTeamAway)

        if eventType == "VICTORY" {
            withAnimation(.easeInOut(duration: 0.3)) {
                isVictoryVisible = true
            }
        } else if eventType == "HOMERUN", isMyTeamBatting {
            withAnimation(.easeInOut(duration: 0.3)) {
                isHomeRunVisible = true
            }
        } else if eventType == "HIT", isMyTeamBatting {
            withAnimation(.easeInOut(duration: 0.3)) {
                isHitVisible = true
            }
        } else if eventType == "DOUBLE_PLAY", isMyTeamFielding {
            withAnimation(.easeInOut(duration: 0.3)) {
                isDoublePlayVisible = true
            }
        } else {
            // 기타 이벤트 또는 상대팀 이벤트: 오버레이
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
}

// MARK: - Event Overlay
struct WatchEventOverlay: View {
    let eventType: String

    private var eventUI: (label: String, icon: String, color: Color)? {
        switch eventType.uppercased() {
        case "HIT": return ("HIT", "bolt.fill", Color(red: 34/255, green: 197/255, blue: 94/255))
        case "WALK": return ("WALK", "bolt.fill", Color(red: 74/255, green: 222/255, blue: 128/255))
        case "STEAL": return ("STEAL", "bolt.fill", Color(red: 6/255, green: 182/255, blue: 212/255))
        case "SCORE": return ("SCORE", "trophy.fill", Color(red: 234/255, green: 179/255, blue: 8/255))
        case "HOMERUN": return ("HOMERUN", "trophy.fill", Color(red: 234/255, green: 179/255, blue: 8/255))
        case "OUT": return ("OUT", "xmark.circle.fill", Color(red: 239/255, green: 68/255, blue: 68/255))
        case "DOUBLE_PLAY": return ("DOUBLE PLAY", "xmark.circle.fill", Color(red: 249/255, green: 115/255, blue: 22/255))
        case "TRIPLE_PLAY": return ("TRIPLE PLAY", "xmark.circle.fill", Color(red: 220/255, green: 38/255, blue: 38/255))
        default: return nil
        }
    }

    var body: some View {
        if let ui = eventUI {
            VStack(spacing: 4) {
                Image(systemName: ui.icon)
                    .foregroundColor(ui.color)
                    .font(.system(size: 20))
                Text(ui.label)
                    .foregroundColor(.white)
                    .font(.system(size: 12, weight: .medium))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.black.opacity(0.8))
            .cornerRadius(14)
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
                Text("경기를 관람하겠습니까?")
                    .foregroundColor(.white)
                    .font(.system(size: 13))
                    .multilineTextAlignment(.center)

                let matchup = [prompt.awayTeam, prompt.homeTeam]
                    .filter { !$0.isEmpty }
                    .joined(separator: " vs ")
                if !matchup.isEmpty {
                    Text(matchup)
                        .foregroundColor(.white.opacity(0.72))
                        .font(.system(size: 12))
                }

                HStack(spacing: 8) {
                    Button("예") { onAccept() }
                        .buttonStyle(.borderedProminent)
                    Button("아니오") { onDecline() }
                        .buttonStyle(.bordered)
                }
                .padding(.top, 10)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 14)
            .background(Color(red: 26/255, green: 26/255, blue: 26/255))
            .cornerRadius(14)
            .padding(.horizontal, 12)
        }
    }
}
