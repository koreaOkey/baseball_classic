package com.basehaptic.mobile.venting.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Blue400
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray600
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Orange500
import com.basehaptic.mobile.ui.theme.Red400
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.ui.theme.Yellow400
import com.basehaptic.mobile.venting.DestructionConstants
import com.basehaptic.mobile.venting.DestructionStage
import com.basehaptic.mobile.venting.VentingEventReporter
import com.basehaptic.mobile.venting.VentingRoomState
import com.basehaptic.mobile.venting.VentingShakeDetector
import com.basehaptic.mobile.venting.VentingTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 제자리 회전 내려치기 모션 상수 (iOS StrikeMotion 포팅):
 * 도구가 인형 우상단 고정 위치에서 손잡이 끝(좌하단)을 축으로
 * 뒤로 젖혀졌다가 회전하며 내려친다. 위치 이동은 없다.
 */
private object StrikeMotion {
    const val WINDUP_ANGLE = -60f
    const val IMPACT_ANGLE = 8f
    const val REBOUND_ANGLE = -14f
    /** 도구 스프라이트 전체 방향 보정: 왼쪽(반시계) 90도 */
    const val ORIENTATION_ADJUST = -90f
    val toolOffsetX = (-40).dp
    val toolOffsetY = (-120).dp
    const val SWING_MS = 80
    const val HIT_STOP_MS = 70
    const val RECOVER_MS = 120
    const val HIT_EFFECT_LINGER_MS = 220
}

/**
 * 인형 뒤 글로우 — 스프라이트의 투명 유니폼 몸판이 흰옷으로 읽히게 하는 채움용 광원.
 *
 * 디자이너 스프라이트는 흰 배경 기준으로 제작되어 유니폼 몸판 일부가 투명 구멍인데,
 * 어두운 룸에서 그대로 쓰면 "검은 옷"으로 보인다. 인형(가슴 위치)보다 작게 깔아
 * 실루엣 뒤에 거의 숨긴다 — 조명 "연출"이 아니라 구멍 메움이 목적 (2026-08-05 톤 다운).
 */
@Composable
internal fun VentingDollSpotlight(
    diameter: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp
) {
    Box(
        modifier = Modifier
            .offset(y = offsetY)
            .size(diameter)
            .background(
                Brush.radialGradient(
                    0.0f to Color(0xFFFAF6EE),
                    0.55f to Color(0xF2FAF6EE),
                    1.0f to Color(0x00FAF6EE)
                ),
                androidx.compose.foundation.shape.CircleShape
            )
    )
}

/**
 * 분풀이 룸 화면 (iOS VentingRoomScreen 포팅).
 *
 * - 펭귄 인형 스프라이트 + 데미지 게이지 + 하단 도구 트레이.
 * - 인형에 선수 이름·등번호·실제 외형 표기 없음.
 * - 인형을 직접 탭하면 선택한 도구가 내려치는 연출 + 히트 이펙트 재생.
 * - 탭 경진동·단계 전환 중진동·완파 성공 진동 — VentingRoomState가 구동.
 */
