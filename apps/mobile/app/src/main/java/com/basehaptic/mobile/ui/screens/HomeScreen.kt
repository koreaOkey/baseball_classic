package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.R
import com.basehaptic.mobile.ui.components.BannerAd
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.*
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedTeam: Team,
    todayGames: List<Game>,
    syncedGameId: String?,
    activeLiveScoreGameId: String?,
    onToggleWatchSync: (Game) -> Unit,
    onToggleLiveScore: (Game) -> Unit,
    onSelectGame: (Game) -> Unit
) {
    val games = remember(todayGames) { sortHomeGames(todayGames) }
    val context = LocalContext.current
    var teamRecordStats by remember(selectedTeam) {
        mutableStateOf<BackendGamesRepository.TeamRecordStats?>(null)
    }
    var showStandingsSheet by remember { mutableStateOf(false) }
    var standingsLoadRequest by remember { mutableIntStateOf(0) }
    var standingsLoading by remember { mutableStateOf(false) }
    var standingsError by remember { mutableStateOf<String?>(null) }
    var standingsItems by remember {
        mutableStateOf<List<BackendGamesRepository.TeamRecordStanding>>(emptyList())
    }
    val standingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showScheduleSheet by remember { mutableStateOf(false) }
    var scheduleLoadRequest by remember { mutableIntStateOf(0) }
    var scheduleLoading by remember { mutableStateOf(false) }
    var scheduleError by remember { mutableStateOf<String?>(null) }
    var scheduleItems by remember {
        mutableStateOf<List<BackendGamesRepository.UpcomingGameSchedule>>(emptyList())
    }
    var scheduleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedScheduleDate by remember { mutableStateOf(LocalDate.now()) }
    val scheduleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(showStandingsSheet, standingsLoadRequest) {
        if (!showStandingsSheet) return@LaunchedEffect

        standingsLoading = true
        standingsError = null
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.fetchTeamRecordStandings()
            }
        }.getOrNull()
        if (loaded == null) {
            standingsItems = emptyList()
            standingsError = "팀 순위를 불러오지 못했습니다."
        } else {
            standingsItems = loaded
        }
        standingsLoading = false
    }

    LaunchedEffect(showScheduleSheet, scheduleLoadRequest, selectedTeam) {
        if (!showScheduleSheet) return@LaunchedEffect

        if (selectedTeam == Team.NONE) {
            scheduleItems = emptyList()
            scheduleError = null
            scheduleLoading = false
            return@LaunchedEffect
        }

        scheduleLoading = true
        scheduleError = null
        val today = LocalDate.now()
        val rangeFrom = scheduleSeasonStartDate(today)
        val rangeTo = scheduleSeasonEndDate(today)
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.fetchMyTeamScheduleRangeCached(
                    context = context.applicationContext,
                    selectedTeam = selectedTeam,
                    fromDate = rangeFrom,
                    toDate = rangeTo,
                    forceRefresh = scheduleLoadRequest > 1
                )
            }
        }.getOrNull()
        if (loaded == null) {
            scheduleItems = emptyList()
            scheduleError = "응원팀 일정을 불러오지 못했습니다."
        } else {
            scheduleItems = loaded
        }
        scheduleLoading = false
    }

    LaunchedEffect(selectedTeam) {
        if (selectedTeam == Team.NONE) {
            teamRecordStats = null
            return@LaunchedEffect
        }

        val cachedTeamRecord = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.peekTodayTeamRecordCache(
                    context = context.applicationContext,
                    selectedTeam = selectedTeam
                )
            }
        }.getOrNull()
        if (cachedTeamRecord != null) {
            teamRecordStats = cachedTeamRecord
        }

        val loadedTeamRecord = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.fetchTodayTeamRecordCached(
                    context = context.applicationContext,
                    selectedTeam = selectedTeam
                )
            }
        }.getOrNull()
        if (loadedTeamRecord != null) {
            teamRecordStats = loadedTeamRecord
        }
    }

    LaunchedEffect(selectedTeam) {
        if (selectedTeam == Team.NONE) return@LaunchedEffect

        val reconnectDelaysMs = listOf(1_000L, 2_000L, 5_000L, 10_000L)
        var reconnectAttempt = 0
        while (currentCoroutineContext().isActive) {
            runCatching {
                BackendGamesRepository.streamTeamRecord(selectedTeam).collect { message ->
                    when (message) {
                        BackendGamesRepository.TeamRecordStreamMessage.Connected -> {
                            reconnectAttempt = 0
                        }

                        BackendGamesRepository.TeamRecordStreamMessage.Closed -> {
                            throw IllegalStateException("team record stream closed")
                        }

                        is BackendGamesRepository.TeamRecordStreamMessage.Error -> {
                            throw message.throwable
                        }

                        is BackendGamesRepository.TeamRecordStreamMessage.TeamRecord -> {
                            teamRecordStats = message.value
                            withContext(Dispatchers.IO) {
                                BackendGamesRepository.cacheTodayTeamRecord(
                                    context = context.applicationContext,
                                    selectedTeam = selectedTeam,
                                    value = message.value
                                )
                            }
                        }

                        is BackendGamesRepository.TeamRecordStreamMessage.Pong -> Unit
                    }
                }
            }

            if (!currentCoroutineContext().isActive) break
            val delayMs = reconnectDelaysMs[reconnectAttempt.coerceAtMost(reconnectDelaysMs.lastIndex)]
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(reconnectDelaysMs.lastIndex)
            delay(delayMs)
        }
    }

    val upcomingGames by produceState(
        initialValue = emptyList<BackendGamesRepository.UpcomingGameSchedule>(),
        selectedTeam
    ) {
        if (selectedTeam == Team.NONE) {
            value = emptyList()
            return@produceState
        }

        val backendUpcomingGames = runCatching {
            withContext(Dispatchers.IO) {
                BackendGamesRepository.fetchTodayUpcomingGamesCached(
                    context = context.applicationContext,
                    selectedTeam = selectedTeam,
                    maxItems = 3,
                    daysAhead = 30
                )
            }
        }.getOrNull()
        if (backendUpcomingGames != null) {
            value = backendUpcomingGames
        }
    }

    val teamTheme = LocalTeamTheme.current
    val primaryColor = teamTheme.primary
    val rankingText = teamRecordStats?.ranking?.let { "${it}위" } ?: "-"
    val wraText = teamRecordStats?.wra?.let { String.format(Locale.US, "%.3f", it) } ?: "-.--"
    val recentWinsText = teamRecordStats?.lastFiveGames?.let { games ->
        val wins = games.count { it == 'W' }
        "${wins}승"
    } ?: "-"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(bottom = AppSpacing.xxxl)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.xxl)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamLogo(team = selectedTeam, size = 92.dp)
                            Spacer(modifier = Modifier.width(AppSpacing.md))
                            Column {
                                Text(
                                    text = "BaseHaptic Live",
                                    style = AppFont.micro,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = selectedTeam.teamName,
                                    style = AppFont.h3Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable {
                                    showStandingsSheet = true
                                    standingsLoadRequest += 1
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.kbo_team_standings_icon),
                                contentDescription = "전체 순위",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.lg))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showScheduleSheet = true
                                scheduleMonth = YearMonth.now()
                                selectedScheduleDate = LocalDate.now()
                                scheduleLoadRequest += 1
                            },
                        shape = AppShapes.lg,
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.lg)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(
                                    text = LocalDate.now().format(
                                        DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
                                    ),
                                    style = AppFont.body,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "\uC624\uB298\uC758 \uACBD\uAE30 ${games.count { isPlayableGameStatus(it.status) }}\uAC1C",
                                style = AppFont.body,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = AppSpacing.xs)
                            )
                        }
                    }
                }
            }
        }

        // Quick Stats
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl)
                    .offset(y = (-16).dp),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = recentWinsText,
                    label = "\uCD5C\uADFC 5\uACBD\uAE30",
                    valueColor = Green500
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = rankingText,
                    label = "\uD604\uC7AC \uC21C\uC704",
                    valueColor = Yellow500
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = wraText,
                    label = "\uC2B9\uB960",
                    valueColor = Blue500
                )
            }
        }

        // Ad Banner
        item {
            BannerAd(
                modifier = Modifier.padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.sm)
            )
        }

        // Games List Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uC624\uB298\uC758 \uACBD\uAE30",
                    style = AppFont.h5Bold,
                    color = Color.White
                )
            }
        }

        // Games List
        if (games.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xs),
                    shape = AppShapes.lg,
                    color = Gray900
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.xxxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        Text(
                            text = "\u26BE",
                            style = AppFont.h2
                        )
                        Text(
                            text = "\uC624\uB298\uC740 \uACBD\uAE30\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4",
                            style = AppFont.bodyLgMedium,
                            color = Gray400
                        )
                    }
                }
            }
        } else {
            items(
                items = games,
                key = { it.id }
            ) { game ->
                val isWatchSynced = game.status == GameStatus.LIVE && syncedGameId == game.id
                GameCard(
                    game = game,
                    primaryColor = primaryColor,
                    isWatchSynced = isWatchSynced,
                    isLiveScoreActive = activeLiveScoreGameId == game.id,
                    onClick = { onSelectGame(game) },
                    onWatchSyncClick = { onToggleWatchSync(game) },
                    onLiveScoreClick = { onToggleLiveScore(game) }
                )
            }
        }

        // Upcoming Games (next 3 my-team schedules after today)
        if (upcomingGames.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(AppSpacing.xxxl))
                Text(
                    text = "\uB2E4\uAC00\uC624\uB294 \uACBD\uAE30",
                    style = AppFont.h5Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg)
                )
            }

            items(
                items = upcomingGames,
                key = { "${it.gameDate}:${it.game.id}" }
            ) { upcoming ->
                UpcomingGameCard(
                    selectedTeam = selectedTeam,
                    upcoming = upcoming
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.bottomSafeSpacer))
        }
    }

    if (showStandingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStandingsSheet = false },
            sheetState = standingsSheetState,
            containerColor = Gray950,
            contentColor = Color.White
        ) {
            TeamStandingsSheetContent(
                standings = standingsItems,
                loading = standingsLoading,
                error = standingsError,
                onRetry = { standingsLoadRequest += 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl)
                    .padding(bottom = AppSpacing.xxxl)
            )
        }
    }

    if (showScheduleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScheduleSheet = false },
            sheetState = scheduleSheetState,
            containerColor = Gray950,
            contentColor = Color.White
        ) {
            MyTeamScheduleSheetContent(
                selectedTeam = selectedTeam,
                schedules = scheduleItems,
                currentMonth = scheduleMonth,
                selectedDate = selectedScheduleDate,
                loading = scheduleLoading,
                error = scheduleError,
                onRetry = { scheduleLoadRequest += 1 },
                onPreviousMonth = { scheduleMonth = scheduleMonth.minusMonths(1) },
                onNextMonth = { scheduleMonth = scheduleMonth.plusMonths(1) },
                onSelectDate = { selectedScheduleDate = it },
                onSelectSchedule = { game ->
                    showScheduleSheet = false
                    onSelectGame(game)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(horizontal = AppSpacing.xxl)
                    .padding(bottom = AppSpacing.xxxl)
            )
        }
    }
}

