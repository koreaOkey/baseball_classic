package com.basehaptic.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.basehaptic.mobile.data.model.GameWeatherSummary
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.StadiumCheerThemeStore
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.TeamDisplayNameStyle
import com.basehaptic.mobile.data.model.ThemeCategory
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.data.model.ThemeStore
import com.basehaptic.mobile.data.ThemeRepository
import com.basehaptic.mobile.push.BaseHapticMessagingService
import com.basehaptic.mobile.push.LiveScoreNotificationManager
import com.basehaptic.mobile.push.NotificationIntentBus
import com.basehaptic.mobile.push.NotificationChannels
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
import com.basehaptic.mobile.ui.components.RewardedAdFormat
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
private const val KEY_TEAM_DISPLAY_NAME_STYLE = "team_display_name_style"
private const val KEY_TEAM_DISPLAY_NAME_PROMPT_SEEN = "team_display_name_prompt_seen"
private const val KEY_UNLOCKED_THEME_IDS = "unlocked_theme_ids"
private const val KEY_ACTIVE_THEME_ID = "active_theme_id"
// 워치 페이스 테마와 무관하게 응원 시 풀스크린에 적용될 테마. ThemeStore와 별도로 StadiumCheerThemeStore에서 매칭.
private const val KEY_ACTIVE_CHEER_THEME_ID = "active_cheer_theme_id"
private const val KEY_LAST_SEEN_UPDATE_VERSION = "last_seen_update_version"
private const val KEY_LAST_SEEN_FEATURE_GUIDE_VERSION = "last_seen_feature_guide_version"
private const val KEY_LAST_NOTIFICATION_SETTINGS_CHECK_VERSION = "last_notification_settings_check_version"
private const val KEY_SYNCED_GAME_ID = "synced_game_id"
private const val KEY_SYNCED_MY_TEAM = "synced_my_team"
private const val KEY_ACTIVE_LIVE_SCORE_GAME_ID = "active_live_score_game_id"
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

internal fun requiresServerUpdate(currentVersion: String, config: BackendGamesRepository.AppConfig): Boolean {
    val minVersion = config.minSupportedVersion.takeIf { it.isNotBlank() }
    val latestVersion = config.latestVersion.takeIf { it.isNotBlank() }
    if (minVersion != null && compareVersionNames(currentVersion, minVersion) < 0) return true
    return config.forceUpdate &&
        latestVersion != null &&
        compareVersionNames(currentVersion, latestVersion) < 0
}

private fun isNotificationRuntimePermissionMissing(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
}

private fun isLiveScoreNotificationPermissionBlocked(context: Context): Boolean {
    return isNotificationRuntimePermissionMissing(context) ||
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun liveScoreNotificationSettingsIssue(context: Context): String? {
    NotificationChannels.ensureCreated(context)
    if (isLiveScoreNotificationPermissionBlocked(context)) {
        return "앱 알림이 꺼져 있어 득점·홈런 알림을 잠금화면에서 받을 수 없습니다."
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

    val manager = context.getSystemService(NotificationManager::class.java) ?: return null
    val channel = manager.getNotificationChannel(NotificationChannels.LIVE_SCORE_ALERTS_ID)
        ?: return "라이브 스코어 주요 이벤트 알림 채널을 확인해야 합니다."

    return when {
        channel.importance < NotificationManager.IMPORTANCE_HIGH ->
            "라이브 스코어 주요 이벤트 알림이 무음 또는 낮은 중요도로 설정되어 있습니다."
        !channel.shouldVibrate() ->
            "라이브 스코어 주요 이벤트 알림의 진동이 꺼져 있습니다."
        channel.lockscreenVisibility == Notification.VISIBILITY_SECRET ->
            "라이브 스코어 주요 이벤트 알림이 잠금화면에서 숨김으로 설정되어 있습니다."
        else -> null
    }
}

private fun openLiveScoreNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.LIVE_SCORE_ALERTS_ID)
        }
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
}

