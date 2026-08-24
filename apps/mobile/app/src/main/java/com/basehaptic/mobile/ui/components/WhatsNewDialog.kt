package com.basehaptic.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.basehaptic.mobile.data.model.ReleaseNote
import com.basehaptic.mobile.data.model.WhatsNewFeaturePage
import com.basehaptic.mobile.data.model.WhatsNewVisual
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray100
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Yellow400
import com.basehaptic.mobile.ui.theme.controlAccent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WhatsNewDialog(
    note: ReleaseNote,
    onConfirm: () -> Unit,
) {
    val teamTheme = LocalTeamTheme.current

    Dialog(
        onDismissRequest = onConfirm,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (note.featurePages.isEmpty()) {
            ClassicCard(note = note, accentColor = teamTheme.controlAccent, onConfirm = onConfirm)
        } else {
            SlideCard(note = note, accentColor = teamTheme.controlAccent, onConfirm = onConfirm)
        }
    }
}

// ── 슬라이드형 (대표 기능 페이지 + 마지막 불릿 페이지) ──

@Composable
private fun SlideCard(
    note: ReleaseNote,
    accentColor: Color,
    onConfirm: () -> Unit,
) {
    val pageCount = note.featurePages.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val cardHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp.coerceAtMost(600.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .height(cardHeight),
        shape = AppShapes.lg,
        color = Gray950,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SlideHeader(version = note.version)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                if (page < note.featurePages.size) {
                    FeaturePage(page = note.featurePages[page])
                } else {
                    BulletsPage(bullets = note.bullets, accentColor = accentColor)
                }
            }

            PageDots(
                pageCount = pageCount,
                currentPage = pagerState.currentPage,
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = AppSpacing.md),
            )

            SlideButton(
                isLast = pagerState.currentPage >= pageCount - 1,
                accentColor = accentColor,
                onClick = {
                    if (pagerState.currentPage >= pageCount - 1) {
                        onConfirm()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun SlideHeader(version: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .padding(top = AppSpacing.xxl, bottom = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .clip(AppShapes.sm)
                .background(Yellow400)
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        ) {
            Text(
                text = "NEW v$version",
                style = AppFont.captionBold,
                color = Gray950,
            )
        }
        Text(
            text = "업데이트 안내",
            style = AppFont.h4Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun FeaturePage(page: WhatsNewFeaturePage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.xxl)
            .padding(top = AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(AppShapes.lg),
        ) {
            when (page.visual) {
                is WhatsNewVisual.Image -> Image(
                    painter = painterResource(page.visual.res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                is WhatsNewVisual.LockScreen -> LockScreenFrame(res = page.visual.res)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 페이지 간 텍스트 양 차이로 비주얼 높이가 출렁이지 않게 고정
                .height(88.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = page.title,
                style = AppFont.h4Bold,
                color = Color.White,
            )
            Text(
                text = page.body,
                style = AppFont.body,
                color = Gray400,
            )
        }
    }
}

/** 시계·날짜를 그린 잠금 화면 프레임 안에 노티 카드 스크린샷을 얹는다. */
@Composable
private fun LockScreenFrame(res: Int) {
    val dateText = remember {
        SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date())
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2B2633), Color(0xFF0F0F17)),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.padding(top = AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dateText,
                style = AppFont.captionSemibold,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = "9:41",
                fontSize = 48.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(bottom = AppSpacing.md),
            )
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(horizontal = AppSpacing.lg)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun BulletsPage(bullets: List<String>, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.xxl)
            .padding(top = AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        Text(
            text = "이런 것도 좋아졌어요",
            style = AppFont.h4Bold,
            color = Color.White,
        )
        BulletList(bullets = bullets, accentColor = accentColor)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "그 외 자잘한 버그 수정과 안정성 개선이 포함되어 있어요.",
            style = AppFont.caption,
            color = Gray400,
            modifier = Modifier.padding(bottom = AppSpacing.sm),
        )
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    currentPage: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        repeat(pageCount) { index ->
            val width by animateDpAsState(if (index == currentPage) 20.dp else 7.dp, label = "dotWidth")
            val color by animateColorAsState(if (index == currentPage) accentColor else Gray800, label = "dotColor")
            Box(
                modifier = Modifier
                    .size(width = width, height = 7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun SlideButton(
    isLast: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Gray800)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray900)
                .height(AppSpacing.buttonHeight)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isLast) "확인" else "다음",
                style = AppFont.bodyLgBold,
                color = accentColor,
            )
        }
    }
}

// ── 기존 단일 불릿 레이아웃 (featurePages 없는 버전) ──

@Composable
private fun ClassicCard(
    note: ReleaseNote,
    accentColor: Color,
    onConfirm: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .heightIn(max = 560.dp),
        shape = AppShapes.lg,
        color = Gray950,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xxl)
                    .padding(top = AppSpacing.xxl, bottom = AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xl),
            ) {
                Header(version = note.version)
                Subtitle(text = note.subtitle)
                BulletList(bullets = note.bullets, accentColor = accentColor)
            }

            ConfirmButton(
                accentColor = accentColor,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun Header(version: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .clip(AppShapes.sm)
                .background(Yellow400)
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        ) {
            Text(
                text = "NEW v$version",
                style = AppFont.captionBold,
                color = Gray950,
            )
        }

        Text(
            text = "업데이트 안내",
            style = AppFont.h2,
            color = Color.White,
        )
    }
}

@Composable
private fun Subtitle(text: String) {
    Text(
        text = text,
        style = AppFont.body,
        color = Gray400,
    )
}

@Composable
private fun BulletList(bullets: List<String>, accentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        bullets.forEach { bullet ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = bullet,
                    style = AppFont.bodyLgMedium,
                    color = Gray100,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConfirmButton(
    accentColor: Color,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Gray800)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray900)
                .height(AppSpacing.buttonHeight)
                .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "확인",
                style = AppFont.bodyLgBold,
                color = accentColor,
            )
        }
    }
}
