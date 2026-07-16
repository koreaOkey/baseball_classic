package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.R
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.WatchSyncAdLedger
import com.basehaptic.mobile.data.model.AtBatGroup
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team

import com.basehaptic.mobile.ui.components.RewardedAdManager
import com.basehaptic.mobile.ui.components.RewardedAdFormat
import com.basehaptic.mobile.ui.components.RewardedAdResult
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
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.LocalTeamDisplayNameStyle
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Orange500
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.ui.theme.Yellow400
import com.basehaptic.mobile.ui.theme.Yellow500
import java.util.Locale
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
    val teamDisplayNameStyle = LocalTeamDisplayNameStyle.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var gameState by remember(gameId) { mutableStateOf<BackendGamesRepository.LiveGameState?>(null) }
    var events by remember(gameId) { mutableStateOf<List<BackendGamesRepository.LiveEvent>>(emptyList()) }
    var loadError by remember(gameId) { mutableStateOf<String?>(null) }

    val currentLineup: FieldLineup? = run {
        if (BuildConfig.DEBUG && gameId == "debug-watch-sync-test") return@run DebugDummyLiveGame.lineup
        gameState?.let { FieldLineup.from(it) }
    }

    // 상세 콘텐츠 탭 (중계 | 박스스코어). 기본은 기존 중계 화면 그대로.
    var selectedDetailTab by remember(gameId) { mutableStateOf(LiveDetailTab.RELAY) }
    var boxscore by remember(gameId) { mutableStateOf<BackendGamesRepository.GameBoxscore?>(null) }
    // 최초 조회 완료 전에는 스피너, 이후에는 실패해도 마지막 데이터/빈 상태 유지
    var boxscoreFetchAttempted by remember(gameId) { mutableStateOf(false) }
    // 박스스코어 팀 토글. 어웨이 팀이 초 공격이므로 어웨이 먼저 노출.
    var boxscoreShowsHome by remember(gameId) { mutableStateOf(false) }

    var selectedInningNumber by remember(gameId) { mutableStateOf<Int?>(null) }
    var hasManualInningSelection by remember(gameId) { mutableStateOf(false) }
    var isScoreFilterActive by remember(gameId) { mutableStateOf(false) }
    var backfilledEvents by remember(gameId) { mutableStateOf<List<BackendGamesRepository.LiveEvent>>(emptyList()) }
    var loadedEventFilterKeys by remember(gameId) { mutableStateOf<Set<String>>(emptySet()) }
    var loadingEventFilterKey by remember(gameId) { mutableStateOf<String?>(null) }

    LaunchedEffect(gameState?.inning) {
        val inning = gameState?.inning ?: return@LaunchedEffect
        if (hasManualInningSelection) return@LaunchedEffect
        val n = inningNumber(inning)
        if (n > 0) selectedInningNumber = n
    }

    val allEvents = remember(events, backfilledEvents) {
        (events + backfilledEvents)
            .distinctBy { it.cursor }
            .sortedByDescending { it.cursor }
    }

    val selectedEventFilterKey = when {
        isScoreFilterActive -> "score"
        selectedInningNumber != null -> "inning:${selectedInningNumber}"
        else -> null
    }

    // 리컴포지션마다 전체 이벤트 재필터링을 피하기 위해 입력이 바뀔 때만 재계산
    val filteredEvents = remember(allEvents, selectedInningNumber, isScoreFilterActive) {
        if (isScoreFilterActive) {
            allEvents.filter { event ->
                val t = event.type.uppercase()
                t == "SCORE" || t == "SAC_FLY_SCORE"
            }
        } else {
            val n = selectedInningNumber
            if (n == null) {
                allEvents
            } else {
                allEvents.filter { event ->
                    val inn = event.inning ?: return@filter false
                    inningNumber(inn) == n
                }
            }
        }
    }

    val filteredAtBats = remember(filteredEvents) { AtBatGroup.group(filteredEvents) }

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

        // 백그라운드 진입 시 소켓을 닫고 포그라운드 복귀 시 재연결 (STARTED 동안만 스트리밍)
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
    }

    // 박스스코어 탭이 열려 있는 동안만 조회. 최초 1회 + LIVE 경기면 30초 주기 갱신.
    // 탭 이탈/화면 백그라운드 시 코루틴이 취소되어 폴링도 함께 멈춘다.
    LaunchedEffect(gameId, selectedDetailTab) {
        val targetGameId = gameId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (selectedDetailTab != LiveDetailTab.BOXSCORE) return@LaunchedEffect
        if (BuildConfig.DEBUG && targetGameId == "debug-watch-sync-test") {
            // 디버그 더미 경기는 백엔드 조회 없이 빈 상태 노출
            boxscoreFetchAttempted = true
            return@LaunchedEffect
        }

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                val fetched = runCatching {
                    withContext(Dispatchers.IO) {
                        BackendGamesRepository.fetchGameBoxscore(targetGameId)
                    }
                }.getOrNull()
                // 갱신 실패 시 마지막 데이터 유지 (fetched == null 이면 덮어쓰지 않음)
                if (fetched != null) boxscore = fetched
                boxscoreFetchAttempted = true
                // LIVE 가 아니면 1회 조회로 종료 (포그라운드 복귀 시 repeatOnLifecycle 이 재조회)
                if (gameState?.status != GameStatus.LIVE) break
                delay(30_000)
            }
        }
    }

    LaunchedEffect(gameId, selectedEventFilterKey) {
        val targetGameId = gameId ?: return@LaunchedEffect
        val key = selectedEventFilterKey ?: return@LaunchedEffect
        if (BuildConfig.DEBUG && targetGameId == "debug-watch-sync-test") return@LaunchedEffect
        if (loadedEventFilterKeys.contains(key)) return@LaunchedEffect

        loadingEventFilterKey = key
        val fetched = withContext(Dispatchers.IO) {
            val items = mutableListOf<BackendGamesRepository.LiveEvent>()
            var after = 0L
            repeat(10) {
                val page = BackendGamesRepository.fetchGameEvents(
                    gameId = targetGameId,
                    after = after,
                    limit = 200,
                    inningNumber = if (key.startsWith("inning:")) key.removePrefix("inning:").toIntOrNull() else null,
                    scoringOnly = key == "score",
                ) ?: return@withContext null
                items.addAll(page.items)
                val next = page.nextCursor ?: return@withContext items
                if (next <= after) return@withContext items
                after = next
            }
            items
        }

        if (fetched != null) {
            backfilledEvents = (fetched + backfilledEvents)
                .distinctBy { it.cursor }
                .sortedByDescending { it.cursor }
            loadedEventFilterKeys = loadedEventFilterKeys + key
        }
        if (loadingEventFilterKey == key) {
            loadingEventFilterKey = null
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
                    ScoreboardCard(state = state, latestEvent = allEvents.firstOrNull())
                }

                // 이닝별 라인스코어. 구버전 백엔드(lineScore 미지원)에서는 카드 자체를 숨긴다.
                if (state.lineScore != null) {
                    item {
                        LineScoreCard(state = state)
                    }
                }

                item {
                    LiveDetailTabBar(
                        selectedTab = selectedDetailTab,
                        onSelect = { selectedDetailTab = it }
                    )
                }

                if (selectedDetailTab == LiveDetailTab.RELAY) {
                    item {
                        BaseballFieldCard(
                            state = state,
                            latestEvent = allEvents.firstOrNull(),
                            recentEvents = allEvents,
                            lineup = currentLineup
                        )
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
                        CurrentMatchupCard(state = state, latestEvent = allEvents.firstOrNull())
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
                            EmptyInningEventCard(isLoading = loadingEventFilterKey == selectedEventFilterKey)
                        }
                    } else {
                        itemsIndexed(filteredAtBats, key = { _, g -> g.id }) { index, group ->
                            val prevKey = filteredAtBats.getOrNull(index - 1)?.let(::sectionKey)
                            val currKey = sectionKey(group)
                            if (index == 0 || prevKey != currKey) {
                                AtBatSectionHeader(title = sectionTitle(group, state, teamDisplayNameStyle))
                            }
                            AtBatCard(
                                group = group,
                                awayTeamName = state.awayTeamId.displayName(teamDisplayNameStyle),
                                homeTeamName = state.homeTeamId.displayName(teamDisplayNameStyle),
                                highlightScoreOutcome = isScoreFilterActive,
                            )
                        }
                    }
                } else {
                    item {
                        BoxscoreSection(
                            state = state,
                            boxscore = boxscore,
                            isLoading = !boxscoreFetchAttempted,
                            showsHome = boxscoreShowsHome,
                            onSelectHome = { boxscoreShowsHome = it }
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
                gameStatus = state?.status,
                syncedGameId = syncedGameId,
                onSetSyncedGame = onSetSyncedGame
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (state?.status == GameStatus.LIVE) {
                LiveBadge()
                Spacer(modifier = Modifier.width(AppSpacing.sm))
            }

            Text(
                text = "경기 상세",
                color = Color.White,
                style = AppFont.h5Bold
            )
        }
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
    val teamDisplayNameStyle = LocalTeamDisplayNameStyle.current

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
                            AppEventColors.eventColor(state.lastEventType.orEmpty()).copy(alpha = 0.09f)
                        )
                    )
                )
                .padding(AppSpacing.md)
        ) {
            Text(
                text = currentAttackLabel(state, teamDisplayNameStyle),
                color = Yellow400,
                style = AppFont.captionBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(AppSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreTeamBlock(
                    team = state.awayTeamId,
                    teamName = state.awayTeamId.displayName(teamDisplayNameStyle),
                    score = state.awayScore,
                    showFavorite = favoriteTeam == state.awayTeamId && favoriteTeam != Team.NONE
                )

                ScoreStateBlock(
                    state = state,
                    latestEvent = latestEvent,
                    modifier = Modifier
                        .padding(horizontal = AppSpacing.xs)
                        .width(104.dp)
                )

                ScoreTeamBlock(
                    team = state.homeTeamId,
                    teamName = state.homeTeamId.displayName(teamDisplayNameStyle),
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
        TeamLogo(team = team, size = 52.dp)
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(
            text = teamName,
            color = Color.White,
            style = AppFont.h5Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = score.toString(), color = Color.White, style = AppFont.h2)
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
            style = AppFont.tinyBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

// MARK: - 이닝별 라인스코어 카드

/// 라인스코어 셀 기본 폭. 이닝 숫자·R/H/E 공통.
private val LineScoreCellWidth = 26.dp

@Composable
private fun LineScoreCard(state: BackendGamesRepository.LiveGameState) {
    val lineScore = state.lineScore ?: return
    val teamDisplayNameStyle = LocalTeamDisplayNameStyle.current
    // 최소 9이닝, 연장이면 기록된 마지막 이닝까지 (10+ 이닝은 가로 스크롤)
    val totalInnings = max(9, lineScore.maxInning)
    val currentInning = if (state.status == GameStatus.LIVE) rawInningNumber(state.inning) else null
    val isHomeBatting = state.inning.contains("말")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900,
        border = BorderStroke(1.dp, Gray800)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                LineScoreHeaderRow(totalInnings = totalInnings)
                LineScoreTeamRow(
                    teamName = state.awayTeamId.displayName(teamDisplayNameStyle),
                    innings = lineScore.away,
                    totalInnings = totalInnings,
                    highlightInning = currentInning.takeIf { !isHomeBatting },
                    runs = state.awayScore,
                    hits = state.awayHits,
                    errors = state.awayErrors
                )
                LineScoreTeamRow(
                    teamName = state.homeTeamId.displayName(teamDisplayNameStyle),
                    innings = lineScore.home,
                    totalInnings = totalInnings,
                    highlightInning = currentInning.takeIf { isHomeBatting },
                    runs = state.homeScore,
                    hits = state.homeHits,
                    errors = state.homeErrors
                )
            }
        }
    }
}

@Composable
private fun LineScoreHeaderRow(totalInnings: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineScoreTeamNameCell(text = "")
        for (inning in 1..totalInnings) {
            LineScoreCell(text = inning.toString(), color = Gray500, style = AppFont.microBold)
        }
        Spacer(modifier = Modifier.width(AppSpacing.sm))
        LineScoreCell(text = "R", color = Gray400, style = AppFont.microBold)
        LineScoreCell(text = "H", color = Gray400, style = AppFont.microBold)
        LineScoreCell(text = "E", color = Gray400, style = AppFont.microBold)
    }
}

