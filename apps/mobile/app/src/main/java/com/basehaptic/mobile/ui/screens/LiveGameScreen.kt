package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.R
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.WatchSyncAdLedger
import com.basehaptic.mobile.data.model.AtBatGroup
import com.basehaptic.mobile.data.model.EventFilterGate
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team

import com.basehaptic.mobile.ui.components.RewardedAdManager
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.AppEventColors
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray100
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray600
import com.basehaptic.mobile.ui.theme.Gray700
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Green400
import com.basehaptic.mobile.ui.theme.Green500
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.ui.theme.Yellow400
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
    gameId: String?,
    syncedGameId: String?,
    onSetSyncedGame: (String?) -> Unit,
    onBack: () -> Unit
) {
    var gameState by remember(gameId) { mutableStateOf<BackendGamesRepository.LiveGameState?>(null) }
    var events by remember(gameId) { mutableStateOf<List<BackendGamesRepository.LiveEvent>>(emptyList()) }
    var loadError by remember(gameId) { mutableStateOf<String?>(null) }

    val currentLineup: FieldLineup? = run {
        if (BuildConfig.DEBUG && gameId == "debug-watch-sync-test") return@run DebugDummyLiveGame.lineup
        gameState?.let { FieldLineup.from(it) }
    }

    var selectedInningNumber by remember(gameId) { mutableStateOf<Int?>(null) }
    var hasManualInningSelection by remember(gameId) { mutableStateOf(false) }
    var isScoreFilterActive by remember(gameId) { mutableStateOf(false) }

    LaunchedEffect(gameState?.inning) {
        val inning = gameState?.inning ?: return@LaunchedEffect
        if (hasManualInningSelection) return@LaunchedEffect
        val n = inningNumber(inning)
        if (n > 0) selectedInningNumber = n
    }

    val filteredEvents = run {
        if (isScoreFilterActive) {
            return@run events.filter { event ->
                val t = event.type.uppercase()
                t == "SCORE" || t == "SAC_FLY_SCORE"
            }
        }
        val n = selectedInningNumber ?: return@run events
        events.filter { event ->
            val inn = event.inning ?: return@filter false
            inningNumber(inn) == n
        }
    }

    val filteredAtBats = AtBatGroup.group(filteredEvents)

    LaunchedEffect(gameId) {
        if (gameId.isNullOrBlank()) return@LaunchedEffect

        if (BuildConfig.DEBUG && gameId == "debug-watch-sync-test") {
            gameState = DebugDummyLiveGame.state
            events = DebugDummyLiveGame.events
            loadError = null
            return@LaunchedEffect
        }

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
        DetailTopBar(
            state = gameState,
            gameId = gameId,
            syncedGameId = syncedGameId,
            onSetSyncedGame = onSetSyncedGame,
            onBack = onBack
        )

        if (gameId.isNullOrBlank() || gameState == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(AppSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (gameId.isNullOrBlank()) {
                        "선택한 경기가 없습니다."
                    } else {
                        loadError ?: "경기 데이터를 불러오는 중..."
                    },
                    color = Gray400,
                    style = AppFont.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val state = gameState!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                item {
                    ScoreboardCard(state = state, latestEvent = events.firstOrNull())
                }

                item {
                    BaseballFieldCard(state = state, latestEvent = events.firstOrNull(), lineup = currentLineup)
                }

                item {
                    InningTabs(
                        state = state,
                        selectedInningNumber = selectedInningNumber,
                        isScoreFilterActive = isScoreFilterActive,
                        onSelectInning = { n ->
                            isScoreFilterActive = false
                            selectedInningNumber = n
                            hasManualInningSelection = true
                        },
                        onSelectScore = {
                            isScoreFilterActive = true
                            selectedInningNumber = null
                            hasManualInningSelection = true
                        }
                    )
                }

                item {
                    CurrentMatchupCard(state = state, latestEvent = events.firstOrNull())
                }

                item {
                    Text(
                        text = "실시간 중계",
                        style = AppFont.h5Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = AppSpacing.sm)
                    )
                }

                if (filteredEvents.isEmpty()) {
                    item {
                        EmptyInningEventCard()
                    }
                } else {
                    itemsIndexed(filteredAtBats, key = { _, g -> g.id }) { index, group ->
                        val prevKey = filteredAtBats.getOrNull(index - 1)?.let(::sectionKey)
                        val currKey = sectionKey(group)
                        if (index == 0 || prevKey != currKey) {
                            AtBatSectionHeader(title = sectionTitle(group, state))
                        }
                        AtBatCard(
                            group = group,
                            awayTeamName = state.awayTeamId.teamName,
                            homeTeamName = state.homeTeamId.teamName,
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(AppSpacing.bottomSafeSpacer)) }
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    state: BackendGamesRepository.LiveGameState?,
    gameId: String?,
    syncedGameId: String?,
    onSetSyncedGame: (String?) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            WatchSyncBadge(
                gameId = gameId,
                syncedGameId = syncedGameId,
                onSetSyncedGame = onSetSyncedGame
            )

            if (state?.status == GameStatus.LIVE) {
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                LiveBadge()
            }
        }

        Text(
            text = "경기 상세",
            color = Color.White,
            style = AppFont.h5Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun LiveBadge() {
    Surface(
        shape = AppShapes.pill,
        color = Red500.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Red500.copy(alpha = 0.44f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(AppSpacing.sm)
                    .clip(CircleShape)
                    .background(Red500)
            )
            Spacer(modifier = Modifier.width(AppSpacing.xs))
            Text(text = "LIVE", style = AppFont.microBold, color = Red500)
        }
    }
}

@Composable
private fun ScoreboardCard(
    state: BackendGamesRepository.LiveGameState,
    latestEvent: BackendGamesRepository.LiveEvent?
) {
    val favoriteTeam = LocalTeamTheme.current.team

    Card(
        colors = CardDefaults.cardColors(containerColor = Gray950),
        shape = AppShapes.lg,
        border = BorderStroke(1.dp, Gray800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Gray950,
                            Gray900,
                            AppEventColors.eventColor(state.lastEventType.orEmpty()).copy(alpha = 0.12f)
                        )
                    )
                )
                .padding(AppSpacing.lg)
        ) {
            Text(
                text = currentAttackLabel(state),
                color = Yellow400,
                style = AppFont.captionBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(AppSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                ScoreTeamBlock(
                    team = state.awayTeamId,
                    teamName = state.awayTeamId.teamName,
                    score = state.awayScore,
                    showFavorite = favoriteTeam == state.awayTeamId && favoriteTeam != Team.NONE
                )

                ScoreStateBlock(
                    state = state,
                    latestEvent = latestEvent,
                    modifier = Modifier
                        .padding(horizontal = AppSpacing.sm)
                        .width(118.dp)
                )

                ScoreTeamBlock(
                    team = state.homeTeamId,
                    teamName = state.homeTeamId.teamName,
                    score = state.homeScore,
                    showFavorite = favoriteTeam == state.homeTeamId && favoriteTeam != Team.NONE
                )
            }
        }
    }
}

@Composable
private fun RowScope.ScoreTeamBlock(
    team: Team,
    teamName: String,
    score: Int,
    showFavorite: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        TeamLogo(team = team, size = 60.dp)
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(text = teamName, color = Color.White, style = AppFont.h4Bold, maxLines = 1)
        Text(text = score.toString(), color = Color.White, style = AppFont.h1)
        if (showFavorite) {
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            FavoriteTeamBadge()
        }
    }
}

