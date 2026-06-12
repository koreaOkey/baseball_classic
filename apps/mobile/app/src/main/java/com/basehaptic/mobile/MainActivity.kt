package com.basehaptic.mobile

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.basehaptic.mobile.auth.AuthManager
import com.basehaptic.mobile.auth.AuthState
import com.basehaptic.mobile.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.handleDeeplinks
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.LiveViewSessionRegistrar
import com.basehaptic.mobile.data.LiveScoreAdLedger
import com.basehaptic.mobile.data.WatchSyncAdLedger
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.StadiumCheerThemeStore
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeCategory
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.data.model.ThemeStore
import com.basehaptic.mobile.data.ThemeRepository
import com.basehaptic.mobile.push.BaseHapticMessagingService
import com.basehaptic.mobile.push.LiveScoreNotificationManager
import com.basehaptic.mobile.push.NotificationIntentBus
import com.basehaptic.mobile.push.PushSetup
import com.basehaptic.mobile.push.TeamSubscriptionRegistrar
import com.basehaptic.mobile.service.GameSyncForegroundService
import com.basehaptic.mobile.ui.screens.*
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.BaseHapticTheme
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.wear.WatchCompanionStatus
import com.basehaptic.mobile.wear.WatchCompanionStatusRepository
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
private const val SHOW_MY_TEAM_TAB = false
private const val DEBUG_FORCE_EMPTY_HOME_GAMES_FOR_UPDATE_QA = false
private const val ACTION_DEBUG_POST_LIVE_SCORE =
    "com.basehaptic.mobile.DEBUG_POST_LIVE_SCORE"
private const val USER_PREFS_NAME = "basehaptic_user_prefs"
private const val KEY_SELECTED_TEAM = "selected_team"
private const val KEY_UNLOCKED_THEME_IDS = "unlocked_theme_ids"
private const val KEY_ACTIVE_THEME_ID = "active_theme_id"
// 워치 페이스 테마와 무관하게 응원 시 풀스크린에 적용될 테마. ThemeStore와 별도로 StadiumCheerThemeStore에서 매칭.
private const val KEY_ACTIVE_CHEER_THEME_ID = "active_cheer_theme_id"
private const val KEY_LAST_SEEN_UPDATE_VERSION = "last_seen_update_version"
private const val KEY_LAST_SEEN_ONBOARDING_VERSION = "last_seen_onboarding_version"
private const val ANDROID_STORE_URL = "market://details?id=com.basehaptic.mobile"

private fun compareVersionNames(left: String, right: String): Int {
    val leftParts = left.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
    val rightParts = right.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val l = leftParts.getOrElse(index) { 0 }
        val r = rightParts.getOrElse(index) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return 0
}

