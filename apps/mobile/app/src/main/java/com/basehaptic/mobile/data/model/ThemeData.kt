package com.basehaptic.mobile.data.model

import androidx.compose.ui.graphics.Color

data class ThemeData(
    val id: String,
    val teamId: Team,
    val name: String,
    val colors: ThemeColors,
    val animation: String? = null
)

data class ThemeColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color
)