@Composable
private fun ScoreStateBlock(
    state: BackendGamesRepository.LiveGameState,
    latestEvent: BackendGamesRepository.LiveEvent?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScoreboardBaseDiamond(state = state)
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            CountDots(label = "B", value = state.ball, max = 3, activeColor = Green400)
            CountDots(label = "S", value = state.strike, max = 2, activeColor = Yellow400)
            CountDots(label = "O", value = state.out, max = 2, activeColor = Red500)
        }
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Text(
            text = "P ${displayPitcher(state, latestEvent)}  |  B ${displayBatter(state, latestEvent)}",
            color = Gray400,
            style = AppFont.microBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScoreboardBaseDiamond(state: BackendGamesRepository.LiveGameState) {
    Canvas(modifier = Modifier.size(width = 86.dp, height = 78.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val second = Offset(c.x, c.y - 18.dp.toPx())
        val first = Offset(c.x + 18.dp.toPx(), c.y)
        val third = Offset(c.x - 18.dp.toPx(), c.y)
        val home = Offset(c.x, c.y + 18.dp.toPx())

        fun drawBase(center: Offset, occupied: Boolean) {
            val baseSize = 34.dp.toPx()
            val path = Path().apply {
                moveTo(center.x, center.y - baseSize / 2)
                lineTo(center.x + baseSize / 2, center.y)
                lineTo(center.x, center.y + baseSize / 2)
                lineTo(center.x - baseSize / 2, center.y)
                close()
            }
            drawPath(path, if (occupied) Yellow500 else Gray800)
            drawPath(path, Color.Black.copy(alpha = 0.36f), style = Stroke(width = 1.dp.toPx()))
        }

        drawBase(second, state.baseSecond)
        drawBase(first, state.baseFirst)
        drawBase(third, state.baseThird)
        drawBase(home, false)
    }
}

@Composable
private fun FavoriteTeamBadge() {
    Surface(
        shape = AppShapes.pill,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Yellow500)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(text = "응원팀", color = Yellow500, style = AppFont.microBold)
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Yellow500,
                modifier = Modifier.size(AppSpacing.md)
            )
        }
    }
}