private fun loadSavedGameId(context: Context, key: String): String? {
    return context.getSharedPreferences(USER_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(key, null)
        ?.takeIf { it.isNotBlank() }
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

    private fun loadTeamDisplayNameStyle(): TeamDisplayNameStyle {
        return TeamDisplayNameStyle.fromString(
            getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_TEAM_DISPLAY_NAME_STYLE, null)
        )
    }

    private fun persistTeamDisplayNameStyle(style: TeamDisplayNameStyle) {
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_TEAM_DISPLAY_NAME_STYLE, style.name)
            .apply()
        TeamSubscriptionRegistrar.syncIfNeeded(this)
    }

    private fun loadTeamDisplayNamePromptSeen(): Boolean {
        return getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_TEAM_DISPLAY_NAME_PROMPT_SEEN, false)
    }

    private fun persistTeamDisplayNamePromptSeen() {
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TEAM_DISPLAY_NAME_PROMPT_SEEN, true)
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

    private fun loadLastSeenFeatureGuideVersion(): String {
        return getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LAST_SEEN_FEATURE_GUIDE_VERSION, null)
            .orEmpty()
    }

    private fun persistLastSeenFeatureGuideVersion(version: String) {
        if (version.isBlank()) return
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SEEN_FEATURE_GUIDE_VERSION, version)
            .apply()
    }

    private fun loadLastNotificationSettingsCheckVersion(): String {
        return getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LAST_NOTIFICATION_SETTINGS_CHECK_VERSION, null)
            .orEmpty()
    }

    private fun persistLastNotificationSettingsCheckVersion(version: String) {
        if (version.isBlank()) return
        getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NOTIFICATION_SETTINGS_CHECK_VERSION, version)
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
        val initialShowOnboarding = savedTeam == null
        val initialTeamDisplayNameStyle = loadTeamDisplayNameStyle()
        val initialTeamDisplayNamePromptSeen = loadTeamDisplayNamePromptSeen()
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
            var teamDisplayNameStyle by remember { mutableStateOf(initialTeamDisplayNameStyle) }
            var teamDisplayNamePromptSeen by remember { mutableStateOf(initialTeamDisplayNamePromptSeen) }
            var showOnboarding by remember { mutableStateOf(initialShowOnboarding) }
            
            BaseHapticTheme(
                selectedTeam = selectedTeam,
                teamDisplayNameStyle = teamDisplayNameStyle
            ) {
                BaseHapticApp(
                    selectedTeam = selectedTeam,
                    onTeamChanged = { team ->
                        selectedTeam = team
                        persistSelectedTeam(team)
                    },
                    teamDisplayNameStyle = teamDisplayNameStyle,
                    onTeamDisplayNameStyleChanged = { style ->
                        teamDisplayNameStyle = style
                        persistTeamDisplayNameStyle(style)
                    },
                    teamDisplayNamePromptSeen = teamDisplayNamePromptSeen,
                    onTeamDisplayNamePromptSeen = {
                        teamDisplayNamePromptSeen = true
                        persistTeamDisplayNamePromptSeen()
                    },
                    showOnboarding = showOnboarding,
                    onOnboardingComplete = { team, style ->
                        selectedTeam = team
                        teamDisplayNameStyle = style
                        persistSelectedTeam(team)
                        persistTeamDisplayNameStyle(style)
                        teamDisplayNamePromptSeen = true
                        persistTeamDisplayNamePromptSeen()
                        showOnboarding = false
                    },
                    isExistingUserAtLaunch = savedTeam != null,
                    initialUnlockedThemeIds = initialUnlockedIds,
                    initialActiveTheme = initialActiveTheme,
                    initialActiveCheerTheme = initialActiveCheerTheme,
                    onPersistUnlockedThemeIds = ::persistUnlockedThemeIds,
                    onPersistActiveThemeId = ::persistActiveThemeId,
                    onPersistActiveCheerThemeId = ::persistActiveCheerThemeId,
                    loadLastSeenUpdateVersion = ::loadLastSeenUpdateVersion,
                    onPersistLastSeenUpdateVersion = ::persistLastSeenUpdateVersion,
                    loadLastSeenFeatureGuideVersion = ::loadLastSeenFeatureGuideVersion,
                    onPersistLastSeenFeatureGuideVersion = ::persistLastSeenFeatureGuideVersion,
                    loadLastNotificationSettingsCheckVersion = ::loadLastNotificationSettingsCheckVersion,
                    onPersistLastNotificationSettingsCheckVersion = ::persistLastNotificationSettingsCheckVersion,
                    onRequestNotificationPermission = { PushSetup.requestNotificationPermission(this) },
                )
            }
        }
    }
}

