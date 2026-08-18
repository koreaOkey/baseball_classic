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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
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
import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.auth.AuthState
import com.basehaptic.mobile.data.model.EventFilterOption
import com.basehaptic.mobile.data.model.EventNotificationChannel
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.TeamDisplayNameStyle
import com.basehaptic.mobile.push.LiveScoreNotificationManager
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
import com.basehaptic.mobile.ui.theme.controlAccent

private const val SHOW_STADIUM_CHEER_TOGGLE = false

@Composable
fun SettingsScreen(
    selectedTeam: Team,
    teamDisplayNameStyle: TeamDisplayNameStyle = TeamDisplayNameStyle.TEAM,
    onChangeTeam: (Team) -> Unit,
    onChangeTeamDisplayNameStyle: (TeamDisplayNameStyle) -> Unit = {},
    onOpenWatchTest: () -> Unit,
    authState: AuthState = AuthState.LoggedOut,
    onSignInWithKakao: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDeleteAccount: (suspend () -> Boolean)? = null
) {
    val teamTheme = LocalTeamTheme.current
    var showTeamPicker by remember { mutableStateOf(false) }
    var showTeamDisplayNameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var manuallyOpenedReleaseNote by remember { mutableStateOf<com.basehaptic.mobile.data.model.ReleaseNote?>(null) }
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
                subtitle = selectedTeam.displayName(teamDisplayNameStyle),
                onClick = { showTeamPicker = !showTeamPicker }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "팀 이름 표시",
                subtitle = if (teamDisplayNameStyle == TeamDisplayNameStyle.TEAM) {
                    "팀명으로 보기 · ${selectedTeam.displayName(TeamDisplayNameStyle.TEAM)}"
                } else {
                    "마스코트명으로 보기 · ${selectedTeam.displayName(TeamDisplayNameStyle.MASCOT)}"
                },
                onClick = {
                    if (selectedTeam != Team.NONE) showTeamDisplayNameDialog = true
                }
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
                                    teamTheme.controlAccent.copy(alpha = 0.2f)
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
                                        text = team.displayName(teamDisplayNameStyle),
                                        style = if (team == selectedTeam) AppFont.labelBold else AppFont.label,
                                        color = if (team == selectedTeam) Color.White else Gray300
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (team == selectedTeam) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = teamTheme.controlAccent,
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
            SettingsSection(title = "알림 이벤트")
            Text(
                text = "경기 카드에서 watch나 잠금화면 보기를 켠 경기에만 적용됩니다.",
                color = Gray400,
                style = AppFont.body,
                modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xs)
            )
        }

        item {
            EventFilterMatrix()
        }

        // promoted 스타일이 불가능한 기기(API<36)에서는 어차피 이전 카드만 나오므로 선택지를 숨긴다.
        if (LiveScoreNotificationManager.isPromotedStyleSupportedOnDevice()) {
            item {
                Spacer(modifier = Modifier.height(AppSpacing.lg))
                SettingsSection(title = "잠금화면 라이브 스코어")
            }

            item {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val prefs = remember {
                    context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
                }
                var promotedStyleEnabled by remember {
                    mutableStateOf(LiveScoreNotificationManager.isPromotedStyleEnabled(context))
                }
                // 승격이 지원되지만 시스템 "실시간 업데이트"가 꺼져 실제 고정이 안 되는 상태면 안내 배너를 띄운다.
                var promotedBlocked by remember {
                    mutableStateOf(LiveScoreNotificationManager.isPromotedBlockedBySystemSetting(context))
                }
                // 사용자가 설정에서 실시간 업데이트를 켜고 돌아오면 배너가 사라지도록 resume마다 재확인.
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            promotedBlocked =
                                LiveScoreNotificationManager.isPromotedBlockedBySystemSetting(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                fun selectStyle(promoted: Boolean) {
                    promotedStyleEnabled = promoted
                    prefs.edit()
                        .putBoolean(LiveScoreNotificationManager.KEY_PROMOTED_STYLE_ENABLED, promoted)
                        .apply()
                    promotedBlocked =
                        LiveScoreNotificationManager.isPromotedBlockedBySystemSetting(context)
                }
                Column {
                    LiveScoreStyleOption(
                        title = "시스템 카드 (Promoted)",
                        subtitle = "OS 기본 알림 스타일 · 픽셀은 잠금화면 고정 지원",
                        selected = promotedStyleEnabled,
                        onClick = { selectStyle(true) }
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    LiveScoreStyleOption(
                        title = "커스텀 카드 (Ongoing)",
                        subtitle = "팀 로고·득점 하이라이트가 있는 야구봄 카드",
                        selected = !promotedStyleEnabled,
                        onClick = { selectStyle(false) }
                    )
                    if (promotedBlocked && promotedStyleEnabled) {
                        PromotedLiveUpdatesBanner(
                            onClick = { LiveScoreNotificationManager.openLiveUpdatesSettings(context) }
                        )
                    }
                }
            }
        }

        // TODO(stadium-cheer): Android 활성화 시 SHOW_STADIUM_CHEER_TOGGLE=true로 전환해 UI 노출.
        if (SHOW_STADIUM_CHEER_TOGGLE) {
            item {
                val context = LocalContext.current
                val prefs = remember {
                    context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
                }
                var stadiumCheerEnabled by remember {
                    mutableStateOf(prefs.getBoolean("stadium_cheer_enabled", true))
                }
                SettingsItemWithSwitch(
                    icon = Icons.Default.LocationOn,
                    title = "경기장 응원",
                    subtitle = "구장 체크인과 경기 시작 워치 응원을 받기",
                    checked = stadiumCheerEnabled,
                    onCheckedChange = {
                        stadiumCheerEnabled = it
                        prefs.edit().putBoolean("stadium_cheer_enabled", it).apply()
                        com.basehaptic.mobile.wear.WearSettingsSyncManager
                            .syncStadiumCheerEnabledToWatch(context, it)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            SettingsSection(title = "워치 영상")
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
                subtitle = "워치에서 캐릭터 영상 재생",
                checked = eventVideoEnabled,
                onCheckedChange = {
                    eventVideoEnabled = it
                    prefs.edit().putBoolean("event_video_enabled", it).apply()
                    com.basehaptic.mobile.wear.WearSettingsSyncManager.syncEventVideoEnabledToWatch(context, it)
                }
            )
        }

        if (BuildConfig.DEBUG) {
            item {
                Spacer(modifier = Modifier.height(AppSpacing.lg))
                SettingsSection(title = "DEBUG")
            }

            item {
                val context = LocalContext.current
                var ventingModeEnabled by remember {
                    mutableStateOf(com.basehaptic.mobile.venting.VentingFeatureFlag.isEnabled(context))
                }
                SettingsItemWithSwitch(
                    icon = Icons.Default.Build,
                    title = "분풀이 모드",
                    subtitle = "DEBUG 전용 피처",
                    checked = ventingModeEnabled,
                    onCheckedChange = {
                        ventingModeEnabled = it
                        com.basehaptic.mobile.venting.VentingFeatureFlag.setEnabled(context, it)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            SettingsSection(title = "정보")
        }

        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "버전",
                subtitle = BuildConfig.VERSION_NAME,
                onClick = {
                    manuallyOpenedReleaseNote = com.basehaptic.mobile.data.model.ReleaseNotes.notes(BuildConfig.VERSION_NAME)
                        ?: if (BuildConfig.DEBUG) {
                            com.basehaptic.mobile.data.model.ReleaseNotes.latest()
                        } else {
                            null
                        }
                }
            )
        }

        item {
            // 워치 미연결 상태에서도 테스트 화면(잠금화면 알림·이벤트 시뮬레이션)에 들어갈 수 있는 진입점
            SettingsItem(
                icon = Icons.Default.Build,
                title = "테스트 도구",
                subtitle = "개발자용 · 워치/잠금화면 알림 테스트",
                onClick = onOpenWatchTest
            )
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.bottomSafeSpacer))
        }
    }

    manuallyOpenedReleaseNote?.let { note ->
        com.basehaptic.mobile.ui.components.WhatsNewDialog(
            note = note,
            onConfirm = { manuallyOpenedReleaseNote = null }
        )
    }

    if (showTeamDisplayNameDialog && selectedTeam != Team.NONE) {
        var pendingStyle by remember(teamDisplayNameStyle) { mutableStateOf(teamDisplayNameStyle) }
        TeamDisplayNameStyleDialog(
            team = selectedTeam,
            selectedStyle = pendingStyle,
            onStyleSelected = { pendingStyle = it },
            onConfirm = {
                onChangeTeamDisplayNameStyle(pendingStyle)
                showTeamDisplayNameDialog = false
            },
            onDismiss = { showTeamDisplayNameDialog = false }
        )
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

// 잠금화면 라이브 스코어 스타일 선택 행(2택 라디오).
@Composable
private fun LiveScoreStyleOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val teamTheme = LocalTeamTheme.current
    Surface(
        shape = AppShapes.md,
        color = Gray900,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selected) "●" else "○",
                color = if (selected) teamTheme.controlAccent else Gray500,
                style = AppFont.bodyBold
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AppFont.bodyBold, color = if (selected) Gray300 else Gray400)
                Text(subtitle, style = AppFont.caption, color = Gray500)
            }
        }
    }
}