@Composable
private fun BaseballFieldCard(
    state: BackendGamesRepository.LiveGameState,
    latestEvent: BackendGamesRepository.LiveEvent?,
    lineup: FieldLineup?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2A18)),
        shape = AppShapes.lg,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val w = maxWidth
            val h = maxHeight

            Image(
                painter = painterResource(R.drawable.baseball_field_background),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            FieldLabel(lineup?.leftFielder, FieldPositions.leftField, w, h)
            FieldLabel(lineup?.centerFielder, FieldPositions.centerField, w, h)
            FieldLabel(lineup?.rightFielder, FieldPositions.rightField, w, h)
            FieldLabel(lineup?.shortstop, FieldPositions.shortstop, w, h)
            FieldLabel(lineup?.secondBaseman, FieldPositions.secondBaseman, w, h)
            FieldLabel(lineup?.thirdBaseman, FieldPositions.thirdBaseman, w, h)
            FieldLabel(lineup?.firstBaseman, FieldPositions.firstBaseman, w, h)
            FieldLabel(lineup?.catcher, FieldPositions.catcher, w, h)

            val pitcherName = displayPitcher(state, latestEvent, placeholder = "")
            if (pitcherName.isNotEmpty()) {
                AtFieldPosition(FieldPositions.pitcher, w, h) {
                    PositionPill(text = pitcherName, modifier = Modifier, highlighted = false)
                }
            }

            val batterName = displayBatter(state, latestEvent, placeholder = "")
            if (batterName.isNotEmpty()) {
                AtFieldPosition(FieldPositions.batter, w, h) {
                    PositionPill(text = batterName, modifier = Modifier, highlighted = true)
                }
            }

            if (state.baseFirst && !lineup?.firstRunner.isNullOrEmpty()) {
                AtFieldPosition(FieldPositions.firstBase, w, h) {
                    PositionPill(text = lineup!!.firstRunner!!, modifier = Modifier, highlighted = true)
                }
            }
            if (state.baseSecond && !lineup?.secondRunner.isNullOrEmpty()) {
                AtFieldPosition(FieldPositions.secondBase, w, h) {
                    PositionPill(text = lineup!!.secondRunner!!, modifier = Modifier, highlighted = true)
                }
            }
            if (state.baseThird && !lineup?.thirdRunner.isNullOrEmpty()) {
                AtFieldPosition(FieldPositions.thirdBase, w, h) {
                    PositionPill(text = lineup!!.thirdRunner!!, modifier = Modifier, highlighted = true)
                }
            }
        }
    }
}