@Composable
fun BaseHapticApp(
    selectedTeam: Team,
    onTeamChanged: (Team) -> Unit,
    teamDisplayNameStyle: TeamDisplayNameStyle,
    onTeamDisplayNameStyleChanged: (TeamDisplayNameStyle) -> Unit,
    teamDisplayNamePromptSeen: Boolean,
    onTeamDisplayNamePromptSeen: () -> Unit,
    showOnboarding: Boolean,
    onOnboardingComplete: (Team, TeamDisplayNameStyle) -> Unit,
    isExistingUserAtLaunch: Boolean = false,
    initialUnlockedThemeIds: Set<String> = setOf("default"),
    initialActiveTheme: ThemeData? = null,
    initialActiveCheerTheme: ThemeData? = null,
    onPersistUnlockedThemeIds: (Set<String>) -> Unit = {},
    onPersistActiveThemeId: (String?) -> Unit = {},
    onPersistActiveCheerThemeId: (String?) -> Unit = {},
    loadLastSeenUpdateVersion: () -> String = { "" },
    onPersistLastSeenUpdateVersion: (String) -> Unit = {},
    loadLastSeenFeatureGuideVersion: () -> String = { "" },
    onPersistLastSeenFeatureGuideVersion: (String) -> Unit = {},
    loadLastNotificationSettingsCheckVersion: () -> String = { "" },
    onPersistLastNotificationSettingsCheckVersion: (String) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
) {
    val authState by AuthManager.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentView by remember { mutableStateOf<Screen>(Screen.Home) }
    val navigationHistory = remember { mutableStateListOf<Screen>() }
    var activeTheme by remember { mutableStateOf(initialActiveTheme) }
    var activeCheerTheme by remember { mutableStateOf(initialActiveCheerTheme) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var syncedGameId by remember { mutableStateOf(loadSavedGameId(context, KEY_SYNCED_GAME_ID)) }
    var activeLiveScoreGameId by remember { mutableStateOf(loadSavedGameId(context, KEY_ACTIVE_LIVE_SCORE_GAME_ID)) }
    var registeredLiveScoreSessionGameId by remember { mutableStateOf<String?>(null) }
    var registeredWatchSessionGameId by remember { mutableStateOf<String?>(null) }
    var showWatchSyncDialog by remember { mutableStateOf(false) }
    var showLiveScoreDialog by remember { mutableStateOf(false) }
    var showLiveScoreNotificationPermissionDialog by remember { mutableStateOf(false) }
    var resumeLiveScoreAfterNotificationSettings by remember { mutableStateOf(false) }
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
    var showFeatureGuide by remember { mutableStateOf(false) }
    var showExistingTeamDisplayNamePrompt by remember { mutableStateOf(false) }
    var pendingTeamDisplayNameStyle by remember(teamDisplayNameStyle) { mutableStateOf(teamDisplayNameStyle) }
    var pendingNotificationSettingsIssue by remember { mutableStateOf<String?>(null) }
    var todayGamesSnapshot by remember(selectedTeam) { mutableStateOf<List<Game>>(emptyList()) }
    var todayGamesLoadedDate by remember(selectedTeam) { mutableStateOf<LocalDate?>(null) }
    var todayGamesReloadToken by remember(selectedTeam) { mutableStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val game = pendingLiveScoreGame
        if (granted && game != null && game.status == GameStatus.LIVE) {
            showLiveScoreDialog = true
        } else if (!granted) {
            pendingLiveScoreGame = null
        }
    }
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

    fun showNotificationSettingsPromptIfNeeded(currentVersion: String) {
        if (currentVersion.isBlank()) return
        if (loadLastNotificationSettingsCheckVersion() == currentVersion) return
        onPersistLastNotificationSettingsCheckVersion(currentVersion)
        pendingNotificationSettingsIssue = liveScoreNotificationSettingsIssue(context)
    }

    fun showFeatureGuideIfNeeded(currentVersion: String): Boolean {
        if (currentVersion.isBlank()) return false
        if (loadLastSeenFeatureGuideVersion() == currentVersion) return false
        showFeatureGuide = true
        return true
    }

    fun dismissFeatureGuide() {
        val currentVersion = com.basehaptic.mobile.BuildConfig.VERSION_NAME
        onPersistLastSeenFeatureGuideVersion(currentVersion)
        showFeatureGuide = false
        showNotificationSettingsPromptIfNeeded(currentVersion)
    }

    fun applyTeamDisplayNameStyle(style: TeamDisplayNameStyle) {
        onTeamDisplayNameStyleChanged(style)
        onTeamDisplayNamePromptSeen()
        com.basehaptic.mobile.wear.WearSettingsSyncManager.syncTeamDisplayNameStyleToWatch(context, style)
        TeamSubscriptionRegistrar.syncIfNeeded(context)
        if (authState is AuthState.LoggedIn) {
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ThemeRepository.saveTeamDisplayNameStyle(style.name)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
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
            format = RewardedAdFormat.REWARDED_INTERSTITIAL,
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
            format = RewardedAdFormat.REWARDED_INTERSTITIAL,
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

        if (isLiveScoreNotificationPermissionBlocked(context)) {
            pendingLiveScoreGame = game
            showLiveScoreNotificationPermissionDialog = true
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
                if (settings.teamDisplayNameStyle != null) {
                    val serverStyle = TeamDisplayNameStyle.fromString(settings.teamDisplayNameStyle)
                    if (serverStyle != teamDisplayNameStyle) {
                        onTeamDisplayNameStyleChanged(serverStyle)
                    }
                    onTeamDisplayNamePromptSeen()
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

    LaunchedEffect(selectedTeam, teamDisplayNameStyle) {
        if (selectedTeam != Team.NONE) {
            WearThemeSyncManager.syncThemeToWatch(context, selectedTeam, activeTheme?.id)
            com.basehaptic.mobile.wear.WearSettingsSyncManager.syncTeamDisplayNameStyleToWatch(context, teamDisplayNameStyle)
        }
    }

    // 워치에 사용자 설정 초기 동기화 (영상 알림 토글, 라이브 알림 마스터 스위치)
    LaunchedEffect(teamDisplayNameStyle) {
        val prefs = context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
        val videoEnabled = prefs.getBoolean("event_video_enabled", true)
        val liveHapticEnabled = prefs.getBoolean("live_haptic_enabled", true)
        com.basehaptic.mobile.wear.WearSettingsSyncManager.syncEventVideoEnabledToWatch(context, videoEnabled)
        com.basehaptic.mobile.wear.WearSettingsSyncManager.syncLiveHapticEnabledToWatch(context, liveHapticEnabled)
        com.basehaptic.mobile.wear.WearSettingsSyncManager.syncTeamDisplayNameStyleToWatch(context, teamDisplayNameStyle)
    }

    LaunchedEffect(showOnboarding, teamDisplayNamePromptSeen, selectedTeam) {
        if (!showOnboarding && selectedTeam != Team.NONE && !teamDisplayNamePromptSeen) {
            pendingTeamDisplayNameStyle = teamDisplayNameStyle
            showExistingTeamDisplayNamePrompt = true
        }
    }

    DisposableEffect(
        lifecycleOwner,
        selectedTeam,
        resumeLiveScoreAfterNotificationSettings,
        pendingLiveScoreGame
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 매 ON_RESUME 마다 새로 fetch — 같은 날 안에서도 SCHEDULED→LIVE 전이를 반영.
                todayGamesReloadToken += 1
                if (resumeLiveScoreAfterNotificationSettings) {
                    resumeLiveScoreAfterNotificationSettings = false
                    val game = pendingLiveScoreGame
                    if (!isLiveScoreNotificationPermissionBlocked(context) &&
                        game != null &&
                        game.status == GameStatus.LIVE
                    ) {
                        showLiveScoreDialog = true
                    } else if (isLiveScoreNotificationPermissionBlocked(context)) {
                        pendingLiveScoreGame = null
                    }
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
            todayGamesSnapshot = hydrateMissingGameWeather(
                games = freshGames,
                previousGames = todayGamesSnapshot
            )
            todayGamesLoadedDate = LocalDate.now()
        }
    }

    // 폴링 service 는 제거됨 (백엔드 visible push 가 응원팀 경기 시작 알림 대체).
    // Service 는 워치 관람 시작 시점부터만 가동 (아래 LaunchedEffect).

    // Start/stop streaming via service when watch sync or live_score lockscreen card changes.
    LaunchedEffect(activeLiveScoreGameId, syncedGameId, selectedTeam) {
        context.getSharedPreferences(USER_PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SYNCED_GAME_ID, syncedGameId.orEmpty())
            .putString(KEY_SYNCED_MY_TEAM, selectedTeam.name)
            .putString(KEY_ACTIVE_LIVE_SCORE_GAME_ID, activeLiveScoreGameId.orEmpty())
            .putBoolean(
                LiveScoreNotificationManager.KEY_LOCK_SCREEN_LIVE_SCORE_ENABLED,
                !activeLiveScoreGameId.isNullOrBlank()
            )
            .apply()

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
            onComplete = { team, style ->
                onOnboardingComplete(team, style)
            },
            initialSelectedTeam = selectedTeam,
            initialDisplayNameStyle = teamDisplayNameStyle,
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
                        showUpdateHighlights = showFeatureGuide,
                        onDismissUpdateHighlights = { dismissFeatureGuide() },
                        onToggleWatchSync = { game ->
                            if (syncedGameId == game.id) {
                                syncedGameId = null
                            } else if (game.status != GameStatus.LIVE) {
                                showGameNotStartedDialog = true
                            } else {
                                requestWatchSyncPrompt(
                                    gameId = game.id,
                                    navigateToLive = false,
                                    homeTeam = game.homeTeamId.displayName(teamDisplayNameStyle),
                                    awayTeam = game.awayTeamId.displayName(teamDisplayNameStyle),
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
                        teamDisplayNameStyle = teamDisplayNameStyle,
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
                        onChangeTeamDisplayNameStyle = { style ->
                            applyTeamDisplayNameStyle(style)
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

        if (showExistingTeamDisplayNamePrompt && selectedTeam != Team.NONE) {
            TeamDisplayNameStyleDialog(
                team = selectedTeam,
                selectedStyle = pendingTeamDisplayNameStyle,
                onStyleSelected = { pendingTeamDisplayNameStyle = it },
                onConfirm = {
                    applyTeamDisplayNameStyle(pendingTeamDisplayNameStyle)
                    showExistingTeamDisplayNamePrompt = false
                },
                onDismiss = {
                    applyTeamDisplayNameStyle(teamDisplayNameStyle)
                    showExistingTeamDisplayNamePrompt = false
                }
            )
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

        if (showLiveScoreNotificationPermissionDialog) {
            AlertDialog(
                onDismissRequest = {
                    showLiveScoreNotificationPermissionDialog = false
                    pendingLiveScoreGame = null
                },
                title = { Text(text = "알림 허용이 필요합니다") },
                text = {
                    Text(text = "경기 확인을 위해서는 알림 허용이 필요합니다.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLiveScoreNotificationPermissionDialog = false
                            if (isNotificationRuntimePermissionMissing(context)) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                resumeLiveScoreAfterNotificationSettings = true
                                openLiveScoreNotificationSettings(context)
                            }
                        }
                    ) {
                        Text("알림 허용")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLiveScoreNotificationPermissionDialog = false
                            pendingLiveScoreGame = null
                        }
                    ) {
                        Text("취소")
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

        if (
            requiredUpdateConfig == null &&
            pendingReleaseNote == null &&
            !showFeatureGuide &&
            pendingNotificationSettingsIssue != null
        ) {
            AlertDialog(
                onDismissRequest = { pendingNotificationSettingsIssue = null },
                title = { Text(text = "알림 설정을 확인해 주세요") },
                text = {
                    Text(
                        text = "경기 시작, 진행상황 알림을 위해 알림 허용이 필요합니다."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingNotificationSettingsIssue = null
                            if (isNotificationRuntimePermissionMissing(context)) {
                                onRequestNotificationPermission()
                            } else {
                                openLiveScoreNotificationSettings(context)
                            }
                        }
                    ) {
                        Text("알림 설정")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingNotificationSettingsIssue = null }) {
                        Text("나중에")
                    }
                }
            )
        }

        pendingReleaseNote?.let { note ->
            com.basehaptic.mobile.ui.components.WhatsNewDialog(
                note = note,
                onConfirm = {
                    pendingReleaseNote = null
                    val currentVersion = com.basehaptic.mobile.BuildConfig.VERSION_NAME
                    if (!showFeatureGuideIfNeeded(currentVersion)) {
                        showNotificationSettingsPromptIfNeeded(currentVersion)
                    }
                }
            )
        }
    }

    LaunchedEffect(showOnboarding) {
        if (showOnboarding) return@LaunchedEffect
        val currentVersion = com.basehaptic.mobile.BuildConfig.VERSION_NAME
        if (currentVersion.isEmpty()) return@LaunchedEffect

        val lastSeen = loadLastSeenUpdateVersion()
        if (lastSeen.isEmpty()) {
            onPersistLastSeenUpdateVersion(currentVersion)
            if (isExistingUserAtLaunch) {
                val note = com.basehaptic.mobile.data.model.ReleaseNotes.notes(currentVersion)
                if (note != null) {
                    pendingReleaseNote = note
                } else if (!showFeatureGuideIfNeeded(currentVersion)) {
                    showNotificationSettingsPromptIfNeeded(currentVersion)
                }
            } else {
                if (!showFeatureGuideIfNeeded(currentVersion)) {
                    showNotificationSettingsPromptIfNeeded(currentVersion)
                }
            }
            return@LaunchedEffect
        }
        if (lastSeen == currentVersion) {
            if (!showFeatureGuideIfNeeded(currentVersion)) {
                showNotificationSettingsPromptIfNeeded(currentVersion)
            }
            return@LaunchedEffect
        }

        onPersistLastSeenUpdateVersion(currentVersion)

        val note = com.basehaptic.mobile.data.model.ReleaseNotes.notes(currentVersion)
        if (note != null) {
            pendingReleaseNote = note
        } else if (!showFeatureGuideIfNeeded(currentVersion)) {
            showNotificationSettingsPromptIfNeeded(currentVersion)
        }
    }
}

private fun BackendGamesRepository.GameWeatherHourly.toGameStartWeatherSummary(): GameWeatherSummary? {
    val item = items.firstOrNull { it.isGameStartForecast } ?: items.firstOrNull() ?: return null
    val timeLabel = item.timeLabel.ifBlank { gameStartTime.orEmpty() }.ifBlank { null }
    val condition = item.condition.ifBlank { "예보" }
    val displayText = buildString {
        append(stadiumShortName.ifBlank { stadiumName })
        timeLabel?.let { append(" · ").append(it).append(" 기준") }
        append(" · ").append(condition)
        item.temperatureC?.let { append(" ").append(it).append("°") }
        item.precipitationProbability?.let { append(" · 강수 ").append(it).append("%") }
    }
    return GameWeatherSummary(
        stadiumCode = stadiumCode,
        stadiumName = stadiumName,
        stadiumShortName = stadiumShortName,
        forecastDate = item.forecastDate.ifBlank { null },
        forecastTime = item.forecastTime.ifBlank { null },
        forecastTimeLabel = timeLabel,
        condition = condition,
        temperatureC = item.temperatureC,
        precipitationProbability = item.precipitationProbability,
        precipitationType = item.precipitationType,
        windSpeedMps = item.windSpeedMps,
        isIndoor = false,
        displayText = displayText
    )
}

private suspend fun hydrateMissingGameWeather(
    games: List<Game>,
    previousGames: List<Game>
): List<Game> = withContext(Dispatchers.IO) {
    val previousWeatherByGameId = previousGames
        .mapNotNull { game -> game.weather?.let { weather -> game.id to weather } }
        .toMap()

    games.map { game ->
        if (game.weather != null || (game.status != GameStatus.SCHEDULED && game.status != GameStatus.LIVE)) {
            game
        } else {
            val preservedWeather = previousWeatherByGameId[game.id]
            val fetchedWeather = preservedWeather ?: runCatching {
                BackendGamesRepository.fetchGameHourlyWeather(
                    gameId = game.id,
                    targetDate = LocalDate.now()
                )?.toGameStartWeatherSummary()
            }.getOrNull()
            if (fetchedWeather != null) game.copy(weather = fetchedWeather) else game
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
