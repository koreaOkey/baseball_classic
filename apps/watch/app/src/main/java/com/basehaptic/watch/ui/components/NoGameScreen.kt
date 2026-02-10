package com.basehaptic.watch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.basehaptic.watch.ui.theme.BaseHapticWatchTheme
import com.basehaptic.watch.ui.theme.Gray400

@Composable
fun NoGameScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚾",
            fontSize = 40.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "진행 중인 경기가\n없습니다",
            style = MaterialTheme.typography.body1,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "휴대폰 앱에서\n경기를 선택해주세요",
            style = MaterialTheme.typography.caption1,
            color = Gray400,
            textAlign = TextAlign.Center,
            fontSize = 11.sp
        )
    }
}

@Preview(device = "id:wearos_small_round")
@Composable
fun NoGameScreenPreview() {
    BaseHapticWatchTheme {
        NoGameScreen()
    }
}

