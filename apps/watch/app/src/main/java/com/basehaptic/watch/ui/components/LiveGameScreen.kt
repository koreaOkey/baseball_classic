package com.basehaptic.watch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.wear.compose.material.*
import com.basehaptic.watch.R
import com.basehaptic.watch.data.GameData
import com.basehaptic.watch.data.getMockGameData
import com.basehaptic.watch.ui.theme.*

@Composable
fun LiveGameScreen(
    gameData: GameData,
    modifier: Modifier = Modifier
) {
    val watchTheme = LocalWatchTeamTheme.current

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Gray900,
                        Gray950
                    )
                )
            )
    ) {
        val (topBanner, myTeam, mainScoreCard, baseBso, playerInfo, tapHint) = createRefs()
        val scoreDiffRef = createRef()
        val myTeamDisplay = gameData.myTeamName.ifBlank { gameData.homeTeam }

        // Top banner
        Box(
            modifier = Modifier
                .constrainAs(topBanner) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    height = Dimension.percent(0.40f)
                    width = Dimension.fillToConstraints
                }
                .clip(RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp))
                .background(watchTheme.primary)
        )

        Column(
            modifier = Modifier.constrainAs(myTeam) {
                top.linkTo(parent.top, margin = 28.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "내 팀",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 8.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = myTeamDisplay,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "▼",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 8.sp
                )
            }
        }


        // Main score card
        Row(
            modifier = Modifier
                .constrainAs(mainScoreCard) {
                    top.linkTo(myTeam.bottom, margin = 4.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 40.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreSide(
                modifier = Modifier
                    .weight(1f),
                team = gameData.awayTeam,
                score = gameData.awayScore,
                alignEnd = false
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .height(38.dp)
                    .widthIn(min = 32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = gameData.inning,
                    color = Orange500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = inningHalfIcon(gameData.inning),
                    color = Orange500,
                    fontSize = 8.sp
                )
            }
            ScoreSide(
                modifier = Modifier
                    .weight(1f),
                team = gameData.homeTeam,
                score = gameData.homeScore,
                alignEnd = true
            )
        }

        // Base + BSO
        Row(
            modifier = Modifier
                .constrainAs(baseBso) {
                    top.linkTo(mainScoreCard.bottom, margin = 20.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            horizontalArrangement = Arrangement.spacedBy(25.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BaseDiamond(gameData.bases)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CountIndicator("B", gameData.ballCount, 3, Green500, Green500.copy(alpha = 0.2f))
                CountIndicator("S", gameData.strikeCount, 2, Orange500, Orange500.copy(alpha = 0.2f))
                CountIndicator("O", gameData.outCount, 2, Red500, Red500.copy(alpha = 0.2f))
            }
        }

        // Player info
        Text(
            text = "P ${gameData.pitcher} • B ${gameData.batter}",
            modifier = Modifier
                .constrainAs(playerInfo) {
                    bottom.linkTo(tapHint.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 8.sp,
            maxLines = 1
        )

        // Tap hint
        Text(
            text = "TAP FOR DETAILS",
            modifier = Modifier
                .constrainAs(tapHint) {
                    bottom.linkTo(parent.bottom, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )

        // scoreDiffRef placeholder (unused)
        Box(modifier = Modifier.constrainAs(scoreDiffRef) {
            top.linkTo(parent.top)
            start.linkTo(parent.start)
        })
    }
}

@Composable
private fun ScoreSide(
    modifier: Modifier = Modifier,
    team: String,
    score: Int,
    alignEnd: Boolean
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = score.toString(),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
        ) {
            val logoRes = getTeamLogoRes(team)
            if (alignEnd && logoRes != null) {
                Icon(
                    painter = painterResource(id = logoRes),
                    contentDescription = "$team logo",
                    modifier = Modifier.size(14.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = team.uppercase(),
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
            )
            if (!alignEnd && logoRes != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    painter = painterResource(id = logoRes),
                    contentDescription = "$team logo",
                    modifier = Modifier.size(14.dp),
                    tint = Color.Unspecified
                )
            }
        }
    }
}

@Composable
private fun CountIndicator(
    label: String,
    current: Int,
    max: Int,
    activeColor: Color,
    inactiveColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(12.dp),
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(max) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index < current) activeColor else inactiveColor
                        )
                )
            }
        }
    }
}

@Composable
private fun BaseDiamond(
    bases: com.basehaptic.watch.data.BaseStatus,
    accentColor: Color = Yellow400
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer(rotationZ = 45f),
        contentAlignment = Alignment.Center
    ) {
        // 회전 후: 왼쪽 위→12시(2루), 오른쪽 위→3시(1루), 왼쪽 아래→9시(3루), 오른쪽 아래→6시(홈)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BaseCell(isOccupied = bases.second, accentColor = accentColor)
                BaseCell(isOccupied = bases.first, accentColor = accentColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BaseCell(isOccupied = bases.third, accentColor = accentColor)
                BaseCell(
                    isOccupied = false,
                    accentColor = Color.White.copy(alpha = 0.18f),
                    isHome = true
                )
            }
        }
    }
}

@Composable
private fun BaseCell(
    isOccupied: Boolean,
    accentColor: Color,
    isHome: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (isOccupied) accentColor else if (isHome) Color.White.copy(alpha = 0.08f) else Gray800,
                RectangleShape
            )
    )
}

private fun inningHalfLabel(inning: String): String {
    return when {
        inning.contains("초") -> "초"
        inning.contains("말") -> "말"
        else -> "-"
    }
}

private fun inningHalfIcon(inning: String): String {
    return when {
        inning.contains("초") -> "◀"
        inning.contains("말") -> "▶"
        else -> ""
    }
}

private fun getTeamLogoRes(team: String): Int? = when (team.uppercase()) {
    "DOOSAN" -> R.drawable.dosan
    "LG" -> R.drawable.lg
    "KIWOOM" -> R.drawable.kiwoom
    "SAMSUNG" -> R.drawable.samsung
    "LOTTE" -> R.drawable.lotte
    "SSG" -> R.drawable.ssg
    "KT" -> R.drawable.kt
    "HANWHA" -> R.drawable.hanwha
    "KIA" -> R.drawable.kia
    "NC" -> R.drawable.nc
    else -> null
}

@Preview(device = "id:wearos_small_round")
@Composable
fun LiveGameScreenPreview() {
    BaseHapticWatchTheme(teamName = "SSG") {
        LiveGameScreen(gameData = getMockGameData())
    }
}
