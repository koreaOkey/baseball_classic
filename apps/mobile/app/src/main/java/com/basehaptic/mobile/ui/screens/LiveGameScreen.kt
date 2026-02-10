package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.basehaptic.mobile.ui.theme.*
import androidx.compose.material3.MaterialTheme as M3Theme

@Composable
fun LiveGameScreen(
    selectedTeam: Team,
    activeTheme: ThemeData?,
    gameId: String?,
    onBack: () -> Unit
) {
    val events = remember { getMockEvents() }
    val count = remember { mutableStateOf(Count(ball = 2, strike = 1, out = 1)) }
    val score = remember { mutableStateOf(Score(home = 3, away = 2)) }
    val inning = remember { mutableStateOf("7회말") }
    val isWatchConnected = remember { mutableStateOf(true) }

    val teamTheme = LocalTeamTheme.current
    val primaryColor = activeTheme?.colors?.primary ?: teamTheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        // Header with scoreboard
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "뒤로",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Red500)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE 중계중",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                // Scoreboard
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = inning.value,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Away team
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "원정",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "LG",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    text = score.value.away.toString(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                text = ":",
                                fontSize = 24.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // Home team
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "홈",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "두산",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    text = score.value.home.toString(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // Count indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CountIndicator("B", count.value.ball, 3, Green500)
                            CountIndicator("S", count.value.strike, 3, Yellow500)
                            CountIndicator("O", count.value.out, 3, Red500)
                        }
                    }
                }
            }
        }

        // Watch connection status
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isWatchConnected.value) Green500.copy(alpha = 0.2f) else Gray800,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .border(
                        width = 1.dp,
                        color = if (isWatchConnected.value) Green500.copy(alpha = 0.3f) else Gray700,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = null,
                    tint = if (isWatchConnected.value) Green500 else Gray500,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 0.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isWatchConnected.value) "스마트워치 연결됨" else "스마트워치 연결 안 됨",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isWatchConnected.value) Green400 else Gray400
                    )
                    Text(
                        text = "마지막 햅틱: ●●●",
                        fontSize = 12.sp,
                        color = Gray400,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (isWatchConnected.value) Green500 else Gray500,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Events list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "실시간 이벤트",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Gray400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "1,234명 시청중",
                            fontSize = 14.sp,
                            color = Gray400
                        )
                    }
                }
            }

            items(events) { event ->
                EventCard(event = event)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Quick Cheer Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Gray900,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "퀵 응원",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val emojis = listOf("👏", "🔥", "⚡", "💪", "😭", "🎉", "⚾", "❤️")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                emojis.take(4).forEach { emoji ->
                                    Button(
                                        onClick = { },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Gray800
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 24.sp
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                emojis.drop(4).forEach { emoji ->
                                    Button(
                                        onClick = { },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Gray800
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 24.sp
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "워치에서도 바로 전송 가능",
                            fontSize = 12.sp,
                            color = Gray500,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun CountIndicator(
    label: String,
    current: Int,
    max: Int,
    activeColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(max) { index ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < current) activeColor else Color.White.copy(alpha = 0.2f)
                        )
                )
            }
        }
    }
}

@Composable
private fun EventCard(event: GameEvent) {
    val eventColor = when (event.type) {
        EventType.HOMERUN, EventType.SCORE -> Yellow500
        EventType.HIT -> Green500
        EventType.STRIKE, EventType.OUT -> Red500
        EventType.BALL -> Gray500
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(eventColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.type.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = eventColor
                    )
                }
                Text(
                    text = event.time,
                    fontSize = 14.sp,
                    color = Gray500
                )
            }

            if (event.description.isNotEmpty()) {
                Text(
                    text = event.description,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (event.hapticPattern != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Gray800)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val thm = LocalTeamTheme.current
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = thm.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "햅틱 패턴:",
                        fontSize = 12.sp,
                        color = Gray400
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = thm.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = event.hapticPattern,
                            fontSize = 12.sp,
                            color = thm.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Count(val ball: Int, val strike: Int, val out: Int)
private data class Score(val home: Int, val away: Int)

private fun getMockEvents(): List<GameEvent> {
    return listOf(
        GameEvent(
            id = "1",
            type = EventType.HOMERUN,
            description = "박건우, 좌중간 담장을 넘기는 2점 홈런!",
            time = "19:45",
            hapticPattern = EventType.getHapticPattern(EventType.HOMERUN)
        ),
        GameEvent(
            id = "2",
            type = EventType.SCORE,
            description = "김재환의 적시타로 1점 추가!",
            time = "19:32",
            hapticPattern = EventType.getHapticPattern(EventType.SCORE)
        ),
        GameEvent(
            id = "3",
            type = EventType.STRIKE,
            description = "삼진 아웃! 불스아이!",
            time = "19:28",
            hapticPattern = EventType.getHapticPattern(EventType.STRIKE)
        ),
        GameEvent(
            id = "4",
            type = EventType.HIT,
            description = "중앙을 가르는 깔끔한 안타!",
            time = "19:15",
            hapticPattern = EventType.getHapticPattern(EventType.HIT)
        )
    )
}