// promoted가 켜져 있지만 시스템 "실시간 업데이트"가 꺼져 잠금화면 고정이 안 될 때 노출하는 안내 배너.
@Composable
private fun PromotedLiveUpdatesBanner(onClick: () -> Unit) {
    val teamTheme = LocalTeamTheme.current
    Spacer(Modifier.height(AppSpacing.sm))
    Surface(
        shape = AppShapes.md,
        color = Gray800,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = teamTheme.controlAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "잠금화면 고정이 꺼져 있어요",
                    style = AppFont.bodyBold,
                    color = Gray300
                )
                Text(
                    "시스템 '실시간 업데이트'를 켜면 promoted 카드가 잠금화면 상단에 고정됩니다.",
                    style = AppFont.caption,
                    color = Gray500
                )
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Text("켜기", style = AppFont.bodyBold, color = teamTheme.controlAccent)
        }
    }
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
                tint = theme.controlAccent,
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
private fun EventFilterMatrix() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.md,
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg)
                    .padding(top = AppSpacing.md, bottom = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "이벤트",
                    style = AppFont.caption,
                    color = Gray500
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Watch",
                    style = AppFont.caption,
                    color = Gray400,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(58.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.md))
                Text(
                    text = "잠금",
                    style = AppFont.caption,
                    color = Gray400,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(58.dp)
                )
            }

            EventFilterOption.all.forEachIndexed { index, option ->
                EventFilterMatrixRow(option = option)
                if (index < EventFilterOption.all.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(start = AppSpacing.lg)
                            .background(Gray800)
                    )
                }
            }
        }
    }
}

