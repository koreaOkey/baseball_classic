package com.basehaptic.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.wear.compose.material.MaterialTheme

/**
 * 현재 워치 팀 테마를 어디서든 접근 가능하게 하는 CompositionLocal
 */
val LocalWatchTeamTheme = compositionLocalOf { WatchTeamThemes.DEFAULT }

/**
 * 동적 테마 적용 Wear OS Composable
 * - teamName에 따라 워치 전체 색상이 변경됨
 * - 모바일 앱에서 Data Layer를 통해 teamName을 전달받음
 */
@Composable
fun BaseHapticWatchTheme(
    teamName: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    val watchTeamTheme = WatchTeamThemes.getThemeForTeam(teamName)
    val wearColors = watchTeamTheme.toWearColors()

    CompositionLocalProvider(LocalWatchTeamTheme provides watchTeamTheme) {
        MaterialTheme(
            colors = wearColors,
            typography = Typography,
            content = content
        )
    }
}
