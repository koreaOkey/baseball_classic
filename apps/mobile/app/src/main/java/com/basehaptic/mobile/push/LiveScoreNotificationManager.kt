package com.basehaptic.mobile.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.basehaptic.mobile.MainActivity
import com.basehaptic.mobile.R
import com.basehaptic.mobile.data.model.EventFilterGate
import com.basehaptic.mobile.data.model.EventNotificationChannel
import com.basehaptic.mobile.data.BackendGamesRepository.LiveGameState
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.TeamDisplayNameStyle

object LiveScoreNotificationManager {
    const val KEY_LOCK_SCREEN_LIVE_SCORE_ENABLED = "lock_screen_live_score_enabled"
    const val KEY_PROMOTED_STYLE_ENABLED = "live_score_promoted_style_enabled"

    private const val NOTIFICATION_ID = 1002
    private const val PREFS_NAME = "basehaptic_user_prefs"
    private const val KEY_TEAM_DISPLAY_NAME_STYLE = "team_display_name_style"

    fun isLockScreenCardEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCK_SCREEN_LIVE_SCORE_ENABLED, true)
    }

    fun isPromotedStyleEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMOTED_STYLE_ENABLED, true)
    }

    // 설정 UI 노출 판단용: OS/제조사 조건만 본다.
    // canPostPromotedNotifications()는 시스템 설정에서 사용자가 끌 수 있어 노출 조건에 넣지 않는다.
    fun isPromotedStyleSupportedOnDevice(): Boolean {
        return Build.VERSION.SDK_INT >= 36 &&
            !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    fun post(
        context: Context,
        state: LiveGameState,
        latestEventType: String? = state.lastEventType,
        latestEventDescription: String? = null,
        highlightEvent: Boolean = false
    ): Boolean {
        if (!isLockScreenCardEnabled(context)) {
            remove(context)
            return false
        }

        NotificationChannels.ensureCreated(context)

        val displayNameStyle = loadDisplayNameStyle(context)
        val awayName = displayTeamName(state.awayTeamId, state.awayTeam, displayNameStyle)
        val homeName = displayTeamName(state.homeTeamId, state.homeTeam, displayNameStyle)
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
            putExtra(
                BaseHapticMessagingService.EXTRA_NOTIFICATION_SOURCE,
                BaseHapticMessagingService.SOURCE_LIVE_SCORE
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            state.gameId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shouldHighlight = highlightEvent &&
            EventFilterGate.isAllowed(context, latestEventType, EventNotificationChannel.LOCK_SCREEN)
        val channelId = if (shouldHighlight) {
            NotificationChannels.LIVE_SCORE_ALERTS_ID
        } else {
            NotificationChannels.LIVE_SCORE_ID
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(!shouldHighlight)
            .setShowWhen(false)
            .setCategory(if (shouldHighlight) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (shouldHighlight) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(if (shouldHighlight) NotificationCompat.DEFAULT_ALL else 0)
            .setVibrate(if (shouldHighlight) longArrayOf(0, 180, 80, 180) else null)
            .setContentIntent(pendingIntent)

        // 삼성 One UI 8.0처럼 API 36이어도 서드파티 승격을 막아둔 기기가 있다.
        // 승격이 안 되는 기기에서 promoted 스타일을 쓰면 기존 리치 커스텀 카드만 잃으므로 런타임 확인.
        // 삼성은 promoted를 Now Bar로 표현하는데 Now Bar 노출은 제품 결정상 쓰지 않기로 해서
        // 제조사 단위로 제외한다 (되돌리려면 이 조건 한 줄만 제거).
        // 마지막 조건: 설정 탭에서 사용자가 이전(리치 카드) 스타일을 선택할 수 있다.
        val canPromote = isPromotedStyleSupportedOnDevice() &&
            NotificationManagerCompat.from(context).canPostPromotedNotifications() &&
            isPromotedStyleEnabled(context)
        if (canPromote) {
            applyPromotedStyle(context, builder, state, eventLabel, latestEventDescription)
        } else {
            val compactView = buildCompactRemoteViews(
                context = context,
                state = state,
                awayName = awayName,
                homeName = homeName
            )
            val expandedView = buildExpandedRemoteViews(
                context = context,
                state = state,
                awayName = awayName,
                homeName = homeName,
                statusText = statusText,
                eventType = latestEventType,
                eventLabel = eventLabel,
                latestEventDescription = latestEventDescription,
                highlightEvent = shouldHighlight
            )
            builder.setContentText(text)
                .setSubText("라이브 스코어")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(compactView)
                .setCustomBigContentView(expandedView)
                .setCustomHeadsUpContentView(expandedView)
        }

        val notification = builder.build()

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

    // Android 16+ promoted Live Update: 커스텀 뷰가 금지되어 시스템 템플릿 + largeIcon 비트맵으로 구성.
    // 잠금화면 최상단 고정 + 상태바 칩(스코어) 노출 대상.
    // largeIcon = 베이스 다이아몬드. 최근 이벤트가 있으면 그 설명이 첫 줄, BSO·타자/투수는 아랫줄.
    private fun applyPromotedStyle(
        context: Context,
        builder: NotificationCompat.Builder,
        state: LiveGameState,
        eventLabel: String,
        eventDescription: String?
    ) {
        val eventLine = eventDescription?.trim()?.takeIf { it.isNotBlank() }?.take(42)
            ?: eventLabel.takeIf { it.isNotBlank() }
        val bsoLine = shrunk(bsoEmojiLine(state))
        val playersLine = "타자 ${state.batter.ifBlank { "-" }} · 투수 ${state.pitcher.ifBlank { "-" }}"
        val expandedText = SpannableStringBuilder()
        eventLine?.let { expandedText.append(it).append("\n") }
        expandedText.append(bsoLine).append("\n").append(playersLine)
        builder.setSubText(state.inning.ifBlank { "라이브" })
            .setContentText(eventLine ?: bsoLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setLargeIcon(LiveScorePromotedIconRenderer.render(state))
            .setShortCriticalText("${state.awayScore}:${state.homeScore}")
            .setRequestPromotedOngoing(true)
    }

    // 이모지 원이 텍스트 폰트 크기를 그대로 따라 커 보여서 한 단계 줄인다.
    // 크기 span을 무시하는 기기에서는 원래 크기로 표시될 뿐 깨지지 않는다.
    private fun shrunk(text: String): CharSequence {
        return SpannableStringBuilder(text).apply {
            setSpan(RelativeSizeSpan(0.8f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun bsoEmojiLine(state: LiveGameState): String {
        fun slots(filled: Int, total: Int, emoji: String): String {
            val active = filled.coerceIn(0, total)
            // 빈 슬롯은 이모지가 아닌 텍스트 글리프 ○(U+25CB) — 속이 투명해 카드 배경이 비치고
            // 테두리는 본문 텍스트 색을 따른다. 이모지 원 세트에는 테두리만 있는 중립색이 없다.
            return emoji.repeat(active) + "○".repeat(total - active)
        }
        return "B ${slots(state.ball, 3, "🟢")} S ${slots(state.strike, 2, "🟡")} O ${slots(state.out, 2, "🔴")}"
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
        homeName: String
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_live_score_compact).apply {
            setTextViewText(R.id.notification_away_team, awayName)
            setTextViewText(R.id.notification_home_team, homeName)
            setTextViewText(R.id.notification_away_score, state.awayScore.toString())
            setTextViewText(R.id.notification_home_score, state.homeScore.toString())
            setImageViewResource(R.id.notification_away_logo, teamLogoRes(state.awayTeamId))
            setImageViewResource(R.id.notification_home_logo, teamLogoRes(state.homeTeamId))
        }
    }

    private fun buildExpandedRemoteViews(
        context: Context,
        state: LiveGameState,
        awayName: String,
        homeName: String,
        statusText: String,
        eventType: String?,
        eventLabel: String,
        latestEventDescription: String?,
        highlightEvent: Boolean
    ): RemoteViews {
        val recentText = recentEventText(eventLabel, latestEventDescription)
        val showHighlight = highlightEvent && recentText.isNotBlank()
        val inningLabel = state.inning.ifBlank { "경기 중" }
        return RemoteViews(context.packageName, R.layout.notification_live_score_expanded).apply {
            setTextViewText(R.id.notification_status, inningLabel)
            setTextViewText(R.id.notification_away_team, awayName)
            setTextViewText(R.id.notification_home_team, homeName)
            setTextViewText(R.id.notification_away_score, state.awayScore.toString())
            setTextViewText(R.id.notification_home_score, state.homeScore.toString())
            setImageViewResource(R.id.notification_away_logo, teamLogoRes(state.awayTeamId))
            setImageViewResource(R.id.notification_home_logo, teamLogoRes(state.homeTeamId))
            setTextViewText(R.id.notification_event_label, eventLabel)
            setTextColor(R.id.notification_event_label, eventColor(eventType))
            setViewVisibility(
                R.id.notification_event_label,
                if (eventLabel.isBlank()) View.GONE else View.VISIBLE
            )
            setTextViewText(R.id.notification_pitcher_batter, "P ${state.pitcher.ifBlank { "-" }}  |  B ${state.batter.ifBlank { "-" }}")
            setTextViewText(R.id.notification_recent_event, recentText)
            setTextViewText(R.id.notification_highlight_event, recentText)
            setTextColor(R.id.notification_highlight_dot, eventColor(eventType))
            setInt(R.id.notification_highlight_row, "setBackgroundResource", eventHighlightBackground(eventType))
            setViewVisibility(R.id.notification_highlight_row, if (showHighlight) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.notification_detail_row, if (showHighlight) View.GONE else View.VISIBLE)

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

    private fun loadDisplayNameStyle(context: Context): TeamDisplayNameStyle {
        return TeamDisplayNameStyle.fromString(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TEAM_DISPLAY_NAME_STYLE, null)
        )
    }

    private fun displayTeamName(team: Team, fallback: String, style: TeamDisplayNameStyle): String {
        return team.takeIf { it != Team.NONE }?.displayName(style)?.takeIf { it.isNotBlank() }
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
            !description.isNullOrBlank() -> description
            eventLabel.isNotBlank() -> eventLabel
            else -> "경기 진행 상황을 업데이트 중입니다"
        }
    }

    private fun eventColor(type: String?): Int {
        return when (type?.uppercase()) {
            "HOMERUN" -> 0xFFFB923C.toInt()
            "SCORE", "SAC_FLY_SCORE" -> 0xFFFACC15.toInt()
            "HIT" -> 0xFF60A5FA.toInt()
            "WALK", "STEAL", "TAG_UP_ADVANCE" -> 0xFF4ADE80.toInt()
            "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY" -> 0xFFF87171.toInt()
            else -> 0xFFFACC15.toInt()
        }
    }

    private fun eventHighlightBackground(type: String?): Int {
        return when (type?.uppercase()) {
            "HOMERUN" -> R.drawable.notification_highlight_orange
            "SCORE", "SAC_FLY_SCORE" -> R.drawable.notification_highlight_yellow
            "HIT" -> R.drawable.notification_highlight_blue
            "WALK", "STEAL", "TAG_UP_ADVANCE" -> R.drawable.notification_highlight_green
            "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY" -> R.drawable.notification_highlight_red
            else -> R.drawable.notification_highlight_yellow
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
