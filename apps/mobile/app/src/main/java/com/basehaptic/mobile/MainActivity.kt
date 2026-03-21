package com.basehaptic.mobile

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.screens.*
import com.basehaptic.mobile.ui.theme.BaseHapticTheme
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.wear.WearGameSyncManager
import com.basehaptic.mobile.wear.WearThemeSyncManager
import com.basehaptic.mobile.wear.WearWatchSyncBridge
import java.time.LocalDate
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val SHOW_COMMUNITY_TAB = false
private const val SHOW_STORE_TAB = false
private const val USER_PREFS_NAME = "basehaptic_user_prefs"
private const val KEY_SELECTED_TEAM = "selected_team"

class MainActivity : ComponentActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedTeam = loadSavedTeamOrNull()
        val initialTeam = savedTeam ?: Team.NONE
        val initialShowOnboarding = savedTeam == null

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
                    }
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
    onOnboardingComplete: (Team) -> Unit
) {
    var currentView by remember { mutableStateOf<Screen>(Screen.Home) }
    val navigationHistory = remember { mutableStateListOf<Screen>() }
    var activeTheme by remember { mutableStateOf<ThemeData?>(null) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var syncedGameId by remember { mutableStateOf<String?>(null) }
    var showWatchSyncDialog by remember { mutableStateOf(false) }
    var pendingWatchSyncGameId by remember { mutableStateOf<String?>(null) }
    var pendingWatchSyncNavigateToLive by remember { mutableStateOf(false) }
    val observedMyTeamGameStatus = remember { mutableStateMapOf<String, GameStatus>() }
    val autoPromptedLiveGames = remember { mutableStateMapOf<String, Boolean>() }
    var purchasedThemes by remember { mutableStateOf<List<ThemeData>>(emptyList()) }
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

    LaunchedEffect(selectedTeam) {
        if (selectedTeam != Team.NONE) {
            WearThemeSyncManager.syncThemeToWatch(context, selectedTeam)
        }
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

    LaunchedEffect(selectedTeam) {
        if (selectedTeam == Team.NONE) return@LaunchedEffect
        observedMyTeamGameStatus.clear()
        autoPromptedLiveGames.clear()

        while (currentCoroutineContext().isActive) {
            val today = LocalDate.now()
            val shouldFreezePolling =
                shouldFreezeTodayGames(
                    games = todayGamesSnapshot,
                    loadedDate = todayGamesLoadedDate,
                    today = today
                )
            if (shouldFreezePolling) {
                delay(60_000L)
                continue
            }

            val fetchedGames = runCatching {
                withContext(Dispatchers.IO) {
                    BackendGamesRepository.fetchTodayGamesCached(
                        context = context.applicationContext,
                        selectedTeam = selectedTeam,
                        forceRefresh = true
                    )
                }
            }.getOrNull()

            // Keep home feed in sync with backend game state updates (score/status/inning).
            if (fetchedGames != null) {
                todayGamesSnapshot = fetchedGames
                todayGamesLoadedDate = today
            }
            val games = fetchedGames.orEmpty()

            val myTeamGames = games.filter { it.isMyTeam }
            for (game in myTeamGames) {
                val previous = observedMyTeamGameStatus[game.id]
                observedMyTeamGameStatus[game.id] = game.status

                val becameLive = game.status == GameStatus.LIVE && previous != GameStatus.LIVE
                val alreadyPrompted = autoPromptedLiveGames[game.id] == true
                val isSynced = syncedGameId == game.id
                if (becameLive && !alreadyPrompted && !isSynced) {
                    autoPromptedLiveGames[game.id] = true
                    selectedGameId = game.id
                    WearGameSyncManager.sendWatchSyncPrompt(
                        context = context,
                        gameId = game.id,
                        homeTeam = game.homeTeam,
                        awayTeam = game.awayTeam,
                        myTeam = selectedTeam.name
                    )
                }
            }

            val pollDelayMs = when {
                fetchedGames == null -> 10_000L
                games.any { it.status == GameStatus.LIVE } -> 5_000L
                games.isNotEmpty() && games.all { isTerminalStatus(it.status) } -> 60_000L
                else -> 30_000L
            }
            delay(pollDelayMs)
        }
    }

    LaunchedEffect(syncedGameId, selectedTeam) {
        val targetGameId = syncedGameId
        if (targetGameId.isNullOrBlank()) return@LaunchedEffect

        var cursor = 0L
        var localEvents: List<BackendGamesRepository.LiveEvent> = emptyList()
        var lastWatchSignature = ""
        var lastSentEventCursor = 0L
        val reconnectDelaysMs = listOf(1000L, 2000L, 5000L, 10000L)
        var reconnectAttempt = 0

        suspend fun pushStateToWatch(state: BackendGamesRepository.LiveGameState) {
            val latestEventType =
                localEvents.firstNotNullOfOrNull { mapToWatchEventType(it.type) }
                    ?: mapToWatchEventType(state.lastEventType)
            val signature = listOf(
                state.gameId,
                state.status.name,
                state.inning,
                state.homeScore.toString(),
                state.awayScore.toString(),
                state.ball.toString(),
                state.strike.toString(),
                state.out.toString(),
                state.baseFirst.toString(),
                state.baseSecond.toString(),
                state.baseThird.toString(),
                latestEventType.orEmpty()
            ).joinToString("|")

            if (signature != lastWatchSignature) {
                WearGameSyncManager.sendGameData(
                    context = context,
                    gameId = state.gameId,
                    homeTeam = state.homeTeam,
                    awayTeam = state.awayTeam,
                    homeScore = state.homeScore,
                    awayScore = state.awayScore,
                    status = state.status.name,
                    inning = state.inning,
                    ball = state.ball,
                    strike = state.strike,
                    out = state.out,
                    baseFirst = state.baseFirst,
                    baseSecond = state.baseSecond,
                    baseThird = state.baseThird,
                    pitcher = state.pitcher,
                    batter = state.batter,
                    myTeam = resolveMyTeamName(selectedTeam, state),
                    eventType = null
                )
                lastWatchSignature = signature
            }
        }

        suspend fun applyIncomingEvents(
            incoming: List<BackendGamesRepository.LiveEvent>,
            sendHaptics: Boolean
        ) {
            if (incoming.isEmpty()) return

            val sorted = incoming.sortedBy { it.cursor }
            if (sendHaptics) {
                sorted
                    .filter { it.cursor > lastSentEventCursor }
                    .forEach { event ->
                        mapToWatchEventType(event.type)?.let { mapped ->
                            WearGameSyncManager.sendHapticEvent(context, mapped, event.cursor)
                        }
                        lastSentEventCursor = max(lastSentEventCursor, event.cursor)
                    }
            } else {
                lastSentEventCursor = max(lastSentEventCursor, sorted.maxOfOrNull { it.cursor } ?: 0L)
            }

            cursor = max(cursor, sorted.maxOfOrNull { it.cursor } ?: cursor)
            localEvents = (sorted + localEvents)
                .distinctBy { it.cursor }
                .sortedByDescending { it.cursor }
                .take(80)
        }

        while (currentCoroutineContext().isActive) {
            runCatching {
                BackendGamesRepository.streamGame(targetGameId).collect { message ->
                    when (message) {
                        BackendGamesRepository.LiveStreamMessage.Connected -> {
                            reconnectAttempt = 0
                        }

                        BackendGamesRepository.LiveStreamMessage.Closed -> {
                            throw IllegalStateException("game stream closed")
                        }

                        is BackendGamesRepository.LiveStreamMessage.Error -> {
                            throw message.throwable
                        }

                        is BackendGamesRepository.LiveStreamMessage.Events -> {
                            applyIncomingEvents(message.items, sendHaptics = true)
                        }

                        is BackendGamesRepository.LiveStreamMessage.State -> {
                            pushStateToWatch(message.state)
                        }

                        is BackendGamesRepository.LiveStreamMessage.Pong -> Unit
                    }
                }
            }

            if (!currentCoroutineContext().isActive) break
            val delayMs = reconnectDelaysMs[reconnectAttempt.coerceAtMost(reconnectDelaysMs.lastIndex)]
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(reconnectDelaysMs.lastIndex)
            delay(delayMs)
        }
    }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = { team ->
                onOnboardingComplete(team)
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
                        activeTheme = activeTheme,
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
                        activeTheme = activeTheme,
                        gameId = selectedGameId,
                        syncedGameId = syncedGameId,
                        onBack = { navigateBack() }
                    )
                    Screen.Community -> CommunityScreen(
                        selectedTeam = selectedTeam,
                        activeTheme = activeTheme
                    )
                    Screen.Store -> ThemeStoreScreen(
                        selectedTeam = selectedTeam,
                        activeTheme = activeTheme,
                        onApplyTheme = { activeTheme = it },
                        onPurchaseTheme = { theme ->
                            purchasedThemes = purchasedThemes + theme
                        },
                        purchasedThemes = purchasedThemes
                    )
                    Screen.Settings -> SettingsScreen(
                        selectedTeam = selectedTeam,
                        onChangeTeam = { team ->
                            onTeamChanged(team) // ? 蹂寃?利됱떆 ???뚮쭏?먮룄 諛섏쁺
                        },
                        purchasedThemes = purchasedThemes,
                        activeTheme = activeTheme,
                        onSelectTheme = { activeTheme = it },
                        onOpenWatchTest = { navigateTo(Screen.WatchTest) }
                    )
                    Screen.WatchTest -> WatchTestScreen(
                        selectedTeam = selectedTeam,
                        onBack = { navigateBack() }
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
}

private fun mapToWatchEventType(type: String?): String? {
    val normalized = type?.uppercase() ?: return null
    return when (normalized) {
        "BALL",
        "STRIKE",
        "OUT",
        "DOUBLE_PLAY",
        "TRIPLE_PLAY",
        "HIT",
        "HOMERUN",
        "SCORE",
        "WALK",
        "STEAL" -> normalized
        "SAC_FLY_SCORE" -> "SCORE"
        "TAG_UP_ADVANCE" -> "OUT"
        else -> null
    }
}

private fun resolveMyTeamName(
    selectedTeam: Team,
    state: BackendGamesRepository.LiveGameState
): String {
    return when {
        selectedTeam != Team.NONE -> selectedTeam.name
        state.homeTeamId != Team.NONE -> state.homeTeamId.name
        state.awayTeamId != Team.NONE -> state.awayTeamId.name
        else -> "DEFAULT"
    }
}

private fun shouldFreezeTodayGames(
    games: List<Game>,
    loadedDate: LocalDate?,
    today: LocalDate
): Boolean {
    if (loadedDate != today) return false
    if (games.isEmpty()) return false
    return games.all { isTerminalStatus(it.status) }
}

private fun isTerminalStatus(status: GameStatus): Boolean {
    return when (status) {
        GameStatus.FINISHED,
        GameStatus.CANCELED,
        GameStatus.POSTPONED -> true
        GameStatus.LIVE,
        GameStatus.SCHEDULED -> false
    }
}
