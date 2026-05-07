package com.basehaptic.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlin.math.min

data class WatchUiProfile(
    val isRound: Boolean,
    val contentOffsetYDp: Int,
    val bannerHeightPercent: Float,
    val bannerBottomCornerDp: Int,
    val myTeamTopMarginDp: Int,
    val scoreCardHorizontalPaddingDp: Int,
    val scoreCardVerticalPaddingDp: Int,
    val scoreValueSp: Int,
    val teamNameSp: Int,
    val logoSizeDp: Int,
    val inningBoxHeightDp: Int,
    val inningBoxMinWidthDp: Int,
    val inningTextSp: Int,
    val inningHalfSp: Int,
    val baseBsoTopMarginDp: Int,
    val baseBsoSpacingDp: Int,
    val baseDiamondSizeDp: Int,
    val baseCellSizeDp: Int,
    val countDotSizeDp: Int,
    val countLabelWidthDp: Int,
    val countLabelSp: Int,
    val playerInfoOffsetYDp: Int,
    val playerInfoSp: Int,
    val tapHintBottomMarginDp: Int,
    val tapHintSp: Int,
    val noGameIconSp: Int,
    val noGamePrimarySp: Int,
    val noGameSecondarySp: Int,
    val promptOuterHorizontalPaddingDp: Int,
    val promptCardCornerDp: Int,
    val promptQuestionSp: Int
)

@Composable
fun rememberWatchUiProfile(): WatchUiProfile {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val shortSideDp = min(configuration.screenWidthDp, configuration.screenHeightDp)
    val isRound = context.resources.configuration.isScreenRound

    val base = when {
        shortSideDp <= 192 -> WatchUiProfile(
            isRound = isRound,
            contentOffsetYDp = -16,
            bannerHeightPercent = 0.34f,
            bannerBottomCornerDp = 30,
            myTeamTopMarginDp = 18,
            scoreCardHorizontalPaddingDp = 22,
            scoreCardVerticalPaddingDp = 6,
            scoreValueSp = 26,
            teamNameSp = 9,
            logoSizeDp = 11,
            inningBoxHeightDp = 30,
            inningBoxMinWidthDp = 28,
            inningTextSp = 9,
            inningHalfSp = 7,
            baseBsoTopMarginDp = 12,
            baseBsoSpacingDp = 16,
            baseDiamondSizeDp = 34,
            baseCellSizeDp = 16,
            countDotSizeDp = 6,
            countLabelWidthDp = 10,
            countLabelSp = 8,
            // 2줄 (P+투구수 / B) 로 늘었으므로 6dp 정도 아래로 더 밀어 위쪽 BSO 와 간격 유지
            playerInfoOffsetYDp = 26,
            playerInfoSp = 7,
            tapHintBottomMarginDp = 6,
            tapHintSp = 6,
            noGameIconSp = 30,
            noGamePrimarySp = 12,
            noGameSecondarySp = 10,
            promptOuterHorizontalPaddingDp = 12,
            promptCardCornerDp = 16,
            promptQuestionSp = 11
        )

        shortSideDp <= 225 -> WatchUiProfile(
            isRound = isRound,
            contentOffsetYDp = -24,
            bannerHeightPercent = 0.37f,
            bannerBottomCornerDp = 38,
            myTeamTopMarginDp = 24,
            scoreCardHorizontalPaddingDp = 30,
            scoreCardVerticalPaddingDp = 7,
            scoreValueSp = 30,
            teamNameSp = 10,
            logoSizeDp = 12,
            inningBoxHeightDp = 34,
            inningBoxMinWidthDp = 30,
            inningTextSp = 10,
            inningHalfSp = 7,
            baseBsoTopMarginDp = 16,
            baseBsoSpacingDp = 21,
            baseDiamondSizeDp = 40,
            baseCellSizeDp = 19,
            countDotSizeDp = 7,
            countLabelWidthDp = 11,
            countLabelSp = 8,
            // 2줄 (P+투구수 / B) 로 늘었으므로 6dp 정도 아래로 더 밀어 위쪽 BSO 와 간격 유지
            playerInfoOffsetYDp = 34,
            playerInfoSp = 8,
            tapHintBottomMarginDp = 7,
            tapHintSp = 7,
            noGameIconSp = 35,
            noGamePrimarySp = 13,
            noGameSecondarySp = 10,
            promptOuterHorizontalPaddingDp = 14,
            promptCardCornerDp = 18,
            promptQuestionSp = 12
        )

        else -> WatchUiProfile(
            isRound = isRound,
            contentOffsetYDp = -30,
            bannerHeightPercent = 0.40f,
            bannerBottomCornerDp = 44,
            myTeamTopMarginDp = 28,
            scoreCardHorizontalPaddingDp = 40,
            scoreCardVerticalPaddingDp = 8,
            scoreValueSp = 34,
            teamNameSp = 11,
            logoSizeDp = 14,
            inningBoxHeightDp = 38,
            inningBoxMinWidthDp = 32,
            inningTextSp = 11,
            inningHalfSp = 8,
            baseBsoTopMarginDp = 20,
            baseBsoSpacingDp = 25,
            baseDiamondSizeDp = 46,
            baseCellSizeDp = 22,
            countDotSizeDp = 8,
            countLabelWidthDp = 12,
            countLabelSp = 9,
            // 2줄 (P+투구수 / B) 로 늘었으므로 6dp 정도 아래로 더 밀어 위쪽 BSO 와 간격 유지
            playerInfoOffsetYDp = 41,
            playerInfoSp = 8,
            tapHintBottomMarginDp = 8,
            tapHintSp = 7,
            noGameIconSp = 40,
            noGamePrimarySp = 14,
            noGameSecondarySp = 11,
            promptOuterHorizontalPaddingDp = 16,
            promptCardCornerDp = 20,
            promptQuestionSp = 13
        )
    }

    return remember(shortSideDp, isRound) {
        if (!isRound) {
            base
        } else {
            base.copy(
                scoreCardHorizontalPaddingDp = (base.scoreCardHorizontalPaddingDp - 2).coerceAtLeast(18),
                promptOuterHorizontalPaddingDp = base.promptOuterHorizontalPaddingDp + 2
            )
        }
    }
}
