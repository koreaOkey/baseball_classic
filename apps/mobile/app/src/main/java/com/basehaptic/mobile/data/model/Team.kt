package com.basehaptic.mobile.data.model

import androidx.compose.ui.graphics.Color

enum class Team(val teamName: String, val color: Color, val clubName: String) {
    NONE("없음", Color(0xFF3B82F6), "없음"),
    DOOSAN("베어스", Color(0xFF131230), "두산"),
    LG("트윈스", Color(0xFFC30452), "LG"),
    KIWOOM("히어로즈", Color(0xFF820024), "키움"),
    SAMSUNG("라이온즈", Color(0xFF074CA1), "삼성"),
    LOTTE("자이언츠", Color(0xFF041E42), "롯데"),
    SSG("랜더스", Color(0xFFCE0E2D), "SSG"),
    KT("위즈", Color(0xFF000000), "KT"),
    HANWHA("이글스", Color(0xFFFF6600), "한화"),
    KIA("타이거즈", Color(0xFFEA0029), "KIA"),
    NC("다이노스", Color(0xFF315288), "NC");

    fun displayName(style: TeamDisplayNameStyle): String =
        if (style == TeamDisplayNameStyle.TEAM) clubName else teamName

    companion object {
        fun fromString(value: String): Team {
            return values().find { it.name == value } ?: NONE
        }
    }
}

enum class TeamDisplayNameStyle {
    TEAM,
    MASCOT;

    companion object {
        fun fromString(value: String?): TeamDisplayNameStyle {
            return values().find { it.name == value } ?: TEAM
        }
    }
}
