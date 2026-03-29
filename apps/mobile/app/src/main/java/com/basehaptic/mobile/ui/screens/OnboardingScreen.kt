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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.auth.AuthState
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.Blue600
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950

@Composable
fun OnboardingScreen(
    onComplete: (Team) -> Unit,
    authState: AuthState = AuthState.LoggedOut,
    onSignInWithKakao: () -> Unit = {},
) {
    var selectedTeam by remember { mutableStateOf(Team.NONE) }
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                1 -> {
                    Text(
                        text = "⚾",
                        fontSize = 64.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "야구봄",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "응원 팀을 선택하고 워치로 실시간 중계를 확인하세요.",
                        fontSize = 14.sp,
                        color = Gray400,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Gray900.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "응원하는 팀을 선택하세요",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(teams) { team ->
                                    TeamSelectionItem(
                                        team = team,
                                        isSelected = selectedTeam == team,
                                        onClick = { selectedTeam = team }
                                    )
                                }
                            }

                            Button(
                                onClick = { step = 2 },
                                enabled = selectedTeam != Team.NONE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue600,
                                    disabledContainerColor = Gray800
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "계속하기",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Gray900.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "기능 설명",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            FeatureCard(
                                emoji = "⌚",
                                title = "워치로 라이브 경기 보기",
                                description = "득점, 홈런 등 주요 이벤트 발생 시 스마트워치로 진동 알림을 보냅니다."
                            )

                            // 경기 일정 자동 동기화 - 추후 공개
                            // FeatureCard(
                            //     emoji = "🗓",
                            //     title = "경기 일정 자동 동기화",
                            //     description = "응원 팀 경기 일정을 캘린더에 자동으로 등록합니다.",
                            //     modifier = Modifier.padding(top = 16.dp)
                            // )

                            Button(
                                onClick = { step = 3 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "계속하기",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                3 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Gray900.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "로그인",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = "로그인하면 데이터를 안전하게 저장하고\n다른 기기에서도 이용할 수 있어요.",
                                fontSize = 14.sp,
                                color = Gray400,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            when (authState) {
                                is AuthState.LoggedIn -> {
                                    Text(
                                        text = "로그인 완료!",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    Button(
                                        onClick = { onComplete(selectedTeam) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "시작하기",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                else -> {
                                    // 카카오 로그인 버튼
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

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 건너뛰기 버튼
                                    TextButton(
                                        onClick = { onComplete(selectedTeam) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "건너뛰기",
                                            fontSize = 14.sp,
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) team.color else Gray800.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamLogo(team = team, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = team.teamName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
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
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Gray800.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = Gray400,
                lineHeight = 20.sp
            )
        }
    }
}
