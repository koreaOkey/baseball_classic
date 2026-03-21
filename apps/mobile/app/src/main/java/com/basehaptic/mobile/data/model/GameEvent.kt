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
    WALK,
    OUT,
    DOUBLE_PLAY,
    TRIPLE_PLAY,
    STEAL,
    HIT,
    HOMERUN,
    SCORE;

    companion object {
        fun getHapticPattern(type: EventType): String {
            return when (type) {
                BALL -> "tap"
                STRIKE -> "tap-tap"
                WALK -> "tap-soft-tap"
                OUT -> "tap-tap-tap"
                DOUBLE_PLAY -> "tap-pause-tap"
                TRIPLE_PLAY -> "tap-pause-tap-pause-tap"
                STEAL -> "quick-double-tap"
                HIT -> "double-tap"
                HOMERUN -> "triple-tap"
                SCORE -> "tap-long"
            }
        }
    }
}
