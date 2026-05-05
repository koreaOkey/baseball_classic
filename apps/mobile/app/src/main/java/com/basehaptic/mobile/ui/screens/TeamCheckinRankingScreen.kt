package com.basehaptic.mobile.ui.screens

// TODO(stadium-cheer): 활성화 시 mock 데이터 → 백엔드 GET /rankings/teams 연동으로 교체.
// 다크 머지 단계에서는 정적 mock 데이터로 UI 골격만 검증.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.theme.AppSpacing

private data class CheerRankRow(
    val rank: Int,
    val teamCode: String,
    val teamName: String,
    val count: Int,
)

private val MOCK_SEASON: List<CheerRankRow> = listOf(
    CheerRankRow(1, "DOOSAN", "베어스", 0),
    CheerRankRow(2, "LG", "트윈스", 0),
    CheerRankRow(3, "KIA", "타이거즈", 0),
    CheerRankRow(4, "SAMSUNG", "라이온즈", 0),
    CheerRankRow(5, "LOTTE", "자이언츠", 0),
    CheerRankRow(6, "SSG", "랜더스", 0),
    CheerRankRow(7, "HANWHA", "이글스", 0),
    CheerRankRow(8, "NC", "다이노스", 0),
    CheerRankRow(9, "KT", "위즈", 0),
    CheerRankRow(10, "KIWOOM", "히어로즈", 0),
)

@Composable
fun TeamCheckinRankingScreen(selectedTeam: Team) {
    var period by remember { mutableStateOf("weekly") }
    val rows = MOCK_SEASON

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodChip(label = "주간", selected = period == "weekly") { period = "weekly" }
            PeriodChip(label = "시즌", selected = period == "season") { period = "season" }
        }
        Text(
            text = "Android · iOS 합산 집계",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.teamCode }) { row ->
                RankRowItem(row = row, isMyTeam = row.teamName == selectedTeam.name)
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFF3B82F6) else Color(0xFF1F2937)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun RankRowItem(row: CheerRankRow, isMyTeam: Boolean) {
    val bg = if (isMyTeam) Color(0xFF1E3A8A) else Color(0xFF111827)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${row.rank}",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = row.teamName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (isMyTeam) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Text(
            text = "${row.count}회",
            color = Color(0xFF93C5FD),
            fontSize = 14.sp,
        )
    }
}
