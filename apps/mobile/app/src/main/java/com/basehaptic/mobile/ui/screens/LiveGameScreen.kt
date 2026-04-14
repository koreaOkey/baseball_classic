package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.AppEventColors
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Green500
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun LiveGameScreen(
    activeTheme: ThemeData?,
    gameId: String?,
    syncedGameId: String?,
    onBack: () -> Unit
) {
    val teamTheme = LocalTeamTheme.current
    val primaryColor = activeTheme?.colors?.primary ?: teamTheme.primary

    var gameState by remember(gameId) { mutableStateOf<BackendGamesRepository.LiveGameState?>(null) }
    var events by remember(gameId) { mutableStateOf<List<BackendGamesRepository.LiveEvent>>(emptyList()) }
    var loadError by remember(gameId) { mutableStateOf<String?>(null) }

    LaunchedEffect(gameId) {
        if (gameId.isNullOrBlank()) return@LaunchedEffect

        var cursor = 0L
        var localEvents: List<BackendGamesRepository.LiveEvent> = emptyList()
        val reconnectDelaysMs = listOf(1000L, 2000L, 5000L, 10000L)
        var reconnectAttempt = 0

        suspend fun mergeEvents(incoming: List<BackendGamesRepository.LiveEvent>) {
            if (incoming.isEmpty()) return
            val sorted = incoming.sortedByDescending { it.cursor }
            localEvents = (sorted + localEvents)
                .distinctBy { it.cursor }
                .sortedByDescending { it.cursor }
                .take(80)
            events = localEvents
            cursor = max(cursor, incoming.maxOfOrNull { it.cursor } ?: cursor)
        }

        suspend fun runRecoveryPull() {
            val fetchedState = runCatching {
                withContext(Dispatchers.IO) {
                    BackendGamesRepository.fetchGameState(gameId)
                }
            }.getOrNull()
            if (fetchedState != null) {
                gameState = fetchedState
                loadError = null
            } else if (gameState == null) {
                loadError = "백엔드 경기 상태를 가져오지 못했습니다."
            }

            val fetchedEvents = runCatching {
                withContext(Dispatchers.IO) {
                    BackendGamesRepository.fetchGameEvents(gameId = gameId, after = cursor, limit = 200)
                }
            }.getOrNull()

            mergeEvents(fetchedEvents?.items.orEmpty())
            if (fetchedEvents != null) {
                cursor = max(cursor, fetchedEvents.nextCursor ?: cursor)
            }
        }

        while (currentCoroutineContext().isActive) {
            runRecoveryPull()

            runCatching {
                BackendGamesRepository.streamGame(gameId).collect { message ->
                    when (message) {
                        BackendGamesRepository.LiveStreamMessage.Connected -> {
                            reconnectAttempt = 0
                            loadError = null
                        }

                        BackendGamesRepository.LiveStreamMessage.Closed -> {
                            throw IllegalStateException("live stream closed")
                        }

                        is BackendGamesRepository.LiveStreamMessage.Error -> {
                            throw message.throwable
                        }

                        is BackendGamesRepository.LiveStreamMessage.Events -> {
                            mergeEvents(message.items)
                        }

                        is BackendGamesRepository.LiveStreamMessage.State -> {
                            gameState = message.state
                            loadError = null
                        }

                        is BackendGamesRepository.LiveStreamMessage.Update -> {
                            mergeEvents(message.events)
                            message.state?.let {
                                gameState = it
                                loadError = null
                            }
                        }

                        is BackendGamesRepository.LiveStreamMessage.Pong -> Unit
                    }
                }
            }.onFailure {
                if (gameState == null) {
                    loadError = "실시간 연결이 불안정합니다. 재연결 중..."
                }
            }

            if (!currentCoroutineContext().isActive) break
            val delayMs = reconnectDelaysMs[reconnectAttempt.coerceAtMost(reconnectDelaysMs.lastIndex)]
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(reconnectDelaysMs.lastIndex)
            delay(delayMs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.85f))
                    )
                )
                .padding(AppSpacing.lg)
        ) {
            Column {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                if (gameId.isNullOrBlank()) {
                    Text(
                        text = "선택한 경기가 없습니다.",
                        color = Color.White,
                        modifier = Modifier.padding(top = AppSpacing.sm, start = AppSpacing.sm, end = AppSpacing.sm, bottom = AppSpacing.lg)
                    )
                } else if (gameState == null) {
                    Text(
                        text = loadError ?: "경기 데이터를 불러오는 중...",
                        color = Color.White,
                        modifier = Modifier.padding(top = AppSpacing.sm, start = AppSpacing.sm, end = AppSpacing.sm, bottom = AppSpacing.lg)
                    )
                } else {
                    ScoreboardCard(state = gameState!!, events = events)
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            shape = AppShapes.md,
            color = Gray900
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = null,
                    tint = if (syncedGameId.isNullOrBlank()) Gray400 else Green500
                )
                Spacer(modifier = Modifier.size(AppSpacing.sm))
                Text(
                    text = when {
                        syncedGameId.isNullOrBlank() -> "워치 동기화 꺼짐"
                        syncedGameId == gameId -> "워치가 현재 경기와 동기화 중"
                        else -> "워치 동기화 대상: $syncedGameId"
                    },
                    color = if (syncedGameId.isNullOrBlank()) Gray400 else Green500,
                    style = AppFont.captionMedium
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = AppSpacing.lg)
        ) {
            item {
                Text(
                    text = "실시간 이벤트",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = AppSpacing.sm)
                )
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = "아직 이벤트가 없습니다.",
                        color = Gray500,
                        modifier = Modifier.padding(vertical = AppSpacing.md)
                    )
                }
            } else {
                items(events, key = { it.cursor }) { event ->
                    EventCard(event = event)
                }
            }

            item { Spacer(modifier = Modifier.height(AppSpacing.bottomSafeSpacer)) }
        }
    }
}

