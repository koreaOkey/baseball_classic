package com.basehaptic.mobile.data.model

import androidx.compose.ui.graphics.Color

enum class Team(val teamName: String, val color: Color) {
    NONE("없음", Color(0xFF3B82F6)),
    DOOSAN("베어스", Color(0xFF131230)),
    LG("트윈스", Color(0xFFC30452)),
    KIWOOM("히어로즈", Color(0xFF820024)),
    SAMSUNG("라이온즈", Color(0xFF074CA1)),
    LOTTE("자이언츠", Color(0xFF041E42)),
    SSG("랜더스", Color(0xFFCE0E2D)),
    KT("위즈", Color(0xFF000000)),
    HANWHA("이글스", Color(0xFFFF6600)),
    KIA("타이거즈", Color(0xFFEA0029)),
    NC("다이노스", Color(0xFF315288));

    companion object {
        fun fromString(value: String): Team {
            return values().find { it.name == value } ?: NONE
        }
    }
}
