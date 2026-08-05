package com.basehaptic.watch.venting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * 워치 분풀이 룸 (lite).
 *
 * - 화면 전체가 탭 영역 (인형만 히트박스로 하면 손가락에 가려 답답함).
 * - 베젤/크라운 회전도 히트로 환산 — 드르륵 감아서 게이지를 올릴 수 있다.
 * - 게이지는 화면 테두리 원형 링 (워치 네이티브 문법, 손가락에 안 가림).
 * - 도구 트레이·대상 선택 없음: 폰 테스트 도구가 보낸 대상 1개 고정.
 */
@Composable
fun WatchVentingScreen(
    request: WatchVentingRequest,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val state = remember(request) { WatchVentingState(context, request.gameId) }
    val focusRequester = remember { FocusRequester() }
    var rotaryAccumPx by remember { mutableFloatStateOf(0f) }
    val dollScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun hit(count: Int) {
        if (state.isDestroyed) return
        state.recordHits(count)
        scope.launch {
            dollScale.snapTo(0.9f)
            dollScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                if (!state.isDestroyed) {
                    rotaryAccumPx += abs(event.verticalScrollPixels)
                    val hits = (rotaryAccumPx / WatchVentingConstants.ROTARY_PX_PER_HIT).toInt()
                    if (hits > 0) {
                        rotaryAccumPx -= hits * WatchVentingConstants.ROTARY_PX_PER_HIT
                        hit(hits)
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(state.isDestroyed) {
                detectTapGestures { if (!state.isDestroyed) hit(1) }
            }
    ) {
        // 테두리 게이지 링
        val ringColor = when (state.stage) {
            WatchDestructionStage.IDLE -> Color(0xFF9CA3AF)
            WatchDestructionStage.CRACKED -> Color(0xFFFACC15)
            WatchDestructionStage.BURST -> Color(0xFFF97316)
            WatchDestructionStage.DESTROYED -> Color(0xFFEF4444)
        }
        val gaugeSweep = (state.gauge * 360f).toFloat()
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            drawArc(
                color = Color(0xFF27272A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = gaugeSweep,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = request.targetLabel,
                color = Color(0xFFF87171),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Box(contentAlignment = Alignment.Center) {
                // 채움광 — 인형(104dp)의 56% 크기·가슴 위치로 실루엣 뒤에 거의 숨긴다.
                // 투명 유니폼 몸판이 흰옷으로 읽히게 하는 목적 (조명 연출 아님)
                Box(
                    modifier = Modifier
                        .offset(y = 11.dp)
                        .size(58.dp)
                        .background(
                            Brush.radialGradient(
                                0.0f to Color(0xFFFAF6EE),
                                0.55f to Color(0xF2FAF6EE),
                                1.0f to Color(0x00FAF6EE)
                            ),
                            CircleShape
                        )
                )
                Image(
                    painter = painterResource(id = state.stage.dollRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(104.dp)
                        .scale(dollScale.value)
                )
            }
            val stageText = when (state.stage) {
                WatchDestructionStage.IDLE -> "탭·베젤로 때리세요!"
                WatchDestructionStage.CRACKED -> "💢 균열!"
                WatchDestructionStage.BURST -> "🔥 터지기 직전!"
                WatchDestructionStage.DESTROYED -> "💥 완파!"
            }
            Text(
                text = stageText,
                color = if (state.stage == WatchDestructionStage.DESTROYED) Color(0xFFF87171) else Color(0xFF9CA3AF),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        if (state.isDestroyed) {
            // 완파 후: 닫기/다시 버튼 (룸 탭 입력은 isDestroyed 가드로 정지 상태)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF991B1B))
                    .clickable { onClose() }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(text = "닫기", color = Color.White, fontSize = 13.sp)
            }
        } else {
            // 진행 중 닫기: 좌측 상단 미니 ✕
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF27272A))
                    .clickable { onClose() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(text = "✕", color = Color(0xFF9CA3AF), fontSize = 11.sp)
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
