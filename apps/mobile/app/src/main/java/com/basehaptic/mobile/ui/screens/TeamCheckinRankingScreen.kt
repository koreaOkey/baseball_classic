package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.stadium.CheerSignalsLoader
import com.basehaptic.mobile.stadium.TeamCheckinRank
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Blue500
import com.basehaptic.mobile.ui.theme.Gray100
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray700
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.LocalTeamTheme

private val FALLBACK_TEAMS: List<Team> = listOf(
    Team.DOOSAN,
    Team.LG,
    Team.KIA,
    Team.SAMSUNG,
    Team.LOTTE,
    Team.SSG,
    Team.HANWHA,
    Team.NC,
    Team.KT,
    Team.KIWOOM,
)

@Composable
fun TeamCheckinRankingScreen(
    selectedTeam: Team,
    localWeeklyBoost: Int = 0,
) {
    val context = LocalContext.current
    var period by remember { mutableStateOf("weekly") }
    var fetchedRows by remember { mutableStateOf<List<TeamCheckinRank>>(emptyList()) }

    LaunchedEffect(period) {
        fetchedRows = CheerSignalsLoader.fetchTeamRankings(context.applicationContext, period)
    }

    val rows = remember(fetchedRows, period, selectedTeam, localWeeklyBoost) {
        val fetchedByTeam = fetchedRows.associateBy { it.team }
        val baseTeams = (fetchedRows.map { it.team } + FALLBACK_TEAMS).distinct().filter { it != Team.NONE }
        baseTeams.map { team ->
            val baseCount = fetchedByTeam[team]?.count ?: 0
            val boostedCount = baseCount + if (period == "weekly" && team == selectedTeam) localWeeklyBoost else 0
            TeamCheckinRank(rank = 0, team = team, count = boostedCount)
        }.sortedWith(
            compareByDescending<TeamCheckinRank> { it.count }
                .thenBy { it.team.teamName }
        ).mapIndexed { index, row ->
            row.copy(rank = index + 1)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            PeriodChip(label = "주간", selected = period == "weekly") { period = "weekly" }
            PeriodChip(label = "시즌", selected = period == "season") { period = "season" }
        }
        Text(
            text = "iOS · Android 합산 집계",
            color = Gray400,
            style = AppFont.tiny,
            modifier = Modifier.padding(top = AppSpacing.sm),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            rows.take(10).forEach { row ->
                RankRowItem(row = row, isMyTeam = row.team == selectedTeam)
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val teamTheme = LocalTeamTheme.current
    val bg = if (selected) teamTheme.primary else Gray800
    val fg = if (selected) Color.White else Gray300
    Row(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = fg, style = AppFont.captionBold)
    }
}

@Composable
private fun RankRowItem(row: TeamCheckinRank, isMyTeam: Boolean) {
    val teamTheme = LocalTeamTheme.current
    val bg = if (isMyTeam) teamTheme.primary.copy(alpha = 0.26f) else Gray800
    val borderHint = if (isMyTeam) teamTheme.primary else Gray700
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.lg)
            .background(bg)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "#${row.rank}",
                color = if (row.rank <= 3) Blue500 else Gray300,
                style = AppFont.bodySemibold,
                modifier = Modifier.width(42.dp),
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.team.teamName,
                        color = Color.White,
                        style = if (isMyTeam) AppFont.labelBold else AppFont.labelMedium,
                    )
                    if (isMyTeam) {
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = borderHint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = if (isMyTeam) "내 응원팀" else "체크인 합산",
                    color = Gray400,
                    style = AppFont.tiny,
                )
            }
        }
        Text(
            text = "${row.count}회",
            color = if (isMyTeam) Gray100 else Gray300,
            style = AppFont.bodyBold,
        )
    }
}
