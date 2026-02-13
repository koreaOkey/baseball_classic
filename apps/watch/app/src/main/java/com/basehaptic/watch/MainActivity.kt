package com.basehaptic.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.basehaptic.watch.data.BaseStatus
import com.basehaptic.watch.data.GameData
import com.basehaptic.watch.data.getMockGameData
import com.basehaptic.watch.ui.components.LiveGameScreen
import com.basehaptic.watch.ui.components.NoGameScreen
import com.basehaptic.watch.ui.theme.BaseHapticWatchTheme

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
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val teamName = if (syncedTeamName != "DEFAULT") syncedTeamName else (gameData?.myTeamName ?: "DEFAULT")

    BaseHapticWatchTheme(teamName = teamName) {
        Scaffold(
            timeText = {
                TimeText()
            },
            vignette = {
                Vignette(vignettePosition = VignettePosition.TopAndBottom)
            }
        ) {
            if (gameData != null) {
                LiveGameScreen(gameData = gameData!!)
            } else {
                NoGameScreen()
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
