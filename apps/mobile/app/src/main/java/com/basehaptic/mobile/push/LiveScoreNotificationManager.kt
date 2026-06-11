package com.basehaptic.mobile.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.basehaptic.mobile.MainActivity
import com.basehaptic.mobile.R
import com.basehaptic.mobile.data.BackendGamesRepository.LiveGameState
import com.basehaptic.mobile.data.model.Team

object LiveScoreNotificationManager {
    const val KEY_LOCK_SCREEN_LIVE_SCORE_ENABLED = "lock_screen_live_score_enabled"

    private const val NOTIFICATION_ID = 1002
    private const val PREFS_NAME = "basehaptic_user_prefs"

    fun isLockScreenCardEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCK_SCREEN_LIVE_SCORE_ENABLED, true)
    }

    fun post(
        context: Context,
        state: LiveGameState,
        latestEventType: String? = state.lastEventType,
        latestEventDescription: String? = null
    ): Boolean {
        if (!isLockScreenCardEnabled(context)) {
            remove(context)
            return false
        }

        NotificationChannels.ensureCreated(context)

        val awayName = displayTeamName(state.awayTeamId, state.awayTeam)
        val homeName = displayTeamName(state.homeTeamId, state.homeTeam)
        val title = "$awayName ${state.awayScore} : ${state.homeScore} $homeName"
        val statusText = currentAttackLabel(state, awayName, homeName)
        val basesText = baseText(state)
        val eventLabel = eventTypeToKorean(latestEventType)
        val text = listOfNotNull(
            statusText.takeIf { it.isNotBlank() },
            "${state.out}아웃",
            basesText.takeIf { it.isNotBlank() },
            eventLabel.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

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

        val compactView = buildCompactRemoteViews(context, state, awayName, homeName, statusText, text)
        val expandedView = buildExpandedRemoteViews(
            context = context,
            state = state,
            awayName = awayName,
            homeName = homeName,
            statusText = statusText,
            eventLabel = eventLabel,
            latestEventDescription = latestEventDescription
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.LIVE_SCORE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("라이브 스코어")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
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
            return true
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; ignore silently
            return false
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

    private fun buildCompactRemoteViews(
        context: Context,
        state: LiveGameState,
        awayName: String,
        homeName: String,
        statusText: String,
        fallbackText: String
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_live_score_compact).apply {
            setTextViewText(R.id.notification_status, statusText)
            setTextViewText(R.id.notification_score, "$awayName ${state.awayScore} : ${state.homeScore} $homeName")
            setTextViewText(R.id.notification_meta, fallbackText)
        }
    }

    private fun buildExpandedRemoteViews(
        context: Context,
        state: LiveGameState,
        awayName: String,
        homeName: String,
        statusText: String,
        eventLabel: String,
        latestEventDescription: String?
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_live_score_expanded).apply {
            setTextViewText(R.id.notification_status, statusText)
            setTextViewText(R.id.notification_away_team, awayName)
            setTextViewText(R.id.notification_home_team, homeName)
            setTextViewText(R.id.notification_away_score, state.awayScore.toString())
            setTextViewText(R.id.notification_home_score, state.homeScore.toString())
            setImageViewResource(R.id.notification_away_logo, teamLogoRes(state.awayTeamId))
            setImageViewResource(R.id.notification_home_logo, teamLogoRes(state.homeTeamId))
            setTextViewText(R.id.notification_pitcher_batter, "P ${state.pitcher.ifBlank { "-" }}  |  B ${state.batter.ifBlank { "-" }}")
            setTextViewText(R.id.notification_recent_event, recentEventText(eventLabel, latestEventDescription))

            setBaseBackground(R.id.notification_base_first, state.baseFirst)
            setBaseBackground(R.id.notification_base_second, state.baseSecond)
            setBaseBackground(R.id.notification_base_third, state.baseThird)

            setCountDotBackground(R.id.notification_ball_1, state.ball >= 1, R.drawable.notification_dot_green)
            setCountDotBackground(R.id.notification_ball_2, state.ball >= 2, R.drawable.notification_dot_green)
            setCountDotBackground(R.id.notification_ball_3, state.ball >= 3, R.drawable.notification_dot_green)
            setCountDotBackground(R.id.notification_strike_1, state.strike >= 1, R.drawable.notification_dot_yellow)
            setCountDotBackground(R.id.notification_strike_2, state.strike >= 2, R.drawable.notification_dot_yellow)
            setCountDotBackground(R.id.notification_out_1, state.out >= 1, R.drawable.notification_dot_red)
            setCountDotBackground(R.id.notification_out_2, state.out >= 2, R.drawable.notification_dot_red)
        }
    }

    private fun RemoteViews.setBaseBackground(viewId: Int, occupied: Boolean) {
        setInt(
            viewId,
            "setBackgroundResource",
            if (occupied) R.drawable.notification_base_occupied else R.drawable.notification_base_empty
        )
    }

    private fun RemoteViews.setCountDotBackground(viewId: Int, active: Boolean, activeRes: Int) {
        setInt(
            viewId,
            "setBackgroundResource",
            if (active) activeRes else R.drawable.notification_dot_empty
        )
    }

    private fun displayTeamName(team: Team, fallback: String): String {
        return team.takeIf { it != Team.NONE }?.teamName?.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: "-"
    }

    private fun currentAttackLabel(state: LiveGameState, awayName: String, homeName: String): String {
        val inning = state.inning.ifBlank { "경기 중" }
        val attackTeam = when {
            state.inning.contains("초") -> awayName
            state.inning.contains("말") -> homeName
            else -> ""
        }
        return if (attackTeam.isBlank()) inning else "$inning · $attackTeam 공격"
    }

    private fun baseText(state: LiveGameState): String {
        val bases = mutableListOf<String>()
        if (state.baseFirst) bases.add("1루")
        if (state.baseSecond) bases.add("2루")
        if (state.baseThird) bases.add("3루")
        return bases.joinToString("·")
    }

    private fun recentEventText(eventLabel: String, latestEventDescription: String?): String {
        val description = latestEventDescription
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(42)
        return when {
            !description.isNullOrBlank() -> "최근: $description"
            eventLabel.isNotBlank() -> "최근: $eventLabel"
            else -> "최근 이벤트 대기 중"
        }
    }

    private fun teamLogoRes(team: Team): Int {
        return when (team) {
            Team.DOOSAN -> R.drawable.dosan
            Team.LG -> R.drawable.lg
            Team.KIWOOM -> R.drawable.kiwoom
            Team.SAMSUNG -> R.drawable.samsung
            Team.LOTTE -> R.drawable.lotte
            Team.SSG -> R.drawable.ssg
            Team.KT -> R.drawable.kt
            Team.HANWHA -> R.drawable.hanwha
            Team.KIA -> R.drawable.kia
            Team.NC -> R.drawable.nc
            Team.NONE -> R.mipmap.ic_launcher
        }
    }
}
