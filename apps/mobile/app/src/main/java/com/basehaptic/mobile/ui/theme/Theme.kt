package com.basehaptic.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.basehaptic.mobile.data.model.Team

/**
 * 현재 팀 테마를 어디서든 접근 가능하게 하는 CompositionLocal
 */
val LocalTeamTheme = compositionLocalOf { TeamThemes.NONE }

/**
 * 동적 테마 적용 Composable
 * - selectedTeam에 따라 앱 전체 색상이 변경됨
 */
@Composable
fun BaseHapticTheme(
    selectedTeam: Team = Team.NONE,
    content: @Composable () -> Unit
) {
    val teamTheme = TeamThemes.getThemeForTeam(selectedTeam)
    val colorScheme = teamTheme.toColorScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalTeamTheme provides teamTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
