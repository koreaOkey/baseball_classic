package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.auth.AuthState
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.TeamDisplayNameStyle
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Blue600
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950

@Composable
fun OnboardingScreen(
    onComplete: (Team, TeamDisplayNameStyle) -> Unit,
    initialSelectedTeam: Team = Team.NONE,
    initialDisplayNameStyle: TeamDisplayNameStyle = TeamDisplayNameStyle.TEAM,
    authState: AuthState = AuthState.LoggedOut,
    onSignInWithKakao: () -> Unit = {},
) {
    var selectedTeam by remember(initialSelectedTeam) { mutableStateOf(initialSelectedTeam) }
    var displayNameStyle by remember(initialDisplayNameStyle) { mutableStateOf(initialDisplayNameStyle) }
    var showDisplayNameDialog by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Reason: 온보딩 전용 3단 그라디언트. 중간색은 디자인 요구사항이라 토큰화하지 않음.
                Brush.verticalGradient(
                    colors = listOf(
                        Gray950,
                        Color(0xFF0F172A),
                        Gray950
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                1 -> {
                    // Reason: 온보딩 아이콘 전용 큰 사이즈 (64sp, display 56보다 크게)
                    Text(
                        text = "⚾",
                        fontSize = 64.sp,
                        modifier = Modifier.padding(bottom = AppSpacing.lg)
                    )

                    Text(
                        text = "야구봄",
                        style = AppFont.h1,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = AppSpacing.sm)
                    )

                    Text(
                        text = "응원 팀을 선택하고 워치로 실시간 중계를 확인하세요.",
                        style = AppFont.body,
                        color = Gray400,
                        modifier = Modifier.padding(bottom = AppSpacing.xxxl)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.lg,
                        color = Gray900.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.xxl)
                        ) {
                            Text(
                                text = "응원하는 팀을 선택하세요",
                                style = AppFont.h4Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = AppSpacing.lg)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                items(teams, key = { it.name }) { team ->
                                    TeamSelectionItem(
                                        team = team,
                                        isSelected = selectedTeam == team,
                                        onClick = { selectedTeam = team }
                                    )
                                }
                            }

                            Button(
                                onClick = { showDisplayNameDialog = true },
                                enabled = selectedTeam != Team.NONE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppSpacing.xxl),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue600,
                                    disabledContainerColor = Gray800
                                ),
                                shape = AppShapes.md
                            ) {
                                Text(
                                    text = "계속하기",
                                    style = AppFont.bodyLgMedium
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.lg,
                        color = Gray900.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.xxl)
                        ) {
                            Text(
                                text = "기능 설명",
                                style = AppFont.h3Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = AppSpacing.xxl)
                            )

                            FeatureCard(
                                emoji = "⌚",
                                title = "워치로 라이브 경기 보기",
                                description = "득점, 홈런 등 주요 이벤트 발생 시 스마트워치로 진동 알림을 보냅니다."
                            )

                            FeatureCard(
                                emoji = "📣",
                                title = "광고 안내",
                                description = "운영비로 인해 부득이하게 광고가 추가되었습니다. 안정적인 운영에 사용하겠습니다.",
                                modifier = Modifier.padding(top = AppSpacing.md)
                            )

                            Button(
                                onClick = { step = 3 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppSpacing.xxl),
                                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                shape = AppShapes.md
                            ) {
                                Text(
                                    text = "계속하기",
                                    style = AppFont.bodyLgMedium
                                )
                            }
                        }
                    }
                }
                3 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.lg,
                        color = Gray900.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "로그인",
                                style = AppFont.h3Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = AppSpacing.sm)
                            )

                            Text(
                                text = "로그인하면 데이터를 안전하게 저장하고\n다른 기기에서도 이용할 수 있어요.",
                                style = AppFont.body,
                                color = Gray400,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = AppSpacing.xxl)
                            )

                            when (authState) {
                                is AuthState.LoggedIn -> {
                                    Text(
                                        text = "로그인 완료!",
                                        style = AppFont.bodyLgMedium,
                                        // Reason: "로그인 완료" 피드백 전용 Material green 톤
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.padding(bottom = AppSpacing.lg)
                                    )

                                    Button(
                                        onClick = { onComplete(selectedTeam, displayNameStyle) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                        shape = AppShapes.md
                                    ) {
                                        Text(
                                            text = "시작하기",
                                            style = AppFont.bodyLgMedium
                                        )
                                    }
                                }
                                else -> {
                                    // 카카오 로그인 버튼
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(AppShapes.md)
                                            .clickable { onSignInWithKakao() },
                                        shape = AppShapes.md,
                                        // Reason: 카카오 브랜드 지정 색 (#FEE500)
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

                                    Spacer(modifier = Modifier.height(AppSpacing.lg))

                                    // 건너뛰기 버튼
                                    TextButton(
                                        onClick = { onComplete(selectedTeam, displayNameStyle) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "건너뛰기",
                                            style = AppFont.body,
                                            color = Gray400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDisplayNameDialog && selectedTeam != Team.NONE) {
            TeamDisplayNameStyleDialog(
                team = selectedTeam,
                selectedStyle = displayNameStyle,
                onStyleSelected = { displayNameStyle = it },
                onConfirm = {
                    showDisplayNameDialog = false
                    step = 2
                },
                onDismiss = {
                    displayNameStyle = TeamDisplayNameStyle.TEAM
                    showDisplayNameDialog = false
                    step = 2
                }
            )
        }
    }
}

@Composable
fun TeamDisplayNameStyleDialog(
    team: Team,
    selectedStyle: TeamDisplayNameStyle,
    onStyleSelected: (TeamDisplayNameStyle) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Gray900,
        title = {
            Text(
                text = "팀 이름 표시 방식",
                style = AppFont.h4Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "선택한 응원팀을 앱과 워치에서 어떻게 표시할까요?",
                    style = AppFont.body,
                    color = Gray400,
                    modifier = Modifier.padding(bottom = AppSpacing.sm)
                )
                TeamDisplayNameOption(
                    team = team,
                    title = team.clubName,
                    subtitle = "팀명으로 보기",
                    selected = selectedStyle == TeamDisplayNameStyle.TEAM,
                    onClick = { onStyleSelected(TeamDisplayNameStyle.TEAM) }
                )
                TeamDisplayNameOption(
                    team = team,
                    title = team.teamName,
                    subtitle = "마스코트명으로 보기",
                    selected = selectedStyle == TeamDisplayNameStyle.MASCOT,
                    onClick = { onStyleSelected(TeamDisplayNameStyle.MASCOT) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                shape = AppShapes.md
            ) {
                Text("적용하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에 설정에서 변경", color = Gray400)
            }
        }
    )
}

@Composable
private fun TeamDisplayNameOption(
    team: Team,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.md)
            .border(
                width = 1.dp,
                color = if (selected) Blue600 else Gray800,
                shape = AppShapes.md
            )
            .background(if (selected) Blue600.copy(alpha = 0.16f) else Gray800.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamLogo(team = team, size = 44.dp)
        Spacer(modifier = Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppFont.bodyLgMedium,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = AppFont.body,
                color = Gray300
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun TeamSelectionItem(
    team: Team,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.md)
            .background(if (isSelected) team.color else Gray800.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamLogo(team = team, size = 72.dp)
        Spacer(modifier = Modifier.width(AppSpacing.md))

        Text(
            text = team.teamName,
            style = AppFont.bodyLgMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun FeatureCard(
    emoji: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(AppShapes.md)
            .border(1.dp, Gray800.copy(alpha = 0.8f), AppShapes.md)
            .padding(AppSpacing.lg)
    ) {
        Text(
            text = emoji,
            style = AppFont.h3,
            modifier = Modifier.padding(end = AppSpacing.md)
        )

        Column {
            Text(
                text = title,
                style = AppFont.bodyLgMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = AppSpacing.xs)
            )
            Text(
                text = description,
                style = AppFont.body,
                color = Gray400,
                // Reason: 설명 텍스트 줄 간격 조정
                lineHeight = 20.sp
            )
        }
    }
}