/// 야구장 9명 수비 라인업 + 루상 주자 이름. 백엔드 라인업 API 도입 전까지는 DEBUG 더미에서만 채움.
data class FieldLineup(
    val leftFielder: String? = null,
    val centerFielder: String? = null,
    val rightFielder: String? = null,
    val shortstop: String? = null,
    val secondBaseman: String? = null,
    val thirdBaseman: String? = null,
    val firstBaseman: String? = null,
    val catcher: String? = null,
    val firstRunner: String? = null,
    val secondRunner: String? = null,
    val thirdRunner: String? = null,
) {
    companion object {
        /// 백엔드 응답의 라인업을 수비팀 기준으로 매핑.
        /// 이닝 "초"=홈수비, "말"=어웨이수비. 라이브 외(SCHEDULED "경기전" 등)는 1회초가
        /// 시작될 예정이므로 home 수비를 가정 — 라인업이 30분 전 노출되는 시점부터 카드가
        /// 비지 않는다. 선택된 수비팀 라인업이 비어있으면 반대편으로 폴백.
        fun from(state: BackendGamesRepository.LiveGameState): FieldLineup? {
            val preferHome = !state.inning.contains("말")
            val primary = if (preferHome) state.homeLineup else state.awayLineup
            val fallback = if (preferHome) state.awayLineup else state.homeLineup
            val defending = if (primary.isEmpty()) fallback else primary
            if (defending.isEmpty()) return null
            val slots = mutableMapOf<String, String>()
            for (slot in defending) {
                if (!slot.isActive) continue
                val posName = slot.positionName?.takeIf { it.isNotEmpty() } ?: continue
                if (posName !in slots) slots[posName] = slot.playerName
            }
            return FieldLineup(
                leftFielder = slots["좌익수"],
                centerFielder = slots["중견수"],
                rightFielder = slots["우익수"],
                shortstop = slots["유격수"],
                secondBaseman = slots["2루수"],
                thirdBaseman = slots["3루수"],
                firstBaseman = slots["1루수"],
                catcher = slots["포수"],
            )
        }
    }
}

/// 야구장 배경 이미지 위의 정규화 좌표(0.0~1.0). iOS 와 동일 값.
private object FieldPositions {
    val leftField = Pair(0.17f, 0.17f)
    val centerField = Pair(0.50f, 0.06f)
    val rightField = Pair(0.83f, 0.17f)
    val shortstop = Pair(0.35f, 0.28f)
    val secondBaseman = Pair(0.65f, 0.28f)
    val thirdBaseman = Pair(0.21f, 0.40f)
    val firstBaseman = Pair(0.79f, 0.40f)
    val pitcher = Pair(0.50f, 0.52f)
    val catcher = Pair(0.50f, 0.91f)
    val batter = Pair(0.45f, 0.83f)
    val firstBase = Pair(0.74f, 0.50f)
    val secondBase = Pair(0.50f, 0.21f)
    val thirdBase = Pair(0.26f, 0.51f)
}

@Composable
private fun BoxWithConstraintsScope.FieldLabel(
    name: String?,
    point: Pair<Float, Float>,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp
) {
    if (name.isNullOrEmpty()) return
    AtFieldPosition(point, w, h) {
        PositionPill(text = name, modifier = Modifier, highlighted = false)
    }
}

@Composable
private fun BoxWithConstraintsScope.AtFieldPosition(
    point: Pair<Float, Float>,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .absoluteOffset(x = w * point.first, y = h * point.second)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(-placeable.width / 2, -placeable.height / 2)
                }
            }
    ) {
        content()
    }
}

@Composable
private fun PositionPill(text: String, modifier: Modifier, highlighted: Boolean) {
    Surface(
        modifier = modifier,
        shape = AppShapes.pill,
        color = if (highlighted) Yellow500 else Color.Black.copy(alpha = 0.46f)
    ) {
        Text(
            text = text,
            style = AppFont.microBold,
            color = if (highlighted) Gray950 else Color.White,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
        )
    }
}

@Composable
private fun InningTabs(
    state: BackendGamesRepository.LiveGameState,
    selectedInningNumber: Int?,
    isScoreFilterActive: Boolean,
    onSelectInning: (Int) -> Unit,
    onSelectScore: () -> Unit,
) {
    val tabs = listOf("득점") + (1..9).map { "${it}회" }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(tabs) { tab ->
            val isScore = tab == "득점"
            val tabNumber = if (isScore) null else tab.removeSuffix("회").toIntOrNull()
            val selected = if (isScore) isScoreFilterActive
            else (!isScoreFilterActive && tabNumber == selectedInningNumber)
            Surface(
                shape = AppShapes.pill,
                color = if (selected) Yellow500 else Gray900,
                border = BorderStroke(1.dp, if (selected) Yellow500 else Gray800),
                modifier = Modifier.clickable {
                    if (isScore) onSelectScore() else tabNumber?.let { onSelectInning(it) }
                }
            ) {
                Text(
                    text = tab,
                    style = AppFont.captionBold,
                    color = if (selected) Gray950 else Gray400,
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                )
            }
        }
    }
}

