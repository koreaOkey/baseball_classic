package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeColors
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Blue500
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Orange500
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.ui.theme.Yellow500

@Composable
fun ThemeStoreScreen(
    selectedTeam: Team,
    activeTheme: ThemeData?,
    onApplyTheme: (ThemeData?) -> Unit,
    onPurchaseTheme: (ThemeData) -> Unit,
    purchasedThemes: List<ThemeData>
) {
    val storeItems = remember(selectedTeam) { getMockThemeStoreItems(selectedTeam) }
    val purchasedIds = remember(purchasedThemes) { purchasedThemes.map { it.id }.toSet() }
    val teamTheme = LocalTeamTheme.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xl)
            ) {
                Text(
                    text = "테마 상점",
                    style = AppFont.h3Bold,
                    color = Color.White
                )
                Text(
                    text = "목업 데이터로 구성된 테마 프리뷰입니다.",
                    style = AppFont.body,
                    color = Gray400,
                    // Reason: 서브 텍스트 미세 조정 여백
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = "현재 적용: ${activeTheme?.name ?: "기본 팀 테마"}",
                    style = AppFont.caption,
                    color = teamTheme.primary,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
                OutlinedButton(
                    onClick = { onApplyTheme(null) },
                    enabled = activeTheme != null,
                    modifier = Modifier.padding(top = AppSpacing.md)
                ) {
                    Text("기본 팀 테마 적용")
                }
            }
        }

        items(storeItems, key = { it.theme.id }) { item ->
            val isPurchased = item.theme.id in purchasedIds
            val isApplied = activeTheme?.id == item.theme.id
            ThemeStoreItemCard(
                item = item,
                isPurchased = isPurchased,
                isApplied = isApplied,
                onPurchase = { onPurchaseTheme(item.theme) },
                onApply = { onApplyTheme(item.theme) }
            )
        }

        item {
            Text(
                text = "목업 데이터는 실제 결제/다운로드와 연결되어 있지 않습니다.",
                style = AppFont.micro,
                color = Gray400,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xl)
            )
            // Reason: 하단 safe area (72dp: 네비게이션 바 높이 + 여유)
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun ThemeStoreItemCard(
    item: ThemeStoreItem,
    isPurchased: Boolean,
    isApplied: Boolean,
    onPurchase: () -> Unit,
    onApply: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.sm),
        color = Gray900,
        shape = AppShapes.lg,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(team = item.theme.teamId, size = 60.dp)
                    Spacer(modifier = Modifier.size(AppSpacing.md))
                    Column {
                        Text(
                            text = item.theme.name,
                            // Reason: 17sp는 h5(18)과 bodyLg(16) 사이, 디자인 상 중간값 필요
                            style = AppFont.bodyLgMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = item.subtitle,
                            style = AppFont.micro,
                            color = Gray400
                        )
                    }
                }
                Text(
                    text = "${item.price}P",
                    style = AppFont.labelBold,
                    color = if (isPurchased) Gray300 else Yellow500
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(item.theme.colors.primary, item.theme.colors.secondary)
                        ),
                        shape = AppShapes.md
                    )
            ) {
                Text(
                    text = "Accent",
                    color = item.theme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = AppSpacing.lg)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isApplied -> {
                        AssistChip(
                            onClick = { },
                            enabled = false,
                            label = { Text("적용 중") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    isPurchased -> {
                        Button(onClick = onApply) {
                            Text("적용하기")
                        }
                    }

                    else -> {
                        FilledTonalButton(onClick = onPurchase) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(AppSpacing.xs))
                            Text("구매하기")
                        }
                    }
                }
            }
        }
    }
}

private data class ThemeStoreItem(
    val theme: ThemeData,
    val price: Int,
    val subtitle: String
)

// Reason: 목업 테마 스토어 데이터. 각 테마가 고유 색 조합을 가지므로 토큰 팔레트 밖의 색상 사용 허용.
private fun getMockThemeStoreItems(selectedTeam: Team): List<ThemeStoreItem> {
    return listOf(
        ThemeStoreItem(
            theme = ThemeData(
                id = "theme_home_crowd",
                teamId = selectedTeam,
                name = "${selectedTeam.teamName} 홈 크라우드",
                colors = ThemeColors(
                    primary = selectedTeam.color,
                    secondary = Gray800,
                    accent = Yellow500
                ),
                animation = "crowd_wave"
            ),
            price = 1200,
            subtitle = "응원단 중앙 하이라이트 + 응원가 무드"
        ),
        ThemeStoreItem(
            theme = ThemeData(
                id = "theme_retro_sunset",
                teamId = Team.KIA,
                name = "레트로 선셋",
                colors = ThemeColors(
                    primary = Orange500,
                    secondary = Red500,
                    accent = Yellow500
                ),
                animation = "retro_scanline"
            ),
            price = 900,
            subtitle = "복고 감성 컬러 + 아날로그 스캔 효과"
        ),
        ThemeStoreItem(
            theme = ThemeData(
                id = "theme_ice_blue",
                teamId = Team.SAMSUNG,
                name = "아이스 블루",
                colors = ThemeColors(
                    primary = Blue500,
                    secondary = Color(0xFF0B1F3A),
                    accent = Color(0xFFA5D8FF)
                ),
                animation = "ice_spark"
            ),
            price = 1000,
            subtitle = "시원한 톤의 라인 + 타격 이펙트 강조"
        ),
        ThemeStoreItem(
            theme = ThemeData(
                id = "theme_dark_monochrome",
                teamId = Team.LOTTE,
                name = "모노크롬 다크",
                colors = ThemeColors(
                    primary = Color(0xFFE5E7EB),
                    secondary = Color(0xFF111827),
                    accent = Color(0xFF93C5FD)
                ),
                animation = "mono_pulse"
            ),
            price = 700,
            subtitle = "미니멀 대비 + 점수 집중형 UI"
        )
    )
}
