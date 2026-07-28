package com.basehaptic.mobile.venting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Red400
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.venting.VentingGameContext

/**
 * 분풀이 모드 홈카드: "오늘의 아쉬운 순간" (iOS VentingHomeCard 포팅).
 * 오픈 조건(DEBUG + 토글 + 마이팀 패배 당일) 충족 시에만 렌더링된다.
 */
@Composable
fun VentingHomeCard(
    context: VentingGameContext,
    onEnterVenting: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl, vertical = 6.dp)
            .clip(AppShapes.lg)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Red500.copy(alpha = 0.18f),
                        Red500.copy(alpha = 0.08f)
                    )
                )
            )
            .border(1.dp, Red500.copy(alpha = 0.35f), AppShapes.lg)
            .padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "💢", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Column {
                Text(
                    text = "오늘의 아쉬운 순간",
                    style = AppFont.captionBold,
                    color = Red400
                )
                Text(
                    text = "분풀이 한번 해볼까요?",
                    style = AppFont.micro,
                    color = Gray400
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // 패배 스코어 배지
            Text(
                text = "${context.myScore} : ${context.opponentScore}",
                style = AppFont.h5Bold,
                color = Red400,
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(Red500.copy(alpha = 0.15f))
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
            )
        }

        // 아쉬운 순간 미리보기 (최대 2개)
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            context.candidates.take(2).forEach { candidate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Red500.copy(alpha = 0.4f))
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Text(
                        text = "${candidate.roleLabel} · ${candidate.eventDescription}",
                        style = AppFont.micro,
                        color = Gray300,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (context.candidates.size > 2) {
                Text(
                    text = "외 ${context.candidates.size - 2}건 더...",
                    style = AppFont.micro,
                    color = Gray500
                )
            }
        }

        // 진입 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.md)
                .background(Red500)
                .clickable { onEnterVenting() }
                .padding(vertical = AppSpacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "분풀이 하러 가기",
                style = AppFont.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