@Composable
private fun EmptyInningEventCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900,
    ) {
        Text(
            text = "해당 회 이벤트가 없습니다",
            style = AppFont.bodyMedium,
            color = Gray400,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.xxxl)
        )
    }
}

@Composable
private fun CurrentMatchupCard(
    state: BackendGamesRepository.LiveGameState,
    latestEvent: BackendGamesRepository.LiveEvent?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "현재 승부", style = AppFont.captionBold, color = Yellow400)
                    Text(
                        text = "${displayPitcher(state, latestEvent)} vs ${displayBatter(state, latestEvent)}",
                        style = AppFont.bodyLgBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                }
                Text(
                    text = state.pitcherPitchCount?.let { "투구수 $it" } ?: "투구수 -",
                    style = AppFont.captionBold,
                    color = Gray100,
                    modifier = Modifier
                        .background(Gray800, AppShapes.pill)
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                )
            }

            if (latestEvent != null) {
                Spacer(modifier = Modifier.height(AppSpacing.md))
                EventSummaryLine(event = latestEvent)
            }
        }
    }
}

@Composable
private fun EventSummaryLine(event: BackendGamesRepository.LiveEvent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = eventLabel(event.type),
            style = AppFont.microBold,
            color = AppEventColors.eventColor(event.type),
            modifier = Modifier
                .background(AppEventColors.eventColor(event.type).copy(alpha = 0.16f), AppShapes.pill)
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
        )
        Spacer(modifier = Modifier.width(AppSpacing.sm))
        Text(
            text = event.description.ifBlank { "최근 이벤트 없음" },
            style = AppFont.caption,
            color = Gray100,
            maxLines = 1
        )
    }
}

@Composable
private fun WatchSyncBadge(
    gameId: String?,
    syncedGameId: String?,
    onSetSyncedGame: (String?) -> Unit
) {
    val context = LocalContext.current
    val isSyncedToCurrent = !gameId.isNullOrBlank() && syncedGameId == gameId
    val isInteractive = !gameId.isNullOrBlank()
    val accent = when {
        isSyncedToCurrent -> Green500
        syncedGameId.isNullOrBlank() -> Gray500
        else -> Yellow400
    }

    var visualOn by remember(gameId, syncedGameId) { mutableStateOf(isSyncedToCurrent) }
    var showEnableDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var isAdLoading by remember { mutableStateOf(false) }

    Surface(
        shape = AppShapes.pill,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(start = AppSpacing.sm, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            if (isAdLoading) {
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = AppSpacing.xs),
                    strokeWidth = 2.dp,
                    color = accent
                )
            } else if (isInteractive && gameId != null) {
                Switch(
                    checked = visualOn,
                    onCheckedChange = { requested ->
                        if (isAdLoading) return@Switch
                        visualOn = requested
                        if (requested) showEnableDialog = true else showDisableDialog = true
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Green500,
                        uncheckedThumbColor = Gray400,
                        uncheckedTrackColor = Gray800
                    ),
                    modifier = Modifier.scale(0.55f)
                )
                Text(
                    text = if (visualOn) "ON" else "OFF",
                    style = AppFont.microBold,
                    color = accent
                )
            } else {
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                Box(
                    modifier = Modifier
                        .size(AppSpacing.sm)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(AppSpacing.xs))
            }
        }
    }

    if (showEnableDialog && gameId != null) {
        AlertDialog(
            onDismissRequest = {
                showEnableDialog = false
                visualOn = isSyncedToCurrent
            },
            title = { Text(text = "워치로 보시겠습니까?") },
            text = { Text(text = "광고 관람 후 동기화됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showEnableDialog = false
                    if (WatchSyncAdLedger.hasViewed(context, gameId)) {
                        onSetSyncedGame(gameId)
                        return@TextButton
                    }
                    isAdLoading = true
                    RewardedAdManager.loadAndShowAd(
                        context = context,
                        adUnitId = RewardedAdManager.WATCH_SYNC_AD_UNIT,
                    ) { rewardEarned ->
                        isAdLoading = false
                        if (rewardEarned) {
                            WatchSyncAdLedger.markViewed(context, gameId)
                        }
                        onSetSyncedGame(gameId)
                    }
                }) {
                    Text(text = "확인")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEnableDialog = false
                    visualOn = isSyncedToCurrent
                }) {
                    Text(text = "취소")
                }
            }
        )
    }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = {
                showDisableDialog = false
                visualOn = isSyncedToCurrent
            },
            title = { Text(text = "워치 동기화를 끄시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showDisableDialog = false
                    onSetSyncedGame(null)
                }) {
                    Text(text = "확인")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisableDialog = false
                    visualOn = isSyncedToCurrent
                }) {
                    Text(text = "취소")
                }
            }
        )
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
private fun CountDots(label: String, value: Int, max: Int, activeColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Gray400,
            style = AppFont.microBold,
            modifier = Modifier.padding(end = AppSpacing.xs)
        )
        repeat(max) { index ->
            Box(
                modifier = Modifier
                    .padding(end = AppSpacing.xxs)
                    .size(AppSpacing.sm)
                    .clip(CircleShape)
                    .background(if (index < value) activeColor else Gray700)
            )
        }
    }
}

