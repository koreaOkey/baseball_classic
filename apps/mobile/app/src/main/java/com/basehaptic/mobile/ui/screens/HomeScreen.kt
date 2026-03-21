package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.*
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    selectedTeam: Team,
    todayGames: List<Game>,
    activeTheme: ThemeData?,
    syncedGameId: String?,
    onSelectGame: (Game) -> Unit
) {
    val games = remember(todayGames) { sortHomeGames(todayGames) }
    val context = LocalContext.current
    var teamRecordStats by remember(selectedTeam) {
        mutableStateOf<BackendGamesRepository.TeamRecordStats?>(null)
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
    val primaryColor = activeTheme?.colors?.primary ?: teamTheme.primary
    val rankingText = teamRecordStats?.ranking?.let { "${it}위" } ?: "-"
    val wraText = teamRecordStats?.wra?.let { String.format(Locale.US, "%.3f", it) } ?: "-.--"

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
                    .padding(bottom = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamLogo(team = selectedTeam, size = 56.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "BaseHaptic Live",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = selectedTeam.teamName,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = LocalDate.now().format(
                                        DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
                                    ),
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "\uC624\uB298\uC758 \uACBD\uAE30 ${games.count { isPlayableGameStatus(it.status) }}\uAC1C",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp)
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
                    .padding(horizontal = 24.dp)
                    .offset(y = (-16).dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "5\uC2B9",
                    label = "\uCD5C\uADFC 10\uACBD\uAE30",
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

        // Games List Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uC624\uB298\uC758 \uACBD\uAE30",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Games List
        items(
            items = games,
            key = { it.id }
        ) { game ->
            val isWatchSynced = game.status == GameStatus.LIVE && syncedGameId == game.id
            GameCard(
                game = game,
                primaryColor = primaryColor,
                isWatchSynced = isWatchSynced,
                onClick = { onSelectGame(game) }
            )
        }

        // Upcoming Games (next 3 my-team schedules after today)
        if (upcomingGames.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "\uB2E4\uAC00\uC624\uB294 \uACBD\uAE30",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
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
            Spacer(modifier = Modifier.height(80.dp))
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
        shape = RoundedCornerShape(12.dp),
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Gray400,
                modifier = Modifier.padding(top = 4.dp)
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
    val myTeamName = if (isMyTeamHome) game.homeTeam else game.awayTeam
    val opponentTeamName = if (isMyTeamHome) game.awayTeam else game.homeTeam
    val dateTimeText = formatUpcomingDateTime(upcoming.gameDate, game.time)
    val venueText = if (isMyTeamHome) {
        "${game.homeTeam} 홈경기"
    } else {
        "${game.homeTeam} 원정경기"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateTimeText,
                    fontSize = 14.sp,
                    color = Gray400
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = myTeamName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = " vs ",
                        fontSize = 14.sp,
                        color = Gray500,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        text = opponentTeamName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            Text(
                text = venueText,
                fontSize = 12.sp,
                color = Gray500,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun GameCard(
    game: Game,
    primaryColor: Color,
    isWatchSynced: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (game.isMyTeam) {
        primaryColor.copy(alpha = 0.15f)
    } else {
        Gray900
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        tonalElevation = if (isWatchSynced || game.isMyTeam) 2.dp else 1.dp
    ) {
        Box {
            if (isWatchSynced || game.isMyTeam) {
                // Gradient border effect
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = if (isWatchSynced) 2.dp else 1.dp,
                            color = if (isWatchSynced) Yellow500 else Yellow500.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }

            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Status and Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (game.status) {
                            GameStatus.LIVE -> {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Red500)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LIVE",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Red500
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = game.inning,
                                    fontSize = 14.sp,
                                    color = if (game.isMyTeam) Color.White.copy(alpha = 0.9f) else Gray400
                                )
                                if (!game.time.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "경기 시작 시간 ${game.time}",
                                        fontSize = 12.sp,
                                        color = Gray400
                                    )
                                }
                                if (isWatchSynced) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(워치에서 중계중)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Yellow400
                                    )
                                }
                            }
                            GameStatus.SCHEDULED -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Gray400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (game.time.isNullOrBlank()) "" else "경기 시작 시간 ${game.time}",
                                    fontSize = 14.sp,
                                    color = Gray400
                                )
                            }
                            GameStatus.FINISHED -> {
                                Text(
                                    text = "\uACBD\uAE30 \uC885\uB8CC",
                                    fontSize = 14.sp,
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
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "경기 취소",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Red500
                                )
                                if (!game.time.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "예정 ${game.time}",
                                        fontSize = 12.sp,
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
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "경기 연기",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Orange500
                                )
                                if (!game.time.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "기존 예정 ${game.time}",
                                        fontSize = 12.sp,
                                        color = Gray500
                                    )
                                }
                            }
                        }
                    }

                    if (game.isMyTeam) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Brush.horizontalGradient(
                                colors = listOf(Yellow500, Orange500)
                            ).let { Color(0xFFEAB308) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "응원팀",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Teams and Scores
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamScoreRow(
                        team = game.awayTeamId,
                        teamName = game.awayTeam,
                        score = game.awayScore,
                        pitcher = game.awayPitcher,
                        isScheduled = isNotStartedStatus(game.status),
                        isWinner = game.status == GameStatus.FINISHED && game.awayScore > game.homeScore,
                        isMyTeam = game.isMyTeam
                    )

                    TeamScoreRow(
                        team = game.homeTeamId,
                        teamName = game.homeTeam,
                        score = game.homeScore,
                        pitcher = game.homePitcher,
                        isScheduled = isNotStartedStatus(game.status),
                        isWinner = game.status == GameStatus.FINISHED && game.homeScore > game.awayScore,
                        isMyTeam = game.isMyTeam
                    )
                }
            }
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
            TeamLogo(team = team, size = 32.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = teamName,
                    fontSize = if (isMyTeam) 18.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isWinner) Color.White else if (isScheduled) Color.White else Gray500
                )
                if (pitcher != null) {
                    Text(
                        text = "선발투수 ${pitcher.name}, 최근 ${pitcher.record.wins}/${pitcher.record.draws}/${pitcher.record.losses}",
                        fontSize = 12.sp,
                        color = if (isWinner) Gray400 else Gray600,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Text(
            text = if (isScheduled) "-" else score.toString(),
            fontSize = if (isMyTeam) 28.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (isWinner) Color.White else if (isScheduled) Color.White else Gray500
        )
    }
}

private fun formatUpcomingDateTime(gameDate: LocalDate, rawTime: String?): String {
    val dateText = gameDate.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN))
    val timeText = if (rawTime.isNullOrBlank()) "--:--" else rawTime
    return "$dateText $timeText"
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
