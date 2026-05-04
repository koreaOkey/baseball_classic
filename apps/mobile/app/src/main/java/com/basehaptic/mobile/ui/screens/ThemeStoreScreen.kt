package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.StadiumCheerThemeStore
import com.basehaptic.mobile.data.model.ThemeCategory
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.data.model.ThemeStore
import com.basehaptic.mobile.ui.components.RewardedAdManager
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Blue500
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray600
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Green500
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Orange500
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.ui.theme.Yellow500

private const val SHOW_STADIUM_CHEER_THEMES = false

@Composable
fun ThemeStoreScreen(
    activeTheme: ThemeData?,
    activeCheerTheme: ThemeData?,
    unlockedThemeIds: Set<String>,
    onApplyTheme: (ThemeData?) -> Unit,
    onApplyCheerTheme: (ThemeData?) -> Unit,
    onUnlockTheme: (ThemeData) -> Unit,
) {
    val teamTheme = LocalTeamTheme.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Watch Themes", "Phone App Themes")

    val freeAndAdThemes = remember {
        ThemeStore.allThemes.filter {
            it.category == ThemeCategory.FREE || it.category == ThemeCategory.AD_REWARD
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg)
        ) {
            Text(
                text = "테마 상점",
                style = AppFont.h3Bold,
                color = Color.White
            )
            Text(
                text = "현재 적용: ${activeTheme?.name ?: "기본형"}",
                style = AppFont.caption,
                color = teamTheme.primary,
                modifier = Modifier.padding(top = AppSpacing.sm)
            )
            if (SHOW_STADIUM_CHEER_THEMES) {
                Text(
                    text = "응원 테마: ${activeCheerTheme?.name ?: "기본 응원"}",
                    style = AppFont.caption,
                    color = Gray400,
                )
            }
        }

        // Tab Bar
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Gray950,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Red500,
                    height = 2.dp
                )
            },
            divider = {},
            modifier = Modifier.padding(horizontal = AppSpacing.xxl)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = AppFont.bodyBold,
                            color = if (selectedTab == index) Red500 else Gray500
                        )
                    }
                )
            }
        }

        // Tab Content
        when (selectedTab) {
            0 -> WatchThemesTab(
                themes = freeAndAdThemes,
                activeTheme = activeTheme,
                activeCheerTheme = activeCheerTheme,
                unlockedThemeIds = unlockedThemeIds,
                onApplyTheme = onApplyTheme,
                onApplyCheerTheme = onApplyCheerTheme,
                onUnlockTheme = onUnlockTheme,
            )
            1 -> PhoneThemesTab()
        }
    }
}

private enum class WatchThemeSection { BASIC, STADIUM_CHEER }

@Composable
private fun WatchThemesTab(
    themes: List<ThemeData>,
    activeTheme: ThemeData?,
    activeCheerTheme: ThemeData?,
    unlockedThemeIds: Set<String>,
    onApplyTheme: (ThemeData?) -> Unit,
    onApplyCheerTheme: (ThemeData?) -> Unit,
    onUnlockTheme: (ThemeData) -> Unit,
) {
    var section by remember { androidx.compose.runtime.mutableStateOf(WatchThemeSection.BASIC) }
    val effectiveSection = if (SHOW_STADIUM_CHEER_THEMES) section else WatchThemeSection.BASIC
    val cheerThemes = if (SHOW_STADIUM_CHEER_THEMES) {
        remember { StadiumCheerThemeStore.allThemes }
    } else {
        emptyList()
    }
    val displayThemes = if (effectiveSection == WatchThemeSection.BASIC) themes else cheerThemes
    val activeForSection = if (effectiveSection == WatchThemeSection.BASIC) activeTheme else activeCheerTheme

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = AppSpacing.xxl, vertical = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        modifier = Modifier.fillMaxSize()
    ) {
        if (SHOW_STADIUM_CHEER_THEMES) {
            item(span = { GridItemSpan(2) }) {
                WatchThemeSectionToggle(
                    current = section,
                    onChange = { section = it },
                )
            }
        }

        if (SHOW_STADIUM_CHEER_THEMES && effectiveSection == WatchThemeSection.STADIUM_CHEER) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "응원 시각에 워치 풀스크린에 적용됩니다 (목업)",
                    style = AppFont.caption,
                    color = Gray500,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )
            }
        }

        items(displayThemes, key = { "${effectiveSection.name}_${it.id}" }) { theme ->
            val isUnlocked = theme.category == ThemeCategory.FREE || theme.id in unlockedThemeIds
            val isApplied = activeForSection?.id == theme.id

            ThemeCard(
                theme = theme,
                isUnlocked = isUnlocked,
                isApplied = isApplied,
                onApply = {
                    if (effectiveSection == WatchThemeSection.BASIC) onApplyTheme(theme)
                    else onApplyCheerTheme(theme)
                },
                onUnlock = { onUnlockTheme(theme) },
            )
        }

        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun WatchThemeSectionToggle(
    current: WatchThemeSection,
    onChange: (WatchThemeSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.md, bottom = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        SectionChip(
            label = "베이직 테마",
            selected = current == WatchThemeSection.BASIC,
            onClick = { onChange(WatchThemeSection.BASIC) },
        )
        SectionChip(
            label = "현장 응원 테마",
            selected = current == WatchThemeSection.STADIUM_CHEER,
            onClick = { onChange(WatchThemeSection.STADIUM_CHEER) },
        )
    }
}