@Composable
private fun RunnerSummary(state: BackendGamesRepository.LiveGameState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Gray800, AppShapes.pill)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
    ) {
        MiniBase(occupied = state.baseThird)
        MiniBase(occupied = state.baseSecond)
        MiniBase(occupied = state.baseFirst)
        Spacer(modifier = Modifier.width(AppSpacing.xs))
        Text(text = baseText(state), color = Gray100, style = AppFont.microBold)
    }
}

@Composable
private fun MiniBase(occupied: Boolean) {
    Box(
        modifier = Modifier
            .padding(horizontal = AppSpacing.xxs)
            .size(AppSpacing.sm)
            .clip(CircleShape)
            .background(if (occupied) Yellow500 else Gray600)
    )
}

@Composable
private fun EmptyEventCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900
    ) {
        Text(
            text = "아직 이벤트가 없습니다.",
            color = Gray500,
            style = AppFont.bodyMedium,
            modifier = Modifier.padding(AppSpacing.lg)
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = eventLabel(event.type),
                        style = AppFont.captionBold,
                        color = AppEventColors.eventColor(event.type),
                        modifier = Modifier
                            .background(AppEventColors.eventColor(event.type).copy(alpha = 0.14f), AppShapes.pill)
                            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                    )
                    val matchup = listOfNotNull(event.pitcher, event.batter).joinToString(" → ")
                    if (matchup.isNotBlank()) {
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(text = matchup, style = AppFont.micro, color = Gray400, maxLines = 1)
                    }
                }
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

// MARK: - At-Bat Card (네이버 릴레이 스타일 타석 단위 카드)

@Composable
private fun AtBatSectionHeader(title: String) {
    Text(
        text = title,
        style = AppFont.captionBold,
        color = Gray400,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.sm)
    )
}

private fun sectionKey(group: AtBatGroup): String = group.inning ?: "?"

private fun sectionTitle(group: AtBatGroup, state: BackendGamesRepository.LiveGameState): String {
    val inning = group.inning?.takeIf { it.isNotEmpty() } ?: return "타석"
    val teamName = when {
        inning.contains("초") -> state.awayTeamId.teamName
        inning.contains("말") -> state.homeTeamId.teamName
        else -> ""
    }
    return if (teamName.isEmpty()) inning else "$inning $teamName 공격"
}

