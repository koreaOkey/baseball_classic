package com.basehaptic.mobile.data.model

data class GameEvent(
    val id: String,
    val type: EventType,
    val description: String,
    val time: String,
    val hapticPattern: String? = null
)

enum class EventType {
    BALL,
    STRIKE,
    OUT,
    DOUBLE_PLAY,
    TRIPLE_PLAY,
    HIT,
    HOMERUN,
    SCORE;

    companion object {
        fun getHapticPattern(type: EventType): String {
            return when (type) {
                BALL -> "tap"
                STRIKE -> "tap-tap"
                OUT -> "tap-tap-tap"
                DOUBLE_PLAY -> "tap-pause-tap"
                TRIPLE_PLAY -> "tap-pause-tap-pause-tap"
                HIT -> "double-tap"
                HOMERUN -> "triple-tap"
                SCORE -> "tap-long"
            }
        }
    }
}
