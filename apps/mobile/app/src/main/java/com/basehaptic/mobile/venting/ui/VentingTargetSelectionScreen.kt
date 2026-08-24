package com.basehaptic.mobile.venting.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray700
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Red400
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.venting.VentingGameContext
import com.basehaptic.mobile.venting.VentingManagerOption
import com.basehaptic.mobile.venting.VentingTarget

/** red300 — 기존 Color.kt에 없어 분풀이 모드 전용으로 정의 (iOS 동일). */
private val Red300 = Color(0xFFFCA5A5)

/**
 * 아쉬운 순간 TOP5 선택 화면 (iOS VentingTargetSelectionScreen 포팅).
 *
 * - 선수 후보(최대 5명) + 감독 고정 6번째 항목을 표시한다.
 * - 후보 5명 미만이어도 있는 후보만 부분 표시하며 진입을 차단하지 않는다.
 * - "기록 기반 자동 선정이며 공식 평가가 아닙니다" 면책 문구 상시 노출.
 * - 후보 제목은 "역할(실명)" 형식(예: "9번 타자(구자욱)") — 실명 노출은 이 선택 화면 한정,
 *   룸·완파·워치 화면은 익명(역할 레이블) 유지.
 */
@Composable
fun VentingTargetSelectionScreen(
    context: VentingGameContext,
    onBack: () -> Unit,
    onSelectTarget: (VentingTarget) -> Unit,
    backLabel: String = "홈",
    /** 워치 연동(앱 설치)됐을 때만 "워치로 분풀이 시작하기" 버튼을 노출한다. */
    showWatchOption: Boolean = false,
    /** 워치로 분풀이 시작 선택 시 호출. */
    onSelectTargetOnWatch: (VentingTarget) -> Unit = {}
) {
    var selectedTarget by remember { mutableStateOf<VentingTarget?>(null) }
    var customName by remember { mutableStateOf("") }
    val isLive = context.inningLabel != null
    val managerOption = remember(context) {
        VentingManagerOption(eventDescription = context.managerEventDescription)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        // 네비게이션 헤더
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
                Text(text = backLabel, style = AppFont.bodyMedium, color = Color.White)
            }
            Text(
                text = "💢 빠따존",
                style = AppFont.h5Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // 경기 스코어 요약
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl)
                    .padding(top = AppSpacing.lg)
                    .clip(AppShapes.lg)
                    .background(Gray900)
                    .padding(AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(
                        text = teamDisplayName(context.myTeamId),
                        style = AppFont.captionBold,
                        color = Gray400
                    )
                    Text(text = "${context.myScore}", style = AppFont.h2, color = Red400)
                }
                Text(text = context.inningLabel ?: "최종", style = AppFont.micro, color = Gray500)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(text = "상대팀", style = AppFont.captionBold, color = Gray400)
                    Text(text = "${context.opponentScore}", style = AppFont.h2, color = Color.White)
                }
            }

            Text(
                text = if (isLive) {
                    "지금까지의 아쉬운 순간"
                } else {
                    "오늘의 아쉬운 순간 TOP${minOf(context.candidates.size, 5)}"
                },
                style = AppFont.h5Bold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl)
                    .padding(top = AppSpacing.lg)
            )

            if (context.candidates.isEmpty()) {
                Text(
                    text = "아직 집계된 아쉬운 순간이 없어요.\n감독을 고르거나 직접 입력으로 지목해보세요.",
                    style = AppFont.caption,
                    color = Gray500,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.xxl)
                )
            }

            // 선수 후보 목록 + 감독 고정 6번째
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                context.candidates.take(5).forEachIndexed { index, candidate ->
                    val target = VentingTarget.Player(candidate)
                    TargetRow(
                        rank = index + 1,
                        roleLabel = candidate.roleLabel,
                        // 실명은 선택 화면 TargetRow에서만 노출 (룸·완파 화면 금지).
                        playerName = candidate.playerName,
                        eventDescription = candidate.eventDescription,
                        isSelected = selectedTarget == target,
                        onTap = { selectedTarget = target }
                    )
                }
                val managerTarget = VentingTarget.Manager(managerOption)
                TargetRow(
                    rank = null,
                    roleLabel = managerOption.label,
                    eventDescription = managerOption.eventDescription,
                    isSelected = selectedTarget == managerTarget,
                    onTap = { selectedTarget = managerTarget }
                )

                // 직접 입력 — 입력값은 표시 전용, 저장·전송하지 않는다.
                CustomTargetRow(
                    name = customName,
                    isSelected = selectedTarget is VentingTarget.Custom,
                    onNameChange = { name ->
                        customName = name
                        val trimmed = name.trim()
                        selectedTarget = if (trimmed.isNotEmpty()) {
                            VentingTarget.Custom(trimmed)
                        } else if (selectedTarget is VentingTarget.Custom) {
                            null
                        } else {
                            selectedTarget
                        }
                    },
                    onTap = {
                        val trimmed = customName.trim()
                        if (trimmed.isNotEmpty()) selectedTarget = VentingTarget.Custom(trimmed)
                    }
                )
            }

            // 면책 문구 (상시 노출, 절대 제거 금지)
            Row(
                modifier = Modifier
                    .padding(horizontal = AppSpacing.xxl)
                    .padding(top = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Gray500,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "기록 기반 자동 선정이며 공식 평가가 아닙니다",
                    style = AppFont.micro,
                    color = Gray500
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        }

        // 분풀이 시작 버튼
        HorizontalDivider(color = Gray800)
        val buttonEnabled = selectedTarget != null
        val buttonColor by animateColorAsState(
            targetValue = if (buttonEnabled) Red500 else Gray800,
            label = "ventingStartButton"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray950)
                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            // 폰에서 분풀이 시작
            Text(
                text = if (buttonEnabled) "빠따존 입장하기" else "대상을 선택하세요",
                style = AppFont.bodyLgMedium,
                color = if (buttonEnabled) Color.White else Gray400,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.md)
                    .background(buttonColor)
                    .clickable(enabled = buttonEnabled) {
                        selectedTarget?.let { onSelectTarget(it) }
                    }
                    .padding(vertical = AppSpacing.lg)
            )

            // 워치로 분풀이 시작 (워치 연동 시에만 노출)
            if (showWatchOption) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.md)
                        .background(Gray900)
                        .border(
                            1.dp,
                            if (buttonEnabled) Red500.copy(alpha = 0.5f) else Gray800,
                            AppShapes.md
                        )
                        .clickable(enabled = buttonEnabled) {
                            selectedTarget?.let { onSelectTargetOnWatch(it) }
                        }
                        .padding(vertical = AppSpacing.lg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = null,
                        tint = if (buttonEnabled) Red400 else Gray500,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Text(
                        text = "워치로 빠따존 열기",
                        style = AppFont.bodyLgMedium,
                        color = if (buttonEnabled) Red400 else Gray500,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 선수명 직접 입력 행. 입력 즉시 [VentingTarget.Custom]으로 선택되며,
 * 입력값을 지우면 선택도 해제된다.
 */
@Composable
private fun CustomTargetRow(
    name: String,
    isSelected: Boolean,
    onNameChange: (String) -> Unit,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.md)
            .background(if (isSelected) Red500.copy(alpha = 0.12f) else Gray900)
            .border(
                1.dp,
                if (isSelected) Red500.copy(alpha = 0.5f) else Gray800,
                AppShapes.md
            )
            .clickable { onTap() }
            .padding(AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = if (isSelected) Red400 else Gray500,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Text(
                text = "직접 입력",
                style = AppFont.captionBold,
                color = if (isSelected) Color.White else Gray300
            )
            Box {
                if (name.isEmpty()) {
                    Text(
                        text = "화풀이할 선수명을 입력하세요",
                        style = AppFont.body,
                        color = Gray500
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    textStyle = AppFont.body.copy(
                        color = if (isSelected) Red300 else Color.White
                    ),
                    cursorBrush = SolidColor(Red400),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (isSelected) Red500 else Gray700,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TargetRow(
    rank: Int?,
    roleLabel: String,
    eventDescription: String,
    isSelected: Boolean,
    onTap: () -> Unit,
    /** 선택 화면 전용 실명 (있을 때만 "역할(실명)" 표기 — 예: "9번 타자(구자욱)"). */
    playerName: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.md)
            .background(if (isSelected) Red500.copy(alpha = 0.12f) else Gray900)
            .border(
                1.dp,
                if (isSelected) Red500.copy(alpha = 0.5f) else Gray800,
                AppShapes.md
            )
            .clickable { onTap() }
            .padding(AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        // 순위 뱃지 (감독은 사람 아이콘)
        if (rank != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Red500.copy(alpha = 0.2f) else Gray800),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    style = AppFont.captionBold,
                    color = if (isSelected) Red400 else Gray500
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (isSelected) Red400 else Gray500,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Text(
                text = if (!playerName.isNullOrBlank()) "$roleLabel($playerName)" else roleLabel,
                style = AppFont.captionBold,
                color = if (isSelected) Color.White else Gray300
            )
            Text(
                text = eventDescription,
                style = AppFont.body,
                color = if (isSelected) Red300 else Gray400,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (isSelected) Red500 else Gray700,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 팀 코드/이름 → 표시명 (iOS teamDisplayName 헬퍼와 동일 역할). */
internal fun teamDisplayName(teamId: String): String {
    val team = Team.fromString(teamId)
    if (team != Team.NONE) return team.clubName
    return when (teamId.uppercase()) {
        // KBO team codes (Phase 2 backend format)
        "HH" -> "한화"
        "LG" -> "LG"
        "OB" -> "두산"
        "WO" -> "키움"
        "SS" -> "삼성"
        "LT" -> "롯데"
        "SK" -> "SSG"
        "KT" -> "KT"
        "HT" -> "KIA"
        "NC" -> "NC"
        else -> teamId
    }
}
