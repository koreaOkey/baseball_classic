package com.basehaptic.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.basehaptic.watch.data.BaseStatus
import com.basehaptic.watch.data.GameData
import com.basehaptic.watch.ui.components.LiveGameScreen
import com.basehaptic.watch.ui.components.NoGameScreen
import com.basehaptic.watch.ui.theme.BaseHapticWatchTheme
import com.basehaptic.watch.ui.theme.LocalWatchTeamTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            WatchApp()
        }
    }
}

@Composable
fun WatchApp() {
    val context = LocalContext.current

    var syncedTeamName by remember {
        mutableStateOf(
            context.getSharedPreferences(
                DataLayerListenerService.PREFS_NAME,
                Context.MODE_PRIVATE
            ).getString(
                DataLayerListenerService.PREF_KEY_TEAM_NAME,
                "DEFAULT"
            ) ?: "DEFAULT"
        )
    }

    var gameData by remember { mutableStateOf(readGameDataFromPrefs(context)) }
    var latestEvent by remember { mutableStateOf(readLatestEventFromPrefs(context)) }
    var isHomeRunTransitionVisible by remember { mutableStateOf(false) }
    var homeRunTransitionToken by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(DataLayerListenerService.ACTION_THEME_UPDATED)
            addAction(DataLayerListenerService.ACTION_GAME_UPDATED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    DataLayerListenerService.ACTION_THEME_UPDATED -> {
                        syncedTeamName = context.getSharedPreferences(
                            DataLayerListenerService.PREFS_NAME,
                            Context.MODE_PRIVATE
                        ).getString(
                            DataLayerListenerService.PREF_KEY_TEAM_NAME,
                            "DEFAULT"
                        ) ?: "DEFAULT"
                    }
                    DataLayerListenerService.ACTION_GAME_UPDATED -> {
                        gameData = readGameDataFromPrefs(context)
                        latestEvent = readLatestEventFromPrefs(context)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val teamName = if (syncedTeamName != "DEFAULT") syncedTeamName else (gameData?.myTeamName ?: "DEFAULT")

    LaunchedEffect(latestEvent?.timestamp) {
        val event = latestEvent ?: return@LaunchedEffect
        if (event.type.uppercase() != "HOMERUN") return@LaunchedEffect
        if (System.currentTimeMillis() - event.timestamp > EVENT_OVERLAY_FRESHNESS_MS) return@LaunchedEffect
        homeRunTransitionToken = event.timestamp
    }

    LaunchedEffect(homeRunTransitionToken) {
        val token = homeRunTransitionToken ?: return@LaunchedEffect
        isHomeRunTransitionVisible = true
        delay(HOMERUN_SCREEN_DURATION_MS)
        if (homeRunTransitionToken == token) {
            isHomeRunTransitionVisible = false
        }
    }

    BaseHapticWatchTheme(teamName = teamName) {
        Scaffold(
            timeText = {
                TimeText()
            },
            vignette = {
                Vignette(vignettePosition = VignettePosition.TopAndBottom)
            }
        ) {
            AnimatedContent(
                targetState = isHomeRunTransitionVisible,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 1.04f))
                },
                label = "home_run_transition"
            ) { showHomeRun ->
                if (showHomeRun) {
                    HomeRunTransitionScreen()
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (gameData != null) {
                            LiveGameScreen(gameData = gameData!!)
                        } else {
                            NoGameScreen()
                        }
                        WatchEventOverlay(latestEvent = latestEvent)
                    }
                }
            }
        }
    }
}