@Composable
private fun LineScoreTeamRow(
    teamName: String,
    innings: Map<Int, Int>,
    totalInnings: Int,
    highlightInning: Int?,
    runs: Int,
    hits: Int?,
    errors: Int?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineScoreTeamNameCell(text = teamName)
        for (inning in 1..totalInnings) {
            val value = innings[inning]
            // 진행 중인 이닝(공격 팀)은 라이브 강조색, 미진행 이닝은 "-"
            val isCurrent = inning == highlightInning
            LineScoreCell(
                text = value?.toString() ?: "-",
                color = when {
                    isCurrent -> Orange500
                    value == null -> Gray600
                    else -> Gray100
                },
                style = if (isCurrent) AppFont.microBold else AppFont.micro
            )
        }
        Spacer(modifier = Modifier.width(AppSpacing.sm))
        LineScoreCell(text = runs.toString(), color = Color.White, style = AppFont.microBold)
        LineScoreCell(text = hits?.toString() ?: "-", color = Gray300, style = AppFont.micro)
        LineScoreCell(text = errors?.toString() ?: "-", color = Gray300, style = AppFont.micro)
    }
}

@Composable
private fun LineScoreTeamNameCell(text: String) {
    Text(
        text = text,
        style = AppFont.microBold,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(56.dp)
    )
}

