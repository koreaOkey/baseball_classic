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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Green500
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Orange500
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.ui.theme.Yellow500
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
                .padding(16.dp)
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
                        modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 16.dp)
                    )
                } else if (gameState == null) {
                    Text(
                        text = loadError ?: "경기 데이터를 불러오는 중...",
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 16.dp)
                    )
                } else {
                    ScoreboardCard(state = gameState!!, events = events)
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(12.dp),
            color = Gray900
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = null,
                    tint = if (syncedGameId.isNullOrBlank()) Gray400 else Green500
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = when {
                        syncedGameId.isNullOrBlank() -> "워치 동기화 꺼짐"
                        syncedGameId == gameId -> "워치가 현재 경기와 동기화 중"
                        else -> "워치 동기화 대상: $syncedGameId"
                    },
                    color = if (syncedGameId.isNullOrBlank()) Gray400 else Green500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "실시간 이벤트",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = "아직 이벤트가 없습니다.",
                        color = Gray500,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(events, key = { it.cursor }) { event ->
                    EventCard(event = event)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusLine(status = state.status, inning = state.inning),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "GAME ${state.gameId}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            TeamScoreRow(
                team = state.awayTeamId,
                teamName = state.awayTeamId.teamName,
                score = state.awayScore
            )
            Spacer(modifier = Modifier.height(10.dp))
            TeamScoreRow(
                team = state.homeTeamId,
                teamName = state.homeTeamId.teamName,
                score = state.homeScore
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountChip(label = "B", value = state.ball)
                CountChip(label = "S", value = state.strike)
                CountChip(label = "O", value = state.out)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "주자 ${baseText(state)}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "투수 ${state.pitcher.ifBlank { "-" }} · 타자 ${state.batter.ifBlank { "-" }}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )

            val latestEvent = events.firstOrNull()
            if (latestEvent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "최근 이벤트 ${latestEvent.type}: ${latestEvent.description.ifBlank { "-" }}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
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
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = teamName,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        }
        Text(
            text = score.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )
    }
}

@Composable
private fun CountChip(label: String, value: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Gray900.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "$label $value",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EventCard(event: BackendGamesRepository.LiveEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Gray900)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.type,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = eventColor(event.type)
                )
                Text(
                    text = event.time,
                    fontSize = 12.sp,
                    color = Gray400
                )
            }
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
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

private fun eventColor(type: String): Color {
    return when (type.uppercase()) {
        "HOMERUN", "SCORE", "SAC_FLY_SCORE" -> Yellow500
        "HIT", "STEAL", "WALK" -> Green500
        "DOUBLE_PLAY", "TRIPLE_PLAY" -> Orange500
        "OUT", "STRIKE" -> Red500
        "BALL" -> Gray400
        else -> Gray500
    }
}
