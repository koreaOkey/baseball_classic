import SwiftUI
import UIKit
import UserNotifications
import CoreLocation
import Darwin

@main
struct BaseHapticApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var connectivityManager = PhoneConnectivityManager.shared
    @StateObject private var authManager = AuthManager.shared
    @AppStorage("selected_team") private var selectedTeamRaw: String = Team.none.rawValue
    @AppStorage("team_display_name_style") private var teamDisplayNameStyleRaw: String = TeamDisplayNameStyle.team.rawValue
    @AppStorage("team_display_name_prompt_seen") private var teamDisplayNamePromptSeen: Bool = false
    @State private var showOnboarding: Bool
    @State private var showAppUpdateAlert = false
    @State private var requiredUpdateTitle = "업데이트가 필요합니다"
    @State private var requiredUpdateMessage = "안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요."
    @State private var requiredUpdateStoreUrl = "itms-apps://itunes.apple.com/app/id6761336752"
    @State private var isRequiredUpdate = true
    @Environment(\.scenePhase) private var scenePhase
    private let isExistingUserAtLaunch: Bool

    init() {
        let savedTeam = UserDefaults.standard.string(forKey: "selected_team") ?? Team.none.rawValue
        let savedTeamValue = Team.fromString(savedTeam)
        isExistingUserAtLaunch = savedTeamValue != .none
        _showOnboarding = State(initialValue: savedTeamValue == .none)
        UserDefaults.standard.register(defaults: [
            "live_haptic_enabled": true,
            "lock_screen_live_score_enabled": true,
            "ball_strike_haptic_enabled": true,
            "event_video_enabled": true,
            "stadium_cheer_enabled": true,
            "event_filter_score_enabled": true,
            "event_filter_homerun_enabled": true,
            "event_filter_hit_enabled": true,
            "event_filter_walk_enabled": false,
            "event_filter_steal_enabled": false,
            "event_filter_out_enabled": false,
            "event_filter_pitch_count_enabled": false,
            "event_filter_pitcher_change_enabled": false,
            "lock_screen_event_filter_score_enabled": true,
            "lock_screen_event_filter_homerun_enabled": true,
            "lock_screen_event_filter_hit_enabled": true,
            "lock_screen_event_filter_walk_enabled": false,
            "lock_screen_event_filter_steal_enabled": false,
            "lock_screen_event_filter_out_enabled": false,
            "lock_screen_event_filter_pitch_count_enabled": false,
            "lock_screen_event_filter_pitcher_change_enabled": false,
            "team_display_name_style": TeamDisplayNameStyle.team.rawValue,
            "team_display_name_prompt_seen": false,
        ])
        UserDefaults.standard.set(true, forKey: "lock_screen_live_score_enabled")
        UserDefaults.standard.set(true, forKey: "live_haptic_enabled")
    }

    private var selectedTeam: Team {
        Team.fromString(selectedTeamRaw)
    }

    private var teamDisplayNameStyle: TeamDisplayNameStyle {
        TeamDisplayNameStyle.fromString(teamDisplayNameStyleRaw)
    }

    private func compareVersions(_ left: String, _ right: String) -> ComparisonResult {
        let leftParts = left.split { ".-_".contains($0) }.compactMap { Int($0) }
        let rightParts = right.split { ".-_".contains($0) }.compactMap { Int($0) }
        let count = max(leftParts.count, rightParts.count)
        for index in 0..<count {
            let l = index < leftParts.count ? leftParts[index] : 0
            let r = index < rightParts.count ? rightParts[index] : 0
            if l < r { return .orderedAscending }
            if l > r { return .orderedDescending }
        }
        return .orderedSame
    }

    private func serverUpdateRequirement(currentVersion: String, config: AppConfig) -> Bool? {
        let minRequiresUpdate = !config.minSupportedVersion.isEmpty &&
            compareVersions(currentVersion, config.minSupportedVersion) == .orderedAscending
        let latestRequiresUpdate = !config.latestVersion.isEmpty &&
            compareVersions(currentVersion, config.latestVersion) == .orderedAscending

        if minRequiresUpdate {
            return true
        }
        if latestRequiresUpdate {
            return config.forceUpdate
        }
        return nil
    }

    private func checkForAppStoreUpdate() async {
        guard let bundleId = Bundle.main.bundleIdentifier,
              let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        else { return }

        if let config = await BackendGamesRepository.shared.fetchAppConfig(platform: "ios", version: currentVersion),
           let updateIsRequired = serverUpdateRequirement(currentVersion: currentVersion, config: config) {
            await MainActor.run {
                requiredUpdateTitle = config.updateTitle.isEmpty ? "업데이트가 필요합니다" : config.updateTitle
                requiredUpdateMessage = config.updateMessage.isEmpty ? "안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요." : config.updateMessage
                requiredUpdateStoreUrl = config.storeUrl.isEmpty ? "itms-apps://itunes.apple.com/app/id6761336752" : config.storeUrl
                isRequiredUpdate = updateIsRequired
                showAppUpdateAlert = true
            }
            return
        }

        guard let url = URL(string: "https://itunes.apple.com/lookup?bundleId=\(bundleId)&country=kr") else { return }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let results = json["results"] as? [[String: Any]],
                  let storeVersion = results.first?["version"] as? String
            else { return }

            if storeVersion.compare(currentVersion, options: .numeric) == .orderedDescending {
                await MainActor.run {
                    requiredUpdateTitle = "업데이트가 필요합니다"
                    requiredUpdateMessage = "새 버전 \(storeVersion)이 출시되었습니다.\n계속 이용하려면 업데이트해 주세요."
                    requiredUpdateStoreUrl = "itms-apps://itunes.apple.com/app/id6761336752"
                    isRequiredUpdate = false
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
                teamDisplayNameStyle: teamDisplayNameStyle,
                onTeamChanged: { team in
                    selectedTeamRaw = team.rawValue
                    WatchThemeSyncManager.syncThemeToWatch(team: team)
                    WatchThemeSyncManager.syncTeamDisplayNameStyleToWatch(style: teamDisplayNameStyle)
                    Task { try? await ThemeRepository.shared.saveSelectedTeam(team.rawValue) }
                    Task { await TeamSubscriptionManager.syncIfNeeded() }
                },
                onTeamDisplayNameStyleChanged: { style in
                    teamDisplayNameStyleRaw = style.rawValue
                    teamDisplayNamePromptSeen = true
                    WatchThemeSyncManager.syncTeamDisplayNameStyleToWatch(style: style)
                    Task { try? await ThemeRepository.shared.saveTeamDisplayNameStyle(style) }
                    Task { await TeamSubscriptionManager.syncIfNeeded() }
                },
                teamDisplayNamePromptSeen: teamDisplayNamePromptSeen,
                onTeamDisplayNamePromptSeen: {
                    teamDisplayNamePromptSeen = true
                },
                showOnboarding: showOnboarding,
                onOnboardingComplete: { team, style in
                    selectedTeamRaw = team.rawValue
                    teamDisplayNameStyleRaw = style.rawValue
                    teamDisplayNamePromptSeen = true
                    showOnboarding = false
                    WatchThemeSyncManager.syncThemeToWatch(team: team)
                    WatchThemeSyncManager.syncTeamDisplayNameStyleToWatch(style: style)
                    Task { try? await ThemeRepository.shared.saveSelectedTeam(team.rawValue) }
                    Task { try? await ThemeRepository.shared.saveTeamDisplayNameStyle(style) }
                    Task { await TeamSubscriptionManager.syncIfNeeded() }
                },
                isExistingUserAtLaunch: isExistingUserAtLaunch,
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
                WatchThemeSyncManager.syncTeamDisplayNameStyleToWatch(style: teamDisplayNameStyle)
                WatchThemeSyncManager.syncEventFiltersToWatch(
                    filters: EventFilterOption.currentValues(channel: .watch)
                )
                Task { await TeamSubscriptionManager.syncIfNeeded() }
                LiveActivityManager.shared.cleanupStaleActivities()
            }
            .task {
                await authManager.initialize()
            }
            .task {
                await checkForAppStoreUpdate()
            }
            .overlay {
                if showAppUpdateAlert {
                    RequiredUpdateOverlay(
                        title: requiredUpdateTitle,
                        message: requiredUpdateMessage,
                        isRequired: isRequiredUpdate,
                        onDismiss: {
                            showAppUpdateAlert = false
                        }
                    ) {
                        if let url = URL(string: requiredUpdateStoreUrl) {
                            UIApplication.shared.open(url)
                        }
                    }
                }
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
                Task { await checkForAppStoreUpdate() }
            default:
                break
            }
        }
    }
}