@Composable
private fun TeamStandingsSheetContent(
    standings: List<BackendGamesRepository.TeamRecordStanding>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "전체 순위",
            style = AppFont.h4Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        when {
            loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Yellow500)
                    Text(
                        text = "순위를 불러오는 중입니다",
                        style = AppFont.body,
                        color = Gray400,
                        modifier = Modifier.padding(top = AppSpacing.md)
                    )
                }
            }

            error != null -> {
                TeamStandingsMessage(
                    title = error,
                    actionLabel = "다시 시도",
                    onAction = onRetry
                )
            }

            standings.isEmpty() -> {
                TeamStandingsMessage(
                    title = "저장된 팀 순위가 없습니다",
                    actionLabel = "새로고침",
                    onAction = onRetry
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(standings, key = { it.teamId }) { item ->
                        TeamStandingRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTeamScheduleSheetContent(
    selectedTeam: Team,
    schedules: List<BackendGamesRepository.UpcomingGameSchedule>,
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSelectSchedule: (Game) -> Unit,
    modifier: Modifier = Modifier
) {
    val schedulesByDate = remember(schedules) { schedules.groupBy { it.gameDate } }
    val selectedSchedules = schedulesByDate[selectedDate].orEmpty()

    Column(modifier = modifier) {
        Text(
            text = "응원팀 경기 일정",
            style = AppFont.h4Bold,
            color = Color.White
        )
        Text(
            text = if (selectedTeam == Team.NONE) "응원팀을 선택하면 일정을 볼 수 있습니다." else "${selectedTeam.teamName} 시즌 일정",
            style = AppFont.body,
            color = Gray400,
            modifier = Modifier.padding(top = AppSpacing.xs)
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        when {
            selectedTeam == Team.NONE -> {
                ScheduleMessageState(
                    icon = Icons.Default.SportsBaseball,
                    title = "응원팀이 선택되지 않았습니다",
                    body = "마이팀에서 응원팀을 먼저 선택해주세요."
                )
            }

            loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "일정을 불러오는 중입니다",
                        style = AppFont.body,
                        color = Gray400
                    )
                }
            }

            error != null -> {
                ScheduleMessageState(
                    icon = Icons.Default.ErrorOutline,
                    title = error,
                    body = "네트워크 상태를 확인한 뒤 다시 시도해주세요.",
                    actionLabel = "다시 시도",
                    onAction = onRetry
                )
            }

            schedules.isEmpty() -> {
                ScheduleMessageState(
                    icon = Icons.Default.EventBusy,
                    title = "표시할 일정이 없습니다",
                    body = "저장된 응원팀 경기 일정이 없습니다."
                )
            }

            else -> {
                ScheduleCalendar(
                    selectedTeam = selectedTeam,
                    month = currentMonth,
                    selectedDate = selectedDate,
                    schedulesByDate = schedulesByDate,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = onSelectDate
                )

                Spacer(modifier = Modifier.height(AppSpacing.lg))

                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                    style = AppFont.bodyLgMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                if (selectedSchedules.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.md,
                        color = Gray900
                    ) {
                        Text(
                            text = "선택한 날짜에 등록된 경기가 없습니다.",
                            style = AppFont.body,
                            color = Gray400,
                            modifier = Modifier.padding(AppSpacing.lg)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(
                            items = selectedSchedules,
                            key = { "${it.gameDate}:${it.game.id}" }
                        ) { schedule ->
                            MyTeamScheduleRow(
                                selectedTeam = selectedTeam,
                                schedule = schedule,
                                onClick = { onSelectSchedule(schedule.game) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCalendar(
    selectedTeam: Team,
    month: YearMonth,
    selectedDate: LocalDate,
    schedulesByDate: Map<LocalDate, List<BackendGamesRepository.UpcomingGameSchedule>>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "이전 달",
                        tint = Color.White
                    )
                }
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)),
                    style = AppFont.bodyLgBold,
                    color = Color.White
                )
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "다음 달",
                        tint = Color.White
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach { weekday ->
                    Text(
                        text = weekday,
                        style = AppFont.microBold,
                        color = Gray500,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            monthGridDates(month).chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            selectedTeam = selectedTeam,
                            date = date,
                            isSelected = date == selectedDate,
                            schedules = date?.let { schedulesByDate[it] }.orEmpty(),
                            onSelectDate = onSelectDate,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.xs))
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    selectedTeam: Team,
    date: LocalDate?,
    isSelected: Boolean,
    schedules: List<BackendGamesRepository.UpcomingGameSchedule>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = remember(selectedTeam, schedules) { calendarDayLabel(selectedTeam, schedules) }
    val hasGame = schedules.isNotEmpty()
    val background = when {
        isSelected -> Gray800
        hasGame -> Gray950
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> Yellow500
        hasGame -> Gray700
        else -> Color.Transparent
    }
    val textColor = when {
        date == null -> Color.Transparent
        else -> Color.White
    }

    Box(
        modifier = modifier
            .height(72.dp)
            .clip(AppShapes.md)
            .background(background)
            .border(width = 1.dp, color = borderColor, shape = AppShapes.md)
            .clickable(enabled = date != null) {
                if (date != null) onSelectDate(date)
            },
        contentAlignment = Alignment.TopCenter
    ) {
        if (date != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = AppSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = if (isSelected) AppFont.bodyMedium else AppFont.body,
                    color = textColor
                )
                if (label != null) {
                    if (label.opponentTeam != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label.text,
                                style = AppFont.microBold,
                                color = label.color,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center
                            )
                            TeamLogo(team = label.opponentTeam, size = 22.dp)
                        }
                    } else {
                        Text(
                            text = label.text,
                            style = AppFont.microBold,
                            color = label.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

private data class CalendarDayLabel(
    val text: String,
    val color: Color,
    val opponentTeam: Team? = null
)

@Composable
private fun ScheduleMessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.lg,
        color = Gray900
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gray500,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                style = AppFont.bodyLgMedium,
                color = Color.White
            )
            Text(
                text = body,
                style = AppFont.body,
                color = Gray400
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun MyTeamScheduleRow(
    selectedTeam: Team,
    schedule: BackendGamesRepository.UpcomingGameSchedule,
    onClick: () -> Unit
) {
    val game = schedule.game
    val isMyTeamHome = game.homeTeamId == selectedTeam
    val opponentTeam = if (isMyTeamHome) game.awayTeamId else game.homeTeamId
    val opponent = if (isMyTeamHome) game.awayTeamId.teamName else game.homeTeamId.teamName
    val venueText = if (isMyTeamHome) "홈" else "원정"
    val stadiumName = stadiumNameForHomeTeam(game.homeTeamId)
    val venueWithStadium = "$venueText 경기 ($stadiumName)"
    val resultLabel = myTeamResultLabel(selectedTeam, game)
    val scoreText = myTeamScoreText(selectedTeam, game)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AppShapes.md,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatUpcomingDateTime(schedule.gameDate, game.time),
                    style = AppFont.captionBold,
                    color = Gray400
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    TeamLogo(team = selectedTeam, size = 22.dp)
                    Text(
                        text = selectedTeam.teamName,
                        style = AppFont.bodyLgMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "vs",
                        style = AppFont.bodyLgMedium,
                        color = Gray400
                    )
                    TeamLogo(team = opponentTeam, size = 22.dp)
                    Text(
                        text = opponent,
                        style = AppFont.bodyLgMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (resultLabel != null && scoreText != null) {
                    Row(
                        modifier = Modifier.padding(top = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        Surface(
                            shape = AppShapes.pill,
                            color = resultLabel.color.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = resultLabel.text,
                                style = AppFont.microBold,
                                color = resultLabel.color,
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.sm,
                                    vertical = AppSpacing.xxs
                                )
                            )
                        }
                        Text(
                            text = "$scoreText · $venueWithStadium",
                            style = AppFont.micro,
                            color = Gray400
                        )
                    }
                } else {
                    Text(
                        text = venueWithStadium,
                        style = AppFont.micro,
                        color = Gray500,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                }
            }

            ScheduleStatusBadge(status = game.status)
        }
    }
}

@Composable
private fun ScheduleStatusBadge(status: GameStatus) {
    val (label, color) = when (status) {
        GameStatus.LIVE -> "LIVE" to Red500
        GameStatus.FINISHED -> "종료" to Gray500
        GameStatus.SCHEDULED -> "예정" to Blue500
        GameStatus.POSTPONED -> "연기" to Yellow500
        GameStatus.CANCELED -> "취소" to Gray500
    }

    Surface(
        shape = AppShapes.pill,
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = label,
            style = AppFont.microBold,
            color = color,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
        )
    }
}

@Composable
private fun TeamStandingsMessage(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Text(text = title, style = AppFont.bodyLgMedium, color = Gray300)
        TextButton(onClick = onAction) {
            Text(text = actionLabel, color = Yellow500)
        }
    }
}

@Composable
private fun TeamStandingRow(item: BackendGamesRepository.TeamRecordStanding) {
    val team = teamFromKboTeamId(item.teamId)
    val displayName = team?.teamName ?: item.teamName
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.md,
        color = Gray900
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.ranking?.let { "$it" } ?: "-",
                style = AppFont.bodyLgBold,
                color = if ((item.ranking ?: 99) <= 3) Yellow500 else Gray300,
                modifier = Modifier.width(30.dp)
            )
            if (team != null) {
                TeamLogo(team = team, size = 36.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Gray800),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1),
                        style = AppFont.microBold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(AppSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = AppFont.bodyLgMedium,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = teamRecordLine(item),
                    style = AppFont.micro,
                    color = Gray400,
                    modifier = Modifier.padding(top = AppSpacing.xxs)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "승률 ${item.wra?.let { String.format(Locale.US, "%.3f", it) } ?: "-.--"}",
                    style = AppFont.bodyMedium,
                    color = Blue400
                )
                Text(
                    text = "게임차 ${formatGameBehind(item.gameBehind)}",
                    style = AppFont.tiny,
                    color = Gray500,
                    modifier = Modifier.padding(top = AppSpacing.xxs)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    valueColor: Color
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.md,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = AppFont.h4Bold,
                color = valueColor
            )
            Text(
                text = label,
                style = AppFont.tiny,
                color = Gray400,
                modifier = Modifier.padding(top = AppSpacing.xs)
            )
        }
    }
}

@Composable
private fun UpcomingGameCard(
    selectedTeam: Team,
    upcoming: BackendGamesRepository.UpcomingGameSchedule
) {
    val game = upcoming.game
    val isMyTeamHome = game.homeTeamId == selectedTeam
    val myTeamName = if (isMyTeamHome) game.homeTeamId.teamName else game.awayTeamId.teamName
    val opponentTeamName = if (isMyTeamHome) game.awayTeamId.teamName else game.homeTeamId.teamName
    val dateTimeText = formatUpcomingDateTime(upcoming.gameDate, game.time)
    val venueText = if (isMyTeamHome) {
        "${game.homeTeamId.teamName} 홈경기"
    } else {
        "${game.homeTeamId.teamName} 원정경기"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .padding(bottom = AppSpacing.md),
        shape = AppShapes.lg,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateTimeText,
                    style = AppFont.body,
                    color = Gray400
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = myTeamName,
                        style = AppFont.bodyLgMedium,
                        color = Color.White
                    )
                    Text(
                        text = " vs ",
                        style = AppFont.body,
                        color = Gray500,
                        modifier = Modifier.padding(horizontal = AppSpacing.sm)
                    )
                    Text(
                        text = opponentTeamName,
                        style = AppFont.bodyLgMedium,
                        color = Color.White
                    )
                }
            }

            Text(
                text = venueText,
                style = AppFont.micro,
                color = Gray500,
                modifier = Modifier.padding(top = AppSpacing.sm)
            )
        }
    }
}

