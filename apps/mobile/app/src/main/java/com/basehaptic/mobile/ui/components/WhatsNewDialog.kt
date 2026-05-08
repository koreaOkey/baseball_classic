package com.basehaptic.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.basehaptic.mobile.data.model.ReleaseNote
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
                    BulletList(bullets = note.bullets, accentColor = teamTheme.primary)
                }

                ConfirmButton(
                    accentColor = teamTheme.primary,
                    onConfirm = onConfirm,
                )
            }
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
