package com.basehaptic.mobile

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.basehaptic.mobile.auth.AuthManager
import com.basehaptic.mobile.auth.AuthState
import com.basehaptic.mobile.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.handleDeeplinks
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.StadiumCheerThemeStore
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeCategory
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.data.model.ThemeStore
import com.basehaptic.mobile.data.ThemeRepository
import com.basehaptic.mobile.service.GameSyncForegroundService
import com.basehaptic.mobile.service.GameSyncState
import com.basehaptic.mobile.ui.screens.*
import com.basehaptic.mobile.ui.theme.BaseHapticTheme
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.wear.WearThemeSyncManager
import com.basehaptic.mobile.wear.WearWatchSyncBridge
import com.basehaptic.mobile.ui.components.RewardedAdManager
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REQUEST_CODE_IN_APP_UPDATE = 9001
private const val SHOW_COMMUNITY_TAB = false
private const val SHOW_STORE_TAB = true
// TODO(my-team-tab): 활성화 시 true로 변경. 다크 머지 단계에서는 탭 미노출.
private const val SHOW_MY_TEAM_TAB = false
private const val USER_PREFS_NAME = "basehaptic_user_prefs"
private const val KEY_SELECTED_TEAM = "selected_team"
private const val KEY_UNLOCKED_THEME_IDS = "unlocked_theme_ids"
private const val KEY_ACTIVE_THEME_ID = "active_theme_id"
// 워치 페이스 테마와 무관하게 응원 시 풀스크린에 적용될 테마. ThemeStore와 별도로 StadiumCheerThemeStore에서 매칭.
private const val KEY_ACTIVE_CHEER_THEME_ID = "active_cheer_theme_id"

class MainActivity : ComponentActivity() {
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // 다운로드 완료 — 앱 재시작으로 설치 완료
            appUpdateManager.completeUpdate()
        }
    }

    private fun checkForAppUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        this,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                        REQUEST_CODE_IN_APP_UPDATE
                    )
                } catch (e: Exception) {
                    Log.e("InAppUpdate", "Failed to start update flow", e)
                }
            }
        }
    }

    private fun loadSavedTeamOrNull(): Team? {
        val savedTeamName = getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SELECTED_TEAM, null)
            .orEmpty()
        if (savedTeamName.isBlank()) return null

        val parsed = Team.fromString(savedTeamName)
        return parsed.takeIf { it != Team.NONE }
    }

    private fun persistSelectedTeam(team: Team) {
        if (team == Team.NONE) return
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_TEAM, team.name)
            .apply()
    }

    private fun loadUnlockedThemeIds(): Set<String> {
        val saved = getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getStringSet(KEY_UNLOCKED_THEME_IDS, null) ?: emptySet()
        return saved + "default"
    }

    private fun persistUnlockedThemeIds(ids: Set<String>) {
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_UNLOCKED_THEME_IDS, ids)
            .apply()
    }

    private fun loadActiveThemeId(): String? {
        return getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ACTIVE_THEME_ID, null)
    }

    private fun persistActiveThemeId(themeId: String?) {
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_THEME_ID, themeId)
            .apply()
    }

    private fun loadActiveCheerThemeId(): String? {
        return getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ACTIVE_CHEER_THEME_ID, null)
    }

    private fun persistActiveCheerThemeId(themeId: String?) {
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_CHEER_THEME_ID, themeId)
            .apply()
    }

    private fun handleAuthDeeplink(intent: Intent) {
        try {
            SupabaseClientProvider.client.handleDeeplinks(intent) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeeplink(intent)
    }

    override fun onResume() {
        super.onResume()
        // 업데이트가 다운로드됐지만 설치 안 된 경우 재시도
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager.registerListener(installStateUpdatedListener)
        checkForAppUpdate()
        MobileAds.initialize(this)
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(
                    // Logcat에서 "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("XXXX")" 확인 후 추가
                    // "여기에_기기_해시_ID_붙여넣기"
                ))
                .build()
        )
        AuthManager.initialize()
        handleAuthDeeplink(intent)
        val savedTeam = loadSavedTeamOrNull()
        val initialTeam = savedTeam ?: Team.NONE
        val initialShowOnboarding = savedTeam == null
        val initialUnlockedIds = loadUnlockedThemeIds()
        val initialActiveThemeId = loadActiveThemeId()
        val initialActiveTheme = initialActiveThemeId?.let { id ->
            ThemeStore.allThemes.find { it.id == id }
        }
        val initialActiveCheerThemeId = loadActiveCheerThemeId()
        val initialActiveCheerTheme = initialActiveCheerThemeId?.let { id ->
            StadiumCheerThemeStore.allThemes.find { it.id == id }
        }

        setContent {
            // ??猷⑦듃?먯꽌 ?좏깮 ? ?곹깭瑜?愿由ы븯怨??섏쐞 ?붾㈃?쇰줈 ?꾨떖
            var selectedTeam by remember { mutableStateOf(initialTeam) }
            var showOnboarding by remember { mutableStateOf(initialShowOnboarding) }
            
            BaseHapticTheme(selectedTeam = selectedTeam) {
                BaseHapticApp(
                    selectedTeam = selectedTeam,
                    onTeamChanged = { team ->
                        selectedTeam = team
                        persistSelectedTeam(team)
                    },
                    showOnboarding = showOnboarding,
                    onOnboardingComplete = { team ->
                        selectedTeam = team
                        persistSelectedTeam(team)
                        showOnboarding = false
                    },
                    initialUnlockedThemeIds = initialUnlockedIds,
                    initialActiveTheme = initialActiveTheme,
                    initialActiveCheerTheme = initialActiveCheerTheme,
                    onPersistUnlockedThemeIds = ::persistUnlockedThemeIds,
                    onPersistActiveThemeId = ::persistActiveThemeId,
                    onPersistActiveCheerThemeId = ::persistActiveCheerThemeId,
                )
            }
        }
    }
}