@Composable
private fun GameCard(
    game: Game,
    primaryColor: Color,
    isWatchSynced: Boolean,
    isLiveScoreActive: Boolean,
    onClick: () -> Unit,
    onWatchSyncClick: () -> Unit,
    onLiveScoreClick: () -> Unit
) {
    val backgroundColor = if (game.isMyTeam) {
        primaryColor.copy(alpha = 0.15f)
    } else {
        Gray900
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xs),
        shape = AppShapes.lg,
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Box {
            if (isWatchSynced || isLiveScoreActive || game.isMyTeam) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = if (isWatchSynced || isLiveScoreActive) 1.5.dp else 1.dp,
                            color = if (isWatchSynced || isLiveScoreActive) {
                                Green500.copy(alpha = 0.82f)
                            } else {
                                Yellow500.copy(alpha = 0.42f)
                            },
                            shape = AppShapes.lg
                        )
                )
            }

            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (game.status) {
                                GameStatus.LIVE -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Red500)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                                    Text(
                                        text = "LIVE",
                                        style = AppFont.captionBold,
                                        color = Red500
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                                    Text(
                                        text = game.inning,
                                        style = AppFont.captionMedium,
                                        color = if (game.isMyTeam) Color.White.copy(alpha = 0.86f) else Gray400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isWatchSynced || isLiveScoreActive) {
                                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                                        Surface(
                                            shape = AppShapes.pill,
                                            color = Yellow500.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "중계중",
                                                style = AppFont.tinyBold,
                                                color = Yellow400,
                                                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
                                            )
                                        }
                                    }
                                }
                                GameStatus.SCHEDULED -> {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Gray400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                                    Text(
                                        text = if (game.time.isNullOrBlank()) "" else "경기 시작 ${game.time}",
                                        style = AppFont.captionMedium,
                                        color = Gray400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                GameStatus.FINISHED -> {
                                    Text(
                                        text = "\uACBD\uAE30 \uC885\uB8CC",
                                        style = AppFont.captionMedium,
                                        color = Gray500
                                    )
                                }
                                GameStatus.CANCELED -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Red500)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                                    Text(
                                        text = "경기 취소",
                                        style = AppFont.captionBold,
                                        color = Red500
                                    )
                                    if (!game.time.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                                        Text(
                                            text = "예정 ${game.time}",
                                            style = AppFont.micro,
                                            color = Gray500
                                        )
                                    }
                                }
                                GameStatus.POSTPONED -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Orange500)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                                    Text(
                                        text = "경기 연기",
                                        style = AppFont.captionBold,
                                        color = Orange500
                                    )
                                    if (!game.time.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                                        Text(
                                            text = "기존 예정 ${game.time}",
                                            style = AppFont.micro,
                                            color = Gray500
                                        )
                                    }
                                }
                            }
                        }

                        if (game.isMyTeam) {
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Surface(
                                shape = AppShapes.pill,
                                color = Yellow500.copy(alpha = 0.94f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.xxs))
                                    Text(
                                        text = "응원팀",
                                        style = AppFont.tinyBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.lg))

                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        TeamScoreRow(
                            team = game.awayTeamId,
                            teamName = game.awayTeamId.teamName,
                            score = game.awayScore,
                            pitcher = game.awayPitcher,
                            isScheduled = isNotStartedStatus(game.status),
                            isWinner = game.status == GameStatus.FINISHED && game.awayScore > game.homeScore,
                            isMyTeam = game.isMyTeam
                        )

                        TeamScoreRow(
                            team = game.homeTeamId,
                            teamName = game.homeTeamId.teamName,
                            score = game.homeScore,
                            pitcher = game.homePitcher,
                            isScheduled = isNotStartedStatus(game.status),
                            isWinner = game.status == GameStatus.FINISHED && game.homeScore > game.awayScore,
                            isMyTeam = game.isMyTeam
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Gray800)
                )
                LiveActionRow(
                    isLiveScoreActive = isLiveScoreActive,
                    isWatchSynced = isWatchSynced,
                    onLiveScoreClick = onLiveScoreClick,
                    onWatchSyncClick = onWatchSyncClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                )
            }
        }
    }
}

