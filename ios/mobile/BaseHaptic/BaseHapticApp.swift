import SwiftUI

@main
struct BaseHapticApp: App {
    @StateObject private var connectivityManager = PhoneConnectivityManager.shared
    @AppStorage("selected_team") private var selectedTeamRaw: String = Team.none.rawValue
    @State private var showOnboarding: Bool

    init() {
        let savedTeam = UserDefaults.standard.string(forKey: "selected_team") ?? Team.none.rawValue
        _showOnboarding = State(initialValue: savedTeam == Team.none.rawValue)
    }

    private var selectedTeam: Team {
        Team.fromString(selectedTeamRaw)
    }

    var body: some Scene {
        WindowGroup {
            ContentView(
                selectedTeam: selectedTeam,
                onTeamChanged: { team in
                    selectedTeamRaw = team.rawValue
                    WatchThemeSyncManager.syncThemeToWatch(team: team)
                },
                showOnboarding: showOnboarding,
                onOnboardingComplete: { team in
                    selectedTeamRaw = team.rawValue
                    showOnboarding = false
                    WatchThemeSyncManager.syncThemeToWatch(team: team)
                }
            )
            .environment(\.teamTheme, TeamThemes.theme(for: selectedTeam))
            .preferredColorScheme(.dark)
            .onAppear {
                connectivityManager.activate()
            }
        }
    }
}

// MARK: - Screen
enum Screen: Hashable {
    case home
    case liveGame
    case community
    case store
    case settings
    case watchTest
}

// MARK: - ContentView
struct ContentView: View {
    let selectedTeam: Team
    let onTeamChanged: (Team) -> Void
    let showOnboarding: Bool
    let onOnboardingComplete: (Team) -> Void

    @State private var currentView: Screen = .home
    @State private var navigationHistory: [Screen] = []
    @State private var activeTheme: ThemeData?
    @State private var selectedGameId: String?
    @State private var syncedGameId: String?
    @State private var showWatchSyncDialog = false
    @State private var pendingWatchSyncGameId: String?
    @State private var pendingWatchSyncNavigateToLive = false
    @State private var todayGames: [Game] = []
    @State private var purchasedThemes: [ThemeData] = []
    @State private var observedMyTeamGameStatus: [String: GameStatus] = [:]
    @State private var autoPromptedLiveGames: [String: Bool] = [:]