@Composable
fun VentingRoomScreen(
    state: VentingRoomState,
    onBack: () -> Unit,
    onDestroyed: () -> Unit,
    entrySource: String = "unknown"
) {
    val reportContext = LocalContext.current
    var selectedTool by remember { mutableStateOf(VentingTool.HAMMER) }

    // 타격 연출 상태 (와인드업 → 회전 스윙 → 히트스톱 임팩트 → 복원)
    val strikeAngle = remember { Animatable(StrikeMotion.WINDUP_ANGLE) }
    val strikeAlpha = remember { Animatable(0f) }
    val dollSquash = remember { Animatable(1f) }
    val dollPushDown = remember { Animatable(0f) }
    val shakeOffset = remember { Animatable(0f) }
    var showHitEffect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var strikeJob by remember { mutableStateOf<Job?>(null) }
    var shakeJob by remember { mutableStateOf<Job?>(null) }
    var hitEffectJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(state.isDestroyed) {
        if (state.isDestroyed) {
            // 6.1 지표: 완파 순간 = destroy_complete
            VentingEventReporter.report(
                context = reportContext,
                eventType = "destroy_complete",
                team = state.gameContext.myTeamId,
                entrySource = entrySource,
                gameId = state.gameContext.gameId
            )
            delay(600)
            onDestroyed()
        }
    }

    // 인형 흔들림 연출 (탭 타격·흔들기 공용)
    fun wobbleDoll() {
        shakeJob?.cancel()
        shakeJob = scope.launch {
            val distance = when (state.stage) {
                DestructionStage.IDLE -> 4f
                DestructionStage.CRACKED -> 7f
                else -> 10f
            } * (if (state.gauge > 0.5) -1f else 1f)
            shakeOffset.animateTo(distance, tween(50))
            shakeOffset.animateTo(-distance, tween(50))
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    // 흔들기 연타: 룸이 보이는 동안만 센서 점유. 버스트마다 세기 비례 데미지 + 인형 흔들림.
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(state) {
        val detector = VentingShakeDetector(appContext) { hits ->
            if (!state.isDestroyed) {
                state.recordShake(hits)
                wobbleDoll()
            }
        }
        detector.start()
        onDispose { detector.stop() }
    }

    fun strike() {
        if (state.isDestroyed) return
        state.recordTap()

        // 인형 흔들림 (iOS VentingRoomViewModel.triggerShake)
        wobbleDoll()

        // 타격 연출 (연타 시에도 매번 와인드업부터 다시 스윙)
        strikeJob?.cancel()
        hitEffectJob?.cancel()
        strikeJob = scope.launch {
            // 1) 와인드업 포즈로 즉시 리셋
            strikeAngle.snapTo(StrikeMotion.WINDUP_ANGLE)
            strikeAlpha.snapTo(1f)
            dollSquash.snapTo(1f)
            dollPushDown.snapTo(0f)
            showHitEffect = false

            // 2) 스윙: 가속 회전으로 내려침
            strikeAngle.animateTo(
                StrikeMotion.IMPACT_ANGLE,
                tween(StrikeMotion.SWING_MS, easing = FastOutLinearInEasing)
            )

            // 3) 임팩트: 인형은 눌리고 이펙트 발동, 도구는 반작용으로 살짝 튕겨 오름
            showHitEffect = true
            launch { dollSquash.animateTo(0.85f, tween(50)) }
            launch { dollPushDown.animateTo(8f, tween(50)) }
            launch {
                strikeAngle.animateTo(
                    StrikeMotion.IMPACT_ANGLE + StrikeMotion.REBOUND_ANGLE,
                    spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                )
            }
            delay(StrikeMotion.HIT_STOP_MS.toLong())

            // 4) 히트스톱 종료 후 복원·퇴장 (히트 이펙트는 남겨둔다)
            launch {
                dollSquash.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
            }
            launch {
                dollPushDown.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
            }
            strikeAlpha.animateTo(0f, tween(StrikeMotion.RECOVER_MS))
        }

        // 5) 히트 이펙트는 잠시 머문 뒤 사라짐
        hitEffectJob = scope.launch {
            delay((StrikeMotion.SWING_MS + StrikeMotion.HIT_STOP_MS + StrikeMotion.HIT_EFFECT_LINGER_MS).toLong())
            showHitEffect = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(stageBackground(state.stage))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 헤더
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { onBack() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(text = "선택", style = AppFont.bodyMedium, color = Color.White)
                }
                Text(
                    text = "💢 빠따존",
                    style = AppFont.h5Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 대상 표시 (역할+사건 문구, 이름/등번호 없음)
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                    style = AppFont.bodyMedium,
                    color = Gray300,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = AppSpacing.xxl)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            // 인형 (직접 탭 = 타격)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .offset { IntOffset(shakeOffset.value.dp.roundToPx(), 0) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { strike() },
                contentAlignment = Alignment.Center
            ) {
                val destroyed = state.stage == DestructionStage.DESTROYED
                Box(contentAlignment = Alignment.Center) {
                    // 인형(220dp)의 56% 크기·가슴 위치 — 실루엣 뒤에 거의 숨는 채움광
                    VentingDollSpotlight(diameter = 124.dp, offsetY = 24.dp)
                    Image(
                        painter = painterResource(id = state.stage.dollDrawableRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(220.dp)
                            // 임팩트: 세로로 눌리고 가로로 살짝 퍼지는 만화식 스쿼시 (바닥 기준)
                            .graphicsLayer {
                                scaleX = (1f + (1f - dollSquash.value) * 0.6f) *
                                    (if (destroyed) 0.85f else 1f)
                                scaleY = dollSquash.value * (if (destroyed) 0.85f else 1f)
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                translationY = dollPushDown.value.dp.toPx()
                                alpha = if (destroyed) 0.5f else 1f
                            }
                    )
                    // 단계별 데미지 퍼센트 표시
                    if (!destroyed) {
                        Text(
                            text = "${(state.gauge * 100).roundToInt()}%",
                            style = AppFont.h5Bold,
                            color = Color.White,
                            modifier = Modifier.offset(y = 40.dp)
                        )
                    }
                }

                // 히트 이펙트 (별·충격파, 타격 순간에만) — 도구 임팩트 접점에 표시
                if (showHitEffect) {
                    Image(
                        painter = painterResource(id = VentingTool.hitEffectRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(130.dp)
                            .offset(x = 45.dp, y = (-60).dp)
                    )
                }

                // 선택한 도구가 인형을 내려치는 연출 (도구 오브젝트만, 캐릭터 없음)
                Image(
                    painter = painterResource(id = selectedTool.drawableRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .offset(x = StrikeMotion.toolOffsetX, y = StrikeMotion.toolOffsetY)
                        .alpha(strikeAlpha.value)
                        // 손잡이 끝(좌하단)을 축으로 한 스윙 회전
                        .graphicsLayer {
                            rotationZ = strikeAngle.value
                            transformOrigin = TransformOrigin(0f, 1f)
                        }
                        // 스프라이트별 방향 보정 (반전 → 기본 회전 순, iOS 동일)
                        .graphicsLayer {
                            rotationZ = selectedTool.strikeBaseRotation + StrikeMotion.ORIENTATION_ADJUST
                            scaleX = if (selectedTool.strikeFlipsHorizontally) -1f else 1f
                            scaleY = if (selectedTool.strikeFlipsVertically) -1f else 1f
                        }
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            // 단계 표시
            val (stageText, stageColor) = when (state.stage) {
                DestructionStage.IDLE -> "도구를 골라 인형을 때리세요!" to Gray400
                DestructionStage.CRACKED -> "💢 균열이 생겼습니다!" to Yellow400
                DestructionStage.BURST -> "🔥 터지기 직전입니다!" to Orange500
                DestructionStage.DESTROYED -> "💥 완파!" to Red400
            }
            Text(
                text = stageText,
                style = AppFont.h5Bold,
                color = stageColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (!state.isDestroyed) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Text(
                    text = "폰을 꽉 잡고 흔들어도 데미지! 세게 흔들수록 아파요",
                    style = AppFont.micro,
                    color = Gray500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 게이지 + 도구 트레이
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl)
                    .padding(bottom = AppSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)
            ) {
                GaugeBar(gauge = state.gauge, stage = state.stage)
                ToolTray(
                    selectedTool = selectedTool,
                    enabled = !state.isDestroyed,
                    onSelect = { selectedTool = it }
                )
            }
        }
    }
}

/** 배경: 파괴 단계에 따라 색상 변화. */
private fun stageBackground(stage: DestructionStage): Brush {
    val top = when (stage) {
        DestructionStage.IDLE -> Gray950
        DestructionStage.CRACKED -> Color(0xFF1A0808)
        DestructionStage.BURST -> Color(0xFF2D0A0A)
        DestructionStage.DESTROYED -> Color(0xFF3D0B0B)
    }
    return Brush.verticalGradient(colors = listOf(top, Gray950))
}

@Composable
private fun GaugeBar(gauge: Double, stage: DestructionStage) {
    val gaugeColor = when (stage) {
        DestructionStage.IDLE -> Blue400
        DestructionStage.CRACKED -> Yellow400
        DestructionStage.BURST -> Orange500
        DestructionStage.DESTROYED -> Red500
    }
    val animatedColor by animateColorAsState(targetValue = gaugeColor, label = "ventingGaugeColor")

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "데미지", style = AppFont.captionBold, color = Gray400)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(gauge * 100).roundToInt()}%",
                style = AppFont.captionBold,
                color = animatedColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(AppShapes.sm)
                .background(Gray800)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(gauge.toFloat())
                    .height(10.dp)
                    .clip(AppShapes.sm)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(animatedColor.copy(alpha = 0.8f), animatedColor)
                        )
                    )
            )
        }

        // 임계값 마커 (33% / 66%)
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(DestructionConstants.THRESHOLD1.toFloat()))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(Gray600)
                )
                Spacer(
                    modifier = Modifier.weight(
                        (DestructionConstants.THRESHOLD2 - DestructionConstants.THRESHOLD1).toFloat()
                    )
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(Gray600)
                )
                Spacer(
                    modifier = Modifier.weight(
                        (1.0 - DestructionConstants.THRESHOLD2).toFloat()
                    )
                )
            }
        }
    }
}

/**
 * 하단 가로 도구 트레이 (A안).
 * 선택된 도구는 레드 하이라이트, 비선택 도구는 그레이 톤.
 */
@Composable
private fun ToolTray(
    selectedTool: VentingTool,
    enabled: Boolean,
    onSelect: (VentingTool) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.lg)
            .background(Gray900)
            .padding(AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        VentingTool.entries.forEach { tool ->
            val isSelected = tool == selectedTool
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.md)
                    .background(if (isSelected) Red500.copy(alpha = 0.22f) else Gray800)
                    .then(
                        if (isSelected) Modifier.border(2.dp, Red500, AppShapes.md) else Modifier
                    )
                    .clickable(enabled = enabled) { onSelect(tool) }
                    .padding(vertical = AppSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Image(
                    painter = painterResource(id = tool.drawableRes),
                    contentDescription = tool.label,
                    modifier = Modifier
                        .size(42.dp)
                        .alpha(if (isSelected) 1f else 0.55f)
                )
                Text(
                    text = tool.label,
                    style = AppFont.tinyBold,
                    color = if (isSelected) Color.White else Gray500,
                    maxLines = 1
                )
            }
        }
    }
}