@Composable
private fun LiveActionRow(
    isLiveScoreActive: Boolean,
    isWatchSynced: Boolean,
    onLiveScoreClick: () -> Unit,
    onWatchSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        GameToggleCell(
            title = "잠금화면",
            icon = if (isLiveScoreActive) Icons.Default.Lock else Icons.Default.LockOpen,
            isActive = isLiveScoreActive,
            onClick = onLiveScoreClick,
            modifier = Modifier.weight(1f)
        )
        GameToggleCell(
            title = "Watch",
            icon = Icons.Default.Watch,
            isActive = isWatchSynced,
            onClick = onWatchSyncClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GameToggleCell(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.md,
        color = if (isActive) Green500.copy(alpha = 0.10f) else Gray950.copy(alpha = 0.28f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isActive) Green500.copy(alpha = 0.30f) else Gray800.copy(alpha = 0.72f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = AppSpacing.sm, end = AppSpacing.sm, top = AppSpacing.xs, bottom = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Green500.copy(alpha = 0.18f) else Gray800),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) Green400 else Gray300,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.xs))
            Text(
                text = title,
                style = AppFont.microBold,
                color = if (isActive) Green400 else Gray300,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isActive,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(width = 34.dp, height = 22.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Green400,
                    checkedTrackColor = Green500.copy(alpha = 0.42f),
                    uncheckedThumbColor = Gray400,
                    uncheckedTrackColor = Gray800,
                    uncheckedBorderColor = Gray700
                )
            )
        }
    }
}

