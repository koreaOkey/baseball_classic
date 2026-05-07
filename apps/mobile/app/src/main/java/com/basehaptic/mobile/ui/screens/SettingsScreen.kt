package com.basehaptic.mobile.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.basehaptic.mobile.auth.AuthState
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.components.WatchInstallCard
import com.basehaptic.mobile.wear.WatchCompanionStatus
import com.basehaptic.mobile.wear.WatchCompanionStatusRepository
import com.basehaptic.mobile.wear.WatchInstallLauncher
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray700
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.LocalTeamTheme

@Composable
fun SettingsScreen(
    selectedTeam: Team,
    onChangeTeam: (Team) -> Unit,
    onOpenWatchTest: () -> Unit,
    authState: AuthState = AuthState.LoggedOut,
    onSignInWithKakao: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDeleteAccount: (suspend () -> Boolean)? = null
) {
    val teamTheme = LocalTeamTheme.current
    var showTeamPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
            .padding(AppSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        item {
            Text(
                text = "설정",
                style = AppFont.h2,
                color = Color.White,
                modifier = Modifier.padding(bottom = AppSpacing.lg)
            )
        }

        item {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var watchStatus by remember {
                mutableStateOf<WatchCompanionStatus>(WatchCompanionStatus.Loading)
            }
            val refreshScope = androidx.compose.runtime.rememberCoroutineScope()
            val refreshStatus = remember<() -> Unit>(context) {
                {
                    refreshScope.launch {
                        watchStatus = WatchCompanionStatusRepository.getStatus(context)
                    }
                }
            }
            LaunchedEffect(Unit) { refreshStatus() }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            WatchInstallCard(
                status = watchStatus,
                onInstall = { WatchInstallLauncher.openPlayStoreForWatch(context) },
                onRecheck = { refreshStatus() },
                onOpenWatchApp = { WatchInstallLauncher.openWearOsCompanion(context) },
                onWatchTest = onOpenWatchTest
            )
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            SettingsSection(title = "팀 설정")
        }

        item {
            SettingsItem(
                icon = Icons.Default.Group,
                title = "응원 팀",
                subtitle = selectedTeam.teamName,
                onClick = { showTeamPicker = !showTeamPicker }
            )
        }

        if (showTeamPicker) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.md,
                    color = Gray800,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.md)) {
                        val teams = listOf(
                            Team.DOOSAN,
                            Team.LG,
                            Team.KIWOOM,
                            Team.SAMSUNG,
                            Team.LOTTE,
                            Team.SSG,
                            Team.KT,
                            Team.HANWHA,
                            Team.KIA,
                            Team.NC
                        )

                        teams.forEach { team ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.sm)
                                    .clickable {
                                        onChangeTeam(team)
                                        showTeamPicker = false
                                    },
                                color = if (team == selectedTeam) {
                                    teamTheme.primary.copy(alpha = 0.2f)
                                } else {
                                    Color.Transparent
                                },
                                shape = AppShapes.sm
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TeamLogo(team = team, size = 56.dp)
                                    Spacer(modifier = Modifier.width(AppSpacing.md))
                                    Text(
                                        text = team.teamName,
                                        style = if (team == selectedTeam) AppFont.labelBold else AppFont.label,
                                        color = if (team == selectedTeam) Color.White else Gray300
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (team == selectedTeam) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = teamTheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "계정")
        }

        item {
            when (authState) {
                is AuthState.LoggedIn -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.md,
                        color = Gray900,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.lg)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    // Reason: 로그인 성공 피드백 Material green 톤
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(
                                    text = "로그인됨",
                                    style = AppFont.bodyMedium,
                                    // Reason: 로그인 성공 피드백 Material green 톤
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.sm))
                            Text(
                                text = authState.email ?: "카카오 계정",
                                style = AppFont.bodyLg,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.md))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.sm)
                                    .clickable { onSignOut() },
                                shape = AppShapes.sm,
                                color = Gray800
                            ) {
                                Text(
                                    text = "로그아웃",
                                    style = AppFont.body,
                                    color = Gray400,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.md),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.sm))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.sm)
                                    .clickable(enabled = !isDeletingAccount) { showDeleteConfirm = true },
                                shape = AppShapes.sm,
                                color = Gray800
                            ) {
                                if (isDeletingAccount) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(AppSpacing.md)
                                            .size(20.dp),
                                        color = Color.Red,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "계정 삭제",
                                        style = AppFont.body,
                                        color = Color.Red,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(AppSpacing.md),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                is AuthState.Loading -> {
                    // Show nothing while loading
                }
                is AuthState.LoggedOut -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.md)
                            .clickable { onSignInWithKakao() },
                        shape = AppShapes.md,
                        // Reason: 카카오 브랜드 지정 색
                        color = Color(0xFFFEE500)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.lg),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCAC",
                                style = AppFont.h4
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Text(
                                text = "카카오로 로그인",
                                style = AppFont.bodyLgBold,
                                // Reason: 카카오 브랜드 지정 색 (거의 블랙)
                                color = Color(0xFF191919)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            SettingsSection(title = "알림")
        }

        item {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
            }
            var hapticEnabled by remember {
                mutableStateOf(prefs.getBoolean("live_haptic_enabled", true))
            }
            SettingsItemWithSwitch(
                icon = Icons.Default.Vibration,
                title = "경기 라이브 알림",
                subtitle = "실시간 경기 내용을 워치로 알림 받기",
                checked = hapticEnabled,
                onCheckedChange = {
                    hapticEnabled = it
                    prefs.edit().putBoolean("live_haptic_enabled", it).apply()
                    com.basehaptic.mobile.wear.WearSettingsSyncManager
                        .syncLiveHapticEnabledToWatch(context, it)
                    if (it) {
                        // OFF→ON 복원: 캐시된 마지막 game_data를 즉시 워치에 push
                        com.basehaptic.mobile.wear.WearGameSyncManager
                            .resyncLastGameDataToWatch(context)
                    }
                }
            )
        }

        item {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
            }
            var ballStrikeEnabled by remember {
                mutableStateOf(prefs.getBoolean("ball_strike_haptic_enabled", true))
            }
            SettingsItemWithSwitch(
                icon = Icons.Default.SportsBaseball,
                title = "스트라이크 · 볼 알림",
                subtitle = "볼, 스트라이크 이벤트를 워치에서 진동으로 받기",
                checked = ballStrikeEnabled,
                onCheckedChange = {
                    ballStrikeEnabled = it
                    prefs.edit().putBoolean("ball_strike_haptic_enabled", it).apply()
                }
            )
        }

        item {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
            }
            var eventVideoEnabled by remember {
                mutableStateOf(prefs.getBoolean("event_video_enabled", true))
            }
            SettingsItemWithSwitch(
                icon = Icons.Default.PlayCircle,
                title = "이벤트 영상 알림",
                subtitle = "홈런·안타·득점 등 이벤트 발생 시 워치 영상 재생",
                checked = eventVideoEnabled,
                onCheckedChange = {
                    eventVideoEnabled = it
                    prefs.edit().putBoolean("event_video_enabled", it).apply()
                    com.basehaptic.mobile.wear.WearSettingsSyncManager.syncEventVideoEnabledToWatch(context, it)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            SettingsSection(title = "정보")
        }

        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "버전",
                subtitle = "1.0.0",
                onClick = {}
            )
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.bottomSafeSpacer))
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("계정 삭제") },
            text = { Text("계정을 삭제하면 모든 데이터가 영구적으로 삭제되며 복구할 수 없습니다. 정말 삭제하시겠습니까?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        isDeletingAccount = true
                        coroutineScope.launch {
                            val success = onDeleteAccount?.invoke() ?: false
                            isDeletingAccount = false
                        }
                    }
                ) {
                    Text("삭제", color = Color.Red)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirm = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = AppFont.bodyMedium,
        color = Gray400,
        modifier = Modifier.padding(top = AppSpacing.sm, bottom = AppSpacing.sm)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.md)
            .clickable(onClick = onClick),
        shape = AppShapes.md,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val theme = LocalTeamTheme.current
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(AppSpacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppFont.bodyLgMedium,
                    color = Color.White
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = AppFont.body,
                        color = Gray400,
                        modifier = Modifier.padding(top = AppSpacing.xxs)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Gray500,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val teamTheme = LocalTeamTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.md,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) teamTheme.primary else Gray500,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(AppSpacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppFont.bodyLgMedium,
                    color = if (enabled) Color.White else Gray500
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = AppFont.body,
                        color = if (enabled) Gray400 else Gray500,
                        modifier = Modifier.padding(top = AppSpacing.xxs)
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = teamTheme.primary,
                    uncheckedThumbColor = Gray500,
                    uncheckedTrackColor = Gray700,
                    disabledUncheckedThumbColor = Gray500,
                    disabledUncheckedTrackColor = Gray700
                )
            )
        }
    }
}