private fun requiresServerUpdate(currentVersion: String, config: BackendGamesRepository.AppConfig): Boolean {
    if (!config.forceUpdate) return false
    val minVersion = config.minSupportedVersion.takeIf { it.isNotBlank() }
    val latestVersion = config.latestVersion.takeIf { it.isNotBlank() }
    return (minVersion != null && compareVersionNames(currentVersion, minVersion) < 0) ||
        (latestVersion != null && compareVersionNames(currentVersion, latestVersion) < 0)
}

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
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        this,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
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
        TeamSubscriptionRegistrar.syncIfNeeded(this)
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

    private fun loadLastSeenUpdateVersion(): String =
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LAST_SEEN_UPDATE_VERSION, null)
            .orEmpty()

    private fun persistLastSeenUpdateVersion(version: String) {
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SEEN_UPDATE_VERSION, version)
            .apply()
    }

    private fun loadLastSeenOnboardingVersion(): String {
        return getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LAST_SEEN_ONBOARDING_VERSION, null)
            .orEmpty()
    }

    private fun persistLastSeenOnboardingVersion(version: String) {
        if (version.isBlank()) return
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SEEN_ONBOARDING_VERSION, version)
            .apply()
    }

    private fun handleAuthDeeplink(intent: Intent) {
        try {
            SupabaseClientProvider.client.handleDeeplinks(intent) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val gameId = intent.getStringExtra(BaseHapticMessagingService.EXTRA_GAME_ID).orEmpty()
        if (gameId.isBlank()) return
        NotificationIntentBus.post(
            gameId = gameId,
            homeTeam = intent.getStringExtra(BaseHapticMessagingService.EXTRA_HOME_TEAM),
            awayTeam = intent.getStringExtra(BaseHapticMessagingService.EXTRA_AWAY_TEAM),
            openHomeOnly = true,
        )
    }

    private fun handleDebugLiveScoreIntent(intent: Intent?): Boolean {
        if (!BuildConfig.DEBUG || intent?.action != ACTION_DEBUG_POST_LIVE_SCORE) return false
        val highlight = intent.getBooleanExtra("highlight", false)
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(LiveScoreNotificationManager.KEY_LOCK_SCREEN_LIVE_SCORE_ENABLED, true)
            .apply()
        val eventType = if (highlight) "SCORE" else "HIT"
        val eventText = if (highlight) "김현수 적시타 · 1점 추가" else "경기 진행 상황을 업데이트 중입니다"
        val posted = LiveScoreNotificationManager.post(
            context = this,
            state = BackendGamesRepository.LiveGameState(
                gameId = "debug-live-score-preview",
                homeTeam = Team.LG.name,
                awayTeam = Team.KIA.name,
                homeTeamId = Team.LG,
                awayTeamId = Team.KIA,
                homeScore = if (highlight) 5 else 4,
                awayScore = 3,
                inning = "9회초",
                status = GameStatus.LIVE,
                ball = 2,
                strike = 1,
                out = 1,
                baseFirst = true,
                baseSecond = false,
                baseThird = highlight,
                pitcher = "임찬규",
                batter = "김현수",
                pitcherPitchCount = 87,
                lastEventType = eventType
            ),
            latestEventType = eventType,
            latestEventDescription = eventText,
            highlightEvent = highlight
        )
        Log.d("LiveScoreDebug", "debug live score notification posted=$posted highlight=$highlight")
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
        if (handleDebugLiveScoreIntent(intent)) return
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 즉시 업데이트 플로우가 중단된 경우 앱 사용 전에 다시 이어간다.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        this,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                        REQUEST_CODE_IN_APP_UPDATE
                    )
                } catch (e: Exception) {
                    Log.e("InAppUpdate", "Failed to resume immediate update flow", e)
                }
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
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
        if (handleDebugLiveScoreIntent(intent)) {
            moveTaskToBack(true)
            return
        }
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
        PushSetup.initialize(this)
        handleDebugLiveScoreIntent(intent)
        handleNotificationIntent(intent)
        val currentVersion = com.basehaptic.mobile.BuildConfig.VERSION_NAME
        val savedTeam = loadSavedTeamOrNull()
        val initialTeam = savedTeam ?: Team.NONE
        val shouldShowUpdateOnboarding = savedTeam != null &&
            currentVersion.isNotBlank() &&
            loadLastSeenOnboardingVersion() != currentVersion
        if (shouldShowUpdateOnboarding) {
            persistLastSeenUpdateVersion(currentVersion)
        }
        val initialShowOnboarding = savedTeam == null || shouldShowUpdateOnboarding
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
                        persistLastSeenOnboardingVersion(currentVersion)
                        showOnboarding = false
                    },
                    initialUnlockedThemeIds = initialUnlockedIds,
                    initialActiveTheme = initialActiveTheme,
                    initialActiveCheerTheme = initialActiveCheerTheme,
                    onPersistUnlockedThemeIds = ::persistUnlockedThemeIds,
                    onPersistActiveThemeId = ::persistActiveThemeId,
                    onPersistActiveCheerThemeId = ::persistActiveCheerThemeId,
                    loadLastSeenUpdateVersion = ::loadLastSeenUpdateVersion,
                    onPersistLastSeenUpdateVersion = ::persistLastSeenUpdateVersion,
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
    loadLastSeenUpdateVersion: () -> String = { "" },
    onPersistLastSeenUpdateVersion: (String) -> Unit = {},
) {
    val authState by AuthManager.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var currentView by remember { mutableStateOf<Screen>(Screen.Home) }
    val navigationHistory = remember { mutableStateListOf<Screen>() }
    var activeTheme by remember { mutableStateOf(initialActiveTheme) }
    var activeCheerTheme by remember { mutableStateOf(initialActiveCheerTheme) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var syncedGameId by remember { mutableStateOf<String?>(null) }
    var activeLiveScoreGameId by remember { mutableStateOf<String?>(null) }
    var registeredLiveScoreSessionGameId by remember { mutableStateOf<String?>(null) }
    var registeredWatchSessionGameId by remember { mutableStateOf<String?>(null) }
    var showWatchSyncDialog by remember { mutableStateOf(false) }
    var showLiveScoreDialog by remember { mutableStateOf(false) }
    var showGameNotStartedDialog by remember { mutableStateOf(false) }
    var pendingWatchSyncGameId by remember { mutableStateOf<String?>(null) }
    var pendingWatchSyncNavigateToLive by remember { mutableStateOf(false) }
    var pendingWatchSyncHomeTeam by remember { mutableStateOf("") }
    var pendingWatchSyncAwayTeam by remember { mutableStateOf("") }
    var pendingLiveScoreGame by remember { mutableStateOf<Game?>(null) }
    var requiredUpdateConfig by remember { mutableStateOf<BackendGamesRepository.AppConfig?>(null) }
    val observedMyTeamGameStatus = remember { mutableStateMapOf<String, GameStatus>() }
    val autoPromptedLiveGames = remember { mutableStateMapOf<String, Boolean>() }
    var unlockedThemeIds by remember { mutableStateOf(initialUnlockedThemeIds) }
    var pendingReleaseNote by remember { mutableStateOf<com.basehaptic.mobile.data.model.ReleaseNote?>(null) }
    var todayGamesSnapshot by remember(selectedTeam) { mutableStateOf<List<Game>>(emptyList()) }
    var todayGamesLoadedDate by remember(selectedTeam) { mutableStateOf<LocalDate?>(null) }
    var todayGamesReloadToken by remember(selectedTeam) { mutableStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun requestWatchSyncPrompt(
        gameId: String,
        navigateToLive: Boolean,
        homeTeam: String = "",
        awayTeam: String = "",
    ) {
        if (syncedGameId == gameId) return
        if (!activeLiveScoreGameId.isNullOrBlank() && activeLiveScoreGameId != gameId) {
            activeLiveScoreGameId = null
            LiveScoreNotificationManager.remove(context)
        }
        pendingWatchSyncGameId = gameId
        pendingWatchSyncNavigateToLive = navigateToLive
        pendingWatchSyncHomeTeam = homeTeam
        pendingWatchSyncAwayTeam = awayTeam
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
        pendingWatchSyncHomeTeam = ""
        pendingWatchSyncAwayTeam = ""
        pendingWatchSyncNavigateToLive = false
    }

    fun confirmPendingWatchSync() {
        val gameId = pendingWatchSyncGameId
        if (gameId.isNullOrBlank()) {
            closeWatchSyncDialog()
            return
        }
        val shouldNavigate = pendingWatchSyncNavigateToLive

        fun completeSync() {
            syncedGameId = gameId
            if (!activeLiveScoreGameId.isNullOrBlank() && activeLiveScoreGameId != gameId) {
                activeLiveScoreGameId = null
                LiveScoreNotificationManager.remove(context)
            }
            closeWatchSyncDialog()
            if (shouldNavigate && currentView != Screen.LiveGame) {
                navigateTo(Screen.LiveGame)
            }
        }

        if (WatchSyncAdLedger.hasViewed(context, gameId)) {
            completeSync()
            return
        }

        showWatchSyncDialog = false
        RewardedAdManager.loadAndShowAd(
            context = context,
            adUnitId = RewardedAdManager.WATCH_SYNC_AD_UNIT,
        ) { rewardEarned ->
            if (rewardEarned) {
                WatchSyncAdLedger.markViewed(context, gameId)
            }
            completeSync()
        }
    }

    fun applyWatchSyncResponse(gameId: String, accepted: Boolean) {
        if (gameId.isBlank()) return

        if (accepted) {
            NotificationManagerCompat.from(context).cancel(gameId.hashCode())
            selectedGameId = gameId
            pendingWatchSyncGameId = gameId
            pendingWatchSyncNavigateToLive = true
            pendingWatchSyncHomeTeam = ""
            pendingWatchSyncAwayTeam = ""
            confirmPendingWatchSync()
        } else if (pendingWatchSyncGameId == gameId) {
            closeWatchSyncDialog()
        }
    }

    fun closeLiveScoreDialog() {
        showLiveScoreDialog = false
        pendingLiveScoreGame = null
    }

    fun openRequiredUpdateStore() {
        val url = requiredUpdateConfig?.storeUrl?.takeIf { it.isNotBlank() } ?: ANDROID_STORE_URL
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.basehaptic.mobile")))
        }
    }

    fun confirmPendingLiveScore() {
        val game = pendingLiveScoreGame ?: run {
            closeLiveScoreDialog()
            return
        }

        fun completeLiveScoreStart() {
            if (!syncedGameId.isNullOrBlank() && syncedGameId != game.id) {
                syncedGameId = null
            }
            context.getSharedPreferences(USER_PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(LiveScoreNotificationManager.KEY_LOCK_SCREEN_LIVE_SCORE_ENABLED, true)
                .apply()
            activeLiveScoreGameId = game.id
            closeLiveScoreDialog()
        }

        if (LiveScoreAdLedger.hasViewed(context, game.id)) {
            completeLiveScoreStart()
            return
        }

        showLiveScoreDialog = false
        RewardedAdManager.loadAndShowAd(
            context = context,
            adUnitId = RewardedAdManager.LIVE_SCORE_AD_UNIT,
        ) { rewardEarned ->
            if (rewardEarned) {
                LiveScoreAdLedger.markViewed(context, game.id)
            }
            completeLiveScoreStart()
        }
    }

    fun toggleHomeLiveScore(game: Game) {
        if (activeLiveScoreGameId == game.id) {
            activeLiveScoreGameId = null
            LiveScoreNotificationManager.remove(context)
            return
        }

        if (game.status != GameStatus.LIVE) {
            showGameNotStartedDialog = true
            return
        }

        pendingLiveScoreGame = game
        showLiveScoreDialog = true
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

    LaunchedEffect(Unit) {
        val config = withContext(Dispatchers.IO) {
            BackendGamesRepository.fetchAppConfig(
                platform = "android",
                version = com.basehaptic.mobile.BuildConfig.VERSION_NAME
            )
        }
        if (config != null && requiresServerUpdate(com.basehaptic.mobile.BuildConfig.VERSION_NAME, config)) {
            requiredUpdateConfig = config
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

    DisposableEffect(lifecycleOwner, selectedTeam) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 매 ON_RESUME 마다 새로 fetch — 같은 날 안에서도 SCHEDULED→LIVE 전이를 반영.
                todayGamesReloadToken += 1
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

        // 첫 진입: 캐시로 즉시 페인트 (이미 채워져 있으면 스킵해 깜빡임 방지)
        if (todayGamesSnapshot.isEmpty()) {
            val cachedGames = runCatching {
                withContext(Dispatchers.IO) {
                    BackendGamesRepository.peekTodayGamesCache(context.applicationContext, selectedTeam)
                }
            }.getOrNull().orEmpty()
            if (cachedGames.isNotEmpty()) {
                todayGamesSnapshot = cachedGames
            }
        }

        // 항상 네트워크에서 갱신 — 점수/상태(LIVE) 변경 반영.
        val freshGames = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.fetchTodayGamesCached(context.applicationContext, selectedTeam)
            }
        }.getOrNull()
        if (freshGames != null) {
            todayGamesSnapshot = freshGames
            todayGamesLoadedDate = LocalDate.now()
        }
    }

    // 폴링 service 는 제거됨 (백엔드 visible push 가 응원팀 경기 시작 알림 대체).
    // Service 는 워치 관람 시작 시점부터만 가동 (아래 LaunchedEffect).

    // Start/stop streaming via service when watch sync or live_score lockscreen card changes.
    LaunchedEffect(activeLiveScoreGameId, syncedGameId, selectedTeam) {
        val previousLiveScore = registeredLiveScoreSessionGameId
        if (!previousLiveScore.isNullOrBlank() && previousLiveScore != activeLiveScoreGameId) {
            LiveViewSessionRegistrar.setActive(
                context = context,
                gameId = previousLiveScore,
                surface = "android",
                active = false,
                myTeam = selectedTeam.name,
            )
        }
        if (!activeLiveScoreGameId.isNullOrBlank() && activeLiveScoreGameId != previousLiveScore) {
            LiveViewSessionRegistrar.setActive(
                context = context,
                gameId = activeLiveScoreGameId!!,
                surface = "android",
                active = true,
                myTeam = selectedTeam.name,
            )
        }
        registeredLiveScoreSessionGameId = activeLiveScoreGameId

        val previousWatch = registeredWatchSessionGameId
        if (!previousWatch.isNullOrBlank() && previousWatch != syncedGameId) {
            LiveViewSessionRegistrar.setActive(
                context = context,
                gameId = previousWatch,
                surface = "wearos",
                active = false,
                myTeam = selectedTeam.name,
            )
        }
        if (!syncedGameId.isNullOrBlank() && syncedGameId != previousWatch) {
            LiveViewSessionRegistrar.setActive(
                context = context,
                gameId = syncedGameId!!,
                surface = "wearos",
                active = true,
                myTeam = selectedTeam.name,
            )
        }
        registeredWatchSessionGameId = syncedGameId
    }

    LaunchedEffect(syncedGameId, activeLiveScoreGameId, selectedTeam) {
        val gameId = syncedGameId ?: activeLiveScoreGameId
        if (!gameId.isNullOrBlank()) {
            val intent = Intent(context, GameSyncForegroundService::class.java).apply {
                action = GameSyncForegroundService.ACTION_START_STREAMING
                putExtra(GameSyncForegroundService.EXTRA_GAME_ID, gameId)
                putExtra(GameSyncForegroundService.EXTRA_SELECTED_TEAM, selectedTeam.name)
                putExtra(GameSyncForegroundService.EXTRA_WATCH_SYNC_ENABLED, syncedGameId == gameId)
                putExtra(GameSyncForegroundService.EXTRA_LIVE_SCORE_ENABLED, activeLiveScoreGameId == gameId)
            }
            context.startService(intent)
        } else {
            val intent = Intent(context, GameSyncForegroundService::class.java).apply {
                action = GameSyncForegroundService.ACTION_STOP_STREAMING
            }
            context.startService(intent)
        }
    }

    // 푸시 알림 탭 → MainActivity 진입 시 NotificationIntentBus 로 게임 정보 전달.
    // 항상 홈으로 착지 → 그 위에 광고 게이트가 있는 워치 관람 팝업. 취소 시 홈 유지.
    // 워치 미설치는 팝업 없이 홈에 머문다.
    val pendingNotificationIntent by NotificationIntentBus.pending.collectAsState()
    LaunchedEffect(pendingNotificationIntent) {
        val pending = pendingNotificationIntent ?: return@LaunchedEffect
        if (showOnboarding) {
            NotificationIntentBus.consume()
            return@LaunchedEffect
        }
        selectedGameId = pending.gameId
        if (currentView != Screen.Home) {
            navigateTo(Screen.Home)
        }
        if (pending.openHomeOnly) {
            NotificationIntentBus.consume()
            return@LaunchedEffect
        }
        val status = withContext(Dispatchers.IO) {
            WatchCompanionStatusRepository.getStatus(context.applicationContext)
        }
        if (status is WatchCompanionStatus.Installed) {
            requestWatchSyncPrompt(
                gameId = pending.gameId,
                navigateToLive = true,
                homeTeam = pending.homeTeam.orEmpty(),
                awayTeam = pending.awayTeam.orEmpty(),
            )
        }
        NotificationIntentBus.consume()
    }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = { team ->
                onOnboardingComplete(team)
            },
            initialSelectedTeam = selectedTeam,
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
                        todayGames = if (BuildConfig.DEBUG && DEBUG_FORCE_EMPTY_HOME_GAMES_FOR_UPDATE_QA) {
                            emptyList()
                        } else {
                            todayGamesSnapshot
                        },
                        syncedGameId = syncedGameId,
                        activeLiveScoreGameId = activeLiveScoreGameId,
                        showUpdateHighlights = BuildConfig.DEBUG && pendingReleaseNote != null,
                        onDismissUpdateHighlights = { pendingReleaseNote = null },
                        onToggleWatchSync = { game ->
                            if (syncedGameId == game.id) {
                                syncedGameId = null
                            } else if (game.status != GameStatus.LIVE) {
                                showGameNotStartedDialog = true
                            } else {
                                requestWatchSyncPrompt(
                                    gameId = game.id,
                                    navigateToLive = false,
                                    homeTeam = game.homeTeamId.teamName,
                                    awayTeam = game.awayTeamId.teamName,
                                )
                            }
                        },
                        onToggleLiveScore = { game ->
                            toggleHomeLiveScore(game)
                        },
                        onSelectGame = { game ->
                            selectedGameId = game.id
                            navigateTo(Screen.LiveGame)
                        }
                    )
                    Screen.LiveGame -> LiveGameScreen(
                        gameId = selectedGameId,
                        syncedGameId = syncedGameId,
                        onSetSyncedGame = { next -> syncedGameId = next },
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
                            RewardedAdManager.loadAndShowAd(
                                context = context,
                                adUnitId = RewardedAdManager.THEME_STORE_AD_UNIT,
                            ) { rewardEarned ->
                                if (!rewardEarned) return@loadAndShowAd
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
                    // TODO(my-team-tab): 컨테이너에 응원 랭킹·향후 팀별 뉴스 임베드.
                    Screen.MyTeam -> MyTeamScreen(
                        selectedTeam = selectedTeam,
                        todayGames = todayGamesSnapshot,
                    )
                }
            }
        }

        if (showWatchSyncDialog && pendingWatchSyncGameId != null) {
            val hasViewedAd = WatchSyncAdLedger.hasViewed(context, pendingWatchSyncGameId!!)
            val suffix = if (hasViewedAd) "" else "광고 관람 후 동기화됩니다."
            val dialogMessage = if (
                pendingWatchSyncHomeTeam.isNotEmpty() && pendingWatchSyncAwayTeam.isNotEmpty()
            ) {
                listOf("$pendingWatchSyncAwayTeam vs $pendingWatchSyncHomeTeam", suffix)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            } else {
                suffix.ifBlank { "워치 동기화를 시작할까요?" }
            }
            AlertDialog(
                onDismissRequest = { closeWatchSyncDialog() },
                title = { Text(text = "워치로 보시겠습니까?") },
                text = { Text(text = dialogMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmPendingWatchSync()
                        }
                    ) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            closeWatchSyncDialog()
                        }
                    ) {
                        Text("취소")
                    }
                }
            )
        }

        if (showLiveScoreDialog && pendingLiveScoreGame != null) {
            val hasViewedAd = LiveScoreAdLedger.hasViewed(context, pendingLiveScoreGame!!.id)
            AlertDialog(
                onDismissRequest = { closeLiveScoreDialog() },
                title = { Text(text = "잠금화면에서 보시겠습니까?") },
                text = {
                    Text(
                        text = if (hasViewedAd) {
                            "잠금화면 라이브 스코어를 시작할까요?"
                        } else {
                            "광고 관람 후 경기 확인 가능합니다."
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmPendingLiveScore()
                        }
                    ) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            closeLiveScoreDialog()
                        }
                    ) {
                        Text("취소")
                    }
                }
            )
        }

        if (showGameNotStartedDialog) {
            AlertDialog(
                onDismissRequest = { showGameNotStartedDialog = false },
                title = { Text(text = "경기 시작 전입니다.") },
                confirmButton = {
                    TextButton(onClick = { showGameNotStartedDialog = false }) {
                        Text("확인")
                    }
                }
            )
        }

        requiredUpdateConfig?.let { config ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(text = config.updateTitle.ifBlank { "업데이트가 필요합니다" }) },
                text = {
                    Text(
                        text = config.updateMessage.ifBlank {
                            "안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요."
                        }
                    )
                },
                confirmButton = {
                    Button(onClick = { openRequiredUpdateStore() }) {
                        Text(text = "업데이트")
                    }
                }
            )
        }

        if (!com.basehaptic.mobile.BuildConfig.DEBUG) {
            pendingReleaseNote?.let { note ->
            com.basehaptic.mobile.ui.components.WhatsNewDialog(
                note = note,
                onConfirm = { pendingReleaseNote = null }
            )
            }
        }
    }

    LaunchedEffect(showOnboarding) {
        if (showOnboarding) return@LaunchedEffect
        val currentVersion = com.basehaptic.mobile.BuildConfig.VERSION_NAME
        if (currentVersion.isEmpty()) return@LaunchedEffect

        if (com.basehaptic.mobile.BuildConfig.DEBUG) {
            pendingReleaseNote = com.basehaptic.mobile.data.model.ReleaseNotes.notes(currentVersion)
                ?: com.basehaptic.mobile.data.model.ReleaseNotes.latest()
            return@LaunchedEffect
        }

        val lastSeen = loadLastSeenUpdateVersion()
        if (lastSeen.isEmpty()) {
            onPersistLastSeenUpdateVersion(currentVersion)
            return@LaunchedEffect
        }
        if (lastSeen == currentVersion) return@LaunchedEffect

        onPersistLastSeenUpdateVersion(currentVersion)

        val note = com.basehaptic.mobile.data.model.ReleaseNotes.notes(currentVersion) ?: return@LaunchedEffect
        pendingReleaseNote = note
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
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets
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

        // TODO(my-team-tab): 1차 콘텐츠는 응원팀 랭킹, 향후 팀별 뉴스 추가.
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
                modifier = Modifier.size(AppSpacing.xl)
            )
        },
        label = {
            Text(
                text = label,
                style = AppFont.tinyBold
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = activeColor,
            selectedTextColor = activeColor,
            unselectedIconColor = Gray500,
            unselectedTextColor = Gray500,
            indicatorColor = activeColor.copy(alpha = 0.08f),
            disabledIconColor = Gray800,
            disabledTextColor = Gray800
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
    object MyTeam : Screen()
}
