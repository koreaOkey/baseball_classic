package com.basehaptic.watch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.basehaptic.watch.data.GameData
import com.basehaptic.watch.data.getMockGameData
import com.basehaptic.watch.ui.theme.*

@Composable
fun LiveGameScreen(
    gameData: GameData,
    modifier: Modifier = Modifier
) {
    // 테마에서 색상 가져오기 (동적으로 변경됨)
    val watchTheme = LocalWatchTeamTheme.current

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        watchTheme.gradientStart,
                        watchTheme.gradientEnd
                    )
                )
            ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        anchorType = ScalingLazyListAnchorType.ItemStart
    ) {
        // Live Badge & Time
        item {
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
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.caption1,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Inning
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = gameData.inning,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.caption1,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Score Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HOME Label
                Text(
                    text = "HOME",
                    style = MaterialTheme.typography.caption1,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Score
                Text(
                    text = gameData.homeScore.toString(),
                    style = MaterialTheme.typography.title1,
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // VS
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.caption1,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                
                // Score Difference
                if (gameData.scoreDiff > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = "+${gameData.scoreDiff}점차",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.caption1,
                            color = watchTheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Count Indicators (BSO)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CountIndicator("B", gameData.ballCount, 3, Green500)
                CountIndicator("S", gameData.strikeCount, 2, Red500)
                CountIndicator("O", gameData.outCount, 2, Yellow500)
            }
        }

        // Base Diamond
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BaseDiamond(gameData.bases, accentColor = watchTheme.secondary)
            }
        }

        // Player Info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "P: ${gameData.pitcher}  •  B: ${gameData.batter}",
                    style = MaterialTheme.typography.caption1,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp
                )
            }
        }

        // Detail Button
        item {
            Chip(
                onClick = { /* Navigate to detail */ },
                label = {
                    Text(
                        text = "경기 상세 보기",
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = Color.White,
                    contentColor = watchTheme.primary
                )
            )
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption1,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(max) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < current) activeColor else Color.White.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@Composable
private fun BaseDiamond(
    bases: com.basehaptic.watch.data.BaseStatus,
    accentColor: Color = Yellow500
) {
    Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Diamond shape
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-4).dp)
        ) {
            // Second base
            BaseIndicator(bases.second, accentColor)
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Third base
                BaseIndicator(bases.third, accentColor)
                // Home (center)
                Box(modifier = Modifier.size(8.dp))
                // First base
                BaseIndicator(bases.first, accentColor)
            }
            
            // Home plate (bottom)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
        
        // Diamond outline
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun BaseIndicator(
    isOccupied: Boolean,
    accentColor: Color = Yellow500
) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(
                color = if (isOccupied) accentColor else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
    )
}

@Preview(device = "id:wearos_small_round")
@Composable
fun LiveGameScreenPreview() {
    BaseHapticWatchTheme(teamName = "SSG") {
        LiveGameScreen(gameData = getMockGameData())
    }
}
