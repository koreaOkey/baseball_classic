package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.components.TeamLogo
import com.basehaptic.mobile.ui.theme.*

@Composable
fun OnboardingScreen(
    onComplete: (Team) -> Unit
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
            if (step == 1) {
                // Step 1: Team Selection
                Text(
                    text = "⚾",
                    fontSize = 64.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "BaseHaptic",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "보지 않아도 손목으로 느끼는 현장감",
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

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { step = 2 },
                            enabled = selectedTeam != Team.NONE,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
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
            } else {
                // Step 2: Notification Settings
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Gray900.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "알림 설정",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        FeatureCard(
                            emoji = "📳",
                            title = "햅틱 피드백",
                            description = "득점, 홈런 등 주요 이벤트 발생 시 스마트워치로 진동 알림을 받습니다"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        FeatureCard(
                            emoji = "📅",
                            title = "일정 자동 동기화",
                            description = "응원 팀의 경기 일정을 캘린더에 자동으로 등록합니다"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        FeatureCard(
                            emoji = "🤝",
                            title = "원격 하이파이브",
                            description = "근처의 같은 팀 팬과 득점 순간을 공유합니다 (블루투스 사용)"
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { onComplete(selectedTeam) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue600
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "시작하기",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
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
            .background(
                if (isSelected) team.color else Gray800.copy(alpha = 0.5f)
            )
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
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gray800.copy(alpha = 0.5f))
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

