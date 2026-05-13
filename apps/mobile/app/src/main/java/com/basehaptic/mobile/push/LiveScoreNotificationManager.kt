package com.basehaptic.mobile.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.basehaptic.mobile.MainActivity
import com.basehaptic.mobile.R
import com.basehaptic.mobile.data.BackendGamesRepository.LiveGameState

object LiveScoreNotificationManager {
    private const val NOTIFICATION_ID = 1002

    fun post(context: Context, state: LiveGameState, latestEventType: String? = state.lastEventType) {
        NotificationChannels.ensureCreated(context)

        val title = "${state.awayTeam} ${state.awayScore} : ${state.homeScore} ${state.homeTeam}"
        val parts = mutableListOf<String>()
        if (state.inning.isNotBlank()) parts.add(state.inning)
        parts.add("${state.out}아웃")
        val bases = mutableListOf<String>().apply {
            if (state.baseFirst) add("1루")
            if (state.baseSecond) add("2루")
            if (state.baseThird) add("3루")
        }
        if (bases.isNotEmpty()) parts.add(bases.joinToString("·"))
        val eventLabel = eventTypeToKorean(latestEventType)
        if (eventLabel.isNotBlank()) parts.add(eventLabel)
        val text = parts.joinToString(" · ")

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_game_id", state.gameId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            state.gameId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.LIVE_SCORE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; ignore silently
        }
    }

    fun remove(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun eventTypeToKorean(type: String?): String {
        if (type.isNullOrBlank()) return ""
        return when (type.uppercase()) {
            "HOMERUN" -> "홈런"
            "SCORE", "SAC_FLY_SCORE" -> "득점"
            "HIT" -> "안타"
            "STEAL", "TAG_UP_ADVANCE" -> "도루"
            "WALK" -> "볼넷"
            "OUT" -> "아웃"
            "DOUBLE_PLAY" -> "병살"
            "TRIPLE_PLAY" -> "삼중살"
            "PITCHER_CHANGE" -> "투수교체"
            "VICTORY" -> "경기 종료"
            else -> ""
        }
    }
}