@Composable
private fun TeamScoreRow(
    team: Team,
    teamName: String,
    score: Int,
    pitcher: Pitcher?,
    isScheduled: Boolean,
    isWinner: Boolean,
    isMyTeam: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamLogo(team = team, size = 56.dp)
            Spacer(modifier = Modifier.width(AppSpacing.md))
            Column {
                Text(
                    text = teamName,
                    style = if (isMyTeam) AppFont.h5Bold else AppFont.bodyLgMedium,
                    color = if (isWinner) Color.White else if (isScheduled) Color.White else Gray500
                )
                if (pitcher != null) {
                    Text(
                        text = "선발투수 ${pitcher.name}, 최근 ${pitcher.record.wins}/${pitcher.record.draws}/${pitcher.record.losses}",
                        style = AppFont.micro,
                        color = if (isWinner) Gray400 else Gray600,
                        modifier = Modifier.padding(top = AppSpacing.xxs)
                    )
                }
            }
        }

        Text(
            text = if (isScheduled) "-" else score.toString(),
            style = if (isMyTeam) AppFont.h2 else AppFont.h3Bold,
            color = if (isWinner) Color.White else if (isScheduled) Color.White else Gray500
        )
    }
}

private fun formatUpcomingDateTime(gameDate: LocalDate, rawTime: String?): String {
    val dateText = gameDate.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN))
    val timeText = if (rawTime.isNullOrBlank()) "--:--" else rawTime
    return "$dateText $timeText"
}

