package com.basehaptic.mobile.venting.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Red400
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.ui.theme.Yellow400
import com.basehaptic.mobile.ui.theme.Yellow500
import com.basehaptic.mobile.venting.DestructionStage
import com.basehaptic.mobile.venting.VentingEventReporter
import com.basehaptic.mobile.venting.VentingRoomState

/**
 * 완파 화면 (iOS VentingDestroyedScreen 포팅).
 *
 * - "분풀이 완료" 상태와 재도전 버튼을 표시한다.
 * - Phase 1: 광고 게이트 없이 항상 재도전 허용 (AlwaysAllowGate 상당).
 */
@Composable
fun VentingDestroyedScreen(
    state: VentingRoomState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    entrySource: String = "unknown"
) {
    val reportContext = LocalContext.current
    val dollScale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        // 6.1 지표: 재도전 프롬프트(완파 화면) 노출 = retry_prompt_shown
        VentingEventReporter.report(
            context = reportContext,
            eventType = "retry_prompt_shown",
            team = state.gameContext.myTeamId,
            entrySource = entrySource,
            gameId = state.gameContext.gameId
        )
        dollScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        // 닫기 버튼 (우상단)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxl)
                .padding(top = AppSpacing.lg)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Gray800)
                    .clickable { onClose() }
                    .padding(AppSpacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Gray400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 완파 연출
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)
        ) {
            // 완파된 인형 (익명 펭귄 스프라이트) — 찢김 연출이 어두운 배경과 어울려 글로우 없음
            Image(
                painter = painterResource(id = DestructionStage.DESTROYED.dollDrawableRes),
                contentDescription = null,
                modifier = Modifier
                    .size(180.dp)
                    .scale(dollScale.value)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text(text = "분풀이 완료!", style = AppFont.h3Bold, color = Red400)
                Text(
                    text = "시원하게 털어냈습니다 🎉",
                    style = AppFont.bodyLgMedium,
                    color = Gray300
                )
            }

            // 대상 정보
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Text(
                    text = state.selectedTarget.roleLabel,
                    style = AppFont.captionBold,
                    color = Red400,
                    modifier = Modifier
                        .clip(AppShapes.pill)
                        .background(Red500.copy(alpha = 0.2f))
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                )
                Text(
                    text = state.selectedTarget.eventDescription,
                    style = AppFont.body,
                    color = Gray400
                )
            }

            // 첫 완파 배지
            if (state.isFirstDestructionRecorded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier
                        .clip(AppShapes.pill)
                        .background(Yellow500.copy(alpha = 0.15f))
                        .border(1.dp, Yellow500.copy(alpha = 0.4f), AppShapes.pill)
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Yellow500,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "오늘 처음 완파 달성!",
                        style = AppFont.captionBold,
                        color = Yellow400
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 재도전 버튼 영역 (Phase 1: 항상 허용)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxl)
                .padding(bottom = AppSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.md)
                    .background(Red500)
                    .clickable {
                        // 6.1 지표: 재도전(리워드 광고 진입 지점) = retry_ad_start
                        VentingEventReporter.report(
                            context = reportContext,
                            eventType = "retry_ad_start",
                            team = state.gameContext.myTeamId,
                            entrySource = entrySource,
                            gameId = state.gameContext.gameId
                        )
                        onRetry()
                    }
                    .padding(vertical = AppSpacing.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.sm))
                Text(text = "재도전하기", style = AppFont.bodyLgMedium, color = Color.White)
            }

            Text(
                text = "홈으로 돌아가기",
                style = AppFont.bodyMedium,
                color = Gray400,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.md)
                    .clickable { onClose() }
                    .padding(vertical = AppSpacing.md)
            )
        }
    }
}
