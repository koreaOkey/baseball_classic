package com.basehaptic.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.ui.screens.*
import com.basehaptic.mobile.ui.theme.BaseHapticTheme
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Gray900

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // selectedTeam 상태를 여기서 관리하여 테마에 전달
            var selectedTeam by remember { mutableStateOf(Team.DOOSAN) }
            var showOnboarding by remember { mutableStateOf(false) }
            
            BaseHapticTheme(selectedTeam = selectedTeam) {
                BaseHapticApp(
                    selectedTeam = selectedTeam,
                    onTeamChanged = { selectedTeam = it },
                    showOnboarding = showOnboarding,
                    onOnboardingComplete = { team ->
                        selectedTeam = team
                        showOnboarding = false
                    }
                )
            }
        }
    }
}

@Composable
fun BaseHapticApp(
    selectedTeam: Team,
    onTeamChanged: (Team) -> Unit,
    showOnboarding: Boolean,
    onOnboardingComplete: (Team) -> Unit
) {
    var currentView by remember { mutableStateOf<Screen>(Screen.Home) }
    var activeTheme by remember { mutableStateOf<ThemeData?>(null) }
    var selectedGame by remember { mutableStateOf<String?>(null) }
    var purchasedThemes by remember { mutableStateOf<List<ThemeData>>(emptyList()) }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = { team ->
                onOnboardingComplete(team)
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                if (currentView != Screen.LiveGame) {
                    BottomNavigationBar(
                        currentView = currentView,
                        onNavigate = { currentView = it }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentView) {
                    Screen.Home -> HomeScreen(
                        selectedTeam = selectedTeam,
                        activeTheme = activeTheme,
                        onSelectGame = { gameId ->
                            selectedGame = gameId
                            currentView = Screen.LiveGame
                        }
                    )
                    Screen.LiveGame -> LiveGameScreen(
                        selectedTeam = selectedTeam,
                        activeTheme = activeTheme,
                        gameId = selectedGame,
                        onBack = { currentView = Screen.Home }
                    )
                    Screen.Community -> CommunityScreen(
                        selectedTeam = selectedTeam,
                        activeTheme = activeTheme
                    )
                    Screen.Store -> ThemeStoreScreen(
                        selectedTeam = selectedTeam,
                        activeTheme = activeTheme,
                        onApplyTheme = { activeTheme = it },
                        onPurchaseTheme = { theme ->
                            purchasedThemes = purchasedThemes + theme
                        },
                        purchasedThemes = purchasedThemes
                    )
                    Screen.Settings -> SettingsScreen(
                        selectedTeam = selectedTeam,
                        onChangeTeam = { team ->
                            onTeamChanged(team) // 팀 변경 시 테마도 즉시 변경됨!
                        },
                        purchasedThemes = purchasedThemes,
                        activeTheme = activeTheme,
                        onSelectTheme = { activeTheme = it }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentView: Screen,
    onNavigate: (Screen) -> Unit
) {
    val teamTheme = LocalTeamTheme.current
    
    NavigationBar(
        containerColor = Gray900,
        tonalElevation = 8.dp
    ) {
        BottomNavItem(
            icon = Icons.Default.Home,
            label = "홈",
            selected = currentView == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
            activeColor = teamTheme.navIndicator
        )
        
        BottomNavItem(
            icon = Icons.Default.Message,
            label = "커뮤니티",
            selected = currentView == Screen.Community,
            onClick = { onNavigate(Screen.Community) },
            activeColor = teamTheme.navIndicator
        )
        
        BottomNavItem(
            icon = Icons.Default.ShoppingBag,
            label = "상점",
            selected = currentView == Screen.Store,
            onClick = { onNavigate(Screen.Store) },
            activeColor = teamTheme.navIndicator
        )
        
        BottomNavItem(
            icon = Icons.Default.Settings,
            label = "설정",
            selected = currentView == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            activeColor = teamTheme.navIndicator
        )
    }
}

@Composable
fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    activeColor: Color
) {
    NavigationBarItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        },
        label = {
            Text(
                text = label,
                fontSize = 12.sp
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = activeColor,
            selectedTextColor = activeColor,
            unselectedIconColor = Color(0xFF71717A),
            unselectedTextColor = Color(0xFF71717A),
            indicatorColor = activeColor.copy(alpha = 0.1f)
        )
    )
}

sealed class Screen {
    object Home : Screen()
    object LiveGame : Screen()
    object Community : Screen()
    object Store : Screen()
    object Settings : Screen()
}