@Composable
private fun AtBatCard(
    group: AtBatGroup,
    awayTeamName: String,
    homeTeamName: String,
) {
    val context = LocalContext.current
    val outcomeType = group.outcome?.type?.uppercase() ?: ""
    val isScoreOutcome = outcomeType == "SCORE" || outcomeType == "SAC_FLY_SCORE"
    val highlighted = group.outcome?.let { EventFilterGate.isAllowed(context, it.type) } ?: false
    val headerText = when {
        isScoreOutcome && !group.outcome?.description.isNullOrEmpty() -> group.outcome!!.description
        !group.batter.isNullOrEmpty() -> group.batter
        else -> group.outcome?.description ?: group.pitches.lastOrNull()?.description ?: "타석"
    }
    val subHeader = buildString {
        if (!group.inning.isNullOrEmpty()) append(group.inning)
        if (!group.pitcher.isNullOrEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("vs ").append(group.pitcher)
        }
    }
    val outcome = group.outcome
    val scoreLine = if (isScoreOutcome &&
        outcome?.awayScoreAfter != null &&
        outcome.homeScoreAfter != null
    ) {
        "$awayTeamName ${outcome.awayScoreAfter} : ${outcome.homeScoreAfter} $homeTeamName"
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        shape = AppShapes.md,
        colors = CardDefaults.cardColors(containerColor = Gray900),
        border = if (highlighted) BorderStroke(1.5.dp, Yellow500) else null,
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = headerText,
                    style = AppFont.bodyBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(text = group.time, style = AppFont.micro, color = Gray400)
            }
            if (subHeader.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(text = subHeader, style = AppFont.micro, color = Gray500)
            }
            if (group.pitches.size > 1) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                FlowingPitchChips(types = group.pitches.map { it.type })
            }
            if (outcome != null && outcome.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Row(verticalAlignment = Alignment.Top) {
                    EventTypePill(type = outcome.type)
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    if (!isScoreOutcome) {
                        Text(
                            text = outcome.description,
                            style = AppFont.caption,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            if (!group.batter.isNullOrEmpty()) {
                                Text(
                                    text = "타석: ${group.batter}",
                                    style = AppFont.micro,
                                    color = Gray400
                                )
                            }
                            if (scoreLine != null) {
                                Text(
                                    text = scoreLine,
                                    style = AppFont.microBold,
                                    color = Yellow500
                                )
                            }
                        }
                    }
                }
            } else if (group.pitches.size == 1) {
                val only = group.pitches.first()
                if (only.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Row(verticalAlignment = Alignment.Top) {
                        EventTypePill(type = only.type)
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(
                            text = only.description,
                            style = AppFont.caption,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTypePill(type: String) {
    Text(
        text = eventLabel(type),
        style = AppFont.captionBold,
        color = AppEventColors.eventColor(type),
        modifier = Modifier
            .background(AppEventColors.eventColor(type).copy(alpha = 0.14f), AppShapes.pill)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
    )
}

@Composable
private fun FlowingPitchChips(types: List<String>) {
    val rows = types.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                for (type in row) {
                    PitchChip(type = type)
                }
            }
        }
    }
}

@Composable
private fun PitchChip(type: String) {
    val color = AppEventColors.eventColor(type)
    Text(
        text = pitchShortLabel(type),
        style = AppFont.microBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), AppShapes.pill)
            .border(0.5.dp, color.copy(alpha = 0.32f), AppShapes.pill)
            .padding(horizontal = AppSpacing.xs, vertical = 2.dp)
    )
}

private fun pitchShortLabel(type: String): String = when (type.uppercase()) {
    "BALL" -> "B"
    "STRIKE" -> "S"
    "HIT" -> "안"
    "HOMERUN" -> "홈"
    "OUT" -> "O"
    "WALK" -> "BB"
    "DOUBLE_PLAY" -> "DP"
    "TRIPLE_PLAY" -> "TP"
    "SCORE", "SAC_FLY_SCORE" -> "득"
    "STEAL" -> "도"
    "TAG_UP_ADVANCE" -> "태"
    "PITCHER_CHANGE" -> "교"
    "HALF_INNING_CHANGE" -> "교대"
    else -> "·"
}

private fun displayPitcher(
    state: BackendGamesRepository.LiveGameState,
    event: BackendGamesRepository.LiveEvent?,
    placeholder: String = "-"
): String {
    val direct = state.pitcher.trim()
    if (direct.isNotEmpty()) return direct
    val fromEvent = event?.pitcher?.trim().orEmpty()
    return if (fromEvent.isNotEmpty()) fromEvent else placeholder
}

private fun displayBatter(
    state: BackendGamesRepository.LiveGameState,
    event: BackendGamesRepository.LiveEvent?,
    placeholder: String = "-"
): String {
    val direct = state.batter.trim()
    if (direct.isNotEmpty()) return direct
    val fromEvent = event?.batter?.trim().orEmpty()
    return if (fromEvent.isNotEmpty()) fromEvent else placeholder
}

