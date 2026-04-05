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
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.wear.WearGameSyncManager
import java.time.LocalDate
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

        const val ACTION_START_POLLING = "com.basehaptic.mobile.ACTION_START_POLLING"
        const val ACTION_START_STREAMING = "com.basehaptic.mobile.ACTION_START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.basehaptic.mobile.ACTION_STOP_STREAMING"
        const val ACTION_STOP_SERVICE = "com.basehaptic.mobile.ACTION_STOP_SERVICE"

        const val EXTRA_SELECTED_TEAM = "selected_team"
        const val EXTRA_GAME_ID = "game_id"

        private const val NOTIFICATION_CHANNEL_ID = "game_sync_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var streamingJob: Job? = null

    private var selectedTeam: Team = Team.NONE
    private var syncedGameId: String? = null
    private var todayGamesSnapshot: List<Game> = emptyList()
    private var todayGamesLoadedDate: LocalDate? = null
    private val observedMyTeamGameStatus = mutableMapOf<String, GameStatus>()
    private val autoPromptedLiveGames = mutableMapOf<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        GameSyncState.setServiceRunning(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_POLLING -> {
                val teamName = intent.getStringExtra(EXTRA_SELECTED_TEAM) ?: ""
                val team = Team.fromString(teamName)
                if (team == Team.NONE) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                selectedTeam = team
                observedMyTeamGameStatus.clear()
                autoPromptedLiveGames.clear()

                val notification = buildNotification("경기 모니터링 중...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                startPollingLoop()
            }

            ACTION_START_STREAMING -> {
                val gameId = intent.getStringExtra(EXTRA_GAME_ID)
                val teamName = intent.getStringExtra(EXTRA_SELECTED_TEAM) ?: ""
                val team = Team.fromString(teamName)
                if (team != Team.NONE) selectedTeam = team
                if (!gameId.isNullOrBlank()) {
                    syncedGameId = gameId
                    updateNotification("실시간 동기화 중...")
                    startStreamingLoop(gameId)
                }
            }

            ACTION_STOP_STREAMING -> {
                streamingJob?.cancel()
                streamingJob = null
                syncedGameId = null
                updateNotification("경기 모니터링 중...")
            }

            ACTION_STOP_SERVICE -> {
                stopSelf()
            }

            null -> {
                // Service restarted with null intent after process death.
                // Load team from SharedPreferences and resume polling.
                val prefs = getSharedPreferences("basehaptic_user_prefs", MODE_PRIVATE)
                val savedTeamName = prefs.getString("selected_team", null).orEmpty()
                val team = Team.fromString(savedTeamName)
                if (team == Team.NONE) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                selectedTeam = team
                val notification = buildNotification("경기 모니터링 중...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startPollingLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        GameSyncState.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Polling loop (moved from MainActivity LaunchedEffect(selectedTeam)) ──

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (currentCoroutineContext().isActive) {
                val today = LocalDate.now()
                val shouldFreeze = shouldFreezeTodayGames(
                    games = todayGamesSnapshot,
                    loadedDate = todayGamesLoadedDate,
                    today = today
                )
                if (shouldFreeze) {
                    delay(60_000L)
                    continue
                }

                val fetchedGames = runCatching {
                    withContext(Dispatchers.IO) {
                        BackendGamesRepository.fetchTodayGamesCached(
                            context = applicationContext,
                            selectedTeam = selectedTeam,
                            forceRefresh = true
                        )
                    }
                }.getOrNull()

                if (fetchedGames != null) {
                    todayGamesSnapshot = fetchedGames
                    todayGamesLoadedDate = today
                    GameSyncState.updateTodayGames(fetchedGames)
                }
                val games = fetchedGames.orEmpty()

                val myTeamGames = games.filter { it.isMyTeam }
                for (game in myTeamGames) {
                    val previous = observedMyTeamGameStatus[game.id]
                    observedMyTeamGameStatus[game.id] = game.status

                    val becameLive = game.status == GameStatus.LIVE && previous != GameStatus.LIVE
                    val alreadyPrompted = autoPromptedLiveGames[game.id] == true
                    val isSynced = syncedGameId == game.id
                    if (becameLive && !alreadyPrompted && !isSynced) {
                        autoPromptedLiveGames[game.id] = true
                        WearGameSyncManager.sendWatchSyncPrompt(
                            context = applicationContext,
                            gameId = game.id,
                            homeTeam = game.homeTeam,
                            awayTeam = game.awayTeam,
                            myTeam = selectedTeam.name
                        )
                        GameSyncState.emitGameWentLive(
                            GameSyncState.GameWentLiveEvent(
                                gameId = game.id,
                                homeTeam = game.homeTeam,
                                awayTeam = game.awayTeam,
                                myTeam = selectedTeam.name
                            )
                        )
                    }
                }

                val pollDelayMs = when {
                    fetchedGames == null -> 10_000L
                    games.any { it.status == GameStatus.LIVE } -> 5_000L
                    games.isNotEmpty() && games.all { isTerminalStatus(it.status) } -> 60_000L
                    else -> 30_000L
                }
                delay(pollDelayMs)
            }
        }
    }

    // ── Streaming loop (moved from MainActivity LaunchedEffect(syncedGameId)) ──

    private fun startStreamingLoop(gameId: String) {
        streamingJob?.cancel()
        streamingJob = serviceScope.launch {
            var cursor = 0L
            var localEvents: List<BackendGamesRepository.LiveEvent> = emptyList()
            var lastWatchSignature = ""
            var lastSentEventCursor = 0L
            val reconnectDelaysMs = listOf(1000L, 2000L, 5000L, 10000L)
            var reconnectAttempt = 0

            fun pushStateToWatch(state: BackendGamesRepository.LiveGameState) {
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
                    latestEventType.orEmpty()
                ).joinToString("|")

                if (signature != lastWatchSignature) {
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
                        myTeam = resolveMyTeamName(selectedTeam, state),
                        eventType = null
                    )
                    lastWatchSignature = signature
                }
            }

            fun applyIncomingEvents(
                incoming: List<BackendGamesRepository.LiveEvent>,
                sendHaptics: Boolean
            ) {
                if (incoming.isEmpty()) return

                val sorted = incoming.sortedBy { it.cursor }
                if (sendHaptics) {
                    val ballStrikeEnabled = getSharedPreferences("basehaptic_user_prefs", MODE_PRIVATE)
                        .getBoolean("ball_strike_haptic_enabled", true)
                    val newEvents = sorted.filter { it.cursor > lastSentEventCursor }
                    val batchEventTypes = newEvents.mapNotNull { mapToWatchEventType(it.type) }.toSet()
                    val hasScore = "SCORE" in batchEventTypes || "HOMERUN" in batchEventTypes
                    newEvents.forEach { event ->
                            mapToWatchEventType(event.type)?.let { mapped ->
                                if (mapped == "HIT" && hasScore) return@let
                                val isBallOrStrike = mapped == "BALL" || mapped == "STRIKE"
                                if (!isBallOrStrike || ballStrikeEnabled) {
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
            }

            while (currentCoroutineContext().isActive) {
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
                                applyIncomingEvents(message.items, sendHaptics = true)
                            }

                            is BackendGamesRepository.LiveStreamMessage.State -> {
                                pushStateToWatch(message.state)
                            }

                            is BackendGamesRepository.LiveStreamMessage.Update -> {
                                applyIncomingEvents(message.events, sendHaptics = true)
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
            .setContentTitle("BaseHaptic")
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
            "HIT", "HOMERUN", "SCORE", "WALK", "STEAL" -> normalized
            "SAC_FLY_SCORE" -> "SCORE"
            "TAG_UP_ADVANCE" -> "OUT"
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

    private fun shouldFreezeTodayGames(
        games: List<Game>,
        loadedDate: LocalDate?,
        today: LocalDate
    ): Boolean {
        if (loadedDate != today) return false
        if (games.isEmpty()) return false
        return games.all { isTerminalStatus(it.status) }
    }

    private fun isTerminalStatus(status: GameStatus): Boolean {
        return when (status) {
            GameStatus.FINISHED, GameStatus.CANCELED, GameStatus.POSTPONED -> true
            GameStatus.LIVE, GameStatus.SCHEDULED -> false
        }
    }
}
