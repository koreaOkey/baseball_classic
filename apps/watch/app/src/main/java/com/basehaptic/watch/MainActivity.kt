package com.basehaptic.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.basehaptic.watch.data.BaseStatus
import com.basehaptic.watch.data.GameData
import com.basehaptic.watch.ui.components.LiveGameScreen
import com.basehaptic.watch.ui.components.NoGameScreen
import com.basehaptic.watch.ui.theme.BaseHapticWatchTheme
import com.basehaptic.watch.ui.theme.Gray950
import com.basehaptic.watch.ui.theme.rememberWatchUiProfile
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        private const val ONGOING_CHANNEL_ID = "game_ongoing_channel"
        private const val ONGOING_NOTIFICATION_ID = 2001
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
        }
        override fun onExitAmbient() {
            isAmbient = false
        }
    }

    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)

    // Exposed to Compose via mutableStateOf
    private var isAmbient by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Ambient Mode: keeps app in foreground when screen dims
        lifecycle.addObserver(ambientObserver)

        // Ongoing Activity: prevents system kill + shows on watch face
        createOngoingNotificationChannel()
        startOngoingActivity()

        setContent {
            WatchApp(isAmbient = isAmbient)
        }
    }

    override fun onDestroy() {
        stopOngoingActivity()
        super.onDestroy()
    }

    private fun createOngoingNotificationChannel() {
        val channel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            "경기 진행 중",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "경기 실시간 동기화 알림"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startOngoingActivity() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BaseHaptic")
            .setContentText("경기 관람 중")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .build()

        val ongoingNotificationBuilder = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BaseHaptic")
            .setContentText("Game tracking in progress")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)

        val ongoingActivity = OngoingActivity.Builder(
            this,
            ONGOING_NOTIFICATION_ID,
            ongoingNotificationBuilder
        )
            .setStaticIcon(R.mipmap.ic_launcher)
            .setTouchIntent(pendingIntent)
            .setStatus(
                Status.Builder()
                    .addTemplate("경기 관람 중")
                    .build()
            )
            .build()

        ongoingActivity.apply(this)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, notification)
    }

    private fun stopOngoingActivity() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(ONGOING_NOTIFICATION_ID)
    }
}