@Composable
private fun ScoreboardCard(
    state: BackendGamesRepository.LiveGameState,
    events: List<BackendGamesRepository.LiveEvent>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        shape = AppShapes.md,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusLine(status = state.status, inning = state.inning),
                    color = Color.White,
                    style = AppFont.captionSemibold
                )
                Text(
                    text = "GAME ${state.gameId}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = AppFont.tiny
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            TeamScoreRow(
                team = state.awayTeamId,
                teamName = state.awayTeamId.teamName,
                score = state.awayScore
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))
            TeamScoreRow(
                team = state.homeTeamId,
                teamName = state.homeTeamId.teamName,
                score = state.homeScore
            )

            Spacer(modifier = Modifier.height(AppSpacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountChip(label = "B", value = state.ball)
                CountChip(label = "S", value = state.strike)
                CountChip(label = "O", value = state.out)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "주자 ${baseText(state)}",
                    color = Color.White.copy(alpha = 0.9f),
                    style = AppFont.micro
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.md))
            Text(
                text = "투수 ${state.pitcher.ifBlank { "-" }} · 타자 ${state.batter.ifBlank { "-" }}",
                color = Color.White.copy(alpha = 0.9f),
                style = AppFont.micro
            )

            val latestEvent = events.firstOrNull()
            if (latestEvent != null) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Text(
                    text = "최근 이벤트 ${latestEvent.type}: ${latestEvent.description.ifBlank { "-" }}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = AppFont.micro,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TeamScoreRow(
    team: Team,
    teamName: String,
    score: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(team = team, size = 52.dp)
            Spacer(modifier = Modifier.size(AppSpacing.md))
            Text(
                text = teamName,
                color = Color.White,
                style = AppFont.bodyLgMedium
            )
        }
        Text(
            text = score.toString(),
            color = Color.White,
            style = AppFont.h2
        )
    }
}

@Composable
private fun CountChip(label: String, value: Int) {
    Box(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(Gray900.copy(alpha = 0.55f))
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
    ) {
        Text(
            text = "$label $value",
            color = Color.White,
            style = AppFont.micro
        )
    }
}

@Composable
private fun EventCard(event: BackendGamesRepository.LiveEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        shape = AppShapes.md,
        colors = CardDefaults.cardColors(containerColor = Gray900)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.type,
                    style = AppFont.captionBold,
                    color = AppEventColors.eventColor(event.type)
                )
                Text(
                    text = event.time,
                    style = AppFont.micro,
                    color = Gray400
                )
            }
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    style = AppFont.caption,
                    color = Color.White,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
            }
        }
    }
}

private fun baseText(state: BackendGamesRepository.LiveGameState): String {
    val bases = ArrayList<String>(3)
    if (state.baseFirst) bases.add("1")
    if (state.baseSecond) bases.add("2")
    if (state.baseThird) bases.add("3")
    return if (bases.isEmpty()) "없음" else bases.joinToString(",")
}

private fun statusLine(status: GameStatus, inning: String): String {
    return when (status) {
        GameStatus.LIVE -> {
            val inningText = inning.ifBlank { "진행 중" }
            "경기 중 · $inningText"
        }
        GameStatus.SCHEDULED -> "경기 전"
        GameStatus.FINISHED -> "경기 종료"
        GameStatus.CANCELED -> "우천 취소"
        GameStatus.POSTPONED -> "경기 연기"
    }
}
