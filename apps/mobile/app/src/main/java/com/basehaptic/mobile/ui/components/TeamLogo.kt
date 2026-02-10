package com.basehaptic.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.R
import com.basehaptic.mobile.data.model.Team

@Composable
fun TeamLogo(
    team: Team,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val logoResource = when (team) {
        Team.DOOSAN -> R.drawable.dosan
        Team.LG -> R.drawable.lg
        Team.KIWOOM -> R.drawable.kiwoom
        Team.SAMSUNG -> R.drawable.samsung
        Team.LOTTE -> R.drawable.lotte
        Team.SSG -> R.drawable.ssg
        Team.KT -> R.drawable.kt
        Team.HANWHA -> R.drawable.hanwha
        Team.KIA -> R.drawable.kia
        Team.NC -> R.drawable.nc
        Team.NONE -> null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(team.color),
        contentAlignment = Alignment.Center
    ) {
        if (logoResource != null) {
            Image(
                painter = painterResource(id = logoResource),
                contentDescription = "${team.teamName} 로고",
                modifier = Modifier
                    .size(size * 0.8f)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "?",
                color = Color.White,
                fontSize = (size.value / 3).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

