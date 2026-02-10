package com.basehaptic.watch.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

/**
 * 워치용 팀별 테마 프리셋
 * 모바일 앱에서 전달받은 팀 정보로 워치 UI 전체 색상을 변경합니다.
 */
data class WatchTeamTheme(
    val teamName: String,
    val primary: Color,
    val primaryDark: Color,
    val secondary: Color,
    val accent: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

object WatchTeamThemes {
    val DEFAULT = WatchTeamTheme(
        teamName = "DEFAULT",
        primary = Blue500,
        primaryDark = Blue600,
        secondary = Blue400,
        accent = Blue400,
        gradientStart = Blue500,
        gradientEnd = Blue600
    )

    val DOOSAN = WatchTeamTheme(
        teamName = "DOOSAN",
        primary = Color(0xFF131230),
        primaryDark = Color(0xFF0A0918),
        secondary = Color(0xFFEF4444),
        accent = Color(0xFF60A5FA),
        gradientStart = Color(0xFF131230),
        gradientEnd = Color(0xFF1E1C4B)
    )

    val LG = WatchTeamTheme(
        teamName = "LG",
        primary = Color(0xFFC30452),
        primaryDark = Color(0xFF8E023B),
        secondary = Color(0xFF000000),
        accent = Color(0xFFF472B6),
        gradientStart = Color(0xFFC30452),
        gradientEnd = Color(0xFF8E023B)
    )

    val KIWOOM = WatchTeamTheme(
        teamName = "KIWOOM",
        primary = Color(0xFF820024),
        primaryDark = Color(0xFF5C001A),
        secondary = Color(0xFFD4A843),
        accent = Color(0xFFFCA5A5),
        gradientStart = Color(0xFF820024),
        gradientEnd = Color(0xFF5C001A)
    )

    val SAMSUNG = WatchTeamTheme(
        teamName = "SAMSUNG",
        primary = Color(0xFF074CA1),
        primaryDark = Color(0xFF053678),
        secondary = Color(0xFFFFFFFF),
        accent = Color(0xFF93C5FD),
        gradientStart = Color(0xFF074CA1),
        gradientEnd = Color(0xFF053678)
    )

    val LOTTE = WatchTeamTheme(
        teamName = "LOTTE",
        primary = Color(0xFF041E42),
        primaryDark = Color(0xFF021230),
        secondary = Color(0xFFE31B23),
        accent = Color(0xFF93C5FD),
        gradientStart = Color(0xFF041E42),
        gradientEnd = Color(0xFF021230)
    )

    val SSG = WatchTeamTheme(
        teamName = "SSG",
        primary = Color(0xFFCE0E2D),
        primaryDark = Color(0xFF960A20),
        secondary = Color(0xFFFFD700),
        accent = Color(0xFFFCA5A5),
        gradientStart = Color(0xFFCE0E2D),
        gradientEnd = Color(0xFF960A20)
    )

    val KT = WatchTeamTheme(
        teamName = "KT",
        primary = Color(0xFF1A1A1A),
        primaryDark = Color(0xFF000000),
        secondary = Color(0xFFED1C24),
        accent = Color(0xFFA3A3A3),
        gradientStart = Color(0xFF1A1A1A),
        gradientEnd = Color(0xFF000000)
    )

    val HANWHA = WatchTeamTheme(
        teamName = "HANWHA",
        primary = Color(0xFFFF6600),
        primaryDark = Color(0xFFCC5200),
        secondary = Color(0xFF000000),
        accent = Color(0xFFFDBA74),
        gradientStart = Color(0xFFFF6600),
        gradientEnd = Color(0xFFCC5200)
    )

    val KIA = WatchTeamTheme(
        teamName = "KIA",
        primary = Color(0xFFEA0029),
        primaryDark = Color(0xFFB5001F),
        secondary = Color(0xFF000000),
        accent = Color(0xFFFCA5A5),
        gradientStart = Color(0xFFEA0029),
        gradientEnd = Color(0xFFB5001F)
    )

    val NC = WatchTeamTheme(
        teamName = "NC",
        primary = Color(0xFF315288),
        primaryDark = Color(0xFF213A61),
        secondary = Color(0xFFCFB53B),
        accent = Color(0xFF93C5FD),
        gradientStart = Color(0xFF315288),
        gradientEnd = Color(0xFF213A61)
    )

    fun getThemeForTeam(teamName: String): WatchTeamTheme = when (teamName.uppercase()) {
        "DOOSAN" -> DOOSAN
        "LG" -> LG
        "KIWOOM" -> KIWOOM
        "SAMSUNG" -> SAMSUNG
        "LOTTE" -> LOTTE
        "SSG" -> SSG
        "KT" -> KT
        "HANWHA" -> HANWHA
        "KIA" -> KIA
        "NC" -> NC
        else -> DEFAULT
    }
}

/**
 * WatchTeamTheme을 Wear OS Colors로 변환
 */
fun WatchTeamTheme.toWearColors(): Colors = Colors(
    primary = this.primary,
    primaryVariant = this.primaryDark,
    secondary = this.secondary,
    secondaryVariant = this.primaryDark,
    error = Red500,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onError = Color.White,
    background = Gray950,
    onBackground = Color.White,
    surface = Gray900,
    onSurface = Color.White,
    onSurfaceVariant = Gray400
)

