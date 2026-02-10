package com.basehaptic.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.basehaptic.mobile.data.model.Team

/**
 * 팀별 테마 프리셋 - 각 팀의 대표 색상을 기반으로
 * primary, secondary, accent 등 전체 Color Scheme을 정의합니다.
 */
data class TeamTheme(
    val team: Team,
    val primary: Color,
    val primaryDark: Color,
    val secondary: Color,
    val accent: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val navIndicator: Color
)

object TeamThemes {
    val NONE = TeamTheme(
        team = Team.NONE,
        primary = Blue500,
        primaryDark = Blue700,
        secondary = Blue400,
        accent = Blue200,
        gradientStart = Blue500,
        gradientEnd = Blue700,
        navIndicator = Blue500
    )

    val DOOSAN = TeamTheme(
        team = Team.DOOSAN,
        primary = Color(0xFF131230),
        primaryDark = Color(0xFF0A0918),
        secondary = Color(0xFFEF4444),
        accent = Color(0xFF60A5FA),
        gradientStart = Color(0xFF131230),
        gradientEnd = Color(0xFF1E1C4B),
        navIndicator = Color(0xFFEF4444)
    )

    val LG = TeamTheme(
        team = Team.LG,
        primary = Color(0xFFC30452),
        primaryDark = Color(0xFF8E023B),
        secondary = Color(0xFF000000),
        accent = Color(0xFFF472B6),
        gradientStart = Color(0xFFC30452),
        gradientEnd = Color(0xFF8E023B),
        navIndicator = Color(0xFFC30452)
    )

    val KIWOOM = TeamTheme(
        team = Team.KIWOOM,
        primary = Color(0xFF820024),
        primaryDark = Color(0xFF5C001A),
        secondary = Color(0xFFD4A843),
        accent = Color(0xFFFCA5A5),
        gradientStart = Color(0xFF820024),
        gradientEnd = Color(0xFF5C001A),
        navIndicator = Color(0xFFD4A843)
    )

    val SAMSUNG = TeamTheme(
        team = Team.SAMSUNG,
        primary = Color(0xFF074CA1),
        primaryDark = Color(0xFF053678),
        secondary = Color(0xFFFFFFFF),
        accent = Color(0xFF93C5FD),
        gradientStart = Color(0xFF074CA1),
        gradientEnd = Color(0xFF053678),
        navIndicator = Color(0xFF074CA1)
    )

    val LOTTE = TeamTheme(
        team = Team.LOTTE,
        primary = Color(0xFF041E42),
        primaryDark = Color(0xFF021230),
        secondary = Color(0xFFE31B23),
        accent = Color(0xFF93C5FD),
        gradientStart = Color(0xFF041E42),
        gradientEnd = Color(0xFF021230),
        navIndicator = Color(0xFFE31B23)
    )

    val SSG = TeamTheme(
        team = Team.SSG,
        primary = Color(0xFFCE0E2D),
        primaryDark = Color(0xFF960A20),
        secondary = Color(0xFFFFD700),
        accent = Color(0xFFFCA5A5),
        gradientStart = Color(0xFFCE0E2D),
        gradientEnd = Color(0xFF960A20),
        navIndicator = Color(0xFFCE0E2D)
    )

    val KT = TeamTheme(
        team = Team.KT,
        primary = Color(0xFF000000),
        primaryDark = Color(0xFF1A1A1A),
        secondary = Color(0xFFED1C24),
        accent = Color(0xFFA3A3A3),
        gradientStart = Color(0xFF1A1A1A),
        gradientEnd = Color(0xFF000000),
        navIndicator = Color(0xFFED1C24)
    )

    val HANWHA = TeamTheme(
        team = Team.HANWHA,
        primary = Color(0xFFFF6600),
        primaryDark = Color(0xFFCC5200),
        secondary = Color(0xFF000000),
        accent = Color(0xFFFDBA74),
        gradientStart = Color(0xFFFF6600),
        gradientEnd = Color(0xFFCC5200),
        navIndicator = Color(0xFFFF6600)
    )

    val KIA = TeamTheme(
        team = Team.KIA,
        primary = Color(0xFFEA0029),
        primaryDark = Color(0xFFB5001F),
        secondary = Color(0xFF000000),
        accent = Color(0xFFFCA5A5),
        gradientStart = Color(0xFFEA0029),
        gradientEnd = Color(0xFFB5001F),
        navIndicator = Color(0xFFEA0029)
    )

    val NC = TeamTheme(
        team = Team.NC,
        primary = Color(0xFF315288),
        primaryDark = Color(0xFF213A61),
        secondary = Color(0xFFCFB53B),
        accent = Color(0xFF93C5FD),
        gradientStart = Color(0xFF315288),
        gradientEnd = Color(0xFF213A61),
        navIndicator = Color(0xFF315288)
    )

    fun getThemeForTeam(team: Team): TeamTheme = when (team) {
        Team.NONE -> NONE
        Team.DOOSAN -> DOOSAN
        Team.LG -> LG
        Team.KIWOOM -> KIWOOM
        Team.SAMSUNG -> SAMSUNG
        Team.LOTTE -> LOTTE
        Team.SSG -> SSG
        Team.KT -> KT
        Team.HANWHA -> HANWHA
        Team.KIA -> KIA
        Team.NC -> NC
    }
}

/**
 * TeamTheme을 Material3 ColorScheme으로 변환
 */
fun TeamTheme.toColorScheme(): ColorScheme = darkColorScheme(
    primary = this.primary,
    onPrimary = Color.White,
    primaryContainer = this.primaryDark,
    onPrimaryContainer = Color.White,
    secondary = this.secondary,
    onSecondary = Color.White,
    secondaryContainer = this.secondary.copy(alpha = 0.3f),
    onSecondaryContainer = Color.White,
    tertiary = this.accent,
    onTertiary = Gray950,
    background = Gray950,
    onBackground = Gray100,
    surface = Gray900,
    onSurface = Gray100,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray400,
    error = Red500,
    onError = Color.White,
    outline = Gray700,
    outlineVariant = Gray800
)

