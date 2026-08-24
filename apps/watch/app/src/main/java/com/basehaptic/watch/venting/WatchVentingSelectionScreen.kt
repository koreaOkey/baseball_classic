package com.basehaptic.watch.venting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/**
 * 워치 분풀이 대상 선택 화면 (라이브 화면에서 옆으로 스와이프해 진입).
 *
 * - 경기 중: 그 시점까지 이슈가 있었던 사람 (최신순)
 * - 패배 확정 후: 경기 전체 패배 지분 순 (심각도 가중)
 * - 후보 + 감독 고정 마지막 항목. 탭 즉시 룸 진입 (워치는 확인 단계 생략).
 * - 실명 미표기 — 이닝 기반 역할 레이블만 사용 (폰 자동 후보와 동일 원칙).
 */
@Composable
fun WatchVentingSelectionScreen(
    gameId: String,
    isFinished: Boolean,
    isLoss: Boolean,
    onSelect: (WatchVentingRequest) -> Unit
) {
    val context = LocalContext.current
    val rankBySeverity = isFinished && isLoss
    val candidates = remember(gameId, rankBySeverity) {
        WatchRegretTracker.candidates(context, gameId, rankBySeverity)
    }

    val managerDescription = if (isFinished) "오늘 경기 운영 아쉬움" else "지금까지의 경기 운영 아쉬움"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "💢 빠따존",
            color = Color(0xFFF87171),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = when {
                rankBySeverity -> "오늘의 아쉬운 순간"
                candidates.isEmpty() -> "아직 집계된 순간이 없어요"
                else -> "지금까지의 아쉬운 순간"
            },
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        candidates.forEach { entry ->
            TargetChip(
                label = entry.label,
                description = entry.description,
                onTap = {
                    onSelect(
                        WatchVentingRequest(
                            gameId = gameId,
                            targetLabel = entry.label,
                            eventDescription = entry.description,
                            requestedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            )
        }

        // 감독 고정 마지막 항목
        TargetChip(
            label = "감독",
            description = managerDescription,
            onTap = {
                onSelect(
                    WatchVentingRequest(
                        gameId = gameId,
                        targetLabel = "감독",
                        eventDescription = managerDescription,
                        requestedAtMs = System.currentTimeMillis()
                    )
                )
            }
        )
    }
}

@Composable
private fun TargetChip(
    label: String,
    description: String,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1C1C1F))
            .clickable { onTap() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(text = label, color = Color.White, fontSize = 13.sp)
        Text(text = description, color = Color(0xFF9CA3AF), fontSize = 10.sp)
    }
}