@Composable
fun BaseHapticApp(
    selectedTeam: Team,
    onTeamChanged: (Team) -> Unit,
    showOnboarding: Boolean,
    onOnboardingComplete: (Team) -> Unit,
    initialUnlockedThemeIds: Set<String> = setOf("default"),
    initialActiveTheme: ThemeData? = null,
    initialActiveCheerTheme: ThemeData? = null,
    onPersistUnlockedThemeIds: (Set<String>) -> Unit = {},
    onPersistActiveThemeId: (String?) -> Unit = {},
    onPersistActiveCheerThemeId: (String?) -> Unit = {},
) {
    val authState by AuthManager.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var currentView by remember { mutableStateOf<Screen>(Screen.Home) }
    val navigationHistory = remember { mutableStateListOf<Screen>() }
    var activeTheme by remember { mutableStateOf(initialActiveTheme) }
    var activeCheerTheme by remember { mutableStateOf(initialActiveCheerTheme) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var syncedGameId by remember { mutableStateOf<String?>(null) }
    var showWatchSyncDialog by remember { mutableStateOf(false) }
    var pendingWatchSyncGameId by remember { mutableStateOf<String?>(null) }
    var pendingWatchSyncNavigateToLive by remember { mutableStateOf(false) }
    val observedMyTeamGameStatus = remember { mutableStateMapOf<String, GameStatus>() }
    val autoPromptedLiveGames = remember { mutableStateMapOf<String, Boolean>() }
    var unlockedThemeIds by remember { mutableStateOf(initialUnlockedThemeIds) }
    var todayGamesSnapshot by remember(selectedTeam) { mutableStateOf<List<Game>>(emptyList()) }
    var todayGamesLoadedDate by remember(selectedTeam) { mutableStateOf<LocalDate?>(null) }
    var todayGamesReloadToken by remember(selectedTeam) { mutableStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun requestWatchSyncPrompt(gameId: String, navigateToLive: Boolean) {
        if (syncedGameId == gameId) return
        pendingWatchSyncGameId = gameId
        pendingWatchSyncNavigateToLive = navigateToLive
        showWatchSyncDialog = true
    }

    fun navigateTo(targetView: Screen) {
        if (targetView == currentView) return
        navigationHistory.add(currentView)
        currentView = targetView
    }

    fun closeWatchSyncDialog() {
        showWatchSyncDialog = false
        pendingWatchSyncGameId = null
        if (pendingWatchSyncNavigateToLive) {
            navigateTo(Screen.LiveGame)
        }
        pendingWatchSyncNavigateToLive = false
    }

    fun applyWatchSyncResponse(gameId: String, accepted: Boolean) {
        if (gameId.isBlank()) return

        if (accepted) {
            selectedGameId = gameId
            syncedGameId = gameId
        }

        if (pendingWatchSyncGameId == gameId) {
            showWatchSyncDialog = false
            pendingWatchSyncGameId = null
            pendingWatchSyncNavigateToLive = false
        }
    }

    fun consumePendingWatchSyncResponse() {
        while (true) {
            val response = WearWatchSyncBridge.consumePendingResponse(context) ?: break
            applyWatchSyncResponse(
                gameId = response.gameId,
                accepted = response.accepted
            )
        }
    }

    fun navigateBack(): Boolean {
        if (showWatchSyncDialog) {
            closeWatchSyncDialog()
            return true
        }
        if (navigationHistory.isNotEmpty()) {
            currentView = navigationHistory.removeAt(navigationHistory.lastIndex)
            return true
        }
        if (currentView != Screen.Home) {
            currentView = Screen.Home
            return true
        }
        return false
    }

    val canHandleBack =
        showWatchSyncDialog || navigationHistory.isNotEmpty() || currentView != Screen.Home
    BackHandler(enabled = canHandleBack) {
        navigateBack()
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == WearWatchSyncBridge.ACTION_WATCH_SYNC_RESPONSE) {
                    consumePendingWatchSyncResponse()
                }
            }
        }
        val filter = IntentFilter(WearWatchSyncBridge.ACTION_WATCH_SYNC_RESPONSE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        consumePendingWatchSyncResponse()

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Supabase 테마/설정 동기화 (로그인 시)
    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            try {
                val ids = withContext(Dispatchers.IO) { ThemeRepository.fetchUnlockedThemeIds() }
                unlockedThemeIds = ids
                onPersistUnlockedThemeIds(ids)

                val settings = withContext(Dispatchers.IO) { ThemeRepository.fetchUserSettings() }
                if (settings.activeThemeId != null) {
                    activeTheme = ThemeStore.allThemes.find { it.id == settings.activeThemeId }
                    onPersistActiveThemeId(settings.activeThemeId)
                }
                if (settings.selectedTeam != null) {
                    val serverTeam = Team.fromString(settings.selectedTeam)
                    if (serverTeam != Team.NONE && serverTeam != selectedTeam) {
                        onTeamChanged(serverTeam)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(selectedTeam) {
        if (selectedTeam != Team.NONE) {
            WearThemeSyncManager.syncThemeToWatch(context, selectedTeam, activeTheme?.id)
        }
    }

    // 워치에 사용자 설정 초기 동기화 (영상 알림 토글, 라이브 알림 마스터 스위치)
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
        val videoEnabled = prefs.getBoolean("event_video_enabled", true)
        val liveHapticEnabled = prefs.getBoolean("live_haptic_enabled", true)
        com.basehaptic.mobile.wear.WearSettingsSyncManager.syncEventVideoEnabledToWatch(context, videoEnabled)
        com.basehaptic.mobile.wear.WearSettingsSyncManager.syncLiveHapticEnabledToWatch(context, liveHapticEnabled)
    }

    DisposableEffect(lifecycleOwner, selectedTeam, todayGamesLoadedDate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val today = LocalDate.now()
                if (todayGamesLoadedDate != today) {
                    todayGamesReloadToken += 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(selectedTeam, todayGamesReloadToken) {
        if (selectedTeam == Team.NONE) {
            todayGamesSnapshot = emptyList()
            todayGamesLoadedDate = null
            return@LaunchedEffect
        }

        val today = LocalDate.now()
        if (todayGamesLoadedDate == today) return@LaunchedEffect

        val cachedGames = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.peekTodayGamesCache(context.applicationContext, selectedTeam)
            }
        }.getOrNull().orEmpty()
        if (cachedGames.isNotEmpty()) {
            todayGamesSnapshot = cachedGames
        }

        val freshGames = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.fetchTodayGamesCached(context.applicationContext, selectedTeam)
            }
        }.getOrNull()
        if (freshGames != null) {
            todayGamesSnapshot = freshGames
            todayGamesLoadedDate = today
        } else if (cachedGames.isNotEmpty()) {
            todayGamesLoadedDate = today
        }
    }

    // Start/stop the foreground service for background polling
    LaunchedEffect(selectedTeam) {
        if (selectedTeam != Team.NONE) {
            val intent = Intent(context, GameSyncForegroundService::class.java).apply {
                action = GameSyncForegroundService.ACTION_START_POLLING
                putExtra(GameSyncForegroundService.EXTRA_SELECTED_TEAM, selectedTeam.name)
            }
            ContextCompat.startForegroundService(context, intent)
        } else {
            val intent = Intent(context, GameSyncForegroundService::class.java).apply {
                action = GameSyncForegroundService.ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    // Start/stop streaming via service when syncedGameId changes
    LaunchedEffect(syncedGameId, selectedTeam) {
        val gameId = syncedGameId
        if (!gameId.isNullOrBlank()) {
            val intent = Intent(context, GameSyncForegroundService::class.java).apply {
                action = GameSyncForegroundService.ACTION_START_STREAMING
                putExtra(GameSyncForegroundService.EXTRA_GAME_ID, gameId)
                putExtra(GameSyncForegroundService.EXTRA_SELECTED_TEAM, selectedTeam.name)
            }
            context.startService(intent)
        } else {
            val intent = Intent(context, GameSyncForegroundService::class.java).apply {
                action = GameSyncForegroundService.ACTION_STOP_STREAMING
            }
            context.startService(intent)
        }
    }

    // Collect today games updates from the service
    val serviceGames by GameSyncState.todayGames.collectAsState()
    LaunchedEffect(serviceGames) {
        if (serviceGames.isNotEmpty()) {
            todayGamesSnapshot = serviceGames
            todayGamesLoadedDate = LocalDate.now()
        }
    }

    // React to game-went-live events from the service
    val gameWentLiveEvent by GameSyncState.gameWentLive.collectAsState()
    LaunchedEffect(gameWentLiveEvent) {
        val event = gameWentLiveEvent ?: return@LaunchedEffect
        selectedGameId = event.gameId
        GameSyncState.consumeGameWentLive()
    }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = { team ->
                onOnboardingComplete(team)
            },
            authState = authState,
            onSignInWithKakao = {
                coroutineScope.launch { AuthManager.signInWithKakao() }
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                if (currentView != Screen.LiveGame && currentView != Screen.WatchTest) {
                    BottomNavigationBar(
                        currentView = currentView,
                        onNavigate = { navigateTo(it) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentView) {
                    Screen.Home -> HomeScreen(
                        selectedTeam = selectedTeam,
                        todayGames = todayGamesSnapshot,
                        syncedGameId = syncedGameId,
                        onSelectGame = { game ->
                            selectedGameId = game.id
                            if (game.status == GameStatus.LIVE && syncedGameId != game.id) {
                                requestWatchSyncPrompt(gameId = game.id, navigateToLive = true)
                            } else {
                                navigateTo(Screen.LiveGame)
                            }
                        }
                    )
                    Screen.LiveGame -> LiveGameScreen(
                        gameId = selectedGameId,
                        syncedGameId = syncedGameId,
                        onBack = { navigateBack() }
                    )
                    Screen.Community -> CommunityScreen(
                        selectedTeam = selectedTeam
                    )
                    Screen.Store -> ThemeStoreScreen(
                        activeTheme = activeTheme,
                        activeCheerTheme = activeCheerTheme,
                        unlockedThemeIds = unlockedThemeIds,
                        onApplyTheme = { theme ->
                            activeTheme = theme
                            onPersistActiveThemeId(theme?.id)
                            WearThemeSyncManager.syncThemeToWatch(context, selectedTeam, theme?.id)
                            if (authState is AuthState.LoggedIn) {
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            ThemeRepository.saveActiveTheme(theme?.id)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        },
                        onApplyCheerTheme = { theme ->
                            // 응원 테마는 워치 페이스 테마와 무관하게 별도 영속화. WearThemeSyncManager 호출 X.
                            // TODO(stadium-cheer): 활성화 시 워치에 active_cheer_theme_id 동기화 + 풀스크린 응원 화면이 이 테마로 렌더링되도록 연결.
                            activeCheerTheme = theme
                            onPersistActiveCheerThemeId(theme?.id)
                        },
                        onUnlockTheme = { theme ->
                            RewardedAdManager.loadAndShowAd(context) {
                                unlockedThemeIds = unlockedThemeIds + theme.id
                                onPersistUnlockedThemeIds(unlockedThemeIds)
                                if (theme.id.startsWith("cheer_")) {
                                    activeCheerTheme = theme
                                    onPersistActiveCheerThemeId(theme.id)
                                } else {
                                    activeTheme = theme
                                    onPersistActiveThemeId(theme.id)
                                    WearThemeSyncManager.syncThemeToWatch(context, selectedTeam, theme.id)
                                }
                                if (authState is AuthState.LoggedIn) {
                                    coroutineScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                ThemeRepository.saveUnlock(theme.id)
                                                if (!theme.id.startsWith("cheer_")) {
                                                    ThemeRepository.saveActiveTheme(theme.id)
                                                }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                            }
                        },
                    )
                    Screen.Settings -> SettingsScreen(
                        selectedTeam = selectedTeam,
                        onChangeTeam = { team ->
                            onTeamChanged(team)
                            if (authState is AuthState.LoggedIn) {
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            ThemeRepository.saveSelectedTeam(team.name)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        },
                        onOpenWatchTest = { navigateTo(Screen.WatchTest) },
                        authState = authState,
                        onSignInWithKakao = {
                            coroutineScope.launch { AuthManager.signInWithKakao() }
                        },
                        onSignOut = {
                            coroutineScope.launch { AuthManager.signOut() }
                        },
                        onDeleteAccount = {
                            AuthManager.deleteAccount(context)
                        }
                    )
                    Screen.WatchTest -> WatchTestScreen(
                        selectedTeam = selectedTeam,
                        onBack = { navigateBack() }
                    )
                    // TODO(my-team-tab): 활성화 시 SHOW_MY_TEAM_TAB=true. 컨테이너에 응원 랭킹·향후 팀별 뉴스 임베드.
                    Screen.MyTeam -> MyTeamScreen(
                        selectedTeam = selectedTeam,
                    )
                }
            }
        }

        if (showWatchSyncDialog && pendingWatchSyncGameId != null) {
            AlertDialog(
                onDismissRequest = { closeWatchSyncDialog() },
                title = { Text(text = "워치 동기화") },
                text = { Text(text = "경기를 관람하겠습니까?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            syncedGameId = pendingWatchSyncGameId
                            closeWatchSyncDialog()
                        }
                    ) {
                        Text("예")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { closeWatchSyncDialog() }
                    ) {
                        Text("아니오")
                    }
                }
            )
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentView: Screen,
    onNavigate: (Screen) -> Unit
) {
    val teamTheme = LocalTeamTheme.current
    
    NavigationBar(
        containerColor = Gray900,
        tonalElevation = 8.dp
    ) {
        BottomNavItem(
            icon = Icons.Default.Home,
            label = "홈",
            selected = currentView == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
            activeColor = teamTheme.navIndicator
        )

        if (SHOW_COMMUNITY_TAB) {
            BottomNavItem(
                icon = Icons.Default.Message,
                label = "커뮤니티",
                selected = currentView == Screen.Community,
                onClick = { onNavigate(Screen.Community) },
                activeColor = teamTheme.navIndicator
            )
        }

        if (SHOW_STORE_TAB) {
            BottomNavItem(
                icon = Icons.Default.ShoppingBag,
                label = "상점",
                selected = currentView == Screen.Store,
                onClick = { onNavigate(Screen.Store) },
                activeColor = teamTheme.navIndicator
            )
        }

        // TODO(my-team-tab): 활성화 시 SHOW_MY_TEAM_TAB=true. 1차 콘텐츠는 응원팀 랭킹, 향후 팀별 뉴스 추가.
        if (SHOW_MY_TEAM_TAB) {
            BottomNavItem(
                icon = Icons.Default.Star,
                label = "내 팀",
                selected = currentView == Screen.MyTeam,
                onClick = { onNavigate(Screen.MyTeam) },
                activeColor = teamTheme.navIndicator
            )
        }

        BottomNavItem(
            icon = Icons.Default.Settings,
            label = "설정",
            selected = currentView == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            activeColor = teamTheme.navIndicator
        )
    }
}

@Composable
fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    activeColor: Color
) {
    NavigationBarItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        },
        label = {
            Text(
                text = label,
                fontSize = 12.sp
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = activeColor,
            selectedTextColor = activeColor,
            unselectedIconColor = Color(0xFF71717A),
            unselectedTextColor = Color(0xFF71717A),
            indicatorColor = activeColor.copy(alpha = 0.1f)
        )
    )
}

sealed class Screen {
    object Home : Screen()
    object LiveGame : Screen()
    object Community : Screen()
    object Store : Screen()
    object Settings : Screen()
    object WatchTest : Screen()
    // TODO(my-team-tab): 활성화 시 BottomNavigationBar에 진입점 노출. 다크 머지 상태에서는 SHOW_MY_TEAM_TAB=false로 미노출.
    object MyTeam : Screen()
}