private fun teamRecordLine(item: BackendGamesRepository.TeamRecordStanding): String {
    val games = item.gameCount?.let { "${it}경기" } ?: "-경기"
    val wins = item.winGameCount ?: 0
    val draws = item.drawnGameCount ?: 0
    val losses = item.loseGameCount ?: 0
    return "$games ${wins}승 ${draws}무 ${losses}패"
}

private fun formatGameBehind(value: Double?): String {
    return when (value) {
        null -> "-"
        0.0 -> "0"
        else -> {
            val whole = value.toInt()
            if (value == whole.toDouble()) whole.toString() else String.format(Locale.US, "%.1f", value)
        }
    }
}

private fun teamFromKboTeamId(teamId: String): Team? {
    return when (teamId.uppercase(Locale.US)) {
        "OB" -> Team.DOOSAN
        "LG" -> Team.LG
        "WO" -> Team.KIWOOM
        "SS" -> Team.SAMSUNG
        "LT" -> Team.LOTTE
        "SK" -> Team.SSG
        "KT" -> Team.KT
        "HH" -> Team.HANWHA
        "HT" -> Team.KIA
        "NC" -> Team.NC
        else -> null
    }
}

private fun sortHomeGames(games: List<Game>): List<Game> {
    return games.sortedWith { a, b ->
        val myTeamCompare = b.isMyTeam.compareTo(a.isMyTeam)
        if (myTeamCompare != 0) return@sortedWith myTeamCompare

        val statusCompare = statusPriority(a.status).compareTo(statusPriority(b.status))
        if (statusCompare != 0) return@sortedWith statusCompare

        if (a.status == GameStatus.FINISHED && b.status == GameStatus.FINISHED) {
            val finishedTimeCompare = gameStartTime(a).compareTo(gameStartTime(b))
            if (finishedTimeCompare != 0) return@sortedWith finishedTimeCompare
        }

        val genericTimeCompare = gameStartTime(a).compareTo(gameStartTime(b))
        if (genericTimeCompare != 0) return@sortedWith genericTimeCompare

        a.id.compareTo(b.id)
    }
}