private struct RequiredUpdateOverlay: View {
    let title: String
    let message: String
    let isRequired: Bool
    let onDismiss: () -> Void
    let onUpdate: () -> Void

    var body: some View {
        ZStack {
            AppColors.gray950
                .ignoresSafeArea()

            VStack(spacing: AppSpacing.xxl) {
                Image(systemName: "arrow.down.circle.fill")
                    .font(.system(size: 56, weight: .semibold))
                    .foregroundColor(AppColors.blue600)

                VStack(spacing: AppSpacing.sm) {
                    Text(title)
                        .font(AppFont.h3Bold)
                        .foregroundColor(.white)

                    Text(message)
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray400)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                }

                Button(action: onUpdate) {
                    Text("업데이트")
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(AppColors.blue600)
                        .cornerRadius(AppRadius.md)
                }

                if !isRequired {
                    Button(action: onDismiss) {
                        Text("나중에")
                            .font(AppFont.bodyMedium)
                            .foregroundColor(AppColors.gray300)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                }
            }
            .padding(AppSpacing.xxl)
            .frame(maxWidth: 360)
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
    let teamDisplayNameStyle: TeamDisplayNameStyle
    let onTeamChanged: (Team) -> Void
    let onTeamDisplayNameStyleChanged: (TeamDisplayNameStyle) -> Void
    let teamDisplayNamePromptSeen: Bool
    let onTeamDisplayNamePromptSeen: () -> Void
    let showOnboarding: Bool
    let onOnboardingComplete: (Team, TeamDisplayNameStyle) -> Void
    let isExistingUserAtLaunch: Bool
    @ObservedObject var authManager: AuthManager

    @State private var currentView: Screen = .home
    @State private var navigationHistory: [Screen] = []
    @State private var activeTheme: ThemeData?
    // 워치 페이스 테마와 무관하게 응원 발화 풀스크린에 적용될 테마.
    @State private var activeCheerTheme: ThemeData? = StadiumCheerThemes.allThemes.first { $0.id == UserDefaults.standard.string(forKey: "active_cheer_theme_id") }
    @State private var selectedGameId: String?
    @State private var syncedGameId: String?
    @State private var activeLiveActivityGameId: String?
    @State private var showWatchSyncDialog = false
    @State private var showLiveActivityDialog = false
    @State private var showGameNotStartedAlert = false
    @State private var pendingReleaseNote: ReleaseNote?
    @State private var showFeatureGuide = false
    @State private var showTeamDisplayNamePrompt = false
    @State private var pendingTeamDisplayNameStyle: TeamDisplayNameStyle = .team
    @State private var pendingWatchSyncGameId: String?
    @State private var pendingWatchSyncNavigateToLive = false
    @State private var pendingWatchSyncHomeTeam: String = ""
    @State private var pendingWatchSyncAwayTeam: String = ""
    @State private var pendingLiveActivityGame: Game?
    @State private var todayGames: [Game] = []
    @State private var purchasedThemes: [ThemeData] = []
    @State private var unlockedThemeIds: Set<String> = Set(UserDefaults.standard.stringArray(forKey: "unlocked_theme_ids") ?? ["default"])
    @State private var observedMyTeamGameStatus: [String: GameStatus] = [:]
    @State private var autoPromptedLiveGames: [String: Bool] = [:]
    @State private var pendingCheckinStadium: Stadium?
    @State private var currentStadiumLocation: CLLocation?
    @State private var dismissedCheckinStadiumCodes: Set<String> = []
    @State private var scheduledCheerSignalIds: Set<String> = []
    @State private var gameStreamTask: Task<Void, Never>?
    @State private var liveActivityStreamTask: Task<Void, Never>?
    @StateObject private var rewardedAdManager = RewardedAdManager.shared

    private var gamesForHome: [Game] {
        return todayGames
    }

    @ObservedObject private var connectivity = PhoneConnectivityManager.shared
    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        ZStack {
            if showOnboarding {
                OnboardingScreen(
                    onComplete: onOnboardingComplete,
                    initialSelectedTeam: selectedTeam,
                    initialDisplayNameStyle: teamDisplayNameStyle,
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
            // 알림 탭은 워치 동기화 팝업을 열지 않고 홈으로만 착지한다.
            selectedGameId = gameId
            if currentView != .home {
                navigateTo(.home)
            }
        }
        .onAppear {
            evaluateWhatsNewTrigger()
            if !showOnboarding && selectedTeam != .none && !teamDisplayNamePromptSeen {
                pendingTeamDisplayNameStyle = teamDisplayNameStyle
                showTeamDisplayNamePrompt = true
            }
        }
        .onChange(of: showOnboarding) { _, showing in
            if !showing {
                evaluateWhatsNewTrigger()
                if selectedTeam != .none && !teamDisplayNamePromptSeen {
                    pendingTeamDisplayNameStyle = teamDisplayNameStyle
                    showTeamDisplayNamePrompt = true
                }
            }
        }
        .overlay {
            if let note = pendingReleaseNote {
                WhatsNewSheet(
                    note: note,
                    onConfirm: {
                        pendingReleaseNote = nil
                        queueFeatureGuideIfNeeded()
                    }
                )
                .transition(.opacity)
                .zIndex(1)
            }
            if showTeamDisplayNamePrompt && selectedTeam != .none {
                TeamDisplayNameStyleDialog(
                    team: selectedTeam,
                    selectedStyle: $pendingTeamDisplayNameStyle,
                    onConfirm: {
                        onTeamDisplayNameStyleChanged(pendingTeamDisplayNameStyle)
                        onTeamDisplayNamePromptSeen()
                        showTeamDisplayNamePrompt = false
                    },
                    onDismiss: {
                        onTeamDisplayNamePromptSeen()
                        showTeamDisplayNamePrompt = false
                    }
                )
                .transition(.opacity)
                .zIndex(2)
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
        .onChange(of: teamDisplayNameStyle) { _, _ in
            WatchThemeSyncManager.syncTeamDisplayNameStyleToWatch(style: teamDisplayNameStyle)
            Task { await TeamSubscriptionManager.syncIfNeeded() }
            if let gameId = syncedGameId, !gameId.isEmpty {
                Task {
                    await PushTokenManager.register(gameId: gameId, myTeam: selectedTeam.rawValue)
                    await PushTokenManager.registerWatchToken(gameId: gameId, myTeam: selectedTeam.rawValue)
                }
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
                    await LiveViewSessionManager.setActive(
                        gameId: oldGameId,
                        surface: .watchos,
                        active: false,
                        myTeam: selectedTeam.rawValue,
                        tokenKey: UserDefaults.standard.string(forKey: "watch_apns_device_token")
                    )
                    if activeLiveActivityGameId != oldGameId {
                        await PushTokenManager.unregisterLiveActivityToken(gameId: oldGameId)
                        LiveActivityManager.shared.endActivity(gameId: oldGameId)
                    }
                }
                if let newGameId = newId, !newGameId.isEmpty {
                    await PushTokenManager.register(gameId: newGameId, myTeam: selectedTeam.rawValue)
                    await PushTokenManager.registerWatchToken(gameId: newGameId, myTeam: selectedTeam.rawValue)
                    await LiveViewSessionManager.setActive(
                        gameId: newGameId,
                        surface: .watchos,
                        active: true,
                        myTeam: selectedTeam.rawValue,
                        tokenKey: UserDefaults.standard.string(forKey: "watch_apns_device_token")
                    )
                }
            }

            guard let gameId = newId, !gameId.isEmpty else {
                gameStreamTask = nil
                refreshLiveActivityStream()
                return
            }
            gameStreamTask = Task {
                await streamSyncedGame()
            }
            refreshLiveActivityStream()
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
                        todayGames: gamesForHome,
                        activeTheme: nil,
                        syncedGameId: syncedGameId,
                        activeLiveActivityGameId: activeLiveActivityGameId,
                        isWatchAppInstalled: connectivity.watchCompanionStatus == .installed,
                        checkinStadium: nil,
                        showUpdateHighlights: showFeatureGuide,
                        onDismissUpdateHighlights: {
                            markFeatureGuideSeen()
                        },
                        onConfirmCheckin: {
                            confirmPendingCheckin()
                        },
                        onDismissCheckin: {
                            dismissPendingCheckin()
                        },
                        onSelectGame: { game in
                            selectedGameId = game.id
                            navigateTo(.liveGame)
                        },
                        onToggleLiveActivity: { game in
                            toggleHomeLiveActivity(for: game)
                        },
                        onToggleWatchSync: { game in
                            selectedGameId = game.id
                            if syncedGameId == game.id {
                                syncedGameId = nil
                                return
                            }
                            guard game.status == .live else {
                                showGameNotStartedAlert = true
                                return
                            }
                            guard connectivity.watchCompanionStatus == .installed else { return }
                            pendingWatchSyncHomeTeam = game.homeTeamId.displayName(style: teamDisplayNameStyle)
                            pendingWatchSyncAwayTeam = game.awayTeamId.displayName(style: teamDisplayNameStyle)
                            requestWatchSyncPrompt(gameId: game.id, navigateToLive: false)
                        }
                    )
                case .liveGame:
                    LiveGameScreen(
                        activeTheme: nil,
                        gameId: selectedGameId,
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
                            rewardedAdManager.loadAndShowAd(
                                adUnitID: RewardedAdManager.themeStoreAdUnitID
                            ) { rewardEarned in
                                guard rewardEarned else { return }
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
                        teamDisplayNameStyle: teamDisplayNameStyle,
                        onChangeTeam: onTeamChanged,
                        onChangeTeamDisplayNameStyle: onTeamDisplayNameStyleChanged,
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
                    MyTeamScreen(
                        selectedTeam: selectedTeam,
                        teamDisplayNameStyle: teamDisplayNameStyle,
                        todayGames: todayGames,
                        checkinStadium: pendingCheckinStadium,
                        currentLocation: currentStadiumLocation,
                        onConfirmCheckin: {
                            confirmPendingCheckin()
                        },
                        onDismissCheckin: {
                            dismissPendingCheckin()
                        }
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
        .alert("워치로 보시겠습니까?", isPresented: $showWatchSyncDialog) {
            Button("확인") {
                confirmPendingWatchSync()
            }
            Button("취소", role: .cancel) {
                closeWatchSyncDialog()
            }
        } message: {
            let hasViewedAd = pendingWatchSyncGameId.map { WatchSyncAdLedger.hasViewed(gameId: $0) } ?? false
            let suffix = hasViewedAd ? "" : "광고 관람 후 동기화됩니다."
            if !pendingWatchSyncHomeTeam.isEmpty && !pendingWatchSyncAwayTeam.isEmpty {
                let lines = ["\(pendingWatchSyncAwayTeam) vs \(pendingWatchSyncHomeTeam)", suffix]
                    .filter { !$0.isEmpty }
                    .joined(separator: "\n")
                Text(lines)
            } else {
                Text(suffix.isEmpty ? "워치 동기화를 시작할까요?" : suffix)
            }
        }
        .alert("잠금화면에서 보시겠습니까?", isPresented: $showLiveActivityDialog) {
            Button("확인") {
                confirmPendingLiveActivity()
            }
            Button("취소", role: .cancel) {
                closeLiveActivityDialog()
            }
        } message: {
            Text(liveActivityPromptMessage)
        }
        .alert("경기 시작 전입니다", isPresented: $showGameNotStartedAlert) {
            Button("확인", role: .cancel) {}
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
            if isExistingUserAtLaunch, let note = ReleaseNotes.notes(for: currentVersion) {
                pendingReleaseNote = note
            } else {
                queueFeatureGuideIfNeeded(currentVersion: currentVersion)
            }
            return
        }
        guard lastSeen != currentVersion else {
            queueFeatureGuideIfNeeded(currentVersion: currentVersion)
            return
        }

        defaults.set(currentVersion, forKey: "last_seen_update_version")

        if let note = ReleaseNotes.notes(for: currentVersion) {
            pendingReleaseNote = note
        } else {
            queueFeatureGuideIfNeeded(currentVersion: currentVersion)
        }
    }

    private func queueFeatureGuideIfNeeded(currentVersion: String? = nil) {
        let version = currentVersion ?? Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        guard !version.isEmpty else { return }
        let lastSeen = UserDefaults.standard.string(forKey: "last_seen_feature_guide_version") ?? ""
        guard lastSeen != version else { return }
        showFeatureGuide = true
    }

    private func markFeatureGuideSeen() {
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        if !currentVersion.isEmpty {
            UserDefaults.standard.set(currentVersion, forKey: "last_seen_feature_guide_version")
        }
        showFeatureGuide = false
    }

    // MARK: - Watch Sync
    private func toggleHomeLiveActivity(for game: Game) {
        selectedGameId = game.id

        if activeLiveActivityGameId == game.id {
            activeLiveActivityGameId = nil
            refreshLiveActivityStream()
            LiveActivityManager.shared.endActivity(gameId: game.id)
            Task {
                await PushTokenManager.unregisterLiveActivityToken(gameId: game.id)
                await LiveViewSessionManager.setActive(
                    gameId: game.id,
                    surface: .ios,
                    active: false,
                    myTeam: selectedTeam.rawValue,
                    tokenKey: UserDefaults.standard.string(forKey: "apns_device_token")
                )
            }
            return
        }

        guard game.status == .live else {
            showGameNotStartedAlert = true
            return
        }

        pendingLiveActivityGame = game
        showLiveActivityDialog = true
    }

    private func closeLiveActivityDialog() {
        showLiveActivityDialog = false
        pendingLiveActivityGame = nil
    }

    private func confirmPendingLiveActivity() {
        guard let game = pendingLiveActivityGame else {
            closeLiveActivityDialog()
            return
        }

        let completeStart: () -> Void = {
            startHomeLiveActivity(for: game)
            closeLiveActivityDialog()
        }

        if LiveActivityAdLedger.hasViewed(gameId: game.id) {
            completeStart()
            return
        }

        showLiveActivityDialog = false
        RewardedAdManager.shared.loadAndShowAd(
            adUnitID: RewardedAdManager.liveActivityAdUnitID
        ) { rewardEarned in
            guard rewardEarned else {
                closeLiveActivityDialog()
                return
            }
            LiveActivityAdLedger.markViewed(gameId: game.id)
            completeStart()
        }
    }

    private func startHomeLiveActivity(for game: Game) {
        if let previousGameId = activeLiveActivityGameId, !previousGameId.isEmpty {
            LiveActivityManager.shared.endActivity(gameId: previousGameId)
            Task {
                await PushTokenManager.unregisterLiveActivityToken(gameId: previousGameId)
                await LiveViewSessionManager.setActive(
                    gameId: previousGameId,
                    surface: .ios,
                    active: false,
                    myTeam: selectedTeam.rawValue,
                    tokenKey: UserDefaults.standard.string(forKey: "apns_device_token")
                )
            }
        }

        activeLiveActivityGameId = game.id
        Task {
            await LiveViewSessionManager.setActive(
                gameId: game.id,
                surface: .ios,
                active: true,
                myTeam: selectedTeam.rawValue,
                tokenKey: UserDefaults.standard.string(forKey: "apns_device_token")
            )
        }
        LiveActivityManager.shared.startActivity(
            gameId: game.id,
            homeTeam: game.homeTeamId.rawValue,
            awayTeam: game.awayTeamId.rawValue,
            homeScore: game.homeScore,
            awayScore: game.awayScore,
            inning: game.inning,
            status: game.status.rawValue,
            myTeam: selectedTeam.rawValue
        )
        refreshLiveActivityStream()
    }

    private func refreshLiveActivityStream() {
        liveActivityStreamTask?.cancel()
        liveActivityStreamTask = nil

        guard let gameId = activeLiveActivityGameId, !gameId.isEmpty else { return }

        // Watch 동기화 스트림이 같은 경기를 이미 받고 있으면 그 스트림이 Live Activity도 갱신한다.
        guard syncedGameId != gameId else { return }

        liveActivityStreamTask = Task {
            await streamLiveActivityGame(gameId: gameId)
        }
    }

    private var liveActivityPromptMessage: String {
        if let game = pendingLiveActivityGame, LiveActivityAdLedger.hasViewed(gameId: game.id) {
            if DeviceCapability.supportsDynamicIsland {
                return "잠금화면과 다이내믹 아일랜드 라이브 스코어를 시작할까요?"
            }
            return "잠금화면 라이브 스코어를 시작할까요?"
        }
        if DeviceCapability.supportsDynamicIsland {
            return "광고 관람 후 잠금화면과 다이내믹 아일랜드에서 볼 수 있습니다."
        }
        return "광고 관람 후 잠금화면에서 볼 수 있습니다."
    }

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
        pendingWatchSyncNavigateToLive = false
    }

    private func confirmPendingWatchSync() {
        guard let gameId = pendingWatchSyncGameId, !gameId.isEmpty else {
            closeWatchSyncDialog()
            return
        }
        let shouldNavigate = pendingWatchSyncNavigateToLive

        let completeSync: () -> Void = {
            syncedGameId = gameId
            closeWatchSyncDialog()
            if shouldNavigate && currentView != .home {
                navigateTo(.home)
            }
        }

        if WatchSyncAdLedger.hasViewed(gameId: gameId) {
            completeSync()
            return
        }

        showWatchSyncDialog = false
        RewardedAdManager.shared.loadAndShowAd(
            adUnitID: RewardedAdManager.watchSyncAdUnitID
        ) { rewardEarned in
            if rewardEarned {
                WatchSyncAdLedger.markViewed(gameId: gameId)
            }
            completeSync()
        }
    }

    private func consumePendingWatchSyncResponse() {
        guard let response = connectivity.consumePendingResponse() else { return }
        if response.accepted {
            selectedGameId = response.gameId
            if currentView != .home {
                navigateTo(.home)
            }
            pendingWatchSyncGameId = response.gameId
            pendingWatchSyncNavigateToLive = false
            pendingWatchSyncHomeTeam = ""
            pendingWatchSyncAwayTeam = ""
            confirmPendingWatchSync()
        } else if pendingWatchSyncGameId == response.gameId {
            closeWatchSyncDialog()
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
        StadiumRegionMonitor.shared.onLocationUpdate = { location in
            Task { @MainActor in
                currentStadiumLocation = location
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
            _ = scheduledCheerSignalIds.insert(signalId)
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
        content.body = "\(stadium.name)에서 \(selectedTeam.displayName(style: teamDisplayNameStyle)) 응원을 시작해요."
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

            if settings.activeThemeId != nil || settings.selectedTeam != nil || settings.teamDisplayNameStyle != nil {
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
                if let styleRaw = settings.teamDisplayNameStyle {
                    let style = TeamDisplayNameStyle.fromString(styleRaw)
                    onTeamDisplayNameStyleChanged(style)
                }
            } else {
                // 서버에 데이터 없음 → 로컬 데이터를 서버에 업로드
                if selectedTeam != .none {
                    try? await ThemeRepository.shared.saveSelectedTeam(selectedTeam.rawValue)
                }
                try? await ThemeRepository.shared.saveTeamDisplayNameStyle(teamDisplayNameStyle)
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
                            homeTeam: game.homeTeamId.displayName(style: teamDisplayNameStyle),
                            awayTeam: game.awayTeamId.displayName(style: teamDisplayNameStyle),
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
            if activeLiveActivityGameId == initialState.gameId {
                LiveActivityManager.shared.startOrUpdateActivity(
                    state: initialState,
                    myTeam: selectedTeam.rawValue,
                    latestEvent: nil,
                    alert: false
                )
            }
            lastWatchSignature = gameProgressSignature(initialState)
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
                    let signature = gameProgressSignature(state)
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
                        if activeLiveActivityGameId == state.gameId {
                            LiveActivityManager.shared.startOrUpdateActivity(
                                state: state,
                                myTeam: selectedTeam.rawValue,
                                latestEvent: nil,
                                alert: false
                            )
                        }
                        lastWatchSignature = signature

                        if wasLive && state.status == .finished {
                            let isMyTeamHome = selectedTeam != .none && state.homeTeamId == selectedTeam
                            let isMyTeamAway = selectedTeam != .none && state.awayTeamId == selectedTeam
                            let myTeamWon = (isMyTeamHome && state.homeScore > state.awayScore) ||
                                            (isMyTeamAway && state.awayScore > state.homeScore)
                            if myTeamWon {
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

                    let newItems = sortedItems.filter { $0.cursor > lastSentEventCursor }
                    let batchTypes = Set(newItems.compactMap { mapToWatchEventType($0.type) })
                    let hasScore = batchTypes.contains("SCORE") || batchTypes.contains("HOMERUN")
                    for event in sortedItems {
                        if event.cursor > lastSentEventCursor {
                            if let mapped = mapToWatchEventType(event.type) {
                                if mapped == "HIT" && hasScore { /* skip HIT when SCORE present */ }
                                else {
                                    if EventFilterGate.isAllowed(eventType: mapped, channel: .watch) {
                                        WatchGameSyncManager.shared.sendHapticEvent(eventType: mapped, cursor: event.cursor)
                                    }
                                }
                            }
                            lastSentEventCursor = max(lastSentEventCursor, event.cursor)
                        }
                    }
                case .update(let state, let events):
                    // events 처리 (햅틱 먼저)
                    let sortedEvents = events.sorted(by: { $0.cursor < $1.cursor })
                    let newEvents = sortedEvents.filter { $0.cursor > lastSentEventCursor }
                    let batchTypes = Set(newEvents.compactMap { mapToWatchEventType($0.type) })
                    let hasScore = batchTypes.contains("SCORE") || batchTypes.contains("HOMERUN")
                    for event in sortedEvents {
                        if event.cursor > lastSentEventCursor {
                            if let mapped = mapToWatchEventType(event.type) {
                                if mapped == "HIT" && hasScore { /* skip HIT when SCORE present */ }
                                else {
                                    if EventFilterGate.isAllowed(eventType: mapped, channel: .watch) {
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
                        let signature = gameProgressSignature(state)
                        let latestEvent = newEvents.last ?? sortedEvents.last
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
                            if activeLiveActivityGameId == state.gameId {
                                LiveActivityManager.shared.startOrUpdateActivity(
                                    state: state,
                                    myTeam: selectedTeam.rawValue,
                                    latestEvent: latestEvent,
                                    alert: latestEvent != nil
                                )
                            }
                            lastWatchSignature = signature

                            if wasLive && state.status == .finished {
                                let isMyTeamHome = selectedTeam != .none && state.homeTeamId == selectedTeam
                                let isMyTeamAway = selectedTeam != .none && state.awayTeamId == selectedTeam
                                let myTeamWon = (isMyTeamHome && state.homeScore > state.awayScore) ||
                                                (isMyTeamAway && state.awayScore > state.homeScore)
                                if myTeamWon {
                                    WatchGameSyncManager.shared.sendHapticEvent(eventType: "VICTORY")
                                }
                            }
                        } else if latestEvent != nil {
                            if activeLiveActivityGameId == state.gameId {
                                LiveActivityManager.shared.startOrUpdateActivity(
                                    state: state,
                                    myTeam: selectedTeam.rawValue,
                                    latestEvent: latestEvent,
                                    alert: true
                                )
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

    private func streamLiveActivityGame(gameId: String) async {
        var lastSignature = ""
        var lastEventCursor: Int64 = 0
        let reconnectDelays: [UInt64] = [1_000_000_000, 2_000_000_000, 5_000_000_000, 10_000_000_000]
        var reconnectAttempt = 0

        if let initialState = await BackendGamesRepository.shared.fetchGameState(gameId: gameId) {
            guard activeLiveActivityGameId == gameId else { return }
            LiveActivityManager.shared.startOrUpdateActivity(
                state: initialState,
                myTeam: selectedTeam.rawValue,
                latestEvent: nil,
                alert: false
            )
            lastSignature = liveActivitySignature(initialState)
            if initialState.status != .live {
                await deactivateLiveActivity(gameId: gameId)
                return
            }
        }

        while !Task.isCancelled {
            var hasConsumedInitialEventsSnapshot = false

            for await message in BackendGamesRepository.shared.streamGame(gameId: gameId) {
                guard activeLiveActivityGameId == gameId else { return }

                switch message {
                case .connected:
                    reconnectAttempt = 0
                case .closed:
                    break
                case .error:
                    break
                case .state(let state):
                    lastSignature = await updateLiveActivityFromStream(
                        state: state,
                        latestEvent: nil,
                        alert: false,
                        lastSignature: lastSignature
                    )
                case .events(let events):
                    let sortedEvents = events.sorted(by: { $0.cursor < $1.cursor })
                    if !hasConsumedInitialEventsSnapshot {
                        if let maxCursor = sortedEvents.last?.cursor {
                            lastEventCursor = max(lastEventCursor, maxCursor)
                        }
                        hasConsumedInitialEventsSnapshot = true
                        break
                    }
                    if let latestEvent = sortedEvents.filter({ $0.cursor > lastEventCursor }).last {
                        lastEventCursor = max(lastEventCursor, latestEvent.cursor)
                        if let state = await BackendGamesRepository.shared.fetchGameState(gameId: gameId) {
                            lastSignature = await updateLiveActivityFromStream(
                                state: state,
                                latestEvent: latestEvent,
                                alert: true,
                                lastSignature: lastSignature
                            )
                        }
                    }
                case .update(let state, let events):
                    let sortedEvents = events.sorted(by: { $0.cursor < $1.cursor })
                    let newEvents = sortedEvents.filter { $0.cursor > lastEventCursor }
                    if let maxCursor = newEvents.last?.cursor {
                        lastEventCursor = max(lastEventCursor, maxCursor)
                    }
                    if let state {
                        lastSignature = await updateLiveActivityFromStream(
                            state: state,
                            latestEvent: newEvents.last ?? sortedEvents.last,
                            alert: !(newEvents.isEmpty && sortedEvents.isEmpty),
                            lastSignature: lastSignature
                        )
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

    private func updateLiveActivityFromStream(
        state: LiveGameState,
        latestEvent: LiveEvent?,
        alert: Bool,
        lastSignature: String
    ) async -> String {
        guard activeLiveActivityGameId == state.gameId else { return lastSignature }

        if state.status != .live {
            LiveActivityManager.shared.startOrUpdateActivity(
                state: state,
                myTeam: selectedTeam.rawValue,
                latestEvent: latestEvent,
                alert: false
            )
            await deactivateLiveActivity(gameId: state.gameId)
            return liveActivitySignature(state)
        }

        let signature = liveActivitySignature(state)
        if signature != lastSignature || latestEvent != nil {
            LiveActivityManager.shared.startOrUpdateActivity(
                state: state,
                myTeam: selectedTeam.rawValue,
                latestEvent: latestEvent,
                alert: alert && latestEvent != nil
            )
            return signature
        }
        return lastSignature
    }

    private func liveActivitySignature(_ state: LiveGameState) -> String {
        gameProgressSignature(state)
    }

    private func gameProgressSignature(_ state: LiveGameState) -> String {
        [
            state.gameId,
            state.status.rawValue,
            state.inning,
            "\(state.homeScore)",
            "\(state.awayScore)",
            "\(state.ball)",
            "\(state.strike)",
            "\(state.out)",
            "\(state.baseFirst)",
            "\(state.baseSecond)",
            "\(state.baseThird)",
            state.pitcher,
            state.batter,
            "\(state.pitcherPitchCount ?? -1)"
        ].joined(separator: "|")
    }

    @MainActor
    private func deactivateLiveActivity(gameId: String) async {
        guard activeLiveActivityGameId == gameId else { return }
        activeLiveActivityGameId = nil
        refreshLiveActivityStream()
        await PushTokenManager.unregisterLiveActivityToken(gameId: gameId)
        await LiveViewSessionManager.setActive(
            gameId: gameId,
            surface: .ios,
            active: false,
            myTeam: selectedTeam.rawValue,
            tokenKey: UserDefaults.standard.string(forKey: "apns_device_token")
        )
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

private enum DeviceCapability {
    static var supportsDynamicIsland: Bool {
        let identifier = modelIdentifier
        guard identifier.hasPrefix("iPhone") else { return false }

        let versionText = String(identifier.dropFirst("iPhone".count))
        let parts = versionText.split(separator: ",").compactMap { Int($0) }
        guard parts.count == 2 else { return false }

        let major = parts[0]
        let minor = parts[1]

        switch major {
        case 15:
            return [2, 3, 4, 5].contains(minor)
        case 16:
            return [1, 2].contains(minor)
        case 17:
            return [1, 2, 3, 4].contains(minor)
        default:
            return major >= 18
        }
    }

    private static var modelIdentifier: String {
        #if targetEnvironment(simulator)
        if let simulatorModel = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"],
           !simulatorModel.isEmpty {
            return simulatorModel
        }
        #endif

        var systemInfo = utsname()
        uname(&systemInfo)
        return withUnsafePointer(to: &systemInfo.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(validatingUTF8: $0) ?? ""
            }
        }
    }
}
