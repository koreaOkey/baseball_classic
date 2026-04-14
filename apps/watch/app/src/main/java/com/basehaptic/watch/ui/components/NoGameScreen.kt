package com.basehaptic.watch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.basehaptic.watch.ui.theme.BaseHapticWatchTheme
import com.basehaptic.watch.ui.theme.Gray400
import com.basehaptic.watch.ui.theme.WatchAppSpacing
import com.basehaptic.watch.ui.theme.rememberWatchUiProfile

@Composable
fun NoGameScreen() {
    val uiProfile = rememberWatchUiProfile()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (uiProfile.isRound) WatchAppSpacing.lg else WatchAppSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Reason: 폰트 크기는 WatchUiProfile에서 디바이스별로 반응형으로 관리
        Text(
            text = "⚾",
            fontSize = uiProfile.noGameIconSp.sp
        )

        Spacer(modifier = Modifier.height(WatchAppSpacing.sm))

        // Reason: 폰트 크기는 WatchUiProfile 기반
        Text(
            text = "진행 중인 경기가 없습니다",
            style = MaterialTheme.typography.body1,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = uiProfile.noGamePrimarySp.sp
        )

        Spacer(modifier = Modifier.height(WatchAppSpacing.xs))

        // Reason: 폰트 크기는 WatchUiProfile 기반
        Text(
            text = "모바일 앱에서 경기를 선택해주세요",
            style = MaterialTheme.typography.caption1,
            color = Gray400,
            textAlign = TextAlign.Center,
            fontSize = uiProfile.noGameSecondarySp.sp
        )
    }
}

@Preview(name = "Small Round", device = "id:wearos_small_round")
@Composable
fun NoGameScreenPreviewSmallRound() {
    BaseHapticWatchTheme {
        NoGameScreen()
    }
}

@Preview(name = "Large Round", device = "id:wearos_large_round")
@Composable
fun NoGameScreenPreviewLargeRound() {
    BaseHapticWatchTheme {
        NoGameScreen()
    }
}

@Preview(name = "Square", device = "id:wearos_square")
@Composable
fun NoGameScreenPreviewSquare() {
    BaseHapticWatchTheme {
        NoGameScreen()
    }
}
