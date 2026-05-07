package com.basehaptic.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray700
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.wear.WatchCompanionStatus

private val SuccessGreen = Color(0xFF4CAF50)
private val WarnRed = Color(0xFFE53935)

@Composable
fun WatchInstallCard(
    status: WatchCompanionStatus,
    onInstall: () -> Unit,
    onRecheck: () -> Unit,
    onOpenWatchApp: () -> Unit,
    onWatchTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalTeamTheme.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.md,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        when (status) {
            is WatchCompanionStatus.Installed -> StateBody(
                glowColor = SuccessGreen,
                badgeColor = SuccessGreen,
                badgeIcon = Icons.Default.Check,
                title = "워치 앱 설치됨",
                description = "[워치 테스트]에서 체험해보세요",
                primary = ActionSpec(
                    label = "워치 테스트",
                    icon = Icons.Default.Watch,
                    onClick = onWatchTest,
                    enabled = true,
                    color = theme.primary
                ),
                secondary = null
            )
            is WatchCompanionStatus.PairedNoApp -> StateBody(
                glowColor = WarnRed,
                badgeColor = WarnRed,
                badgeIcon = Icons.Default.PriorityHigh,
                title = "워치 앱 설치 필요",
                description = "Play Store에서 설치 대상 기기를 워치로 선택해 주세요.",
                primary = ActionSpec(
                    label = "워치 앱 설치하기",
                    icon = Icons.Default.Download,
                    onClick = onInstall,
                    enabled = true,
                    color = theme.primary
                ),
                secondary = ActionSpec(
                    label = "연결 확인",
                    icon = Icons.Default.Link,
                    onClick = onRecheck,
                    enabled = true,
                    color = Color.Transparent
                )
            )
            // PairedNone 케이스
            is WatchCompanionStatus.PairedNone -> StateBody(
                glowColor = Gray500,
                badgeColor = Gray700,
                badgeIcon = Icons.Default.PriorityHigh,
                title = "워치 페어링 필요",
                description = "Wear OS 앱에서 워치를 폰과 먼저 페어링해 주세요.",
                primary = ActionSpec(
                    label = "Wear OS 앱 열기",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenWatchApp,
                    enabled = true,
                    color = theme.primary
                ),
                secondary = ActionSpec(
                    label = "연결 확인",
                    icon = Icons.Default.Link,
                    onClick = onRecheck,
                    enabled = true,
                    color = Color.Transparent
                )
            )
            is WatchCompanionStatus.Loading -> CompactLoadingRow()
        }
    }
}

private data class ActionSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean,
    val color: Color
)

@Composable
private fun StateBody(
    glowColor: Color,
    badgeColor: Color,
    badgeIcon: ImageVector,
    title: String,
    description: String,
    primary: ActionSpec?,
    secondary: ActionSpec?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "워치 앱",
                style = AppFont.bodyMedium,
                color = Gray400
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.md))

        WatchIllustration(glowColor = glowColor, badgeColor = badgeColor, badgeIcon = badgeIcon)

        Spacer(modifier = Modifier.height(AppSpacing.md))

        Text(
            text = title,
            style = AppFont.bodyLgBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(AppSpacing.xs))
        Text(
            text = description,
            style = AppFont.body,
            color = Gray400,
            textAlign = TextAlign.Center
        )

        if (primary != null) {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            PrimaryButton(spec = primary)
        }
        if (secondary != null) {
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            SecondaryButton(spec = secondary)
        }
    }
}

@Composable
private fun WatchIllustration(
    glowColor: Color,
    badgeColor: Color,
    badgeIcon: ImageVector
) {
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Reason: 상태색 radial glow — 워치 아이콘 뒤에서 부드럽게 퍼지는 효과
        Box(
            modifier = Modifier
                .size(110.dp)
                .blur(28.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.55f),
                            glowColor.copy(alpha = 0.0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color(0x1AFFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(120.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(badgeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(spec: ActionSpec) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(AppShapes.sm)
            .clickable(enabled = spec.enabled, onClick = spec.onClick),
        shape = AppShapes.sm,
        color = if (spec.enabled) spec.color else Gray800
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = if (spec.enabled) Color.White else Gray400,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Text(
                text = spec.label,
                style = AppFont.bodyLgMedium,
                color = if (spec.enabled) Color.White else Gray400
            )
        }
    }
}

@Composable
private fun SecondaryButton(spec: ActionSpec) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(AppShapes.sm)
            .clickable(onClick = spec.onClick),
        shape = AppShapes.sm,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Gray700)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = Gray300,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Text(
                text = spec.label,
                style = AppFont.body,
                color = Gray300
            )
        }
    }
}

@Composable
private fun CompactLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Watch,
            contentDescription = null,
            tint = Gray400,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(AppSpacing.md))
        Text(
            text = "워치 상태 확인 중…",
            style = AppFont.bodyLgMedium,
            color = Gray400
        )
    }
}
