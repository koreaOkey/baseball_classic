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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.auth.AuthState
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray700
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.LocalTeamTheme

@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    selectedTeam: Team,
    onChangeTeam: (Team) -> Unit,
    purchasedThemes: List<ThemeData>,
    activeTheme: ThemeData?,
    onSelectTheme: (ThemeData?) -> Unit,
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "설정",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
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
                    shape = RoundedCornerShape(12.dp),
                    color = Gray800,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onChangeTeam(team)
                                        showTeamPicker = false
                                    },
                                color = if (team == selectedTeam) {
                                    teamTheme.primary.copy(alpha = 0.2f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TeamLogo(team = team, size = 56.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = team.teamName,
                                        fontSize = 15.sp,
                                        color = if (team == selectedTeam) Color.White else Gray300,
                                        fontWeight = if (team == selectedTeam) FontWeight.Bold else FontWeight.Normal
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
                        shape = RoundedCornerShape(12.dp),
                        color = Gray900,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "로그인됨",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = authState.email ?: "카카오 계정",
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSignOut() },
                                shape = RoundedCornerShape(8.dp),
                                color = Gray800
                            ) {
                                Text(
                                    text = "로그아웃",
                                    fontSize = 14.sp,
                                    color = Gray400,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isDeletingAccount) { showDeleteConfirm = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Gray800
                            ) {
                                if (isDeletingAccount) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .size(20.dp),
                                        color = Color.Red,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "계정 삭제",
                                        fontSize = 14.sp,
                                        color = Color.Red,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSignInWithKakao() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEE500)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCAC",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "카카오로 로그인",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191919)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(title = "알림")
        }

        item {
            var hapticEnabled by remember { mutableStateOf(true) }
            SettingsItemWithSwitch(
                icon = Icons.Default.Vibration,
                title = "경기 라이브 알림",
                subtitle = "실시간 경기 내용을 워치로 알림 받기",
                checked = hapticEnabled,
                onCheckedChange = { hapticEnabled = it }
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

        // 원격 하이파이브 - 추후 공개
        // item {
        //     var highFive by remember { mutableStateOf(true) }
        //     SettingsItemWithSwitch(
        //         icon = Icons.Default.Bluetooth,
        //         title = "원격 하이파이브",
        //         subtitle = "친구와 득점 순간을 함께 공유",
        //         checked = highFive,
        //         onCheckedChange = { highFive = it }
        //     )
        // }

        // 개발자 섹션 - 배포 시 숨김
        // item {
        //     Spacer(modifier = Modifier.height(16.dp))
        //     SettingsSection(title = "개발자")
        // }
        // item {
        //     SettingsItem(
        //         icon = Icons.Default.Watch,
        //         title = "워치 테스트",
        //         subtitle = "시뮬레이션 이벤트로 워치 동기화 테스트",
        //         onClick = onOpenWatchTest
        //     )
        // }

        item {
            Spacer(modifier = Modifier.height(16.dp))
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
            Spacer(modifier = Modifier.height(80.dp))
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
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Gray400,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val theme = LocalTeamTheme.current
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = Gray400,
                        modifier = Modifier.padding(top = 2.dp)
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
        shape = RoundedCornerShape(12.dp),
        color = Gray900,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) teamTheme.primary else Gray500,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) Color.White else Gray500
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = if (enabled) Gray400 else Gray500,
                        modifier = Modifier.padding(top = 2.dp)
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
