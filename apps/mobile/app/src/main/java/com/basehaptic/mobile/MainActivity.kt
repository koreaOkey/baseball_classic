package com.basehaptic.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.screens.*
import com.basehaptic.mobile.ui.theme.BaseHapticTheme
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.wear.WearGameSyncManager
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val SHOW_COMMUNITY_TAB = false
private const val SHOW_STORE_TAB = false

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // ??猷⑦듃?먯꽌 ?좏깮 ? ?곹깭瑜?愿由ы븯怨??섏쐞 ?붾㈃?쇰줈 ?꾨떖
            var selectedTeam by remember { mutableStateOf(Team.DOOSAN) }
            var showOnboarding by remember { mutableStateOf(false) }
            
            BaseHapticTheme(selectedTeam = selectedTeam) {
                BaseHapticApp(
                    selectedTeam = selectedTeam,
                    onTeamChanged = { selectedTeam = it },
                    showOnboarding = showOnboarding,
                    onOnboardingComplete = { team ->
                        selectedTeam = team
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
    var activeTheme by remember { mutableStateOf<ThemeData?>(null) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var syncedGameId by remember { mutableStateOf<String?>(null) }
    var showWatchSyncDialog by remember { mutableStateOf(false) }
    var pendingWatchSyncGameId by remember { mutableStateOf<String?>(null) }
    var purchasedThemes by remember { mutableStateOf<List<ThemeData>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(syncedGameId, selectedTeam) {
        val targetGameId = syncedGameId
        if (targetGameId.isNullOrBlank()) return@LaunchedEffect

        var cursor = 0L
        var localEvents: List<BackendGamesRepository.LiveEvent> = emptyList()
        var lastWatchSignature = ""
        var lastSentEventCursor = 0L
        var realtimeWindowReady = false

        while (currentCoroutineContext().isActive) {
            val fetchedState = runCatching {
                withContext(Dispatchers.IO) {
                    BackendGamesRepository.fetchGameState(targetGameId)
                }
            }.getOrNull()

            val fetchedEvents = runCatching {
                withContext(Dispatchers.IO) {
                    BackendGamesRepository.fetchGameEvents(gameId = targetGameId, after = cursor, limit = 200)
                }
            }.getOrNull()

            val newItems = fetchedEvents?.items.orEmpty()
            if (newItems.isNotEmpty()) {
                if (realtimeWindowReady) {
                    newItems
                        .sortedBy { it.cursor }
                        .filter { it.cursor > lastSentEventCursor }
                        .forEach { event ->
                            mapToWatchEventType(event.type)?.let { mapped ->
                                WearGameSyncManager.sendHapticEvent(context, mapped)
                            }
                            lastSentEventCursor = max(lastSentEventCursor, event.cursor)
                        }
                }

                localEvents = (newItems + localEvents)
                    .distinctBy { it.cursor }
                    .sortedByDescending { it.cursor }
                    .take(80)
            }

            if (fetchedEvents != null) {
                val nextCursor = fetchedEvents.nextCursor
                    ?: newItems.maxOfOrNull { it.cursor }
                    ?: cursor
                cursor = max(cursor, nextCursor)

                if (!realtimeWindowReady && fetchedEvents.nextCursor == null) {
                    realtimeWindowReady = true
                    lastSentEventCursor = max(
                        lastSentEventCursor,
                        localEvents.maxOfOrNull { it.cursor } ?: 0L
                    )
                }
            }

            val state = fetchedState
            if (state != null) {
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

            delay(2500)
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
                        onNavigate = { currentView = it }
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
                        activeTheme = activeTheme,
                        syncedGameId = syncedGameId,
                        onSelectGame = { game ->
                            selectedGameId = game.id
                            if (game.status == GameStatus.LIVE && syncedGameId != game.id) {
                                pendingWatchSyncGameId = game.id
                                showWatchSyncDialog = true
                            } else {
                                currentView = Screen.LiveGame
                            }
                        }
                    )
                    Screen.LiveGame -> LiveGameScreen(
                        activeTheme = activeTheme,
                        gameId = selectedGameId,
                        syncedGameId = syncedGameId,
                        onBack = { currentView = Screen.Home }
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
                        onOpenWatchTest = { currentView = Screen.WatchTest }
                    )
                    Screen.WatchTest -> WatchTestScreen(
                        selectedTeam = selectedTeam,
                        onBack = { currentView = Screen.Settings }
                    )
                }
            }
        }

        if (showWatchSyncDialog && pendingWatchSyncGameId != null) {
            AlertDialog(
                onDismissRequest = {
                    showWatchSyncDialog = false
                    pendingWatchSyncGameId = null
                    currentView = Screen.LiveGame
                },
                title = { Text(text = "워치 동기화") },
                text = { Text(text = "워치로 경기 관람하시겠습니까?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            syncedGameId = pendingWatchSyncGameId
                            showWatchSyncDialog = false
                            pendingWatchSyncGameId = null
                            currentView = Screen.LiveGame
                        }
                    ) {
                        Text("예")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showWatchSyncDialog = false
                            pendingWatchSyncGameId = null
                            currentView = Screen.LiveGame
                        }
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
        "SCORE" -> normalized
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
        selectedTeam == state.homeTeamId -> state.homeTeam
        selectedTeam == state.awayTeamId -> state.awayTeam
        selectedTeam != Team.NONE -> selectedTeam.teamName
        else -> state.homeTeam
    }
}
