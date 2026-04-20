package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.Team

import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray950

@Suppress("UNUSED_PARAMETER")
@Composable
fun CommunityScreen(
    selectedTeam: Team
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.xxl),
            shape = AppShapes.lg,
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.xxxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💬",
                    // Reason: 대형 placeholder 이모지 (48sp, display 56보다 조금 작게)
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = AppSpacing.lg)
                )
                Text(
                    text = "커뮤니티",
                    style = AppFont.h3Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = AppSpacing.sm)
                )
                Text(
                    text = "실시간 응원 채팅과 친구 응원 공유 기능은 곧 추가됩니다.",
                    style = AppFont.body,
                    color = Gray400,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
