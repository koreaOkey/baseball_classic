package com.basehaptic.watch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.basehaptic.watch.R
import com.basehaptic.watch.data.BaseStatus
import com.basehaptic.watch.data.GameData
import com.basehaptic.watch.data.getMockGameData
import com.basehaptic.watch.ui.theme.BaseHapticWatchTheme
import com.basehaptic.watch.ui.theme.Gray800
import com.basehaptic.watch.ui.theme.Gray900
import com.basehaptic.watch.ui.theme.Gray950
import com.basehaptic.watch.ui.theme.Green500
import com.basehaptic.watch.ui.theme.LocalWatchTeamTheme
import com.basehaptic.watch.ui.theme.Orange500
import com.basehaptic.watch.ui.theme.Red500
import com.basehaptic.watch.ui.theme.WatchUiProfile
import com.basehaptic.watch.ui.theme.Yellow400
import com.basehaptic.watch.ui.theme.rememberWatchUiProfile

@Composable
fun LiveGameScreen(
    gameData: GameData,
    modifier: Modifier = Modifier
) {
    val watchTheme = LocalWatchTeamTheme.current
    val uiProfile = rememberWatchUiProfile()

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .offset(y = uiProfile.contentOffsetYDp.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Gray900, Gray950)
                )
            )
    ) {
        val (topBanner, myTeam, mainScoreCard, baseBso, playerInfo, tapHint) = createRefs()
        val scoreDiffRef = createRef()
        val myTeamDisplay = gameData.myTeamName.ifBlank { gameData.homeTeam }

        Box(
            modifier = Modifier
                .constrainAs(topBanner) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    height = Dimension.percent(uiProfile.bannerHeightPercent)
                    width = Dimension.fillToConstraints
                }
                .clip(
                    RoundedCornerShape(
                        bottomStart = uiProfile.bannerBottomCornerDp.dp,
                        bottomEnd = uiProfile.bannerBottomCornerDp.dp
                    )
                )
                .background(watchTheme.primary)
        )

        Column(
            modifier = Modifier.constrainAs(myTeam) {
                top.linkTo(parent.top, margin = uiProfile.myTeamTopMarginDp.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MY TEAM",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = (uiProfile.teamNameSp - 2).sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = myTeamDisplay,
                    color = Color.White,
                    fontSize = (uiProfile.teamNameSp + 1).sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "LIVE",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = (uiProfile.teamNameSp - 2).sp
                )
            }
        }

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
                .padding(
                    horizontal = uiProfile.scoreCardHorizontalPaddingDp.dp,
                    vertical = uiProfile.scoreCardVerticalPaddingDp.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreSide(
                modifier = Modifier.weight(1f),
                team = gameData.awayTeam,
                score = gameData.awayScore,
                alignEnd = false,
                uiProfile = uiProfile
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(
                        width = uiProfile.inningBoxMinWidthDp.dp,
                        height = uiProfile.inningBoxHeightDp.dp
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = gameData.inning,
                    color = Orange500,
                    fontSize = uiProfile.inningTextSp.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = inningHalfIcon(gameData.inning),
                    color = Orange500,
                    fontSize = uiProfile.inningHalfSp.sp
                )
            }

            ScoreSide(
                modifier = Modifier.weight(1f),
                team = gameData.homeTeam,
                score = gameData.homeScore,
                alignEnd = true,
                uiProfile = uiProfile
            )
        }

        Row(
            modifier = Modifier
                .constrainAs(baseBso) {
                    top.linkTo(mainScoreCard.bottom, margin = uiProfile.baseBsoTopMarginDp.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            horizontalArrangement = Arrangement.spacedBy(uiProfile.baseBsoSpacingDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BaseDiamond(
                bases = gameData.bases,
                uiProfile = uiProfile
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CountIndicator(
                    label = "B",
                    current = gameData.ballCount,
                    max = 3,
                    activeColor = Green500,
                    inactiveColor = Green500.copy(alpha = 0.2f),
                    uiProfile = uiProfile
                )
                CountIndicator(
                    label = "S",
                    current = gameData.strikeCount,
                    max = 2,
                    activeColor = Orange500,
                    inactiveColor = Orange500.copy(alpha = 0.2f),
                    uiProfile = uiProfile
                )
                CountIndicator(
                    label = "O",
                    current = gameData.outCount,
                    max = 2,
                    activeColor = Red500,
                    inactiveColor = Red500.copy(alpha = 0.2f),
                    uiProfile = uiProfile
                )
            }
        }

        Text(
            text = "P ${gameData.pitcher}  B ${gameData.batter}",
            modifier = Modifier
                .constrainAs(playerInfo) {
                    bottom.linkTo(tapHint.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .offset(y = uiProfile.playerInfoOffsetYDp.dp),
            color = Color.White.copy(alpha = 0.62f),
            fontSize = uiProfile.playerInfoSp.sp,
            maxLines = 1
        )

        Text(
            text = "TAP FOR DETAILS",
            modifier = Modifier.constrainAs(tapHint) {
                bottom.linkTo(parent.bottom, margin = uiProfile.tapHintBottomMarginDp.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            color = Color.Transparent,
            fontSize = uiProfile.tapHintSp.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )

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
    alignEnd: Boolean,
    uiProfile: WatchUiProfile
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = score.toString(),
            modifier = Modifier.offset(x = 2.dp),
            color = Color.White,
            fontSize = uiProfile.scoreValueSp.sp,
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
                    modifier = Modifier.size(uiProfile.logoSizeDp.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = team.uppercase(),
                color = Color.White.copy(alpha = 0.76f),
                fontSize = uiProfile.teamNameSp.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
            )
            if (!alignEnd && logoRes != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    painter = painterResource(id = logoRes),
                    contentDescription = "$team logo",
                    modifier = Modifier.size(uiProfile.logoSizeDp.dp),
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
    inactiveColor: Color,
    uiProfile: WatchUiProfile
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(uiProfile.countLabelWidthDp.dp),
            color = Color.White.copy(alpha = 0.35f),
            fontSize = uiProfile.countLabelSp.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(max) { index ->
                Box(
                    modifier = Modifier
                        .size(uiProfile.countDotSizeDp.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (index < current) activeColor else inactiveColor)
                )
            }
        }
    }
}

@Composable
private fun BaseDiamond(
    bases: BaseStatus,
    accentColor: Color = Yellow400,
    uiProfile: WatchUiProfile
) {
    Box(
        modifier = Modifier
            .size(uiProfile.baseDiamondSizeDp.dp)
            .graphicsLayer(rotationZ = 45f),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BaseCell(isOccupied = bases.second, accentColor = accentColor, uiProfile = uiProfile)
                BaseCell(isOccupied = bases.first, accentColor = accentColor, uiProfile = uiProfile)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BaseCell(isOccupied = bases.third, accentColor = accentColor, uiProfile = uiProfile)
                BaseCell(
                    isOccupied = false,
                    accentColor = Color.White.copy(alpha = 0.18f),
                    isHome = true,
                    uiProfile = uiProfile
                )
            }
        }
    }
}

@Composable
private fun BaseCell(
    isOccupied: Boolean,
    accentColor: Color,
    isHome: Boolean = false,
    uiProfile: WatchUiProfile
) {
    Box(
        modifier = Modifier
            .size(uiProfile.baseCellSizeDp.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (isOccupied) accentColor else if (isHome) Color.White.copy(alpha = 0.08f) else Gray800,
                RectangleShape
            )
    )
}

private fun inningHalfIcon(inning: String): String {
    return when {
        inning.contains("TOP", ignoreCase = true) || inning.contains("초") -> "▲"
        inning.contains("BOT", ignoreCase = true) || inning.contains("말") -> "▼"
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

@Preview(name = "Small Round", device = "id:wearos_small_round")
@Composable
fun LiveGameScreenPreviewSmallRound() {
    BaseHapticWatchTheme(teamName = "SSG") {
        LiveGameScreen(gameData = getMockGameData())
    }
}

@Preview(name = "Large Round", device = "id:wearos_large_round")
@Composable
fun LiveGameScreenPreviewLargeRound() {
    BaseHapticWatchTheme(teamName = "SSG") {
        LiveGameScreen(gameData = getMockGameData())
    }
}

@Preview(name = "Square", device = "id:wearos_square")
@Composable
fun LiveGameScreenPreviewSquare() {
    BaseHapticWatchTheme(teamName = "SSG") {
        LiveGameScreen(gameData = getMockGameData())
    }
}