@Composable
private fun LineScoreCell(
    text: String,
    color: Color,
    style: TextStyle,
    width: Dp = LineScoreCellWidth
) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(width)
    )
}

/// "9회말" → 9. inningNumber() 와 달리 연장(10회+)을 자르지 않는다.
private fun rawInningNumber(inning: String): Int? =
    Regex("(\\d+)회").find(inning)?.groupValues?.getOrNull(1)?.toIntOrNull()

// MARK: - 상세 콘텐츠 탭 (중계 | 박스스코어)

private enum class LiveDetailTab(val label: String) {
    RELAY("중계"),
    BOXSCORE("박스스코어"),
}

@Composable
private fun LiveDetailTabBar(
    selectedTab: LiveDetailTab,
    onSelect: (LiveDetailTab) -> Unit
) {
    SegmentedTabRow(
        options = LiveDetailTab.values().map { it.label },
        selectedIndex = LiveDetailTab.values().indexOf(selectedTab),
        onSelect = { index -> onSelect(LiveDetailTab.values()[index]) }
    )
}

/// 동일 폭 2분할 세그먼트 토글. 상세 탭·박스스코어 팀 토글 공용.
@Composable
private fun SegmentedTabRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Gray900, AppShapes.pill)
            .border(1.dp, Gray800, AppShapes.pill)
            .padding(AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.pill)
                    .background(if (selected) Gray700 else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = AppFont.captionBold,
                    color = if (selected) Color.White else Gray400,
                    maxLines = 1
                )
            }
        }
    }
}

// MARK: - 박스스코어 탭 콘텐츠

@Composable
private fun BoxscoreSection(
    state: BackendGamesRepository.LiveGameState,
    boxscore: BackendGamesRepository.GameBoxscore?,
    isLoading: Boolean,
    showsHome: Boolean,
    onSelectHome: (Boolean) -> Unit
) {
    val teamDisplayNameStyle = LocalTeamDisplayNameStyle.current

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        // 초 공격인 어웨이 팀이 먼저 (라인스코어 표기 순서와 동일)
        SegmentedTabRow(
            options = listOf(
                "${state.awayTeamId.displayName(teamDisplayNameStyle)} 타자",
                "${state.homeTeamId.displayName(teamDisplayNameStyle)} 타자"
            ),
            selectedIndex = if (showsHome) 1 else 0,
            onSelect = { index -> onSelectHome(index == 1) }
        )

        val batters = if (showsHome) boxscore?.homeBatters.orEmpty() else boxscore?.awayBatters.orEmpty()
        val pitchers = if (showsHome) boxscore?.homePitchers.orEmpty() else boxscore?.awayPitchers.orEmpty()

        when {
            boxscore == null && isLoading -> {
                // 아직 데이터가 없을 때만 스피너 노출 (갱신 중엔 기존 데이터 유지)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.xxxl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = Gray400
                    )
                }
            }

            batters.isEmpty() && pitchers.isEmpty() -> {
                EmptyBoxscoreCard()
            }

            else -> {
                if (batters.isNotEmpty()) {
                    BoxscoreBatterTable(batters = batters)
                }
                if (pitchers.isNotEmpty()) {
                    BoxscorePitcherTable(pitchers = pitchers)
                }
            }
        }
    }
}

@Composable
private fun EmptyBoxscoreCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900
    ) {
        Text(
            text = "박스스코어가 아직 준비되지 않았습니다",
            style = AppFont.bodyMedium,
            color = Gray400,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.xxxl)
        )
    }
}

/// 박스스코어 숫자 컬럼 폭 (타자·투수 테이블 공통)
private val BoxscoreStatCellWidth = 36.dp

@Composable
private fun BoxscoreBatterTable(batters: List<BackendGamesRepository.BoxscoreBatter>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900,
        border = BorderStroke(1.dp, Gray800)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "타순 · 선수",
                    style = AppFont.microBold,
                    color = Gray500,
                    modifier = Modifier.weight(1f)
                )
                for (header in listOf("타수", "안타", "타점", "득점", "홈런")) {
                    BoxscoreStatCell(text = header, color = Gray500, style = AppFont.microBold)
                }
            }

            for (batter in batters) {
                BoxscoreBatterRow(batter = batter)
            }
        }
    }
}

@Composable
private fun BoxscoreBatterRow(batter: BackendGamesRepository.BoxscoreBatter) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = batter.battingOrder?.toString() ?: "-",
                style = AppFont.microBold,
                color = Gray500,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(14.dp)
            )
            Text(
                text = batter.playerName,
                style = AppFont.captionMedium,
                // 교체 출전 선수는 살짝 흐리게 구분
                color = if (batter.isStarter) Color.White else Gray400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val position = batter.position?.trim().orEmpty()
            if (position.isNotEmpty()) {
                Text(
                    text = position,
                    style = AppFont.micro,
                    color = Gray500,
                    maxLines = 1
                )
            }
        }
        BoxscoreStatCell(text = batter.atBats.toString(), color = Gray300)
        // 멀티히트(2안타+)·2타점+·홈런은 그린 강조
        BoxscoreHighlightStatCell(value = batter.hits, threshold = 2)
        BoxscoreHighlightStatCell(value = batter.rbi, threshold = 2)
        BoxscoreStatCell(text = batter.runs.toString(), color = Gray300)
        BoxscoreHighlightStatCell(value = batter.homeRuns, threshold = 1)
    }
}