private fun statusPriority(status: GameStatus): Int {
    return when (status) {
        GameStatus.LIVE -> 0
        GameStatus.FINISHED -> 1
        GameStatus.SCHEDULED -> 2
        GameStatus.POSTPONED -> 3
        GameStatus.CANCELED -> 4
    }
}

private fun isNotStartedStatus(status: GameStatus): Boolean {
    return when (status) {
        GameStatus.SCHEDULED,
        GameStatus.POSTPONED,
        GameStatus.CANCELED -> true
        GameStatus.LIVE,
        GameStatus.FINISHED -> false
    }
}

private fun isPlayableGameStatus(status: GameStatus): Boolean {
    return when (status) {
        GameStatus.LIVE,
        GameStatus.SCHEDULED -> true
        GameStatus.FINISHED,
        GameStatus.CANCELED,
        GameStatus.POSTPONED -> false
    }
}

private fun isTerminalStatus(status: GameStatus): Boolean {
    return when (status) {
        GameStatus.FINISHED,
        GameStatus.CANCELED,
        GameStatus.POSTPONED -> true
        GameStatus.LIVE,
        GameStatus.SCHEDULED -> false
    }
}

private fun gameStartTime(game: Game): LocalTime {
    val raw = game.time.orEmpty()
    if (raw.isBlank()) return LocalTime.MAX
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return runCatching {
        LocalTime.parse(raw, formatter)
    }.getOrElse { LocalTime.MAX }
}

private fun calendarDayLabel(
    selectedTeam: Team,
    schedules: List<BackendGamesRepository.UpcomingGameSchedule>
): CalendarDayLabel? {
    if (schedules.isEmpty()) return null
    if (schedules.size > 1) return CalendarDayLabel("${schedules.size}경기", Yellow500)

    val game = schedules.first().game
    return when (game.status) {
        GameStatus.FINISHED -> myTeamResultLabel(selectedTeam, game)

        GameStatus.LIVE -> CalendarDayLabel("LIVE", Red500)
        GameStatus.CANCELED -> CalendarDayLabel("취소", Gray400)
        GameStatus.POSTPONED -> CalendarDayLabel("연기", Yellow500)
        GameStatus.SCHEDULED -> {
            val opponent = if (game.homeTeamId == selectedTeam) game.awayTeamId else game.homeTeamId
            CalendarDayLabel("vs", Gray100, opponentTeam = opponent)
        }
    }
}