@Composable
private fun EventFilterMatrixRow(option: EventFilterOption) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
    }
    var watchEnabled by remember(option.id) {
        mutableStateOf(
            if (prefs.contains(option.storageKey(EventNotificationChannel.WATCH))) {
                prefs.getBoolean(
                    option.storageKey(EventNotificationChannel.WATCH),
                    option.defaultEnabled(EventNotificationChannel.WATCH)
                )
            } else {
                option.defaultEnabled(EventNotificationChannel.WATCH)
            }
        )
    }
    var lockScreenEnabled by remember(option.id) {
        mutableStateOf(
            if (prefs.contains(option.storageKey(EventNotificationChannel.LOCK_SCREEN))) {
                prefs.getBoolean(
                    option.storageKey(EventNotificationChannel.LOCK_SCREEN),
                    option.defaultEnabled(EventNotificationChannel.LOCK_SCREEN)
                )
            } else {
                option.defaultEnabled(EventNotificationChannel.LOCK_SCREEN)
            }
        )
    }
    val teamTheme = LocalTeamTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = teamTheme.controlAccent,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(AppSpacing.md))

        Text(
            text = option.title,
            style = AppFont.bodyLgMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        EventChannelToggleChip(
            checked = watchEnabled,
            onClick = {
                watchEnabled = !watchEnabled
                prefs.edit()
                    .putBoolean(option.storageKey(EventNotificationChannel.WATCH), watchEnabled)
                    .apply()
                com.basehaptic.mobile.wear.WearSettingsSyncManager.syncEventFiltersToWatch(
                    context,
                    EventFilterOption.currentValues(context, EventNotificationChannel.WATCH)
                )
            }
        )

        Spacer(modifier = Modifier.width(AppSpacing.md))

        EventChannelToggleChip(
            checked = lockScreenEnabled,
            onClick = {
                lockScreenEnabled = !lockScreenEnabled
                prefs.edit()
                    .putBoolean(option.storageKey(EventNotificationChannel.LOCK_SCREEN), lockScreenEnabled)
                    .apply()
            }
        )
    }
}

@Composable
private fun EventChannelToggleChip(
    checked: Boolean,
    onClick: () -> Unit
) {
    val teamTheme = LocalTeamTheme.current
    Surface(
        modifier = Modifier
            .width(58.dp)
            .height(32.dp)
            .clip(AppShapes.sm)
            .clickable(onClick = onClick),
        shape = AppShapes.sm,
        color = if (checked) teamTheme.controlAccent else Gray800,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (checked) teamTheme.controlAccent else Gray700
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                style = AppFont.caption,
                color = if (checked) Gray950 else Gray300
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
                tint = if (enabled) teamTheme.controlAccent else Gray500,
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
                    checkedTrackColor = teamTheme.controlAccent,
                    uncheckedThumbColor = Gray500,
                    uncheckedTrackColor = Gray700,
                    disabledUncheckedThumbColor = Gray500,
                    disabledUncheckedTrackColor = Gray700
                )
            )
        }
    }
}