    @ObservedObject private var connectivity = PhoneConnectivityManager.shared
    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        ZStack {
            if showOnboarding {
                OnboardingScreen(onComplete: onOnboardingComplete)
            } else {
                mainContent
            }
        }
        .onChange(of: connectivity.watchSyncResponse?.gameId) {
            consumePendingWatchSyncResponse()
        }
        .task(id: selectedTeam) {
            await loadTodayGames()
        }
        .task(id: selectedTeam) {
            await pollGames()
        }
        .task(id: syncedGameId) {
            await streamSyncedGame()
        }
    }

    // MARK: - Main Content
    private var mainContent: some View {
        VStack(spacing: 0) {
            // Current screen
            Group {
                switch currentView {
                case .home:
                    HomeScreen(
                        selectedTeam: selectedTeam,
                        todayGames: todayGames,
                        activeTheme: activeTheme,
                        syncedGameId: syncedGameId,
                        onSelectGame: { game in
                            selectedGameId = game.id
                            if game.status == .live && syncedGameId != game.id {
                                requestWatchSyncPrompt(gameId: game.id, navigateToLive: true)
                            } else {
                                navigateTo(.liveGame)
                            }
                        }
                    )
                case .liveGame:
                    LiveGameScreen(
                        activeTheme: activeTheme,
                        gameId: selectedGameId,
                        syncedGameId: syncedGameId,
                        onBack: { navigateBack() }
                    )
                case .watchTest:
                    WatchTestScreen(
                        selectedTeam: selectedTeam,
                        onBack: { navigateBack() }
                    )
                case .settings:
                    SettingsScreen(
                        selectedTeam: selectedTeam,
                        onChangeTeam: onTeamChanged,
                        purchasedThemes: purchasedThemes,
                        activeTheme: activeTheme,
                        onSelectTheme: { activeTheme = $0 },
                        onOpenWatchTest: { navigateTo(.watchTest) }
                    )
                default:
                    Text("준비 중")
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(AppColors.gray950)
                }
            }

            // Bottom navigation
            if currentView != .liveGame && currentView != .watchTest {
                bottomNavigationBar
            }
        }
        .alert("워치 동기화", isPresented: $showWatchSyncDialog) {
            Button("예") {
                syncedGameId = pendingWatchSyncGameId
                closeWatchSyncDialog()
            }
            Button("아니오", role: .cancel) {
                closeWatchSyncDialog()
            }
        } message: {
            Text("경기를 관람하겠습니까?")
        }
    }

    // MARK: - Bottom Navigation
    private var bottomNavigationBar: some View {
        HStack(spacing: 0) {
            BottomNavItem(icon: "house.fill", label: "홈", isSelected: currentView == .home) {
                navigateTo(.home)
            }
            BottomNavItem(icon: "gearshape.fill", label: "설정", isSelected: currentView == .settings) {
                navigateTo(.settings)
            }
        }
        .padding(.vertical, 8)
        .background(AppColors.gray900)
    }

    // MARK: - Navigation
    private func navigateTo(_ target: Screen) {
        guard target != currentView else { return }
        navigationHistory.append(currentView)
        currentView = target
    }

    private func navigateBack() {
        if !navigationHistory.isEmpty {
            currentView = navigationHistory.removeLast()
        } else if currentView != .home {
            currentView = .home
        }
    }

    // MARK: - Watch Sync
    private func requestWatchSyncPrompt(gameId: String, navigateToLive: Bool) {
        guard syncedGameId != gameId else { return }
        pendingWatchSyncGameId = gameId
        pendingWatchSyncNavigateToLive = navigateToLive
        showWatchSyncDialog = true
    }

    private func closeWatchSyncDialog() {
        showWatchSyncDialog = false
        pendingWatchSyncGameId = nil
        if pendingWatchSyncNavigateToLive {
            navigateTo(.liveGame)
        }
        pendingWatchSyncNavigateToLive = false
    }

    private func consumePendingWatchSyncResponse() {
        guard let response = connectivity.consumePendingResponse() else { return }
        if response.accepted {
            selectedGameId = response.gameId
            syncedGameId = response.gameId
        }
    }

    // MARK: - Data Loading
    private func loadTodayGames() async {
        guard selectedTeam != .none else {
            todayGames = []
            return
        }
        if let cached = BackendGamesRepository.shared.peekTodayGamesCache(selectedTeam: selectedTeam), !cached.isEmpty {
            todayGames = cached
        }
        if let fresh = await BackendGamesRepository.shared.fetchTodayGamesCached(selectedTeam: selectedTeam) {
            todayGames = fresh
        }
    }

    private func pollGames() async {
        guard selectedTeam != .none else { return }
        observedMyTeamGameStatus = [:]
        autoPromptedLiveGames = [:]

        while !Task.isCancelled {
            if let fetched = await BackendGamesRepository.shared.fetchTodayGamesCached(selectedTeam: selectedTeam, forceRefresh: true) {
                todayGames = fetched

                // Auto-detect LIVE games for watch sync prompt
                let myTeamGames = fetched.filter { $0.isMyTeam }
                for game in myTeamGames {
                    let previous = observedMyTeamGameStatus[game.id]
                    observedMyTeamGameStatus[game.id] = game.status

                    let becameLive = game.status == .live && previous != .live
                    let alreadyPrompted = autoPromptedLiveGames[game.id] == true
                    let isSynced = syncedGameId == game.id
                    if becameLive && !alreadyPrompted && !isSynced {
                        autoPromptedLiveGames[game.id] = true
                        selectedGameId = game.id
                        WatchGameSyncManager.shared.sendWatchSyncPrompt(
                            gameId: game.id,
                            homeTeam: game.homeTeam,
                            awayTeam: game.awayTeam,
                            myTeam: selectedTeam.rawValue
                        )
                    }
                }

                let pollDelay: UInt64
                if fetched.contains(where: { $0.status == .live }) {
                    pollDelay = 5_000_000_000
                } else if fetched.allSatisfy({ isTerminalStatus($0.status) }) {
                    pollDelay = 60_000_000_000
                } else {
                    pollDelay = 30_000_000_000
                }
                try? await Task.sleep(nanoseconds: pollDelay)
            } else {
                try? await Task.sleep(nanoseconds: 10_000_000_000)
            }
        }
    }

    private func streamSyncedGame() async {
        guard let targetGameId = syncedGameId, !targetGameId.isEmpty else { return }

        var lastWatchSignature = ""
        var lastSentEventCursor: Int64 = 0
        let reconnectDelays: [UInt64] = [1_000_000_000, 2_000_000_000, 5_000_000_000, 10_000_000_000]
        var reconnectAttempt = 0

        while !Task.isCancelled {
            for await message in BackendGamesRepository.shared.streamGame(gameId: targetGameId) {
                switch message {
                case .connected:
                    reconnectAttempt = 0
                case .closed:
                    break
                case .error:
                    break
                case .state(let state):
                    let signature = "\(state.gameId)|\(state.status)|\(state.inning)|\(state.homeScore)|\(state.awayScore)|\(state.ball)|\(state.strike)|\(state.out)"
                    if signature != lastWatchSignature {
                        WatchGameSyncManager.shared.sendGameData(
                            gameId: state.gameId,
                            homeTeam: state.homeTeam,
                            awayTeam: state.awayTeam,
                            homeScore: state.homeScore,
                            awayScore: state.awayScore,
                            status: state.status.rawValue,
                            inning: state.inning,
                            ball: state.ball,
                            strike: state.strike,
                            out: state.out,
                            baseFirst: state.baseFirst,
                            baseSecond: state.baseSecond,
                            baseThird: state.baseThird,
                            pitcher: state.pitcher,
                            batter: state.batter,
                            myTeam: selectedTeam.rawValue
                        )
                        lastWatchSignature = signature
                    }
                case .events(let items):
                    for event in items.sorted(by: { $0.cursor < $1.cursor }) {
                        if event.cursor > lastSentEventCursor {
                            if let mapped = mapToWatchEventType(event.type) {
                                WatchGameSyncManager.shared.sendHapticEvent(eventType: mapped, cursor: event.cursor)
                            }
                            lastSentEventCursor = max(lastSentEventCursor, event.cursor)
                        }
                    }
                case .pong:
                    break
                }
            }

            if Task.isCancelled { break }
            let delay = reconnectDelays[min(reconnectAttempt, reconnectDelays.count - 1)]
            reconnectAttempt = min(reconnectAttempt + 1, reconnectDelays.count - 1)
            try? await Task.sleep(nanoseconds: delay)
        }
    }
}

// MARK: - BottomNavItem
private struct BottomNavItem: View {
    let icon: String
    let label: String
    let isSelected: Bool
    let action: () -> Void

    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                Text(label)
                    .font(.system(size: 12))
            }
            .foregroundColor(isSelected ? teamTheme.navIndicator : Color(hex: 0x71717A))
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Helpers
private func mapToWatchEventType(_ type: String) -> String? {
    switch type.uppercased() {
    case "BALL", "STRIKE", "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY",
         "HIT", "HOMERUN", "SCORE", "WALK", "STEAL":
        return type.uppercased()
    case "SAC_FLY_SCORE":
        return "SCORE"
    case "TAG_UP_ADVANCE":
        return "OUT"
    default:
        return nil
    }
}

private func isTerminalStatus(_ status: GameStatus) -> Bool {
    switch status {
    case .finished, .canceled, .postponed: return true
    case .live, .scheduled: return false
    }
}
