package com.basehaptic.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.basehaptic.mobile.MainActivity
import com.basehaptic.mobile.R
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.EventFilterGate
import com.basehaptic.mobile.data.model.EventNotificationChannel
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.wear.WearGameSyncManager
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameSyncForegroundService : Service() {

    companion object {
        private const val TAG = "GameSyncFgService"

        const val ACTION_START_STREAMING = "com.basehaptic.mobile.ACTION_START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.basehaptic.mobile.ACTION_STOP_STREAMING"

        const val EXTRA_SELECTED_TEAM = "selected_team"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_WATCH_SYNC_ENABLED = "watch_sync_enabled"
        const val EXTRA_LIVE_SCORE_ENABLED = "live_score_enabled"

        private const val NOTIFICATION_CHANNEL_ID = "game_sync_channel"
        private const val NOTIFICATION_ID = 1001

        // 경기 상태가 LIVE 가 아닌 관측이 연속 N회면 service 종료 (무한 가동 방지)
        private const val MAX_NON_LIVE_OBSERVATIONS = 3
        // 최대 가동 시간 backstop — 어떤 이유로든 6시간을 넘기면 강제 종료
        private const val MAX_RUNTIME_MS = 6 * 60 * 60 * 1000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var streamingJob: Job? = null
    private var maxRuntimeJob: Job? = null

    private var selectedTeam: Team = Team.NONE
    private var syncedGameId: String? = null
    private var watchSyncEnabled: Boolean = true
    private var liveScoreEnabled: Boolean = true
    private var lastNotificationText: String? = null
    private var nonLiveObservationCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        GameSyncState.setServiceRunning(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STREAMING -> {
                val gameId = intent.getStringExtra(EXTRA_GAME_ID)
                val teamName = intent.getStringExtra(EXTRA_SELECTED_TEAM) ?: ""
                val team = Team.fromString(teamName)
                if (team != Team.NONE) selectedTeam = team
                if (gameId.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                watchSyncEnabled = intent.getBooleanExtra(EXTRA_WATCH_SYNC_ENABLED, true)
                liveScoreEnabled = intent.getBooleanExtra(EXTRA_LIVE_SCORE_ENABLED, true)
                if (!watchSyncEnabled && !liveScoreEnabled) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                syncedGameId = gameId

                val initialText = if (watchSyncEnabled) "워치로 관람 중..." else "잠금화면 경기 카드 업데이트 중..."
                lastNotificationText = initialText
                val notification = buildNotification(initialText)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startStreamingLoop(gameId)

                // 최대 가동 시간 backstop
                maxRuntimeJob?.cancel()
                maxRuntimeJob = serviceScope.launch {
                    delay(MAX_RUNTIME_MS)
                    Log.w(TAG, "Max runtime reached — stopping service")
                    stopStreamingAndSelf()
                }
            }

            ACTION_STOP_STREAMING -> {
                maxRuntimeJob?.cancel()
                maxRuntimeJob = null
                watchSyncEnabled = false
                liveScoreEnabled = false
                com.basehaptic.mobile.push.LiveScoreNotificationManager.remove(applicationContext)
                stopStreamingAndSelf()
            }

            null -> {
                // process death 후 재시작 — 폴링 service 제거됨. 그냥 종료.
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        GameSyncState.setServiceRunning(false)
        com.basehaptic.mobile.push.LiveScoreNotificationManager.remove(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Streaming loop ──

    /** 스트리밍 중단 + foreground 알림 제거 + service 종료 공통 처리 */
    private fun stopStreamingAndSelf() {
        syncedGameId = null
        streamingJob?.cancel()
        streamingJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    /**
     * LIVE 가 아닌 상태 관측 카운트 갱신. 연속 MAX_NON_LIVE_OBSERVATIONS 회면 service 종료.
     * FINISHED 뿐 아니라 CANCELED/SCHEDULED 등 어떤 비-LIVE 상태로 고착돼도 무한 가동을 막는다.
     */
    private fun trackNonLiveObservation(status: GameStatus): Boolean {
        if (status == GameStatus.LIVE) {
            nonLiveObservationCount = 0
            return false
        }
        nonLiveObservationCount += 1
        if (nonLiveObservationCount >= MAX_NON_LIVE_OBSERVATIONS) {
            Log.i(TAG, "Game not LIVE for $nonLiveObservationCount consecutive observations — stopping service")
            com.basehaptic.mobile.push.LiveScoreNotificationManager.remove(applicationContext)
            stopStreamingAndSelf()
            return true
        }
        return false
    }

    private fun startStreamingLoop(gameId: String) {
        streamingJob?.cancel()
        nonLiveObservationCount = 0
        streamingJob = serviceScope.launch {
            var cursor = 0L
            var localEvents: List<BackendGamesRepository.LiveEvent> = emptyList()
            var lastWatchSignature = ""
            var lastSentEventCursor = 0L
            var highlightedLiveScoreEventCursor: Long? = null
            var latestLiveScoreState: BackendGamesRepository.LiveGameState? = null
            var highlightRevertJob: Job? = null
            val reconnectDelaysMs = listOf(1000L, 2000L, 5000L, 10000L)
            var reconnectAttempt = 0

            fun pushStateToWatch(state: BackendGamesRepository.LiveGameState) {
                // 비-LIVE 상태 연속 관측 시 종료 (FINISHED 메시지를 못 받아도 무한 가동 방지)
                if (trackNonLiveObservation(state.status)) return

                // 폰 라이브 스코어 ongoing notification (잠금화면·드로어 표시)
                if (liveScoreEnabled && state.status == GameStatus.LIVE) {
                    latestLiveScoreState = state
                    val latestEventForNoti = localEvents.firstOrNull()
                    val latestForNoti = latestEventForNoti?.type ?: state.lastEventType
                    val shouldHighlight =
                        latestEventForNoti != null &&
                            highlightedLiveScoreEventCursor == latestEventForNoti.cursor
                    com.basehaptic.mobile.push.LiveScoreNotificationManager.post(
                        applicationContext,
                        state,
                        latestForNoti,
                        latestEventForNoti?.description,
                        highlightEvent = shouldHighlight
                    )
                    if (shouldHighlight) {
                        val cursorToRevert = latestEventForNoti.cursor
                        highlightRevertJob?.cancel()
                        highlightRevertJob = launch {
                            delay(3_000)
                            if (
                                liveScoreEnabled &&
                                highlightedLiveScoreEventCursor == cursorToRevert
                            ) {
                                highlightedLiveScoreEventCursor = null
                                latestLiveScoreState?.takeIf { it.status == GameStatus.LIVE }?.let { currentState ->
                                    val currentEvent = localEvents.firstOrNull()
                                    com.basehaptic.mobile.push.LiveScoreNotificationManager.post(
                                        applicationContext,
                                        currentState,
                                        currentEvent?.type ?: currentState.lastEventType,
                                        currentEvent?.description,
                                        highlightEvent = false
                                    )
                                }
                            }
                        }
                    }
                } else if (!liveScoreEnabled || state.status == GameStatus.FINISHED) {
                    highlightedLiveScoreEventCursor = null
                    highlightRevertJob?.cancel()
                    com.basehaptic.mobile.push.LiveScoreNotificationManager.remove(applicationContext)
                }

                val awayMascot = state.awayTeamId
                    .takeIf { it != Team.NONE }
                    ?.teamName
                    ?.takeIf { it.isNotBlank() }
                    ?: state.awayTeam
                val homeMascot = state.homeTeamId
                    .takeIf { it != Team.NONE }
                    ?.teamName
                    ?.takeIf { it.isNotBlank() }
                    ?: state.homeTeam
                if (awayMascot.isNotBlank() && homeMascot.isNotBlank()) {
                    val notifText = if (watchSyncEnabled) {
                        "$awayMascot vs $homeMascot 경기 워치로 관람 중..."
                    } else {
                        "$awayMascot vs $homeMascot 잠금화면 경기 카드 업데이트 중..."
                    }
                    if (notifText != lastNotificationText) {
                        lastNotificationText = notifText
                        updateNotification(notifText)
                    }
                }
                val latestEventType =
                    localEvents.firstNotNullOfOrNull { mapToWatchEventType(it.type) }
                        ?: mapToWatchEventType(state.lastEventType)
                val signature = listOf(
                    state.gameId,
                    state.status.name,
                    state.inning,
                    state.homeScore.toString(),
                    state.awayScore.toString(),
                    state.ball.toString(),
                    state.strike.toString(),
                    state.out.toString(),
                    state.baseFirst.toString(),
                    state.baseSecond.toString(),
                    state.baseThird.toString(),
                    state.pitcherPitchCount?.toString().orEmpty(),
                    latestEventType.orEmpty()
                ).joinToString("|")

                if (watchSyncEnabled && signature != lastWatchSignature) {
                    val wasLive = lastWatchSignature.contains("|LIVE|")
                    WearGameSyncManager.sendGameData(
                        context = applicationContext,
                        gameId = state.gameId,
                        homeTeam = state.homeTeam,
                        awayTeam = state.awayTeam,
                        homeScore = state.homeScore,
                        awayScore = state.awayScore,
                        status = state.status.name,
                        inning = state.inning,
                        ball = state.ball,
                        strike = state.strike,
                        out = state.out,
                        baseFirst = state.baseFirst,
                        baseSecond = state.baseSecond,
                        baseThird = state.baseThird,
                        pitcher = state.pitcher,
                        batter = state.batter,
                        pitcherPitchCount = state.pitcherPitchCount,
                        myTeam = resolveMyTeamName(selectedTeam, state),
                        eventType = null
                    )
                    lastWatchSignature = signature

                    // 경기 종료 + 내 팀 승리 → VICTORY 햅틱
                    if (wasLive && state.status == GameStatus.FINISHED) {
                        val isMyTeamHome = selectedTeam != Team.NONE && state.homeTeamId == selectedTeam
                        val isMyTeamAway = selectedTeam != Team.NONE && state.awayTeamId == selectedTeam
                        val myTeamWon = (isMyTeamHome && state.homeScore > state.awayScore) ||
                            (isMyTeamAway && state.awayScore > state.homeScore)
                        if (myTeamWon) {
                            WearGameSyncManager.sendHapticEvent(applicationContext, "VICTORY")
                        }

                        // 워치 관람 자동 종료 → foreground service 종료해 알림 제거.
                        // 사용자가 다시 앱 켜면 polling 재시작.
                        stopStreamingAndSelf()
                    }
                } else if (!watchSyncEnabled && state.status == GameStatus.FINISHED) {
                    stopStreamingAndSelf()
                }
            }

            withContext(Dispatchers.IO) {
                BackendGamesRepository.fetchGameState(gameId)
            }?.let { initialState ->
                pushStateToWatch(initialState)
            }

            fun applyIncomingEvents(
                incoming: List<BackendGamesRepository.LiveEvent>,
                sendHaptics: Boolean,
                allowLiveScoreHighlight: Boolean
            ) {
                if (incoming.isEmpty()) return

                val sorted = incoming.sortedBy { it.cursor }
                val freshEvents = sorted.filter { it.cursor > cursor }
                if (sendHaptics) {
                    val newEvents = sorted.filter { it.cursor > lastSentEventCursor }
                    val batchEventTypes = newEvents.mapNotNull { mapToWatchEventType(it.type) }.toSet()
                    val hasScore = "SCORE" in batchEventTypes || "HOMERUN" in batchEventTypes
                    newEvents.forEach { event ->
                            mapToWatchEventType(event.type)?.let { mapped ->
                                if (mapped == "HIT" && hasScore) return@let
                                if (EventFilterGate.isAllowed(
                                        applicationContext,
                                        mapped,
                                        EventNotificationChannel.WATCH
                                    )
                                ) {
                                    WearGameSyncManager.sendHapticEvent(
                                        applicationContext,
                                        mapped,
                                        event.cursor
                                    )
                                }
                            }
                            lastSentEventCursor = max(lastSentEventCursor, event.cursor)
                        }
                } else {
                    lastSentEventCursor =
                        max(lastSentEventCursor, sorted.maxOfOrNull { it.cursor } ?: 0L)
                }

                cursor = max(cursor, sorted.maxOfOrNull { it.cursor } ?: cursor)
                localEvents = (sorted + localEvents)
                    .distinctBy { it.cursor }
                    .sortedByDescending { it.cursor }
                    .take(80)

                if (allowLiveScoreHighlight && liveScoreEnabled) {
                    freshEvents.lastOrNull {
                        EventFilterGate.isAllowed(
                            applicationContext,
                            it.type,
                            EventNotificationChannel.LOCK_SCREEN
                        )
                    }?.let { latestFreshEvent ->
                        highlightedLiveScoreEventCursor = latestFreshEvent.cursor
                        latestLiveScoreState?.takeIf { it.status == GameStatus.LIVE }?.let { currentState ->
                            pushStateToWatch(currentState)
                        }
                    }
                }
            }

            while (currentCoroutineContext().isActive) {
                var hasConsumedInitialEventsSnapshot = false
                runCatching {
                    BackendGamesRepository.streamGame(gameId).collect { message ->
                        when (message) {
                            BackendGamesRepository.LiveStreamMessage.Connected -> {
                                reconnectAttempt = 0
                            }

                            BackendGamesRepository.LiveStreamMessage.Closed -> {
                                throw IllegalStateException("game stream closed")
                            }

                            is BackendGamesRepository.LiveStreamMessage.Error -> {
                                throw message.throwable
                            }

                            is BackendGamesRepository.LiveStreamMessage.Events -> {
                                applyIncomingEvents(
                                    incoming = message.items,
                                    sendHaptics = watchSyncEnabled && hasConsumedInitialEventsSnapshot,
                                    allowLiveScoreHighlight = hasConsumedInitialEventsSnapshot
                                )
                                hasConsumedInitialEventsSnapshot = true
                            }

                            is BackendGamesRepository.LiveStreamMessage.State -> {
                                pushStateToWatch(message.state)
                            }

                            is BackendGamesRepository.LiveStreamMessage.Update -> {
                                applyIncomingEvents(
                                    incoming = message.events,
                                    sendHaptics = watchSyncEnabled,
                                    allowLiveScoreHighlight = true
                                )
                                message.state?.let { pushStateToWatch(it) }
                            }

                            is BackendGamesRepository.LiveStreamMessage.Pong -> Unit
                        }
                    }
                }

                if (!currentCoroutineContext().isActive) break
                val delayMs =
                    reconnectDelaysMs[reconnectAttempt.coerceAtMost(reconnectDelaysMs.lastIndex)]
                reconnectAttempt =
                    (reconnectAttempt + 1).coerceAtMost(reconnectDelaysMs.lastIndex)
                delay(delayMs)
            }
        }
    }

    // ── Notification helpers ──

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "경기 동기화",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "워치 경기 실시간 동기화 알림"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("야구봄")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    // ── Utility functions ──

    private fun mapToWatchEventType(type: String?): String? {
        val normalized = type?.uppercase() ?: return null
        return when (normalized) {
            "BALL", "STRIKE", "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY",
            "HIT", "HOMERUN", "SCORE", "WALK", "HIT_BY_PITCH", "STEAL",
            "PITCHER_CHANGE", "MOUND_VISIT" -> normalized
            "SAC_FLY_SCORE" -> "SCORE"
            "TAG_UP_ADVANCE" -> "STEAL"
            else -> null
        }
    }

    private fun resolveMyTeamName(
        selectedTeam: Team,
        state: BackendGamesRepository.LiveGameState
    ): String {
        return when {
            selectedTeam != Team.NONE -> selectedTeam.name
            state.homeTeamId != Team.NONE -> state.homeTeamId.name
            state.awayTeamId != Team.NONE -> state.awayTeamId.name
            else -> "DEFAULT"
        }
    }

}
