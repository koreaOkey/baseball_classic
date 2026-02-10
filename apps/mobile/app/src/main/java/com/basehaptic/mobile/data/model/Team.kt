package com.basehaptic.mobile.data.model

import androidx.compose.ui.graphics.Color

enum class Team(val teamName: String, val color: Color) {
    NONE("없음", Color(0xFF3B82F6)),
    DOOSAN("두산 베어스", Color(0xFF131230)),
    LG("LG 트윈스", Color(0xFFC30452)),
    KIWOOM("키움 히어로즈", Color(0xFF820024)),
    SAMSUNG("삼성 라이온즈", Color(0xFF074CA1)),
    LOTTE("롯데 자이언츠", Color(0xFF041E42)),
    SSG("SSG 랜더스", Color(0xFFCE0E2D)),
    KT("KT 위즈", Color(0xFF000000)),
    HANWHA("한화 이글스", Color(0xFFFF6600)),
    KIA("KIA 타이거즈", Color(0xFFEA0029)),
    NC("NC 다이노스", Color(0xFF315288));

    companion object {
        fun fromString(value: String): Team {
            return values().find { it.name == value } ?: NONE
        }
    }
}

