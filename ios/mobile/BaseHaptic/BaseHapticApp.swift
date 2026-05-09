import SwiftUI
import UIKit
import UserNotifications

@main
struct BaseHapticApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var connectivityManager = PhoneConnectivityManager.shared
    @StateObject private var authManager = AuthManager.shared
    @AppStorage("selected_team") private var selectedTeamRaw: String = Team.none.rawValue
    @State private var showOnboarding: Bool
    @State private var showAppUpdateAlert = false
    @State private var appStoreVersion: String = ""
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let savedTeam = UserDefaults.standard.string(forKey: "selected_team") ?? Team.none.rawValue
        _showOnboarding = State(initialValue: savedTeam == Team.none.rawValue)
        UserDefaults.standard.register(defaults: [
            "live_haptic_enabled": true,
            "ball_strike_haptic_enabled": true,
            "event_video_enabled": true,
            "stadium_cheer_enabled": true,
        ])
    }

    private var selectedTeam: Team {
        Team.fromString(selectedTeamRaw)
    }

    private func checkForAppStoreUpdate() async {
        guard let bundleId = Bundle.main.bundleIdentifier,
              let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
              let url = URL(string: "https://itunes.apple.com/lookup?bundleId=\(bundleId)&country=kr")
        else { return }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let results = json["results"] as? [[String: Any]],
                  let storeVersion = results.first?["version"] as? String
            else { return }

            if storeVersion.compare(currentVersion, options: .numeric) == .orderedDescending {
                await MainActor.run {
                    appStoreVersion = storeVersion
                    showAppUpdateAlert = true
                }
            }
        } catch {
            print("[AppUpdate] version check failed: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView(
                selectedTeam: selectedTeam,
                onTeamChanged: { team in
                    selectedTeamRaw = team.rawValue
                    WatchThemeSyncManager.syncThemeToWatch(team: team)
                    Task { try? await ThemeRepository.shared.saveSelectedTeam(team.rawValue) }
                    Task { await TeamSubscriptionManager.syncIfNeeded() }
                },
                showOnboarding: showOnboarding,
                onOnboardingComplete: { team in
                    selectedTeamRaw = team.rawValue
                    showOnboarding = false
                    WatchThemeSyncManager.syncThemeToWatch(team: team)
                    Task { try? await ThemeRepository.shared.saveSelectedTeam(team.rawValue) }
                    Task { await TeamSubscriptionManager.syncIfNeeded() }
                },
                authManager: authManager
            )
            .environment(\.teamTheme, TeamThemes.theme(for: selectedTeam))
            .preferredColorScheme(.dark)
            .onAppear {
                connectivityManager.activate()
                // 워치에 사용자 설정 초기 동기화 (영상 알림 토글 등)
                WatchThemeSyncManager.syncEventVideoEnabledToWatch(
                    enabled: UserDefaults.standard.bool(forKey: "event_video_enabled")
                )
                WatchThemeSyncManager.syncLiveHapticEnabledToWatch(
                    enabled: UserDefaults.standard.bool(forKey: "live_haptic_enabled")
                )
                Task { await TeamSubscriptionManager.syncIfNeeded() }
                // TODO: Live Activity 배포 시 활성화
                // LiveActivityManager.shared.cleanupStaleActivities()
            }
            .task {
                await authManager.initialize()
            }
            .task {
                await checkForAppStoreUpdate()
            }
            .alert("업데이트 안내", isPresented: $showAppUpdateAlert) {
                Button("업데이트") {
                    if let url = URL(string: "itms-apps://itunes.apple.com/app/id6761336752") {
                        UIApplication.shared.open(url)
                    }
                }
                Button("나중에", role: .cancel) {}
            } message: {
                Text("새 버전(\(appStoreVersion))이 출시되었습니다. 업데이트하시겠습니까?")
            }
            .onOpenURL { url in
                authManager.handleOpenURL(url)
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .background:
                BackgroundStreamManager.shared.beginBackgroundStreaming()
            case .active:
                BackgroundStreamManager.shared.endBackgroundStreaming()
            default:
                break
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
    case myTeam
}

private let SHOW_MY_TEAM_TAB = false

// MARK: - ContentView
struct ContentView: View {
    let selectedTeam: Team
    let onTeamChanged: (Team) -> Void
    let showOnboarding: Bool
    let onOnboardingComplete: (Team) -> Void
    @ObservedObject var authManager: AuthManager

    @State private var currentView: Screen = .home
    @State private var navigationHistory: [Screen] = []
    @State private var activeTheme: ThemeData?
    // 워치 페이스 테마와 무관하게 응원 발화 풀스크린에 적용될 테마.
    @State private var activeCheerTheme: ThemeData? = StadiumCheerThemes.allThemes.first { $0.id == UserDefaults.standard.string(forKey: "active_cheer_theme_id") }
    @State private var selectedGameId: String?
    @State private var syncedGameId: String?
    @State private var showWatchSyncDialog = false
    @State private var pendingReleaseNote: ReleaseNote?
    @State private var pendingWatchSyncGameId: String?
    @State private var pendingWatchSyncNavigateToLive = false
    @State private var pendingWatchSyncHomeTeam: String = ""
    @State private var pendingWatchSyncAwayTeam: String = ""
    @State private var todayGames: [Game] = []
    @State private var purchasedThemes: [ThemeData] = []
    @State private var unlockedThemeIds: Set<String> = Set(UserDefaults.standard.stringArray(forKey: "unlocked_theme_ids") ?? ["default"])
    @State private var observedMyTeamGameStatus: [String: GameStatus] = [:]
    @State private var autoPromptedLiveGames: [String: Bool] = [:]
    @State private var pendingCheckinStadium: Stadium?
    @State private var dismissedCheckinStadiumCodes: Set<String> = []
    @State private var scheduledCheerSignalIds: Set<String> = []
    @State private var gameStreamTask: Task<Void, Never>?
    @StateObject private var rewardedAdManager = RewardedAdManager.shared

    @ObservedObject private var connectivity = PhoneConnectivityManager.shared
    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        ZStack {
            if showOnboarding {
                OnboardingScreen(
                    onComplete: onOnboardingComplete,
                    authState: authManager.authState,
                    onSignInWithKakao: {
                        Task {
                            do {
                                try await authManager.signInWithKakao()
                                print("[Auth] Kakao sign-in succeeded")
                            } catch {
                                print("[Auth] Kakao sign-in error: \(error)")
                            }
                        }
                    },
                    onSignInWithApple: { authorization in
                        Task {
                            do {
                                try await authManager.signInWithApple(authorization: authorization)
                                print("[Auth] Apple sign-in succeeded")
                            } catch {
                                print("[Auth] Apple sign-in error: \(error)")
                            }
                        }
                    }
                )
            } else {
                mainContent
            }
        }
        .onChange(of: connectivity.watchSyncResponse?.gameId) {
            consumePendingWatchSyncResponse()
        }
        .onReceive(NotificationCenter.default.publisher(for: .openLiveGameRequested)) { notification in
            guard let gameId = notification.userInfo?["game_id"] as? String, !gameId.isEmpty else { return }
            // 온보딩 중이거나 응원팀 미설정 상태면 무시
            guard !showOnboarding else { return }
            let homeTeam = notification.userInfo?["home_team"] as? String ?? ""
            let awayTeam = notification.userInfo?["away_team"] as? String ?? ""
            // 워치 앱 설치된 사용자에게만 다이얼로그 노출. 그 외는 직진.
            if connectivity.watchCompanionStatus == .installed {
                pendingWatchSyncHomeTeam = homeTeam
                pendingWatchSyncAwayTeam = awayTeam
                requestWatchSyncPrompt(gameId: gameId, navigateToLive: true)
            } else {
                selectedGameId = gameId
                if currentView != .liveGame {
                    navigateTo(.liveGame)
                }
            }
        }
        .onAppear {
            evaluateWhatsNewTrigger()
        }
        .overlay {
            if let note = pendingReleaseNote {
                WhatsNewSheet(
                    note: note,
                    onConfirm: { pendingReleaseNote = nil }
                )
                .transition(.opacity)
                .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: pendingReleaseNote?.id)
        .task(id: selectedTeam) {
            await loadTodayGames()
        }
        .task(id: selectedTeam) {
            await pollGames()
        }
        .onChange(of: authManager.authState) { _, newState in
            if case .loggedIn = newState {
                Task { await restoreThemesFromServer() }
            }
        }
        .onChange(of: syncedGameId) { oldId, newId in
            // UserDefaults에 저장 (워치 토큰 등록 시 참조)
            UserDefaults.standard.set(newId ?? "", forKey: "synced_game_id")
            UserDefaults.standard.set(selectedTeam.rawValue, forKey: "synced_my_team")

            // 기존 스트림 취소 후 새 스트림 시작 (별도 Task로 실행하여 백그라운드에서도 유지)
            gameStreamTask?.cancel()

            // APNs 디바이스 토큰 등록/해제
            Task {
                if let oldGameId = oldId, !oldGameId.isEmpty {
                    await PushTokenManager.unregister(gameId: oldGameId)
                    await PushTokenManager.unregisterWatchToken(gameId: oldGameId)
                    // TODO: Live Activity 배포 시 활성화
                    // await PushTokenManager.unregisterLiveActivityToken(gameId: oldGameId)
                    // LiveActivityManager.shared.endCurrentActivity()
                }
                if let newGameId = newId, !newGameId.isEmpty {
                    await PushTokenManager.register(gameId: newGameId, myTeam: selectedTeam.rawValue)
                    await PushTokenManager.registerWatchToken(gameId: newGameId, myTeam: selectedTeam.rawValue)
                    // TODO: Live Activity 배포 시 활성화
                    // if let game = todayGames.first(where: { $0.id == newGameId }), game.status == .live {
                    //     LiveActivityManager.shared.startActivity(...)
                    // }
                }
            }

            guard let gameId = newId, !gameId.isEmpty else {
                gameStreamTask = nil
                return
            }
            gameStreamTask = Task {
                await streamSyncedGame()
            }
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
                        activeTheme: nil,
                        syncedGameId: syncedGameId,
                        checkinStadium: pendingCheckinStadium,
                        onConfirmCheckin: {
                            confirmPendingCheckin()
                        },
                        onDismissCheckin: {
                            dismissPendingCheckin()
                        },
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
                        activeTheme: nil,
                        gameId: selectedGameId,
                        syncedGameId: syncedGameId,
                        onBack: { navigateBack() }
                    )
                case .watchTest:
                    WatchTestScreen(
                        selectedTeam: selectedTeam,
                        onBack: { navigateBack() }
                    )
                case .store:
                    ThemeStoreScreen(
                        activeTheme: activeTheme,
                        activeCheerTheme: activeCheerTheme,
                        unlockedThemeIds: unlockedThemeIds,
                        onApplyTheme: { theme in
                            activeTheme = theme
                            WatchThemeSyncManager.syncStoreThemeToWatch(themeId: theme?.id ?? "default")
                            Task {
                                try? await ThemeRepository.shared.saveActiveTheme(themeId: theme?.id)
                            }
                        },
                        onApplyCheerTheme: { theme in
                            // 응원 테마는 워치 페이스와 무관하게 별도 영속화한다.
                            activeCheerTheme = theme
                            UserDefaults.standard.set(theme?.id, forKey: "active_cheer_theme_id")
                        },
                        onUnlockTheme: { theme in
                            rewardedAdManager.loadAndShowAd {
                                unlockedThemeIds.insert(theme.id)
                                UserDefaults.standard.set(Array(unlockedThemeIds), forKey: "unlocked_theme_ids")
                                if theme.id.hasPrefix("cheer_") {
                                    activeCheerTheme = theme
                                    UserDefaults.standard.set(theme.id, forKey: "active_cheer_theme_id")
                                } else {
                                    activeTheme = theme
                                    WatchThemeSyncManager.syncStoreThemeToWatch(themeId: theme.id)
                                    Task {
                                        try? await ThemeRepository.shared.saveActiveTheme(themeId: theme.id)
                                    }
                                }
                                Task { try? await ThemeRepository.shared.saveUnlock(themeId: theme.id) }
                            }
                        },
                        onPurchaseTheme: { theme in
                            // TODO: StoreKit 인앱 결제 완료 후 호출
                            unlockedThemeIds.insert(theme.id)
                            UserDefaults.standard.set(Array(unlockedThemeIds), forKey: "unlocked_theme_ids")
                            if theme.id.hasPrefix("cheer_") {
                                activeCheerTheme = theme
                                UserDefaults.standard.set(theme.id, forKey: "active_cheer_theme_id")
                            } else {
                                activeTheme = theme
                                WatchThemeSyncManager.syncStoreThemeToWatch(themeId: theme.id)
                                Task {
                                    try? await ThemeRepository.shared.saveActiveTheme(themeId: theme.id)
                                }
                            }
                            Task { try? await ThemeRepository.shared.saveUnlock(themeId: theme.id) }
                        }
                    )
                case .settings:
                    SettingsScreen(
                        selectedTeam: selectedTeam,
                        onChangeTeam: onTeamChanged,
                        activeTheme: activeTheme,
                        onSelectTheme: { activeTheme = $0 },
                        onOpenWatchTest: { navigateTo(.watchTest) },
                        authState: authManager.authState,
                        onSignInWithKakao: {
                            Task {
                                do {
                                    try await authManager.signInWithKakao()
                                    print("[Auth] Kakao sign-in succeeded")
                                } catch {
                                    print("[Auth] Kakao sign-in error: \(error)")
                                }
                            }
                        },
                        onSignInWithApple: { authorization in
                            Task {
                                do {
                                    try await authManager.signInWithApple(authorization: authorization)
                                    print("[Auth] Apple sign-in succeeded")
                                } catch {
                                    print("[Auth] Apple sign-in error: \(error)")
                                }
                            }
                        },
                        onSignOut: {
                            Task { try? await authManager.signOut() }
                        },
                        onDeleteAccount: {
                            do {
                                try await authManager.deleteAccount()
                                return true
                            } catch {
                                print("[Auth] Account deletion error: \(error)")
                                return false
                            }
                        }
                    )
                case .myTeam:
                    MyTeamScreen(selectedTeam: selectedTeam)
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
            if !pendingWatchSyncHomeTeam.isEmpty && !pendingWatchSyncAwayTeam.isEmpty {
                Text("\(pendingWatchSyncAwayTeam) vs \(pendingWatchSyncHomeTeam) 경기를 워치로 관람하시겠습니까?")
            } else {
                Text("경기를 관람하겠습니까?")
            }
        }
        .onAppear {
            activateStadiumCheer()
        }
    }

    // MARK: - Bottom Navigation
    private var bottomNavigationBar: some View {
        HStack(spacing: 0) {
            BottomNavItem(icon: "house.fill", label: "홈", isSelected: currentView == .home) {
                navigateTo(.home)
            }
            BottomNavItem(icon: "bag.fill", label: "상점", isSelected: currentView == .store) {
                navigateTo(.store)
            }
            if SHOW_MY_TEAM_TAB {
                BottomNavItem(icon: "star.fill", label: "내 팀", isSelected: currentView == .myTeam) {
                    navigateTo(.myTeam)
                }
            }
            BottomNavItem(icon: "gearshape.fill", label: "설정", isSelected: currentView == .settings) {
                navigateTo(.settings)
            }
        }
        .padding(.vertical, AppSpacing.sm)
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

    // MARK: - What's New
    private func evaluateWhatsNewTrigger() {
        guard !showOnboarding else { return }
        let defaults = UserDefaults.standard
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        guard !currentVersion.isEmpty else { return }

        let lastSeen = defaults.string(forKey: "last_seen_update_version") ?? ""
        if lastSeen.isEmpty {
            defaults.set(currentVersion, forKey: "last_seen_update_version")
            return
        }
        guard lastSeen != currentVersion else { return }

        defer { defaults.set(currentVersion, forKey: "last_seen_update_version") }

        guard let note = ReleaseNotes.notes(for: currentVersion) else { return }

        pendingReleaseNote = note
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
        pendingWatchSyncHomeTeam = ""
        pendingWatchSyncAwayTeam = ""
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

    // MARK: - Stadium Cheer
    private func activateStadiumCheer() {
        guard UserDefaults.standard.bool(forKey: "stadium_cheer_enabled") else { return }
        StadiumRegionMonitor.shared.onEnterStadium = { stadium in
            Task { @MainActor in
                handleEnteredStadium(stadium)
            }
        }
        StadiumRegionMonitor.shared.start()
        Task {
            await CheerSignalsLoader.shared.refresh()
        }
    }

    private func handleEnteredStadium(_ stadium: Stadium) {
        guard selectedTeam != .none,
              UserDefaults.standard.bool(forKey: "live_haptic_enabled"),
              UserDefaults.standard.bool(forKey: "stadium_cheer_enabled"),
              let game = myTeamGame(at: stadium) else { return }

        if !dismissedCheckinStadiumCodes.contains(stadium.code) {
            pendingCheckinStadium = stadium
            scheduleLocalCheckinNotification(stadium: stadium)
        }

        Task {
            await CheerSignalsLoader.shared.refresh()
            await scheduleCheerTriggerIfAvailable(stadium: stadium, game: game)
        }
    }

    private func confirmPendingCheckin() {
        guard let stadium = pendingCheckinStadium else { return }
        let game = myTeamGame(at: stadium)
        Task {
            do {
                try await CheerSignalsLoader.shared.postCheckin(
                    stadium: stadium,
                    selectedTeam: selectedTeam,
                    game: game
                )
                await MainActor.run {
                    pendingCheckinStadium = nil
                }
            } catch {
                print("[StadiumCheer] check-in failed: \(error)")
            }
        }
    }

    private func dismissPendingCheckin() {
        if let stadium = pendingCheckinStadium {
            dismissedCheckinStadiumCodes.insert(stadium.code)
        }
        pendingCheckinStadium = nil
    }

    private func scheduleCheerTriggerIfAvailable(stadium: Stadium, game: Game) async {
        guard let pair = CheerSignalsLoader.shared.signal(forStadium: stadium.code, team: selectedTeam) else { return }
        let signalId = "\(pair.entry.gameId):\(pair.signal.teamCode):\(pair.entry.fireAtIso)"
        guard !scheduledCheerSignalIds.contains(signalId) else { return }
        await MainActor.run {
            scheduledCheerSignalIds.insert(signalId)
        }

        let fireAtMs = Self.fireAtUnixMs(pair.entry.fireAtIso)
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        WatchThemeSyncManager.sendCheerTrigger(
            teamCode: pair.signal.teamCode,
            stadiumCode: stadium.code,
            cheerText: pair.signal.cheerText,
            primaryColorHex: pair.signal.primaryColorHex,
            hapticPatternId: pair.signal.hapticPatternId,
            fireAtUnixMs: max(fireAtMs, nowMs + 500)
        )
        print("[StadiumCheer] scheduled trigger for \(game.id) \(signalId)")
    }

    private func myTeamGame(at stadium: Stadium) -> Game? {
        todayGames.first { game in
            guard game.isMyTeam else { return false }
            return stadium.matchesHomeTeam(game.homeTeamId)
        }
    }

    private func scheduleLocalCheckinNotification(stadium: Stadium) {
        let content = UNMutableNotificationContent()
        content.title = "경기장 응원 체크인"
        content.body = "\(stadium.name)에서 \(selectedTeam.teamName) 응원을 시작해요."
        content.sound = .default
        content.userInfo = ["stadium_code": stadium.code]

        let request = UNNotificationRequest(
            identifier: "stadium-cheer-\(stadium.code)-\(Int(Date().timeIntervalSince1970))",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    private static func fireAtUnixMs(_ iso: String) -> Int64 {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from: iso) ?? {
            formatter.formatOptions = [.withInternetDateTime]
            return formatter.date(from: iso)
        }() ?? Date()
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    // MARK: - Data Loading
    private func restoreThemesFromServer() async {
        do {
            let serverIds = try await ThemeRepository.shared.fetchUnlockedThemeIds()
            unlockedThemeIds = unlockedThemeIds.union(serverIds)
            UserDefaults.standard.set(Array(unlockedThemeIds), forKey: "unlocked_theme_ids")

            let settings = try await ThemeRepository.shared.fetchUserSettings()

            if settings.activeThemeId != nil || settings.selectedTeam != nil {
                // 서버에 데이터 있음 → 서버 기준으로 복원
                if let activeId = settings.activeThemeId {
                    activeTheme = ThemeData.allThemes.first { $0.id == activeId }
                    WatchThemeSyncManager.syncStoreThemeToWatch(themeId: activeId)
                }
                if let teamRaw = settings.selectedTeam, !teamRaw.isEmpty {
                    let team = Team.fromString(teamRaw)
                    if team != .none {
                        onTeamChanged(team)
                    }
                }
            } else {
                // 서버에 데이터 없음 → 로컬 데이터를 서버에 업로드
                if selectedTeam != .none {
                    try? await ThemeRepository.shared.saveSelectedTeam(selectedTeam.rawValue)
                }
                if let themeId = activeTheme?.id {
                    try? await ThemeRepository.shared.saveActiveTheme(themeId: themeId)
                }
                // 로컬에서 잠금해제된 테마 중 default 제외하고 서버에 업로드
                for themeId in unlockedThemeIds where themeId != "default" {
                    try? await ThemeRepository.shared.saveUnlock(themeId: themeId)
                }
            }
        } catch {
            print("[ThemeRepository] restore failed: \(error)")
        }
    }

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
                            homeTeam: game.homeTeamId.teamName,
                            awayTeam: game.awayTeamId.teamName,
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

        if let initialState = await BackendGamesRepository.shared.fetchGameState(gameId: targetGameId) {
            WatchGameSyncManager.shared.sendGameData(
                gameId: initialState.gameId,
                homeTeam: initialState.homeTeam,
                awayTeam: initialState.awayTeam,
                homeScore: initialState.homeScore,
                awayScore: initialState.awayScore,
                status: initialState.status.rawValue,
                inning: initialState.inning,
                ball: initialState.ball,
                strike: initialState.strike,
                out: initialState.out,
                baseFirst: initialState.baseFirst,
                baseSecond: initialState.baseSecond,
                baseThird: initialState.baseThird,
                pitcher: initialState.pitcher,
                batter: initialState.batter,
                pitcherPitchCount: initialState.pitcherPitchCount,
                myTeam: selectedTeam.rawValue
            )
            lastWatchSignature = "\(initialState.gameId)|\(initialState.status)|\(initialState.inning)|\(initialState.homeScore)|\(initialState.awayScore)|\(initialState.ball)|\(initialState.strike)|\(initialState.out)|\(initialState.pitcherPitchCount ?? -1)"
        }

        while !Task.isCancelled {
            var hasConsumedInitialEventsSnapshot = false
            for await message in BackendGamesRepository.shared.streamGame(gameId: targetGameId) {
                switch message {
                case .connected:
                    reconnectAttempt = 0
                case .closed:
                    break
                case .error:
                    break
                case .state(let state):
                    let signature = "\(state.gameId)|\(state.status)|\(state.inning)|\(state.homeScore)|\(state.awayScore)|\(state.ball)|\(state.strike)|\(state.out)|\(state.pitcherPitchCount ?? -1)"
                    if signature != lastWatchSignature {
                        let wasLive = lastWatchSignature.contains("|live|") || lastWatchSignature.contains("|LIVE|")
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
                            pitcherPitchCount: state.pitcherPitchCount,
                            myTeam: selectedTeam.rawValue
                        )
                        lastWatchSignature = signature

                        if wasLive && state.status == .finished {
                            let isMyTeamHome = selectedTeam != .none && state.homeTeamId == selectedTeam
                            let isMyTeamAway = selectedTeam != .none && state.awayTeamId == selectedTeam
                            let myTeamWon = (isMyTeamHome && state.homeScore > state.awayScore) ||
                                            (isMyTeamAway && state.awayScore > state.homeScore)
                            let liveHapticEnabled = UserDefaults.standard.bool(forKey: "live_haptic_enabled")
                            if myTeamWon && liveHapticEnabled {
                                WatchGameSyncManager.shared.sendHapticEvent(eventType: "VICTORY")
                            }
                        }
                    }
                case .events(let items):
                    let sortedItems = items.sorted(by: { $0.cursor < $1.cursor })
                    if !hasConsumedInitialEventsSnapshot {
                        if let maxCursor = sortedItems.last?.cursor {
                            lastSentEventCursor = max(lastSentEventCursor, maxCursor)
                        }
                        hasConsumedInitialEventsSnapshot = true
                        break
                    }

                    let liveHapticEnabled = UserDefaults.standard.bool(forKey: "live_haptic_enabled")
                    guard liveHapticEnabled else {
                        if let maxCursor = sortedItems.last?.cursor {
                            lastSentEventCursor = max(lastSentEventCursor, maxCursor)
                        }
                        break
                    }
                    let ballStrikeEnabled = UserDefaults.standard.bool(forKey: "ball_strike_haptic_enabled")
                    let newItems = sortedItems.filter { $0.cursor > lastSentEventCursor }
                    let batchTypes = Set(newItems.compactMap { mapToWatchEventType($0.type) })
                    let hasScore = batchTypes.contains("SCORE") || batchTypes.contains("HOMERUN")
                    for event in sortedItems {
                        if event.cursor > lastSentEventCursor {
                            if let mapped = mapToWatchEventType(event.type) {
                                if mapped == "HIT" && hasScore { /* skip HIT when SCORE present */ }
                                else {
                                    let isBallOrStrike = mapped == "BALL" || mapped == "STRIKE"
                                    if !isBallOrStrike || ballStrikeEnabled {
                                        WatchGameSyncManager.shared.sendHapticEvent(eventType: mapped, cursor: event.cursor)
                                    }
                                }
                            }
                            lastSentEventCursor = max(lastSentEventCursor, event.cursor)
                        }
                    }
                case .update(let state, let events):
                    // events 처리 (햅틱 먼저)
                    let liveHapticEnabled = UserDefaults.standard.bool(forKey: "live_haptic_enabled")
                    let ballStrikeEnabled = UserDefaults.standard.bool(forKey: "ball_strike_haptic_enabled")
                    let sortedEvents = events.sorted(by: { $0.cursor < $1.cursor })
                    let newEvents = sortedEvents.filter { $0.cursor > lastSentEventCursor }
                    let batchTypes = Set(newEvents.compactMap { mapToWatchEventType($0.type) })
                    let hasScore = batchTypes.contains("SCORE") || batchTypes.contains("HOMERUN")
                    for event in sortedEvents {
                        if event.cursor > lastSentEventCursor {
                            if liveHapticEnabled, let mapped = mapToWatchEventType(event.type) {
                                if mapped == "HIT" && hasScore { /* skip HIT when SCORE present */ }
                                else {
                                    let isBallOrStrike = mapped == "BALL" || mapped == "STRIKE"
                                    if !isBallOrStrike || ballStrikeEnabled {
                                        WatchGameSyncManager.shared.sendHapticEvent(eventType: mapped, cursor: event.cursor)
                                    }
                                }
                            }
                            lastSentEventCursor = max(lastSentEventCursor, event.cursor)
                        }
                    }
                    // state 반영 (점수 즉시 전송, 이닝 전환 딜레이는 이벤트 없을 때만)
                    if let state {
                        let isInningChange = state.out == 0 && (lastWatchSignature.contains("|0|") == false) && state.status == .live
                        if isInningChange && events.isEmpty {
                            try? await Task.sleep(nanoseconds: 1_500_000_000)
                        }
                        let signature = "\(state.gameId)|\(state.status)|\(state.inning)|\(state.homeScore)|\(state.awayScore)|\(state.ball)|\(state.strike)|\(state.out)|\(state.pitcherPitchCount ?? -1)"
                        if signature != lastWatchSignature {
                            let wasLive = lastWatchSignature.contains("|live|") || lastWatchSignature.contains("|LIVE|")
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
                                pitcherPitchCount: state.pitcherPitchCount,
                                myTeam: selectedTeam.rawValue
                            )
                            lastWatchSignature = signature

                            if wasLive && state.status == .finished {
                                let isMyTeamHome = selectedTeam != .none && state.homeTeamId == selectedTeam
                                let isMyTeamAway = selectedTeam != .none && state.awayTeamId == selectedTeam
                                let myTeamWon = (isMyTeamHome && state.homeScore > state.awayScore) ||
                                                (isMyTeamAway && state.awayScore > state.homeScore)
                                if myTeamWon && liveHapticEnabled {
                                    WatchGameSyncManager.shared.sendHapticEvent(eventType: "VICTORY")
                                }
                            }
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
            VStack(spacing: AppSpacing.xs) {
                Image(systemName: icon)
                    .font(AppFont.h3)
                Text(label)
                    .font(AppFont.micro)
            }
            .foregroundColor(isSelected ? teamTheme.navIndicator : AppColors.gray500)
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Helpers
private func mapToWatchEventType(_ type: String) -> String? {
    switch type.uppercased() {
    case "BALL", "STRIKE", "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY",
         "HIT", "HOMERUN", "SCORE", "WALK", "STEAL",
         "PITCHER_CHANGE", "MOUND_VISIT":
        return type.uppercased()
    case "SAC_FLY_SCORE":
        return "SCORE"
    case "TAG_UP_ADVANCE":
        return "STEAL"
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
