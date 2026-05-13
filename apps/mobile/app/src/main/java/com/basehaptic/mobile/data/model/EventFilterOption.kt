package com.basehaptic.mobile.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

data class EventFilterOption(
    val id: String,
    val storageKey: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    companion object {
        val all: List<EventFilterOption> = listOf(
            EventFilterOption(
                id = "homerun",
                storageKey = "event_filter_homerun_enabled",
                title = "홈런",
                subtitle = "홈런 발생 시 강한 알림",
                icon = Icons.Default.Star
            ),
            EventFilterOption(
                id = "score",
                storageKey = "event_filter_score_enabled",
                title = "득점",
                subtitle = "득점 발생 시 강한 알림",
                icon = Icons.Default.Flag
            ),
            EventFilterOption(
                id = "hit",
                storageKey = "event_filter_hit_enabled",
                title = "안타",
                subtitle = "안타 발생 시 강한 알림",
                icon = Icons.Default.SportsBaseball
            ),
            EventFilterOption(
                id = "steal",
                storageKey = "event_filter_steal_enabled",
                title = "도루",
                subtitle = "도루 발생 시 강한 알림",
                icon = Icons.Default.DirectionsRun
            ),
            EventFilterOption(
                id = "walk",
                storageKey = "event_filter_walk_enabled",
                title = "볼넷",
                subtitle = "볼넷 발생 시 강한 알림",
                icon = Icons.Default.DirectionsWalk
            ),
            EventFilterOption(
                id = "out",
                storageKey = "event_filter_out_enabled",
                title = "아웃",
                subtitle = "아웃 발생 시 강한 알림",
                icon = Icons.Default.Cancel
            ),
            EventFilterOption(
                id = "double_play",
                storageKey = "event_filter_double_play_enabled",
                title = "병살",
                subtitle = "병살 발생 시 강한 알림",
                icon = Icons.Default.MergeType
            ),
            EventFilterOption(
                id = "pitcher_change",
                storageKey = "event_filter_pitcher_change_enabled",
                title = "투수 교체",
                subtitle = "투수 교체 시 강한 알림",
                icon = Icons.Default.SwapHoriz
            )
        )
    }
}
