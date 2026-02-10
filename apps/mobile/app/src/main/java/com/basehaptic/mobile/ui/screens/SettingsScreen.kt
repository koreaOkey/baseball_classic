package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.*

@Composable
fun SettingsScreen(
    selectedTeam: Team,
    onChangeTeam: (Team) -> Unit,
    purchasedThemes: List<ThemeData>,
    activeTheme: ThemeData?,
    onSelectTheme: (ThemeData?) -> Unit
) {
    val teamTheme = LocalTeamTheme.current
    var showTeamPicker by remember { mutableStateOf(false) }

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
            SettingsSection(title = "계정")
        }

        item {
            SettingsItem(
                icon = Icons.Default.Group,
                title = "응원 팀",
                subtitle = selectedTeam.teamName,
                onClick = { showTeamPicker = !showTeamPicker }
            )
        }

        // 팀 선택 피커
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
                            Team.DOOSAN, Team.LG, Team.KIWOOM, Team.SAMSUNG, Team.LOTTE,
                            Team.SSG, Team.KT, Team.HANWHA, Team.KIA, Team.NC
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
                                color = if (team == selectedTeam) teamTheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TeamLogo(team = team, size = 32.dp)
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
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(title = "알림")
        }

        item {
            var hapticEnabled by remember { mutableStateOf(true) }
            SettingsItemWithSwitch(
                icon = Icons.Default.Vibration,
                title = "햅틱 피드백",
                subtitle = "주요 이벤트 발생 시 진동 알림",
                checked = hapticEnabled,
                onCheckedChange = { hapticEnabled = it }
            )
        }

        item {
            var calendarSync by remember { mutableStateOf(true) }
            SettingsItemWithSwitch(
                icon = Icons.Default.CalendarToday,
                title = "일정 자동 동기화",
                subtitle = "경기 일정을 캘린더에 자동 등록",
                checked = calendarSync,
                onCheckedChange = { calendarSync = it }
            )
        }

        item {
            var highFive by remember { mutableStateOf(true) }
            SettingsItemWithSwitch(
                icon = Icons.Default.Bluetooth,
                title = "원격 하이파이브",
                subtitle = "근처 팬과 득점 순간 공유",
                checked = highFive,
                onCheckedChange = { highFive = it }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(title = "정보")
        }

        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "버전",
                subtitle = "1.0.0",
                onClick = { }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Default.Description,
                title = "오픈소스 라이선스",
                subtitle = "",
                onClick = { }
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
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
            val thm = LocalTeamTheme.current
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = thm.primary,
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
    onCheckedChange: (Boolean) -> Unit
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
                tint = teamTheme.primary,
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
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = teamTheme.primary,
                    uncheckedThumbColor = Gray500,
                    uncheckedTrackColor = Gray700
                )
            )
        }
    }
}

