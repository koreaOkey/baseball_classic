package com.basehaptic.watch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.wear.compose.material.Text
import com.basehaptic.watch.data.BaseStatus
import com.basehaptic.watch.data.GameData
import com.basehaptic.watch.data.getMockGameData
import com.basehaptic.watch.ui.theme.BaseHapticWatchTheme
import com.basehaptic.watch.ui.theme.Gray800
import com.basehaptic.watch.ui.theme.Gray900
import com.basehaptic.watch.ui.theme.Gray950
import com.basehaptic.watch.ui.theme.Green500
import com.basehaptic.watch.ui.theme.Orange500
import com.basehaptic.watch.ui.theme.Red500
import com.basehaptic.watch.ui.theme.WatchAppShapes
import com.basehaptic.watch.ui.theme.WatchAppSpacing
import com.basehaptic.watch.ui.theme.WatchUiProfile
import com.basehaptic.watch.ui.theme.Yellow400
import com.basehaptic.watch.ui.theme.rememberWatchUiProfile

@Composable
fun LiveGameScreen(
    gameData: GameData,
    modifier: Modifier = Modifier
) {
    val uiProfile = rememberWatchUiProfile()
    val globalContentShiftDownDp = -16
    val isGameFinished = gameData.inning.contains("경기 종료") ||
        gameData.inning.contains("finished", ignoreCase = true)

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .offset(y = (uiProfile.contentOffsetYDp + globalContentShiftDownDp).dp)
            .background(Gray950)
    ) {
        val (topBanner, mainScoreCard, baseBso, playerInfo, tapHint) = createRefs()
        val scoreDiffRef = createRef()

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
                .background(Gray950)
        )

        Row(
            modifier = Modifier
                .constrainAs(mainScoreCard) {
                    top.linkTo(topBanner.bottom, margin = WatchAppSpacing.xs)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
                .offset(y = (-4).dp)
                // Reason: 메인 스코어 카드 전용 18dp 둥근 모서리 (md(12)와 lg(14) 사이)
                .clip(RoundedCornerShape(18.dp))
                .background(Gray950)
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
                uiProfile = uiProfile
            )

            Column(
                modifier = Modifier
                    // Reason: 이닝 박스 좌우 여백 6dp (xs 4와 sm 8 사이 미세 조정)
                    .padding(horizontal = 6.dp)
                    .size(
                        width = uiProfile.inningBoxMinWidthDp.dp,
                        height = uiProfile.inningBoxHeightDp.dp
                    )
                    .clip(WatchAppShapes.md10)
                    .background(Color.White.copy(alpha = 0.05f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isGameFinished) {
                    Text(
                        text = gameData.inning,
                        modifier = Modifier.fillMaxWidth(),
                        color = Orange500,
                        fontSize = uiProfile.inningTextSp.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                } else {
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
            }

            ScoreSide(
                modifier = Modifier.weight(1f),
                team = gameData.homeTeam,
                score = gameData.homeScore,
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
                .offset(y = (uiProfile.playerInfoOffsetYDp - globalContentShiftDownDp).dp),
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
    uiProfile: WatchUiProfile
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = score.toString(),
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            fontSize = uiProfile.scoreValueSp.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = team.uppercase(),
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.76f),
            fontSize = uiProfile.teamNameSp.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
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
            .clip(WatchAppShapes.xxs)
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
