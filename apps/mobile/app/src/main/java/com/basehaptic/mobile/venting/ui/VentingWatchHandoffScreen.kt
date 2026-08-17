package com.basehaptic.mobile.venting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Red400
import com.basehaptic.mobile.ui.theme.Red500

/**
 * 워치로 분풀이를 시작했을 때 표시하는 안내 화면 (결정 ⓑ).
 * 폰은 룸으로 진입하지 않고, 손목의 워치 앱에서 룸이 열렸음을 안내한다.
 * (iOS VentingWatchHandoffScreen과 동일 역할.)
 */
@Composable
fun VentingWatchHandoffScreen(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
            .padding(horizontal = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Watch,
            contentDescription = null,
            tint = Red400,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(AppSpacing.xl))
        Text(
            text = "워치에서 분풀이를 시작하세요",
            style = AppFont.h5Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Text(
            text = "손목의 야구봄 워치 앱에서\n분풀이 룸이 열렸어요. 마음껏 풀어보세요!",
            style = AppFont.body,
            color = Gray400,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        Text(
            text = "닫기",
            style = AppFont.bodyLgMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.md)
                .background(Red500)
                .clickable { onClose() }
                .padding(vertical = AppSpacing.lg)
        )
    }
}
