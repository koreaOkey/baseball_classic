package com.basehaptic.mobile.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.ui.graphics.vector.ImageVector

enum class EventNotificationChannel {
    WATCH,
    LOCK_SCREEN
}

object EventFilterGate {
    fun isAllowed(
        context: android.content.Context,
        eventType: String?,
        channel: EventNotificationChannel = EventNotificationChannel.WATCH
    ): Boolean {
        val type = eventType?.uppercase() ?: return true
        if (type.isBlank()) return true
        val option = EventFilterOption.optionForEventType(type) ?: run {
            if (type in setOf("OUT", "DOUBLE_PLAY", "TRIPLE_PLAY")) return false
            return true
        }
        val key = option.storageKey(channel)
        val prefs = context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
        return if (prefs.contains(key)) prefs.getBoolean(key, option.defaultEnabled(channel))
        else option.defaultEnabled(channel)
    }
}

data class EventFilterOption(
    val id: String,
    val watchStorageKey: String,
    val lockScreenStorageKey: String,
    val title: String,
    val icon: ImageVector,
    val watchDefaultEnabled: Boolean,
    val lockScreenDefaultEnabled: Boolean
) {
    fun storageKey(channel: EventNotificationChannel): String = when (channel) {
        EventNotificationChannel.WATCH -> watchStorageKey
        EventNotificationChannel.LOCK_SCREEN -> lockScreenStorageKey
    }

    fun defaultEnabled(channel: EventNotificationChannel): Boolean = when (channel) {
        EventNotificationChannel.WATCH -> watchDefaultEnabled
        EventNotificationChannel.LOCK_SCREEN -> lockScreenDefaultEnabled
    }

    companion object {
        val all: List<EventFilterOption> = listOf(
            EventFilterOption(
                id = "score",
                watchStorageKey = "event_filter_score_enabled",
                lockScreenStorageKey = "lock_screen_event_filter_score_enabled",
                title = "득점",
                icon = Icons.Default.Flag,
                watchDefaultEnabled = true,
                lockScreenDefaultEnabled = true
            ),
            EventFilterOption(
                id = "homerun",
                watchStorageKey = "event_filter_homerun_enabled",
                lockScreenStorageKey = "lock_screen_event_filter_homerun_enabled",
                title = "홈런",
                icon = Icons.Default.Star,
                watchDefaultEnabled = true,
                lockScreenDefaultEnabled = true
            ),
            EventFilterOption(
                id = "hit",
                watchStorageKey = "event_filter_hit_enabled",
                lockScreenStorageKey = "lock_screen_event_filter_hit_enabled",
                title = "안타",
                icon = Icons.Default.SportsBaseball,
                watchDefaultEnabled = true,
                lockScreenDefaultEnabled = true
            ),
            EventFilterOption(
                id = "walk",
                watchStorageKey = "event_filter_walk_enabled",
                lockScreenStorageKey = "lock_screen_event_filter_walk_enabled",
                title = "볼넷·출루",
                icon = Icons.Default.DirectionsWalk,
                watchDefaultEnabled = false,
                lockScreenDefaultEnabled = false
            ),
            EventFilterOption(
                id = "steal",
                watchStorageKey = "event_filter_steal_enabled",
                lockScreenStorageKey = "lock_screen_event_filter_steal_enabled",
                title = "도루·주루",
                icon = Icons.Default.DirectionsRun,
                watchDefaultEnabled = false,
                lockScreenDefaultEnabled = false
            ),
            EventFilterOption(
                id = "pitch_count",
                watchStorageKey = "event_filter_pitch_count_enabled",
                lockScreenStorageKey = "lock_screen_event_filter_pitch_count_enabled",
                title = "스트라이크·볼",
                icon = Icons.Default.Dashboard,
                watchDefaultEnabled = false,
                lockScreenDefaultEnabled = false
            ),
            EventFilterOption(
                id = "pitcher_change",
                watchStorageKey = "event_filter_pitcher_change_enabled",
                lockScreenStorageKey = "lock_screen_event_filter_pitcher_change_enabled",
                title = "투수 교체",
                icon = Icons.Default.SwapHoriz,
                watchDefaultEnabled = false,
                lockScreenDefaultEnabled = false
            )
        )

        fun optionForEventType(eventType: String): EventFilterOption? = when (eventType.uppercase()) {
            "SCORE", "SAC_FLY_SCORE" -> all.first { it.id == "score" }
            "HOMERUN" -> all.first { it.id == "homerun" }
            "HIT" -> all.first { it.id == "hit" }
            "WALK", "HIT_BY_PITCH" -> all.first { it.id == "walk" }
            "STEAL", "TAG_UP_ADVANCE" -> all.first { it.id == "steal" }
            "BALL", "STRIKE" -> all.first { it.id == "pitch_count" }
            "PITCHER_CHANGE" -> all.first { it.id == "pitcher_change" }
            else -> null
        }

        fun currentValues(
            context: android.content.Context,
            channel: EventNotificationChannel
        ): Map<String, Boolean> {
            val prefs = context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
            return all.associate { option ->
                val key = option.storageKey(channel)
                key to if (prefs.contains(key)) prefs.getBoolean(key, option.defaultEnabled(channel))
                else option.defaultEnabled(channel)
            }
        }
    }
}