@Composable
private fun SectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Red500 else Gray800
    val fg = if (selected) Color.White else Gray500
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = 8.dp)
    ) {
        Text(text = label, style = AppFont.bodyBold, color = fg)
    }
}

@Composable
private fun PhoneThemesTab() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Gray600,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Text(
            text = "준비 중",
            style = AppFont.bodyLgMedium,
            color = Gray500
        )
        Spacer(modifier = Modifier.height(AppSpacing.xs))
        Text(
            text = "폰 앱 테마는 곧 추가될 예정입니다.",
            style = AppFont.body,
            color = Gray600
        )
    }
}

// Reason: 워치 프리뷰 원형 크기 120dp — 카드 내부에서 최대한 큰 원을 그리되 여백 확보
private val WATCH_FACE_SIZE = 120.dp

@Composable
private fun ThemeCard(
    theme: ThemeData,
    isUnlocked: Boolean,
    isApplied: Boolean,
    onApply: () -> Unit,
    onUnlock: () -> Unit,
) {
    val context = LocalContext.current
    val previewResId = theme.previewImage?.let { imageName ->
        context.resources.getIdentifier(imageName, "drawable", context.packageName)
    }?.takeIf { it != 0 }

    Surface(
        shape = AppShapes.lg,
        color = Gray900
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Watch face preview — 원형
            Box(
                modifier = Modifier.size(WATCH_FACE_SIZE),
                contentAlignment = Alignment.Center
            ) {
                if (previewResId != null) {
                    // 스크린샷 이미지 프리뷰
                    Image(
                        painter = painterResource(id = previewResId),
                        contentDescription = theme.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    // 폴백: 코드 기반 미니 워치 UI
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF0A0A0B))
                    )
                    MiniWatchPreview(theme = theme)
                }

                // Applied badge
                if (isApplied) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                            .size(22.dp)
                            .background(Green500, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "적용 중",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Lock badge
                if (!isUnlocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "잠김",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.md))

            // Theme name
            Text(
                text = theme.name,
                style = AppFont.bodyBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Action button — 모든 상태에서 동일한 높이 유지
            val actionModifier = Modifier
                .clip(AppShapes.pill)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
            when {
                isApplied -> {
                    Text(
                        text = "적용 중",
                        style = AppFont.micro,
                        color = Gray400,
                        modifier = Modifier
                            .background(Gray800, AppShapes.pill)
                            .then(actionModifier)
                    )
                }
                isUnlocked -> {
                    Text(
                        text = "적용하기",
                        style = AppFont.microBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(AppShapes.pill)
                            .background(Blue500, AppShapes.pill)
                            .clickable(onClick = onApply)
                            .then(actionModifier)
                    )
                }
                theme.category == ThemeCategory.AD_REWARD -> {
                    val isLoading by RewardedAdManager.isLoading.collectAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(AppShapes.pill)
                            .background(Green500, AppShapes.pill)
                            .clickable(enabled = !isLoading, onClick = onUnlock)
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Text(
                            text = "광고 보고 받기",
                            style = AppFont.micro,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Mini Watch Preview (원형 워치 안에 표시되는 미니 UI)

@Composable
private fun MiniWatchPreview(
    theme: ThemeData,
    modifier: Modifier = Modifier
) {
    val accentColor = if (theme.id == "default") Orange500 else theme.colors.accent
    val inningBgColor = if (theme.id == "default") {
        Color.White.copy(alpha = 0.05f)
    } else {
        theme.colors.primary.copy(alpha = 0.2f)
    }

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Score row
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Away
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "팀 1",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "5",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Inning
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(inningBgColor, AppShapes.sm)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "7회말",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = "▼",
                    fontSize = 6.sp,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Home
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "팀 2",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "4",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // BSO row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Mini base diamond
            Box(modifier = Modifier.size(16.dp, 14.dp)) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .align(Alignment.TopCenter)
                        .background(Yellow500)
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .align(Alignment.CenterStart)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .align(Alignment.CenterEnd)
                        .background(Yellow500)
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.2f))
                )
            }

            Spacer(modifier = Modifier.width(5.dp))

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                CountDots(label = "B", count = 2, max = 3, color = Green500)
                CountDots(label = "S", count = 1, max = 2, color = Orange500)
                CountDots(label = "O", count = 1, max = 2, color = Red500)
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "P 김광현  B 이대호",
            fontSize = 6.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun CountDots(label: String, count: Int, max: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(6.dp),
            textAlign = TextAlign.Center
        )
        repeat(max) { i ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        if (i < count) color else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    )
            )
        }
    }
}
