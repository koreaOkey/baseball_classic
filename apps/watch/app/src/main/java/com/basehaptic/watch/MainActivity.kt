package com.basehaptic.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
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
    // TODO: Replace with actual data from DataLayer
    var gameData by remember { mutableStateOf<GameData?>(getMockGameData()) }
    
    // 팀 이름으로 테마 결정 (모바일 앱에서 Data Layer로 전달)
    val teamName = gameData?.myTeamName ?: "DEFAULT"
    
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