private fun readGameDataFromPrefs(context: Context): GameData? {
    val prefs = context.getSharedPreferences(
        DataLayerListenerService.GAME_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val gameId = prefs.getString(DataLayerListenerService.KEY_GAME_ID, "") ?: ""
    if (gameId.isBlank()) return null

    return GameData(
        gameId = gameId,
        homeTeam = prefs.getString(DataLayerListenerService.KEY_HOME_TEAM, "") ?: "",
        awayTeam = prefs.getString(DataLayerListenerService.KEY_AWAY_TEAM, "") ?: "",
        homeScore = prefs.getInt(DataLayerListenerService.KEY_HOME_SCORE, 0),
        awayScore = prefs.getInt(DataLayerListenerService.KEY_AWAY_SCORE, 0),
        inning = prefs.getString(DataLayerListenerService.KEY_INNING, "") ?: "",
        isLive = true,
        ballCount = prefs.getInt(DataLayerListenerService.KEY_BALL, 0),
        strikeCount = prefs.getInt(DataLayerListenerService.KEY_STRIKE, 0),
        outCount = prefs.getInt(DataLayerListenerService.KEY_OUT, 0),
        bases = BaseStatus(
            first = prefs.getBoolean(DataLayerListenerService.KEY_BASE_FIRST, false),
            second = prefs.getBoolean(DataLayerListenerService.KEY_BASE_SECOND, false),
            third = prefs.getBoolean(DataLayerListenerService.KEY_BASE_THIRD, false)
        ),
        pitcher = prefs.getString(DataLayerListenerService.KEY_PITCHER, "") ?: "",
        batter = prefs.getString(DataLayerListenerService.KEY_BATTER, "") ?: "",
        scoreDiff = 0,
        myTeamName = prefs.getString(DataLayerListenerService.KEY_MY_TEAM, "") ?: ""
    )
}

private data class WatchEventInfo(
    val type: String,
    val timestamp: Long
)

private data class WatchEventUi(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private const val EVENT_OVERLAY_DURATION_MS = 2200L
private const val EVENT_OVERLAY_FRESHNESS_MS = 5000L
private const val HOMERUN_SCREEN_DURATION_MS = 2000L

private fun readLatestEventFromPrefs(context: Context): WatchEventInfo? {
    val prefs = context.getSharedPreferences(
        DataLayerListenerService.GAME_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val type = prefs.getString(DataLayerListenerService.KEY_LAST_EVENT_TYPE, "") ?: ""
    val timestamp = prefs.getLong(DataLayerListenerService.KEY_LAST_EVENT_AT, 0L)
    if (type.isBlank() || timestamp <= 0L) return null
    return WatchEventInfo(type = type, timestamp = timestamp)
}

@Composable
private fun WatchEventOverlay(latestEvent: WatchEventInfo?) {
    var visibleEvent by remember { mutableStateOf<WatchEventInfo?>(null) }

    LaunchedEffect(latestEvent?.timestamp) {
        val event = latestEvent ?: return@LaunchedEffect
        if (System.currentTimeMillis() - event.timestamp > EVENT_OVERLAY_FRESHNESS_MS) {
            return@LaunchedEffect
        }
        visibleEvent = event
        delay(EVENT_OVERLAY_DURATION_MS)
        if (visibleEvent?.timestamp == event.timestamp) {
            visibleEvent = null
        }
    }

    val event = visibleEvent ?: return
    val eventUi = eventUiFor(event.type) ?: return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = eventUi.icon,
                contentDescription = eventUi.label,
                tint = eventUi.color
            )
            Text(
                text = eventUi.label,
                color = Color.White
            )
        }
    }
}


@Composable
private fun HomeRunTransitionScreen() {
    val watchTheme = LocalWatchTeamTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        watchTheme.secondary.copy(alpha = 0.95f),
                        watchTheme.primaryDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SportsBaseball,
                contentDescription = "HOME RUN",
                tint = Color.White
            )
            Text(
                text = "HOME RUN",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

private fun eventUiFor(type: String): WatchEventUi? {
    return when (type.uppercase()) {
        "HIT" -> WatchEventUi("HIT", Icons.Default.Bolt, Color(0xFF22C55E))
        "SCORE" -> WatchEventUi("SCORE", Icons.Default.EmojiEvents, Color(0xFFEAB308))
        "OUT" -> WatchEventUi("OUT", Icons.Default.HighlightOff, Color(0xFFEF4444))
        else -> null
    }
}