private fun myTeamResultLabel(selectedTeam: Team, game: Game): CalendarDayLabel? {
    if (game.status != GameStatus.FINISHED) return null

    val isHome = game.homeTeamId == selectedTeam
    val myScore = if (isHome) game.homeScore else game.awayScore
    val opponentScore = if (isHome) game.awayScore else game.homeScore
    return when {
        myScore > opponentScore -> CalendarDayLabel("승", Green500)
        myScore < opponentScore -> CalendarDayLabel("패", Red500)
        else -> CalendarDayLabel("무", Gray400)
    }
}

private fun myTeamScoreText(selectedTeam: Team, game: Game): String? {
    if (game.status != GameStatus.FINISHED) return null

    val isHome = game.homeTeamId == selectedTeam
    val myScore = if (isHome) game.homeScore else game.awayScore
    val opponentScore = if (isHome) game.awayScore else game.homeScore
    return "$myScore : $opponentScore"
}

private fun stadiumNameForHomeTeam(team: Team): String {
    return when (team) {
        Team.DOOSAN,
        Team.LG -> "잠실야구장"
        Team.KIWOOM -> "고척스카이돔"
        Team.SSG -> "인천SSG랜더스필드"
        Team.KT -> "수원KT위즈파크"
        Team.HANWHA -> "대전한화생명이글스파크"
        Team.SAMSUNG -> "대구삼성라이온즈파크"
        Team.LOTTE -> "사직야구장"
        Team.KIA -> "광주기아챔피언스필드"
        Team.NC -> "창원NC파크"
        Team.NONE -> "오늘 경기장"
    }
}

private fun monthGridDates(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val leadingEmptyDays = firstDay.dayOfWeek.value % 7
    val days = mutableListOf<LocalDate?>()
    repeat(leadingEmptyDays) { days.add(null) }
    for (day in 1..month.lengthOfMonth()) {
        days.add(month.atDay(day))
    }
    while (days.size % 7 != 0) {
        days.add(null)
    }
    return days
}

private fun scheduleSeasonEndDate(today: LocalDate): LocalDate {
    val septemberEnd = LocalDate.of(today.year, 9, 30)
    return if (!today.isAfter(septemberEnd)) septemberEnd else today.plusDays(30)
}

private fun scheduleSeasonStartDate(today: LocalDate): LocalDate {
    return LocalDate.of(today.year, 3, 1)
}

private fun getMockGames(selectedTeam: Team): List<Game> {
    val isSsgOrKiwoomFan = selectedTeam == Team.SSG || selectedTeam == Team.KIWOOM
    return listOf(
        Game(
            id = "20250902WOSK02025",
            homeTeam = "SSG Landers",
            awayTeam = "Kiwoom Heroes",
            homeTeamId = Team.SSG,
            awayTeamId = Team.KIWOOM,
            homeScore = 3,
            awayScore = 2,
            inning = "7회초",
            status = GameStatus.LIVE,
            isMyTeam = isSsgOrKiwoomFan,
            homePitcher = Pitcher("Kim Minsu", 3, PitcherRecord(10, 2, 5)),
            awayPitcher = Pitcher("Park Chulwoo", 2, PitcherRecord(8, 1, 6))
        ),
        Game(
            id = "2",
            homeTeam = "Samsung Lions",
            awayTeam = "KIA Tigers",
            homeTeamId = Team.SAMSUNG,
            awayTeamId = Team.KIA,
            homeScore = 5,
            awayScore = 4,
            inning = "9회말",
            status = GameStatus.LIVE,
            homePitcher = Pitcher("Lee Sanghoon", 4, PitcherRecord(12, 0, 4)),
            awayPitcher = Pitcher("Kim Junho", 1, PitcherRecord(7, 2, 7))
        ),
        Game(
            id = "3",
            homeTeam = "SSG Landers",
            awayTeam = "Hanwha Eagles",
            homeTeamId = Team.SSG,
            awayTeamId = Team.HANWHA,
            homeScore = 0,
            awayScore = 0,
            inning = "",
            status = GameStatus.SCHEDULED,
            time = "18:30",
            homePitcher = Pitcher("Jung Sangwoo", 2, PitcherRecord(9, 1, 5)),
            awayPitcher = Pitcher("Choi Doyoon", 3, PitcherRecord(10, 2, 5))
        ),
        Game(
            id = "4",
            homeTeam = "NC Dinos",
            awayTeam = "Lotte Giants",
            homeTeamId = Team.NC,
            awayTeamId = Team.LOTTE,
            homeScore = 8,
            awayScore = 3,
            inning = "FINAL",
            status = GameStatus.FINISHED,
            homePitcher = Pitcher("Kim Taekho", 1, PitcherRecord(7, 2, 7)),
            awayPitcher = Pitcher("Park Jungwoo", 2, PitcherRecord(8, 1, 6))
        )
    )
}