@Composable
private fun BoxscorePitcherTable(pitchers: List<BackendGamesRepository.BoxscorePitcher>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900,
        border = BorderStroke(1.dp, Gray800)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Text(text = "투수 기록", style = AppFont.captionBold, color = Yellow400)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "투수",
                    style = AppFont.microBold,
                    color = Gray500,
                    modifier = Modifier.weight(1f)
                )
                for (header in listOf("이닝", "투구", "피안타", "실점", "K")) {
                    BoxscoreStatCell(text = header, color = Gray500, style = AppFont.microBold)
                }
            }

            for (pitcher in pitchers) {
                BoxscorePitcherRow(pitcher = pitcher)
            }
        }
    }
}

@Composable
private fun BoxscorePitcherRow(pitcher: BackendGamesRepository.BoxscorePitcher) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = pitcher.playerName,
                style = AppFont.captionMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (pitcher.isStarter) {
                Text(text = "(선발)", style = AppFont.micro, color = Gray500, maxLines = 1)
            }
        }
        BoxscoreStatCell(text = pitcherInningsText(pitcher.outsRecorded), color = Gray300)
        BoxscoreStatCell(text = pitcher.pitchesThrown.toString(), color = Gray300)
        BoxscoreStatCell(text = pitcher.hitsAllowed.toString(), color = Gray300)
        BoxscoreStatCell(text = pitcher.runsAllowed.toString(), color = Gray300)
        // 5K+ 는 그린 강조
        BoxscoreHighlightStatCell(value = pitcher.strikeouts, threshold = 5)
    }
}

@Composable
private fun BoxscoreStatCell(
    text: String,
    color: Color,
    style: TextStyle = AppFont.caption
) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(BoxscoreStatCellWidth)
    )
}

/// threshold 이상이면 그린 강조 (멀티히트·홈런·다K 등 주목할 기록)
@Composable
private fun BoxscoreHighlightStatCell(value: Int, threshold: Int) {
    val highlighted = value >= threshold
    BoxscoreStatCell(
        text = value.toString(),
        color = if (highlighted) Green400 else Gray300,
        style = if (highlighted) AppFont.captionBold else AppFont.caption
    )
}

/// 잡은 아웃카운트 → 이닝 표기. 19아웃 → "6⅓", 2아웃 → "⅔", 0아웃 → "0"
private fun pitcherInningsText(outsRecorded: Int): String {
    val outs = outsRecorded.coerceAtLeast(0)
    val whole = outs / 3
    return when (outs % 3) {
        1 -> if (whole > 0) "$whole⅓" else "⅓"
        2 -> if (whole > 0) "$whole⅔" else "⅔"
        else -> whole.toString()
    }
}

