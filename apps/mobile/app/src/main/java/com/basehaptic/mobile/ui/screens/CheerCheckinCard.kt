package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.R
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray100
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.LocalTeamTheme

enum class CheerCheckinState {
    Ready,
    CheckedIn,
    OutsideVenue,
    PermissionNeeded,
    NoGameToday,
    Dark,
}

@Composable
fun CheerCheckinCard(
    selectedTeamLabel: String,
    stadiumName: String?,
    stadiumRegion: String?,
    state: CheerCheckinState,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val teamTheme = LocalTeamTheme.current
    val status = when (state) {
        CheerCheckinState.Ready -> CheckinStatus(
            icon = Icons.Default.LocationOn,
            label = "${stadiumName ?: "경기장"} 근처",
            title = "오늘 직관 인증",
            subtitle = "$selectedTeamLabel 경기 시각에 위치가 함께 울려요",
            button = "체크인하기",
            footer = "위치 확인 완료",
            enabled = true,
        )
        CheerCheckinState.CheckedIn -> CheckinStatus(
            icon = Icons.Default.CheckCircle,
            label = "체크인 완료",
            title = "오늘 응원 인증 완료",
            subtitle = "팀 체크인 랭킹에 반영될 예정이에요",
            button = "완료됨",
            footer = "위치 확인 완료",
            enabled = false,
        )
        CheerCheckinState.OutsideVenue -> CheckinStatus(
            icon = Icons.Default.LocationOn,
            label = "구장 밖",
            title = "오늘 직관 인증",
            subtitle = "$selectedTeamLabel 경기 구장 반경 500m 안에서 체크인할 수 있어요",
            button = "체크인하기",
            footer = "현재 경기장 장소가 아닙니다",
            enabled = true,
        )
        CheerCheckinState.PermissionNeeded -> CheckinStatus(
            icon = Icons.Default.Lock,
            label = "권한 필요",
            title = "위치 권한을 켜면 자동으로 확인해요",
            subtitle = "운영 활성화 전까지는 다크 상태로 보존됩니다",
            button = "권한 안내",
            footer = "위치 확인 대기",
            enabled = false,
        )
        CheerCheckinState.NoGameToday -> CheckinStatus(
            icon = Icons.Default.SportsBaseball,
            label = "오늘 경기 없음",
            title = "오늘은 체크인할 경기가 없어요",
            subtitle = "랭킹은 계속 볼 수 있고 다음 경기 때 다시 알려드릴게요",
            button = "대기 중",
            footer = "경기 일정 대기",
            enabled = false,
        )
        CheerCheckinState.Dark -> CheckinStatus(
            icon = Icons.Default.LocationOn,
            label = "다크 머지",
            title = "경기장 체크인 준비 중",
            subtitle = "내 팀 탭에서 체크인, 워치 응원, 랭킹을 한 번에 제공합니다",
            button = "준비 중",
            footer = "위치 확인 준비 중",
            enabled = false,
        )
    }
    val footerColor = when (status.footer) {
        "위치 확인 완료" -> Color(0xFF22C55E)
        "현재 경기장 장소가 아닙니다" -> Color(0xFFF97316)
        else -> teamTheme.accent
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.xl,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF07152E),
                            Gray800,
                        )
                    )
                )
                .padding(AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(AppShapes.lg),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.stadium_checkin_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(164.dp),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = AppSpacing.md, end = AppSpacing.md),
                    shape = AppShapes.lg,
                    color = Color(0xFF020617).copy(alpha = 0.72f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stadiumName ?: "오늘 경기장",
                            style = AppFont.captionBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stadiumRegion ?: "지역 확인 중",
                            style = AppFont.tiny,
                            color = Gray300,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            Text(
                text = status.title,
                style = AppFont.h3Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(
                text = status.subtitle,
                style = AppFont.body,
                color = Gray300,
            )
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.pill)
                    .background(
                        if (status.enabled) {
                            Brush.horizontalGradient(
                                listOf(Color(0xFFEF4444), Color(0xFFDB2777))
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.08f))
                            )
                        }
                    )
                    .clickable(enabled = status.enabled, onClick = onPrimaryClick)
                    .padding(vertical = AppSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (status.enabled) Color.White else Gray400,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Text(
                        text = status.button,
                        style = AppFont.captionBold,
                        color = if (status.enabled) Color.White else Gray400,
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.md))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = status.icon,
                    contentDescription = null,
                    tint = footerColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                Text(
                    text = status.footer,
                    style = AppFont.tiny,
                    color = footerColor,
                )
            }
        }
    }
}

private data class CheckinStatus(
    val icon: ImageVector,
    val label: String,
    val title: String,
    val subtitle: String,
    val button: String,
    val footer: String,
    val enabled: Boolean,
)