@Composable
fun WatchApp(isAmbient: Boolean = false) {
    val context = LocalContext.current

    var syncedTeamName by remember {
        mutableStateOf(
            context.getSharedPreferences(
                DataLayerListenerService.PREFS_NAME,
                Context.MODE_PRIVATE
            ).getString(
                DataLayerListenerService.PREF_KEY_TEAM_NAME,
                "DEFAULT"
            ) ?: "DEFAULT"
        )
    }

    var gameData by remember { mutableStateOf(readGameDataFromPrefs(context)) }
    var latestEvent by remember { mutableStateOf(readLatestEventFromPrefs(context)) }
    var watchSyncPrompt by remember { mutableStateOf(readWatchSyncPromptFromPrefs(context)) }
    var isHomeRunTransitionVisible by remember { mutableStateOf(false) }
    var homeRunTransitionToken by remember { mutableStateOf<Long?>(null) }
    var isHitTransitionVisible by remember { mutableStateOf(false) }
    var hitTransitionToken by remember { mutableStateOf<Long?>(null) }
    var isDoublePlayTransitionVisible by remember { mutableStateOf(false) }
    var doublePlayTransitionToken by remember { mutableStateOf<Long?>(null) }
    var isScoreTransitionVisible by remember { mutableStateOf(false) }
    var scoreTransitionToken by remember { mutableStateOf<Long?>(null) }
    var isVictoryTransitionVisible by remember { mutableStateOf(false) }
    var victoryTransitionToken by remember { mutableStateOf<Long?>(null) }
    var previousGameIsLive by remember { mutableStateOf<Boolean?>(null) }
    // 비디오 재생 중 후속 이벤트로 화면이 끊기지 않도록 하는 가드.
    // true 인 동안은 새 전환 토큰 발급을 막고, 각 LaunchedEffect 가 try/finally
    // 로 종료 시점에 해제. onFinally 누락 대비 delay() 완료 후 자동 해제되는 구조.
    var isPlayingVideo by remember { mutableStateOf(false) }

    // 영상 ExoPlayer 미리 초기화
    val hitPlayer = remember(context) {
        val clipUri = Uri.parse("android.resource://${context.packageName}/${R.raw.penguin_hit_clip}")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }
    val homeRunPlayer = remember(context) {
        val clipUri = Uri.parse("android.resource://${context.packageName}/${R.raw.penguin_homerun_clip}")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }
    val doublePlayPlayer = remember(context) {
        val clipUri = Uri.parse("android.resource://${context.packageName}/${R.raw.penguin_double_play_clip}")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }
    val scorePlayer = remember(context) {
        val clipUri = Uri.parse("android.resource://${context.packageName}/${R.raw.penguin_score_clip}")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }
    val victoryPlayer = remember(context) {
        val clipUri = Uri.parse("android.resource://${context.packageName}/${R.raw.penguin_victory_clip}")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(hitPlayer, homeRunPlayer, doublePlayPlayer, scorePlayer, victoryPlayer) {
        onDispose {
            hitPlayer.release()
            homeRunPlayer.release()
            doublePlayPlayer.release()
            scorePlayer.release()
            victoryPlayer.release()
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(DataLayerListenerService.ACTION_THEME_UPDATED)
            addAction(DataLayerListenerService.ACTION_GAME_UPDATED)
            addAction(DataLayerListenerService.ACTION_WATCH_SYNC_PROMPT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    DataLayerListenerService.ACTION_THEME_UPDATED -> {
                        syncedTeamName = context.getSharedPreferences(
                            DataLayerListenerService.PREFS_NAME,
                            Context.MODE_PRIVATE
                        ).getString(
                            DataLayerListenerService.PREF_KEY_TEAM_NAME,
                            "DEFAULT"
                        ) ?: "DEFAULT"
                    }
                    DataLayerListenerService.ACTION_GAME_UPDATED -> {
                        gameData = readGameDataFromPrefs(context)
                        latestEvent = readLatestEventFromPrefs(context)
                        // 모바일에서 경기 관람 시작 시 팝업이 자동 수락되었을 수 있으므로 갱신
                        watchSyncPrompt = readWatchSyncPromptFromPrefs(context)
                    }
                    DataLayerListenerService.ACTION_WATCH_SYNC_PROMPT -> {
                        watchSyncPrompt = readWatchSyncPromptFromPrefs(context)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 워치 독립 경기 폴링 시작
    LaunchedEffect(syncedTeamName) {
        val team = syncedTeamName
        if (team.isNotBlank() && team != "DEFAULT" && gameData?.isLive != true) {
            WatchGamePoller.startPolling(context, team)
        }
    }

    val teamName = if (syncedTeamName != "DEFAULT") syncedTeamName else (gameData?.myTeamName ?: "DEFAULT")

    LaunchedEffect(latestEvent?.timestamp) {
        val event = latestEvent ?: return@LaunchedEffect
        if (System.currentTimeMillis() - event.timestamp > EVENT_OVERLAY_FRESHNESS_MS) return@LaunchedEffect
        val game = gameData ?: return@LaunchedEffect
        // 비디오 재생 중에는 후속 이벤트로 인한 전환 토큰 발급을 차단
        if (isPlayingVideo) return@LaunchedEffect
        val isTestGame = game.gameId.startsWith("test_")
        val myTeam = game.myTeamName.uppercase()
        val isMyTeamHome = myTeam == game.homeTeam.uppercase()
        val isMyTeamAway = myTeam == game.awayTeam.uppercase()
        val isMyTeamBatting = isTestGame || (isMyTeamHome && isBottomInning(game.inning)) ||
                              (isMyTeamAway && isTopInning(game.inning))
        val isMyTeamFielding = isTestGame || (!isMyTeamBatting && (isMyTeamHome || isMyTeamAway))
        when (event.type.uppercase()) {
            "HOMERUN" -> if (isMyTeamBatting) homeRunTransitionToken = event.timestamp
            "HIT" -> if (isMyTeamBatting) hitTransitionToken = event.timestamp
            "DOUBLE_PLAY" -> if (isMyTeamFielding) doublePlayTransitionToken = event.timestamp
            "SCORE" -> if (isMyTeamBatting) scoreTransitionToken = event.timestamp
            "VICTORY" -> victoryTransitionToken = event.timestamp
        }
    }

    LaunchedEffect(homeRunTransitionToken) {
        val token = homeRunTransitionToken ?: return@LaunchedEffect
        isPlayingVideo = true
        try {
            homeRunPlayer.seekTo(0)
            homeRunPlayer.play()
            isHomeRunTransitionVisible = true
            delay(HOMERUN_SCREEN_DURATION_MS)
            if (homeRunTransitionToken == token) {
                isHomeRunTransitionVisible = false
                homeRunPlayer.pause()
            }
        } finally {
            isPlayingVideo = false
        }
    }

    LaunchedEffect(hitTransitionToken) {
        val token = hitTransitionToken ?: return@LaunchedEffect
        isPlayingVideo = true
        try {
            hitPlayer.seekTo(0)
            hitPlayer.play()
            isHitTransitionVisible = true
            delay(HIT_SCREEN_DURATION_MS)
            if (hitTransitionToken == token) {
                isHitTransitionVisible = false
                hitPlayer.pause()
            }
        } finally {
            isPlayingVideo = false
        }
    }

    LaunchedEffect(doublePlayTransitionToken) {
        val token = doublePlayTransitionToken ?: return@LaunchedEffect
        isPlayingVideo = true
        try {
            doublePlayPlayer.seekTo(0)
            doublePlayPlayer.play()
            isDoublePlayTransitionVisible = true
            delay(DOUBLE_PLAY_SCREEN_DURATION_MS)
            if (doublePlayTransitionToken == token) {
                isDoublePlayTransitionVisible = false
                doublePlayPlayer.pause()
            }
        } finally {
            isPlayingVideo = false
        }
    }

    LaunchedEffect(scoreTransitionToken) {
        val token = scoreTransitionToken ?: return@LaunchedEffect
        isPlayingVideo = true
        try {
            scorePlayer.seekTo(0)
            scorePlayer.play()
            isScoreTransitionVisible = true
            delay(SCORE_SCREEN_DURATION_MS)
            if (scoreTransitionToken == token) {
                isScoreTransitionVisible = false
                scorePlayer.pause()
            }
        } finally {
            isPlayingVideo = false
        }
    }

    LaunchedEffect(victoryTransitionToken) {
        val token = victoryTransitionToken ?: return@LaunchedEffect
        isPlayingVideo = true
        try {
            victoryPlayer.seekTo(0)
            victoryPlayer.play()
            isVictoryTransitionVisible = true
            delay(VICTORY_SCREEN_DURATION_MS)
            if (victoryTransitionToken == token) {
                isVictoryTransitionVisible = false
                victoryPlayer.pause()
            }
        } finally {
            isPlayingVideo = false
        }
    }

    // 경기 종료 + 내 팀 승리 감지
    LaunchedEffect(gameData?.isLive) {
        val game = gameData ?: return@LaunchedEffect
        if (previousGameIsLive == true && !game.isLive) {
            val myTeam = game.myTeamName.uppercase()
            val isMyTeamHome = myTeam == game.homeTeam.uppercase()
            val isMyTeamAway = myTeam == game.awayTeam.uppercase()
            val myTeamWon = (isMyTeamHome && game.homeScore > game.awayScore) ||
                            (isMyTeamAway && game.awayScore > game.homeScore)
            if (myTeamWon) {
                isPlayingVideo = true
                try {
                    victoryPlayer.seekTo(0)
                    victoryPlayer.play()
                    isVictoryTransitionVisible = true
                    delay(VICTORY_SCREEN_DURATION_MS)
                    isVictoryTransitionVisible = false
                    victoryPlayer.pause()
                } finally {
                    isPlayingVideo = false
                }
            }
        }
        previousGameIsLive = game.isLive
    }

    BaseHapticWatchTheme(teamName = teamName) {
        Scaffold(
            timeText = {
                TimeText()
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (isAmbient) {
                    // Ambient mode: simplified low-power screen
                    AmbientGameScreen(gameData = gameData)
                } else {
                    val transitionState = when {
                        isVictoryTransitionVisible -> "victory"
                        isHomeRunTransitionVisible -> "homerun"
                        isScoreTransitionVisible -> "score"
                        isDoublePlayTransitionVisible -> "double_play"
                        isHitTransitionVisible -> "hit"
                        else -> "game"
                    }
                    AnimatedContent(
                        targetState = transitionState,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                                    (fadeOut() + scaleOut(targetScale = 1.04f))
                        },
                        label = "event_transition"
                    ) { state ->
                        when (state) {
                            "victory" -> PlayerTransitionScreen(victoryPlayer)
                            "homerun" -> PlayerTransitionScreen(homeRunPlayer)
                            "score" -> PlayerTransitionScreen(scorePlayer)
                            "double_play" -> PlayerTransitionScreen(doublePlayPlayer)
                            "hit" -> PlayerTransitionScreen(hitPlayer)
                            else -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (gameData != null) {
                                        LiveGameScreen(gameData = gameData!!)
                                    } else {
                                        NoGameScreen()
                                    }
                                    WatchEventOverlay(
                                        latestEvent = latestEvent,
                                        hideTypes = setOf("HOMERUN", "HIT", "DOUBLE_PLAY", "VICTORY") +
                                            (if (scoreTransitionToken != null) setOf("SCORE") else emptySet())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isAmbient) {
                val prompt = watchSyncPrompt
                if (prompt != null) {
                    WatchSyncPromptDialog(
                        prompt = prompt,
                        onAccept = {
                            WatchSyncResponseSender.send(
                                context = context,
                                gameId = prompt.gameId,
                                accepted = true
                            )
                            clearWatchSyncPrompt(context)
                            watchSyncPrompt = null
                        },
                        onDecline = {
                            WatchSyncResponseSender.send(
                                context = context,
                                gameId = prompt.gameId,
                                accepted = false
                            )
                            clearWatchSyncPrompt(context)
                            watchSyncPrompt = null
                        }
                    )
                }
            }
        }
    }
}

private fun readGameDataFromPrefs(context: Context): GameData? {
    val prefs = context.getSharedPreferences(
        DataLayerListenerService.GAME_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val gameId = prefs.getString(DataLayerListenerService.KEY_GAME_ID, "") ?: ""
    if (gameId.isBlank()) return null
    val inning = prefs.getString(DataLayerListenerService.KEY_INNING, "") ?: ""
    val status = prefs.getString(DataLayerListenerService.KEY_STATUS, "") ?: ""
    val isFinished = status.equals("FINISHED", ignoreCase = true) || isFinishedInning(inning)

    // 종료된 경기가 자정을 넘긴 경우 → 경기 없음으로 표시
    if (isFinished) {
        val updatedAt = prefs.getLong(DataLayerListenerService.KEY_GAME_UPDATED_AT, 0L)
        if (updatedAt > 0L) {
            val todayMidnight = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (updatedAt < todayMidnight) return null
        }
    }

    return GameData(
        gameId = gameId,
        homeTeam = prefs.getString(DataLayerListenerService.KEY_HOME_TEAM, "") ?: "",
        awayTeam = prefs.getString(DataLayerListenerService.KEY_AWAY_TEAM, "") ?: "",
        homeScore = prefs.getInt(DataLayerListenerService.KEY_HOME_SCORE, 0),
        awayScore = prefs.getInt(DataLayerListenerService.KEY_AWAY_SCORE, 0),
        inning = inning,
        isLive = !isFinished,
        ballCount = prefs.getInt(DataLayerListenerService.KEY_BALL, 0),
        strikeCount = prefs.getInt(DataLayerListenerService.KEY_STRIKE, 0),
        outCount = prefs.getInt(DataLayerListenerService.KEY_OUT, 0),
        bases = BaseStatus(
            first = prefs.getBoolean(DataLayerListenerService.KEY_BASE_FIRST, false),
            second = prefs.getBoolean(DataLayerListenerService.KEY_BASE_SECOND, false),
            third = prefs.getBoolean(DataLayerListenerService.KEY_BASE_THIRD, false)
        ),
        pitcher = prefs.getString(DataLayerListenerService.KEY_PITCHER, "") ?: "",
        batter = prefs.getString(DataLayerListenerService.KEY_BATTER, "") ?: "",
        scoreDiff = 0,
        myTeamName = prefs.getString(DataLayerListenerService.KEY_MY_TEAM, "") ?: ""
    )
}

private data class WatchEventInfo(
    val type: String,
    val timestamp: Long
)

private data class WatchEventUi(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private data class WatchSyncPrompt(
    val gameId: String,
    val homeTeam: String,
    val awayTeam: String
)

private const val EVENT_OVERLAY_DURATION_MS = 2200L
private const val EVENT_OVERLAY_FRESHNESS_MS = 5000L
private const val HOMERUN_SCREEN_DURATION_MS = 5100L
private const val HIT_SCREEN_DURATION_MS = 4100L
private const val DOUBLE_PLAY_SCREEN_DURATION_MS = 4100L
private const val SCORE_SCREEN_DURATION_MS = 4100L
private const val VICTORY_SCREEN_DURATION_MS = 4100L

private fun isTopInning(inning: String): Boolean {
    val normalized = inning.trim()
    val upper = normalized.uppercase()
    return normalized.contains("\uCD08") || upper.startsWith("TOP") || Regex("^T\\d+").containsMatchIn(upper)
}

private fun isBottomInning(inning: String): Boolean {
    val normalized = inning.trim()
    val upper = normalized.uppercase()
    return normalized.contains("\uB9D0") || upper.startsWith("BOT") || Regex("^B\\d+").containsMatchIn(upper)
}

private fun isFinishedInning(inning: String): Boolean {
    val upper = inning.uppercase()
    return inning.contains("\uACBD\uAE30 \uC885\uB8CC") || upper.contains("FINAL") || upper.contains("GAME OVER")
}
private fun readLatestEventFromPrefs(context: Context): WatchEventInfo? {
    val prefs = context.getSharedPreferences(
        DataLayerListenerService.GAME_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val type = prefs.getString(DataLayerListenerService.KEY_LAST_EVENT_TYPE, "") ?: ""
    val timestamp = prefs.getLong(DataLayerListenerService.KEY_LAST_EVENT_AT, 0L)
    if (type.isBlank() || timestamp <= 0L) return null
    return WatchEventInfo(type = type, timestamp = timestamp)
}

private fun readWatchSyncPromptFromPrefs(context: Context): WatchSyncPrompt? {
    val prefs = context.getSharedPreferences(
        DataLayerListenerService.GAME_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val gameId = prefs.getString(DataLayerListenerService.KEY_PENDING_SYNC_GAME_ID, "") ?: ""
    if (gameId.isBlank()) return null
    return WatchSyncPrompt(
        gameId = gameId,
        homeTeam = prefs.getString(DataLayerListenerService.KEY_PENDING_SYNC_HOME_TEAM, "") ?: "",
        awayTeam = prefs.getString(DataLayerListenerService.KEY_PENDING_SYNC_AWAY_TEAM, "") ?: ""
    )
}

private fun clearWatchSyncPrompt(context: Context) {
    context.getSharedPreferences(
        DataLayerListenerService.GAME_PREFS_NAME,
        Context.MODE_PRIVATE
    ).edit()
        .remove(DataLayerListenerService.KEY_PENDING_SYNC_GAME_ID)
        .remove(DataLayerListenerService.KEY_PENDING_SYNC_HOME_TEAM)
        .remove(DataLayerListenerService.KEY_PENDING_SYNC_AWAY_TEAM)
        .remove(DataLayerListenerService.KEY_PENDING_SYNC_MY_TEAM)
        .apply()
}

@Composable
private fun WatchEventOverlay(latestEvent: WatchEventInfo?, hideTypes: Set<String> = emptySet()) {
    val uiProfile = rememberWatchUiProfile()
    var visibleEvent by remember { mutableStateOf<WatchEventInfo?>(null) }

    LaunchedEffect(latestEvent?.timestamp) {
        val event = latestEvent ?: return@LaunchedEffect
        if (System.currentTimeMillis() - event.timestamp > EVENT_OVERLAY_FRESHNESS_MS) {
            return@LaunchedEffect
        }
        if (event.type.uppercase() in hideTypes) return@LaunchedEffect
        visibleEvent = event
        delay(EVENT_OVERLAY_DURATION_MS)
        if (visibleEvent?.timestamp == event.timestamp) {
            visibleEvent = null
        }
    }

    val event = visibleEvent ?: return
    val eventUi = eventUiFor(event.type) ?: return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(uiProfile.promptCardCornerDp.dp)
                )
                .padding(
                    horizontal = (uiProfile.promptOuterHorizontalPaddingDp + 2).dp,
                    vertical = 10.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = eventUi.icon,
                contentDescription = eventUi.label,
                tint = eventUi.color
            )
            Text(
                text = eventUi.label,
                color = Color.White,
                fontSize = uiProfile.promptQuestionSp.sp
            )
        }
    }
}

@Composable
private fun WatchSyncPromptDialog(
    prompt: WatchSyncPrompt,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val uiProfile = rememberWatchUiProfile()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = uiProfile.promptOuterHorizontalPaddingDp.dp)
                .background(
                    color = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(uiProfile.promptCardCornerDp.dp)
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "경기를 관람하겠습니까?",
                color = Color.White,
                fontSize = uiProfile.promptQuestionSp.sp
            )
            val matchup = listOf(prompt.awayTeam, prompt.homeTeam)
                .filter { it.isNotBlank() }
                .joinToString(" vs ")
            if (matchup.isNotBlank()) {
                Text(
                    text = matchup,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = (uiProfile.promptQuestionSp - 1).sp
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("예")
                }
                Button(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("아니오")
                }
            }
        }
    }
}

@Preview(name = "Prompt Small Round", device = "id:wearos_small_round")
@Composable
private fun WatchSyncPromptPreviewSmallRound() {
    BaseHapticWatchTheme(teamName = "SSG") {
        WatchSyncPromptDialog(
            prompt = WatchSyncPrompt(
                gameId = "20260321SSLG0001",
                homeTeam = "LG",
                awayTeam = "SSG"
            ),
            onAccept = {},
            onDecline = {}
        )
    }
}

@Preview(name = "Prompt Large Round", device = "id:wearos_large_round")
@Composable
private fun WatchSyncPromptPreviewLargeRound() {
    BaseHapticWatchTheme(teamName = "SSG") {
        WatchSyncPromptDialog(
            prompt = WatchSyncPrompt(
                gameId = "20260321SSLG0001",
                homeTeam = "LG",
                awayTeam = "SSG"
            ),
            onAccept = {},
            onDecline = {}
        )
    }
}

@Preview(name = "Prompt Square", device = "id:wearos_square")
@Composable
private fun WatchSyncPromptPreviewSquare() {
    BaseHapticWatchTheme(teamName = "SSG") {
        WatchSyncPromptDialog(
            prompt = WatchSyncPrompt(
                gameId = "20260321SSLG0001",
                homeTeam = "LG",
                awayTeam = "SSG"
            ),
            onAccept = {},
            onDecline = {}
        )
    }
}


@Composable
private fun AmbientGameScreen(gameData: GameData?) {
    val uiProfile = rememberWatchUiProfile()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (gameData != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Score: "AWAY 4 - 5 HOME"
                Text(
                    text = "${gameData.awayTeam} ${gameData.awayScore} - ${gameData.homeScore} ${gameData.homeTeam}",
                    color = Color.White,
                    fontSize = uiProfile.scoreValueSp.sp
                )
                // Inning
                Text(
                    text = gameData.inning,
                    color = Color.Gray,
                    fontSize = uiProfile.inningTextSp.sp
                )
            }
        } else {
            Text(
                text = "BaseHaptic",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PlayerTransitionScreen(player: ExoPlayer) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
            }
        )
    }
}

private fun eventUiFor(type: String): WatchEventUi? {
    return when (type.uppercase()) {
        "WALK" -> WatchEventUi("WALK", Icons.Default.Bolt, Color(0xFF4ADE80))
        "STEAL" -> WatchEventUi("STEAL", Icons.Default.Bolt, Color(0xFF06B6D4))
        "SCORE" -> WatchEventUi("SCORE", Icons.Default.EmojiEvents, Color(0xFFEAB308))
        "OUT" -> WatchEventUi("OUT", Icons.Default.HighlightOff, Color(0xFFEF4444))
        "TRIPLE_PLAY" -> WatchEventUi("TRIPLE PLAY", Icons.Default.HighlightOff, Color(0xFFDC2626))
        else -> null
    }
}