@Composable
private fun BaseballFieldCard(
    state: BackendGamesRepository.LiveGameState,
    latestEvent: BackendGamesRepository.LiveEvent?,
    recentEvents: List<BackendGamesRepository.LiveEvent>,
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

            val hasLineupLabels = lineup?.hasDefensiveLabels == true
            val showPregamePlaceholder = state.status == GameStatus.SCHEDULED && !hasLineupLabels

            if (showPregamePlaceholder) {
                Text(
                    text = "경기 시작 전입니다.",
                    style = AppFont.bodyBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = AppSpacing.xl)
                        .background(Color.Black.copy(alpha = 0.42f), AppShapes.pill)
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                )
            } else {
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

                val inferredRunners = FieldRunners.from(state, recentEvents, lineup)
                if (state.baseFirst) {
                    AtFieldPosition(FieldPositions.firstBase, w, h) {
                        PositionPill(
                            text = runnerDisplayName(cleanPlayerName(state.baseFirstRunner) ?: inferredRunners.first),
                            modifier = Modifier,
                            highlighted = true
                        )
                    }
                }
                if (state.baseSecond) {
                    AtFieldPosition(FieldPositions.secondBase, w, h) {
                        PositionPill(
                            text = runnerDisplayName(cleanPlayerName(state.baseSecondRunner) ?: inferredRunners.second),
                            modifier = Modifier,
                            highlighted = true
                        )
                    }
                }
                if (state.baseThird) {
                    AtFieldPosition(FieldPositions.thirdBase, w, h) {
                        PositionPill(
                            text = runnerDisplayName(cleanPlayerName(state.baseThirdRunner) ?: inferredRunners.third),
                            modifier = Modifier,
                            highlighted = true
                        )
                    }
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
    val hasDefensiveLabels: Boolean
        get() = listOf(
            leftFielder,
            centerFielder,
            rightFielder,
            shortstop,
            secondBaseman,
            thirdBaseman,
            firstBaseman,
            catcher,
        ).any { !cleanPlayerName(it).isNullOrEmpty() }

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

private data class FieldRunners(
    val first: String? = null,
    val second: String? = null,
    val third: String? = null,
) {
    companion object {
        fun from(
            state: BackendGamesRepository.LiveGameState,
            events: List<BackendGamesRepository.LiveEvent>,
            lineup: FieldLineup?,
        ): FieldRunners {
            val bases = mutableMapOf<Int, String>()
            events
                .filter { it.inning == null || it.inning == state.inning }
                .sortedWith(compareBy<BackendGamesRepository.LiveEvent> { it.cursor }.thenBy { it.seqno ?: 0 })
                .groupBy { it.atBatId ?: it.id }
                .values
                .sortedBy { group -> group.minOfOrNull { it.cursor } ?: 0L }
                .forEach { group ->
                    var batterPlacement: Pair<Int, String>? = null
                    group.sortedBy { it.seqno ?: 0 }.forEach { event ->
                        if (event.type.equals("HALF_INNING_CHANGE", ignoreCase = true)) {
                            bases.clear()
                            return@forEach
                        }
                        runnerMovement(event.description)?.let { movement ->
                            bases.entries.removeAll { it.value == movement.name }
                            movement.targetBase?.let { bases[it] = movement.name }
                        }
                        batterRunnerPlacement(event)?.let { batterPlacement = it }
                    }
                    batterPlacement?.let { (base, name) ->
                        bases.entries.removeAll { it.value == name }
                        bases[base] = name
                    }
                }

            return FieldRunners(
                first = lineup?.firstRunner ?: bases[1].takeIf { state.baseFirst },
                second = lineup?.secondRunner ?: bases[2].takeIf { state.baseSecond },
                third = lineup?.thirdRunner ?: bases[3].takeIf { state.baseThird },
            )
        }

        private data class RunnerMovement(val name: String, val targetBase: Int?)

        private fun runnerMovement(description: String): RunnerMovement? {
            val match = Regex("""([123])루주자\s+([^:]+)\s*:\s*(.+)""").find(description) ?: return null
            val name = cleanPlayerName(match.groupValues[2]) ?: return null
            val action = match.groupValues[3]
            if (action.contains("홈인") || action.contains("아웃")) {
                return RunnerMovement(name, null)
            }
            return when {
                action.contains("3루") -> RunnerMovement(name, 3)
                action.contains("2루") -> RunnerMovement(name, 2)
                action.contains("1루") -> RunnerMovement(name, 1)
                else -> null
            }
        }

        private fun batterRunnerPlacement(event: BackendGamesRepository.LiveEvent): Pair<Int, String>? {
            val name = cleanPlayerName(event.batter) ?: return null
            val type = event.type.uppercase()
            val desc = event.description
            if (desc.contains("홈런")) return null
            return when {
                desc.contains("3루타") -> 3 to name
                desc.contains("2루타") -> 2 to name
                type == "WALK" || type == "HIT_BY_PITCH" || type == "HIT" || desc.contains("출루") -> 1 to name
                else -> null
            }
        }
    }
}

private fun runnerDisplayName(name: String?): String = cleanPlayerName(name) ?: "주자"

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
    val cleanName = cleanPlayerName(name) ?: return
    AtFieldPosition(point, w, h) {
        PositionPill(text = cleanName, modifier = Modifier, highlighted = false)
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
private fun EmptyInningEventCard(isLoading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900,
    ) {
        Text(
            text = if (isLoading) "이벤트를 불러오는 중..." else "해당 회 이벤트가 없습니다",
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
    gameStatus: GameStatus?,
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
    var showGameNotStartedDialog by remember { mutableStateOf(false) }
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
                        if (requested && gameStatus != GameStatus.LIVE) {
                            visualOn = isSyncedToCurrent
                            showGameNotStartedDialog = true
                            return@Switch
                        }
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

    if (showGameNotStartedDialog) {
        AlertDialog(
            onDismissRequest = { showGameNotStartedDialog = false },
            title = { Text(text = "경기 시작 전입니다.") },
            confirmButton = {
                TextButton(onClick = { showGameNotStartedDialog = false }) {
                    Text(text = "확인")
                }
            }
        )
    }

    if (showEnableDialog && gameId != null) {
        val hasViewedAd = WatchSyncAdLedger.hasViewed(context, gameId)
        AlertDialog(
            onDismissRequest = {
                showEnableDialog = false
                visualOn = isSyncedToCurrent
            },
            title = { Text(text = "워치로 보시겠습니까?") },
            text = {
                Text(
                    text = if (hasViewedAd) {
                        "워치 동기화를 시작할까요?"
                    } else {
                        "광고 관람 후 동기화됩니다."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEnableDialog = false
                    if (hasViewedAd) {
                        onSetSyncedGame(gameId)
                        return@TextButton
                    }
                    isAdLoading = true
                    // 광고 게이트 정책: 보상 획득 또는 광고 로드 실패만 동기화 허용, 조기 닫기는 거부, BUSY 는 no-op
                    RewardedAdManager.loadAndShowAd(
                        context = context,
                        adUnitId = RewardedAdManager.WATCH_SYNC_AD_UNIT,
                        format = RewardedAdFormat.REWARDED_INTERSTITIAL,
                    ) { result ->
                        isAdLoading = false
                        when (result) {
                            RewardedAdResult.REWARD_EARNED -> {
                                WatchSyncAdLedger.markViewed(context, gameId)
                                onSetSyncedGame(gameId)
                            }
                            RewardedAdResult.LOAD_FAILED -> onSetSyncedGame(gameId)
                            RewardedAdResult.DISMISSED_WITHOUT_REWARD,
                            RewardedAdResult.BUSY -> {
                                visualOn = isSyncedToCurrent
                            }
                        }
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

private fun sectionTitle(
    group: AtBatGroup,
    state: BackendGamesRepository.LiveGameState,
    teamDisplayNameStyle: com.basehaptic.mobile.data.model.TeamDisplayNameStyle
): String {
    val inning = group.inning?.takeIf { it.isNotEmpty() } ?: return "타석"
    val teamName = when {
        inning.contains("초") -> state.awayTeamId.displayName(teamDisplayNameStyle)
        inning.contains("말") -> state.homeTeamId.displayName(teamDisplayNameStyle)
        else -> ""
    }
    return if (teamName.isEmpty()) inning else "$inning $teamName 공격"
}

@Composable
private fun AtBatCard(
    group: AtBatGroup,
    awayTeamName: String,
    homeTeamName: String,
    highlightScoreOutcome: Boolean,
) {
    val outcomeType = group.outcome?.type?.uppercase() ?: ""
    val isScoreOutcome = outcomeType == "SCORE" || outcomeType == "SAC_FLY_SCORE"
    val highlighted = highlightScoreOutcome && isScoreOutcome
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
    val batterRecord = group.pitches.reversed().firstNotNullOfOrNull { it.batterRecord }
    val pitchDetailEvents = group.pitches
        .filter { event ->
            event.pitchNum != null ||
                event.pitchSpeed != null ||
                !event.pitchStuff.isNullOrEmpty()
        }
        .sortedByDescending { it.pitchNum ?: it.seqno ?: it.cursor.toInt() }
    val showsNaverStyleDetails = !isScoreOutcome && (batterRecord != null || pitchDetailEvents.isNotEmpty())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        shape = AppShapes.md,
        colors = CardDefaults.cardColors(containerColor = Gray900),
        border = if (highlighted) BorderStroke(1.5.dp, Yellow500) else null,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            if (showsNaverStyleDetails) {
                AtBatBatterHeader(
                    batterName = batterRecordString(batterRecord, "name") ?: group.batter ?: headerText,
                    pitcherName = group.pitcher,
                    inning = group.inning,
                    record = batterRecord,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
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
                        Text(text = subHeader, style = AppFont.micro, color = Gray500)
                    }
                }
            }

            if (pitchDetailEvents.isEmpty()) {
                val chipTypes = normalizePitchTypes(group.pitches)
                if (chipTypes.size > 1) {
                    FlowingPitchChips(types = chipTypes)
                }
            }

            if (outcome != null && outcome.description.isNotEmpty()) {
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

            if (pitchDetailEvents.isNotEmpty()) {
                PitchDetailRows(events = pitchDetailEvents)
            }
        }
    }
}

@Composable
private fun AtBatBatterHeader(
    batterName: String,
    pitcherName: String?,
    inning: String?,
    record: Map<String, Any?>?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(
                        text = batterName,
                        style = AppFont.h5Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    val metaText = batterMetaText(record)
                    if (metaText.isNotEmpty()) {
                        Text(
                            text = metaText,
                            style = AppFont.micro,
                            color = Gray400,
                            maxLines = 1
                        )
                    }
                }

                if (!inning.isNullOrEmpty()) {
                    Text(text = inning, style = AppFont.micro, color = Gray500)
                }
            }

            if (!pitcherName.isNullOrEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                    modifier = Modifier.padding(start = AppSpacing.sm)
                ) {
                    Text(text = "상대투수", style = AppFont.micro, color = Gray500)
                    Text(text = pitcherName, style = AppFont.captionBold, color = Gray100, maxLines = 1)
                }
            }
        }

        if (record != null) {
            BatterStatGrid(record = record)
        }
    }
}

@Composable
private fun BatterStatGrid(record: Map<String, Any?>) {
    val stats = listOf(
        "타석" to batterRecordDisplayInt(record, "pa", "plateAppearance", "plateAppearances"),
        "타수" to batterRecordDisplayInt(record, "ab", "atBat", "atBats"),
        "안타" to batterRecordDisplayInt(record, "hit", "hits"),
        "득점" to batterRecordDisplayInt(record, "run", "runs", "score"),
        "타점" to batterRecordDisplayInt(record, "rbi"),
        "홈런" to batterRecordDisplayInt(record, "hr", "homeRuns"),
        "볼넷" to batterRecordDisplayInt(record, "bb", "walk", "walks", "baseOnBalls"),
        "삼진" to batterRecordDisplayInt(record, "so", "strikeOuts", "strikeouts"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        for (row in stats.chunked(4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                for ((label, value) in row) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, style = AppFont.micro, color = Gray500, maxLines = 1)
                        Text(text = value, style = AppFont.micro, color = Gray400, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PitchDetailRows(events: List<BackendGamesRepository.LiveEvent>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Gray800.copy(alpha = 0.42f), AppShapes.sm)
            .padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        for (event in events) {
            PitchDetailRow(event = event)
        }
    }
}

@Composable
private fun PitchDetailRow(event: BackendGamesRepository.LiveEvent) {
    val displayType = pitchDisplayType(event)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AppEventColors.eventColor(displayType)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (event.pitchNum ?: 0).toString(),
                style = AppFont.microBold,
                color = Gray950,
                maxLines = 1
            )
        }

        Text(
            text = pitchOutcomeText(event),
            style = AppFont.captionMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = pitchMetricText(event),
            style = AppFont.micro,
            color = Gray400,
            maxLines = 1
        )

        Text(
            text = pitchCountText(event),
            style = AppFont.microMedium,
            color = Gray400,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp)
        )
    }
}

/// HOMERUN 칩·라벨 무지개 그라데이션. iOS 와 동일 색 위계 (red→orange→yellow→green→blue→violet).
private val homerunRainbowColors: List<Color> = listOf(
    Color(0xFFFF4D4D),
    Color(0xFFFF9E33),
    Color(0xFFFFD93D),
    Color(0xFF5AD966),
    Color(0xFF4D99FF),
    Color(0xFFB266F2),
)
private val homerunRainbowBrush: Brush
    get() = Brush.horizontalGradient(homerunRainbowColors)
private val homerunRainbowBrushSoft: Brush
    get() = Brush.horizontalGradient(homerunRainbowColors.map { it.copy(alpha = 0.18f) })

@Composable
private fun EventTypePill(type: String) {
    val isHomerun = type.uppercase() == "HOMERUN"
    if (isHomerun) {
        Text(
            text = eventLabel(type),
            style = AppFont.captionBold.copy(brush = homerunRainbowBrush),
            modifier = Modifier
                .background(homerunRainbowBrushSoft, AppShapes.pill)
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
        )
    } else {
        Text(
            text = eventLabel(type),
            style = AppFont.captionBold,
            color = AppEventColors.eventColor(type),
            modifier = Modifier
                .background(AppEventColors.eventColor(type).copy(alpha = 0.14f), AppShapes.pill)
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
        )
    }
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
    val isHomerun = type.uppercase() == "HOMERUN"
    if (isHomerun) {
        Text(
            text = pitchShortLabel(type),
            style = AppFont.microBold.copy(brush = homerunRainbowBrush),
            modifier = Modifier
                .background(homerunRainbowBrushSoft, AppShapes.pill)
                .border(0.8.dp, homerunRainbowBrush, AppShapes.pill)
                .padding(horizontal = AppSpacing.xs, vertical = 2.dp)
        )
    } else {
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
}

private fun pitchShortLabel(type: String): String = when (type.uppercase()) {
    "BALL" -> "B"
    "STRIKE" -> "S"
    "FOUL" -> "F"
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

/// 타석 카드 PitchChip 시퀀스에 들어갈 타입을 정제한다. iOS normalizePitchTypes 와 동등.
/// - 타석 시작 안내성 OTHER → 칩 제외
/// - 파울/타격 OTHER → 가상 타입 "FOUL"
/// - 그 외 OTHER → 칩 제외
/// - 그 외 타입은 그대로 보존
private fun normalizePitchTypes(events: List<BackendGamesRepository.LiveEvent>): List<String> =
    events.mapNotNull { event ->
        val upper = pitchDisplayType(event)
        if (upper != "OTHER") return@mapNotNull upper
        null
    }

private fun pitchDisplayType(event: BackendGamesRepository.LiveEvent): String {
    val desc = event.description
    if (desc.contains("파울") || desc.contains("타격")) return "FOUL"
    return event.type.uppercase()
}

private fun pitchOutcomeText(event: BackendGamesRepository.LiveEvent): String {
    val text = event.description.trim().replace(Regex("^\\d+구\\s*"), "")
    if (text.isNotEmpty()) return text
    return eventLabel(pitchDisplayType(event))
}

private fun pitchMetricText(event: BackendGamesRepository.LiveEvent): String {
    val speed = event.pitchSpeed
    val stuff = event.pitchStuff?.trim().orEmpty()
    return when {
        speed != null && stuff.isNotEmpty() -> "${speed}km/h | $stuff"
        speed != null -> "${speed}km/h"
        stuff.isNotEmpty() -> stuff
        else -> "-"
    }
}

private fun pitchCountText(event: BackendGamesRepository.LiveEvent): String {
    val ball = event.ballAfter ?: return "-"
    val strike = event.strikeAfter ?: return "-"
    return "$ball-$strike"
}

private fun batterMetaText(record: Map<String, Any?>?): String {
    val parts = mutableListOf<String>()
    batterRecordInt(record, "batOrder", "battingOrder")?.let { parts.add("${it}번타자") }
    batterRecordAverageText(record)?.let { parts.add("타율 $it") }
    return parts.joinToString(" · ")
}

private fun batterRecordString(record: Map<String, Any?>?, vararg keys: String): String? {
    if (record == null) return null
    for (key in keys) {
        val value = record[key]?.toString()?.trim().orEmpty()
        if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
    }
    return null
}

private fun batterRecordInt(record: Map<String, Any?>?, vararg keys: String): Int? {
    if (record == null) return null
    for (key in keys) {
        when (val raw = record[key]) {
            is Int -> return raw
            is Long -> return raw.toInt()
            is Double -> return raw.toInt()
            is Float -> return raw.toInt()
            is Number -> return raw.toInt()
            is String -> raw.trim().toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun batterRecordDouble(record: Map<String, Any?>?, vararg keys: String): Double? {
    if (record == null) return null
    for (key in keys) {
        when (val raw = record[key]) {
            is Double -> return raw
            is Float -> return raw.toDouble()
            is Int -> return raw.toDouble()
            is Long -> return raw.toDouble()
            is Number -> return raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()?.let { return it }
        }
    }
    return null
}

private fun batterRecordAverageText(record: Map<String, Any?>?): String? {
    val average = batterRecordDouble(record, "seasonHra", "avg", "average", "battingAverage") ?: return null
    return String.format(Locale.US, "%.3f", average)
}

private fun batterRecordDisplayInt(record: Map<String, Any?>, vararg keys: String): String {
    return (batterRecordInt(record, *keys) ?: 0).toString()
}

private fun displayPitcher(
    state: BackendGamesRepository.LiveGameState,
    event: BackendGamesRepository.LiveEvent?,
    placeholder: String = "-"
): String {
    cleanPlayerName(state.pitcher)?.let { return it }
    cleanPlayerName(event?.pitcher)?.let { return it }
    // 라인업 공개 후 라이브 진입 전: 수비팀 선발투수로 폴백. FieldLineup.from 과 동일 규칙.
    val preferHome = !state.inning.contains("말")
    val starter = if (preferHome) state.homeStartingPitcher else state.awayStartingPitcher
    cleanPlayerName(starter)?.let { return it }
    return placeholder
}

private fun displayBatter(
    state: BackendGamesRepository.LiveGameState,
    event: BackendGamesRepository.LiveEvent?,
    placeholder: String = "-"
): String {
    cleanPlayerName(state.batter)?.let { return it }
    cleanPlayerName(event?.batter)?.let { return it }
    return placeholder
}

private fun cleanPlayerName(name: String?): String? {
    val value = name?.trim().orEmpty()
    return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun baseText(state: BackendGamesRepository.LiveGameState): String {
    val bases = ArrayList<String>(3)
    if (state.baseFirst) bases.add("1")
    if (state.baseSecond) bases.add("2")
    if (state.baseThird) bases.add("3")
    return if (bases.isEmpty()) "없음" else bases.joinToString(",")
}

private fun currentAttackLabel(
    state: BackendGamesRepository.LiveGameState,
    teamDisplayNameStyle: com.basehaptic.mobile.data.model.TeamDisplayNameStyle
): String {
    val battingTeam = when {
        state.inning.contains("초") -> state.awayTeamId.displayName(teamDisplayNameStyle)
        state.inning.contains("말") -> state.homeTeamId.displayName(teamDisplayNameStyle)
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
        homeTeam = "LG",
        awayTeam = "SSG",
        homeTeamId = Team.LG,
        awayTeamId = Team.SSG,
        homeScore = 10,
        awayScore = 1,
        inning = "4회말",
        status = GameStatus.LIVE,
        ball = 0,
        strike = 0,
        out = 2,
        baseFirst = true,
        baseSecond = true,
        baseThird = true,
        baseFirstRunner = "박해민",
        baseSecondRunner = "홍창기",
        baseThirdRunner = "신민재",
        pitcher = "최용준",
        batter = "오스틴",
        pitcherPitchCount = 18,
        lastEventType = "SCORE",
        homeStartingPitcher = "김윤식",
        awayStartingPitcher = "김건우",
        // 라인스코어 카드 확인용 더미 (합계가 homeScore/awayScore 와 일치)
        lineScore = BackendGamesRepository.LineScore(
            home = mapOf(1 to 2, 2 to 0, 3 to 4, 4 to 4),
            away = mapOf(1 to 0, 2 to 1, 3 to 0, 4 to 0),
        ),
        homeHits = 11,
        awayHits = 3,
        homeErrors = 0,
        awayErrors = 1,
    )

    private val austinRecord = mapOf(
        "name" to "오스틴",
        "batOrder" to 3,
        "seasonHra" to 0.349,
        "todayHra" to 0.75,
        "pa" to 3,
        "ab" to 3,
        "hit" to 2,
        "run" to 3,
        "rbi" to 3,
        "hr" to 0,
        "bb" to 0,
        "so" to 1,
    )
    private val parkRecord = mapOf(
        "name" to "박해민",
        "batOrder" to 2,
        "seasonHra" to 0.28,
        "todayHra" to 0.333,
        "pa" to 3,
        "ab" to 2,
        "hit" to 1,
        "run" to 2,
        "rbi" to 0,
        "hr" to 0,
        "bb" to 1,
        "so" to 0,
    )
    private val hongRecord = mapOf(
        "name" to "홍창기",
        "batOrder" to 1,
        "seasonHra" to 0.236,
        "todayHra" to 0.333,
        "pa" to 3,
        "ab" to 3,
        "hit" to 1,
        "run" to 1,
        "rbi" to 1,
        "hr" to 0,
        "bb" to 0,
        "so" to 0,
    )
    // 2026-06-11 SSG 1 : 15 LG, 네이버 relay 4회말 일부.
    // 실제 relayNo/seqno 흐름으로 타석 그룹, 선수별 당일 기록, 주자명, 득점 테두리를 확인한다.
    val events: List<BackendGamesRepository.LiveEvent> = listOf(
        BackendGamesRepository.LiveEvent(
            13,
            "04-045-0283",
            "SCORE",
            "3루주자 신민재 : 홈인",
            "20:12",
            "최용준",
            "오스틴",
            "4회말",
            atBatId = "04-045",
            seqno = 283,
            homeScoreAfter = 9,
            awayScoreAfter = 1,
            batterRecord = austinRecord,
            homeWinProbability = 98.7,
            awayWinProbability = 1.3,
            wpaByPlate = -0.2,
        ),
        BackendGamesRepository.LiveEvent(
            12,
            "04-045-0282",
            "SCORE",
            "2루주자 홍창기 : 홈인",
            "20:12",
            "최용준",
            "오스틴",
            "4회말",
            atBatId = "04-045",
            seqno = 282,
            homeScoreAfter = 8,
            awayScoreAfter = 1,
            batterRecord = austinRecord,
        ),
        BackendGamesRepository.LiveEvent(
            11,
            "04-045-0281",
            "SCORE",
            "1루주자 박해민 : 홈인",
            "20:12",
            "최용준",
            "오스틴",
            "4회말",
            atBatId = "04-045",
            seqno = 281,
            homeScoreAfter = 7,
            awayScoreAfter = 1,
            batterRecord = austinRecord,
        ),
        BackendGamesRepository.LiveEvent(10, "04-045-0280", "HIT", "오스틴 : 좌중간 2루타", "20:12", "최용준", "오스틴", "4회말", atBatId = "04-045", seqno = 280, batterRecord = austinRecord),
        BackendGamesRepository.LiveEvent(9, "04-045-0279", "OTHER", "5구 타격", "20:11", "최용준", "오스틴", "4회말", atBatId = "04-045", seqno = 279, pitchNum = 5, pitchSpeed = 146, pitchStuff = "직구", ballAfter = 3, strikeAfter = 1, outAfter = 0, batterRecord = austinRecord),
        BackendGamesRepository.LiveEvent(8, "04-045-0278", "BALL", "4구 볼", "20:11", "최용준", "오스틴", "4회말", atBatId = "04-045", seqno = 278, pitchNum = 4, pitchSpeed = 132, pitchStuff = "체인지업", ballAfter = 3, strikeAfter = 1, outAfter = 0, batterRecord = austinRecord),
        BackendGamesRepository.LiveEvent(7, "04-045-0277", "BALL", "3구 볼", "20:10", "최용준", "오스틴", "4회말", atBatId = "04-045", seqno = 277, pitchNum = 3, pitchSpeed = 147, pitchStuff = "직구", ballAfter = 2, strikeAfter = 1, outAfter = 0, batterRecord = austinRecord),
        BackendGamesRepository.LiveEvent(6, "04-045-0276", "STRIKE", "2구 파울", "20:10", "최용준", "오스틴", "4회말", atBatId = "04-045", seqno = 276, pitchNum = 2, pitchSpeed = 133, pitchStuff = "체인지업", ballAfter = 1, strikeAfter = 1, outAfter = 0, batterRecord = austinRecord),
        BackendGamesRepository.LiveEvent(5, "04-045-0275", "BALL", "1구 볼", "20:09", "최용준", "오스틴", "4회말", atBatId = "04-045", seqno = 275, pitchNum = 1, pitchSpeed = 146, pitchStuff = "직구", ballAfter = 1, strikeAfter = 0, outAfter = 0, batterRecord = austinRecord),
        BackendGamesRepository.LiveEvent(4, "04-044-0270", "HIT", "박해민 : 우익수 앞 1루타", "20:07", "김건우", "박해민", "4회말", atBatId = "04-044", seqno = 270, batterRecord = parkRecord),
        BackendGamesRepository.LiveEvent(3, "04-043-0262", "SCORE", "2루주자 이주헌 : 홈인", "20:05", "김건우", "홍창기", "4회말", atBatId = "04-043", seqno = 262, homeScoreAfter = 6, awayScoreAfter = 1, batterRecord = hongRecord),
        BackendGamesRepository.LiveEvent(2, "04-043-0260", "HIT", "홍창기 : 중견수 앞 1루타", "20:04", "김건우", "홍창기", "4회말", atBatId = "04-043", seqno = 260, batterRecord = hongRecord),
        BackendGamesRepository.LiveEvent(1, "04-042-0254", "WALK", "신민재 : 볼넷", "20:01", "김건우", "신민재", "4회말", atBatId = "04-042", seqno = 254),
    )

    val lineup = FieldLineup(
        leftFielder = "채현우",
        centerFielder = "김성욱",
        rightFielder = "오태곤",
        shortstop = "안상현",
        secondBaseman = "홍대인",
        thirdBaseman = "최윤석",
        firstBaseman = "전의산",
        catcher = "신범수",
        firstRunner = "박해민",
        secondRunner = "홍창기",
        thirdRunner = "신민재"
    )
}
