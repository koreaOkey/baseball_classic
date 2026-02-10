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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.*
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.MaterialTheme as M3Theme

@Composable
fun HomeScreen(
    selectedTeam: Team,
    activeTheme: ThemeData?,
    onSelectGame: (String) -> Unit
) {
    val games = remember {
        getMockGames(selectedTeam)
    }
    
    val teamTheme = LocalTeamTheme.current
    val primaryColor = activeTheme?.colors?.primary ?: teamTheme.primary

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
                                    text = "나의 응원팀",
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
                                text = "오늘의 경기 ${games.count { it.status != GameStatus.FINISHED }}개",
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
                    value = "5승",
                    label = "최근 10경기",
                    valueColor = Green500
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "2위",
                    label = "현재 순위",
                    valueColor = Yellow500
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "0.625",
                    label = "승률",
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "오늘의 경기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                TextButton(onClick = { }) {
                    Text(
                        text = "전체보기",
                        fontSize = 14.sp,
                        color = teamTheme.primary
                    )
                }
            }
        }

        // Games List
        items(games) { game ->
            GameCard(
                game = game,
                primaryColor = primaryColor,
                onClick = { onSelectGame(game.id) }
            )
        }

        // Upcoming Games
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "다가오는 경기",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = Gray900,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2월 5일 (목) 18:30",
                            fontSize = 14.sp,
                            color = Gray400
                        )
                        TextButton(onClick = { }) {
                            Text(
                                text = "캘린더 추가",
                                fontSize = 14.sp,
                                color = teamTheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedTeam.teamName,
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
                                text = "KT 위즈",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Green500,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "잠실야구장",
                        fontSize = 12.sp,
                        color = Gray500,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
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
private fun GameCard(
    game: Game,
    primaryColor: Color,
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
        tonalElevation = if (game.isMyTeam) 2.dp else 1.dp
    ) {
        Box {
            if (game.isMyTeam) {
                // Gradient border effect
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 1.dp,
                            color = Yellow500.copy(alpha = 0.5f),
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
                                    text = game.time ?: "",
                                    fontSize = 14.sp,
                                    color = Gray400
                                )
                            }
                            GameStatus.FINISHED -> {
                                Text(
                                    text = game.inning,
                                    fontSize = 14.sp,
                                    color = Gray500
                                )
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
                                    text = "나의 팀",
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
                        isScheduled = game.status == GameStatus.SCHEDULED,
                        isWinner = game.status == GameStatus.FINISHED && game.awayScore > game.homeScore,
                        isMyTeam = game.isMyTeam
                    )

                    TeamScoreRow(
                        team = game.homeTeamId,
                        teamName = game.homeTeam,
                        score = game.homeScore,
                        pitcher = game.homePitcher,
                        isScheduled = game.status == GameStatus.SCHEDULED,
                        isWinner = game.status == GameStatus.FINISHED && game.homeScore > game.awayScore,
                        isMyTeam = game.isMyTeam
                    )
                }

                // Watch Status
                if (game.status == GameStatus.LIVE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(
                        color = if (game.isMyTeam) Yellow500.copy(alpha = 0.3f) else Gray800,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (game.isMyTeam) Icons.Default.Whatshot else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (game.isMyTeam) Yellow400 else Blue400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (game.isMyTeam) "워치에서 실시간 햅틱 중계 중" else "워치에서 실시간 중계 중",
                            fontSize = 14.sp,
                            fontWeight = if (game.isMyTeam) FontWeight.Medium else FontWeight.Normal,
                            color = if (game.isMyTeam) Yellow400 else Blue400
                        )
                    }
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
                        text = "선발: ${pitcher.name}, ${pitcher.winStreak}연승, ${pitcher.record.wins}/${pitcher.record.draws}/${pitcher.record.losses}",
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

private fun getMockGames(selectedTeam: Team): List<Game> {
    return listOf(
        Game(
            id = "1",
            homeTeam = selectedTeam.teamName,
            awayTeam = "LG 트윈스",
            homeTeamId = selectedTeam,
            awayTeamId = Team.LG,
            homeScore = 3,
            awayScore = 2,
            inning = "7회말",
            status = GameStatus.LIVE,
            isMyTeam = true,
            homePitcher = Pitcher("김민수", 3, PitcherRecord(10, 2, 5)),
            awayPitcher = Pitcher("박준용", 2, PitcherRecord(8, 1, 6))
        ),
        Game(
            id = "2",
            homeTeam = "삼성 라이온즈",
            awayTeam = "KIA 타이거즈",
            homeTeamId = Team.SAMSUNG,
            awayTeamId = Team.KIA,
            homeScore = 5,
            awayScore = 4,
            inning = "9회초",
            status = GameStatus.LIVE,
            homePitcher = Pitcher("이상호", 4, PitcherRecord(12, 0, 4)),
            awayPitcher = Pitcher("김태호", 1, PitcherRecord(7, 2, 7))
        ),
        Game(
            id = "3",
            homeTeam = "SSG 랜더스",
            awayTeam = "한화 이글스",
            homeTeamId = Team.SSG,
            awayTeamId = Team.HANWHA,
            homeScore = 0,
            awayScore = 0,
            inning = "",
            status = GameStatus.SCHEDULED,
            time = "18:30",
            homePitcher = Pitcher("정상호", 2, PitcherRecord(9, 1, 5)),
            awayPitcher = Pitcher("김민수", 3, PitcherRecord(10, 2, 5))
        ),
        Game(
            id = "4",
            homeTeam = "NC 다이노스",
            awayTeam = "롯데 자이언츠",
            homeTeamId = Team.NC,
            awayTeamId = Team.LOTTE,
            homeScore = 8,
            awayScore = 3,
            inning = "최종",
            status = GameStatus.FINISHED,
            homePitcher = Pitcher("김태호", 1, PitcherRecord(7, 2, 7)),
            awayPitcher = Pitcher("박준용", 2, PitcherRecord(8, 1, 6))
        )
    )
}