private fun baseText(state: BackendGamesRepository.LiveGameState): String {
    val bases = ArrayList<String>(3)
    if (state.baseFirst) bases.add("1")
    if (state.baseSecond) bases.add("2")
    if (state.baseThird) bases.add("3")
    return if (bases.isEmpty()) "없음" else bases.joinToString(",")
}

private fun currentAttackLabel(state: BackendGamesRepository.LiveGameState): String {
    val battingTeam = when {
        state.inning.contains("초") -> state.awayTeamId.teamName
        state.inning.contains("말") -> state.homeTeamId.teamName
        else -> "공격"
    }
    return "${state.inning.ifBlank { "경기 중" }} · $battingTeam 공격"
}

private fun inningNumber(inning: String): Int {
    return Regex("(\\d+)회").find(inning)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 9) ?: 1
}

private fun eventLabel(type: String): String = when (type.uppercase()) {
    "HOMERUN" -> "홈런"
    "SCORE" -> "득점"
    "SAC_FLY_SCORE" -> "희생플라이"
    "TAG_UP_ADVANCE" -> "태그업"
    "HIT" -> "안타"
    "WALK" -> "볼넷"
    "STEAL" -> "도루"
    "OUT" -> "아웃"
    "STRIKE" -> "스트라이크"
    "BALL" -> "볼"
    "DOUBLE_PLAY" -> "병살"
    "TRIPLE_PLAY" -> "삼중살"
    "PITCHER_CHANGE" -> "투수교체"
    "HALF_INNING_CHANGE" -> "공수교대"
    else -> type
}



private object DebugDummyLiveGame {
    val state = BackendGamesRepository.LiveGameState(
        gameId = "debug-watch-sync-test",
        homeTeam = "두산",
        awayTeam = "LG",
        homeTeamId = Team.DOOSAN,
        awayTeamId = Team.LG,
        homeScore = 3,
        awayScore = 5,
        inning = "7회초",
        status = GameStatus.LIVE,
        ball = 2,
        strike = 1,
        out = 1,
        baseFirst = true,
        baseSecond = true,
        baseThird = true,
        pitcher = "곽빈",
        batter = "오스틴",
        pitcherPitchCount = 87,
        lastEventType = "HIT"
    )

    // 타석 그룹화 + 정확 점수 시연용 atBatId/seqno/스코어 부여:
    //   - 오스틴 7회초 타석(relayNo 003) STRIKE→BALL→HIT 3구 → 1개 카드
    //   - 박해민 7회초 타석(relayNo 002) 삼진 아웃 → 1개 카드
    //   - 신민재 6회말 득점(relayNo 001) → 1개 카드 + 누적 스코어 3-5
    val events: List<BackendGamesRepository.LiveEvent> = listOf(
        BackendGamesRepository.LiveEvent(5, "dbg-5", "HIT", "오스틴 우전 안타로 1루 진루", "19:42", "곽빈", "오스틴", "7회초", atBatId = "07-003", seqno = 3),
        BackendGamesRepository.LiveEvent(4, "dbg-4", "BALL", "곽빈 → 오스틴 볼", "19:41", "곽빈", "오스틴", "7회초", atBatId = "07-003", seqno = 2),
        BackendGamesRepository.LiveEvent(3, "dbg-3", "STRIKE", "곽빈 → 오스틴 스트라이크", "19:40", "곽빈", "오스틴", "7회초", atBatId = "07-003", seqno = 1),
        BackendGamesRepository.LiveEvent(2, "dbg-2", "OUT", "박해민 삼진 아웃", "19:37", "곽빈", "박해민", "7회초", atBatId = "07-002", seqno = 1),
        BackendGamesRepository.LiveEvent(1, "dbg-1", "SCORE", "신민재 적시타로 1점 추가", "19:34", "곽빈", "오지환", "6회말", atBatId = "06-001", seqno = 1, homeScoreAfter = 3, awayScoreAfter = 5),
    )

    val lineup = FieldLineup(
        leftFielder = "김재환",
        centerFielder = "박해민",
        rightFielder = "문보경",
        shortstop = "오지환",
        secondBaseman = "신민재",
        thirdBaseman = "허경민",
        firstBaseman = "오스틴",
        catcher = "박동원",
        firstRunner = "문성주",
        secondRunner = "오스틴",
        thirdRunner = "김현수"
    )
}

