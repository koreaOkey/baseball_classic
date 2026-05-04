package com.basehaptic.mobile.ui.screens

// TODO(my-team-tab): 활성화 시 SHOW_MY_TEAM_TAB=true 변경 후 진입점 노출.
// 1차 콘텐츠는 응원팀 누적 체크인 랭킹. 향후 팀별 뉴스 등 segmented content 추가 가능.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray950

@Composable
fun MyTeamScreen(
    selectedTeam: Team,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacing.lg),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "내 팀",
                fontSize = 22.sp,
            )
            Box(modifier = Modifier.padding(top = 16.dp))
            TeamCheckinRankingScreen(selectedTeam = selectedTeam)
        }
    }
}
