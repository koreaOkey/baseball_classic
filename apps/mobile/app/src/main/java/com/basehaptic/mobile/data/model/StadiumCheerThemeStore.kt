package com.basehaptic.mobile.data.model

import androidx.compose.ui.graphics.Color

// TODO(stadium-cheer): 활성화 시 응원 발화 풀스크린(워치)에 선택된 테마 컬러/배경/햅틱 패턴 반영.
// 현재는 상점 mock 데이터만 제공. 구매·적용 콜백은 기존 ThemeStore와 동일 흐름 재사용.
object StadiumCheerThemeStore {
    val allThemes: List<ThemeData> = listOf(
        ThemeData(
            id = "cheer_default",
            name = "기본 응원",
            category = ThemeCategory.FREE,
            colors = ThemeColors(
                primary = Color(0xFF3B82F6),
                secondary = Color(0xFF1E3A8A),
                accent = Color(0xFFFFFFFF)
            ),
            previewImage = "theme_preview_default"
        ),
        ThemeData(
            id = "cheer_team_color_flash",
            name = "팀 컬러 플래시",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFDC141E),
                secondary = Color(0xFFFFFFFF),
                accent = Color(0xFFFFD700)
            ),
            previewImage = "theme_preview_baseball_love"
        ),
        ThemeData(
            id = "cheer_spotlight",
            name = "스포트라이트",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF111827),
                secondary = Color(0xFFFBBF24),
                accent = Color(0xFFFFFFFF)
            ),
            previewImage = "theme_preview_charcoal_black"
        ),
        ThemeData(
            id = "cheer_festival",
            name = "축제 모드",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFFF6B6B),
                secondary = Color(0xFFFFE66D),
                accent = Color(0xFF4ECDC4)
            ),
            previewImage = "theme_preview_sunset_orange"
        ),
        ThemeData(
            id = "cheer_neon_pulse",
            name = "네온 펄스",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF00D9FF),
                secondary = Color(0xFFFF00FF),
                accent = Color(0xFFFFFFFF)
            ),
            previewImage = "theme_preview_slate_blue"
        ),
        ThemeData(
            id = "cheer_sunrise_chant",
            name = "선라이즈 챈트",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFFF9966),
                secondary = Color(0xFFFF5E62),
                accent = Color(0xFFFFFFFF)
            ),
            previewImage = "theme_preview_fire_red"
        ),
    )
}
