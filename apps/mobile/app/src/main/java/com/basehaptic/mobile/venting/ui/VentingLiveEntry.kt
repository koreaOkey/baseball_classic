package com.basehaptic.mobile.venting.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Red400
import com.basehaptic.mobile.ui.theme.Red500
import com.basehaptic.mobile.venting.LiveRegretProvider
import com.basehaptic.mobile.venting.VentingFeatureFlag
import com.basehaptic.mobile.venting.VentingGameResult

private const val PREFS_NAME = "basehaptic_user_prefs"
private const val KEY_LOSS_PROMPTED_GAME_IDS = "venting_loss_prompted_game_ids"

/**
 * 경기 상세 화면의 분풀이 라이브 진입 오버레이 (경기 상세 루트 Box 안에 배치).
 *
 * - 왼쪽 하단 플로팅 💢 버튼 — 경기전·라이브·종료, 스코어와 무관하게 언제든지 진입.
 * - 종료 시: 마이팀 패배면 "분풀이로 진입하시겠습니까?" 팝업 (경기당 1회).
 * - 마이팀이 참가하지 않은 경기·피처 플래그 OFF에서는 아무것도 렌더링하지 않는다.
 */
@Composable
fun VentingLiveEntryOverlay(
    state: BackendGamesRepository.LiveGameState?,
    events: List<BackendGamesRepository.LiveEvent>,
    boxscore: BackendGamesRepository.GameBoxscore?,
    modifier: Modifier = Modifier
) {
    val androidContext = LocalContext.current
    if (!VentingFeatureFlag.isEnabled(androidContext)) return
    val gameState = state ?: return

    val myTeam = remember {
        val stored = androidContext.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("selected_team", null)
        stored?.let { Team.fromString(it) } ?: Team.NONE
    }
    if (myTeam == Team.NONE) return
    if (myTeam != gameState.homeTeamId && myTeam != gameState.awayTeamId) return

    var showLossPrompt by remember { mutableStateOf(false) }

    // 종료 감지 → 마이팀 패배면 경기당 1회 팝업 ("다음에"를 눌러도 재노출하지 않는다)
    LaunchedEffect(gameState.status, gameState.gameId) {
        if (gameState.status != GameStatus.FINISHED) return@LaunchedEffect
        val context = LiveRegretProvider.buildContext(gameState, events, boxscore, myTeam)
            ?: return@LaunchedEffect
        if (context.gameResult != VentingGameResult.LOSS) return@LaunchedEffect

        val prefs = androidContext.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prompted = prefs.getStringSet(KEY_LOSS_PROMPTED_GAME_IDS, emptySet()).orEmpty()
        if (gameState.gameId in prompted) return@LaunchedEffect
        prefs.edit()
            .putStringSet(KEY_LOSS_PROMPTED_GAME_IDS, prompted + gameState.gameId)
            .apply()
        showLossPrompt = true
    }

    // 경기전·라이브·종료 상태 구분 없이 상시 노출 — 언제든지 진입 가능 (2026-08-05 사용자 결정)
    VentingFloatingButton(
        modifier = modifier,
        onTap = {
            LiveRegretProvider.buildContext(gameState, events, boxscore, myTeam)
                ?.let { VentingFlowController.open(it, backLabel = "경기", entrySource = "live") }
        }
    )

    if (showLossPrompt) {
        val myLabel = teamDisplayName(myTeam.name)
        AlertDialog(
            onDismissRequest = { showLossPrompt = false },
            containerColor = Gray900,
            title = { Text(text = "오늘은 아쉽게 졌어요 💢", style = AppFont.h5Bold, color = Color.White) },
            text = {
                Text(
                    text = "${myLabel} 패배… 분풀이 모드로 진입하시겠습니까?",
                    style = AppFont.body,
                    color = Gray300
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLossPrompt = false
                    LiveRegretProvider.buildContext(gameState, events, boxscore, myTeam)
                        ?.let { VentingFlowController.open(it, backLabel = "경기", entrySource = "live") }
                }) {
                    Text(text = "분풀이 하러 가기", style = AppFont.bodyMedium, color = Red400)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLossPrompt = false }) {
                    Text(text = "다음에", style = AppFont.bodyMedium, color = Gray300)
                }
            }
        )
    }

}

@Composable
private fun VentingFloatingButton(
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(start = 16.dp, bottom = 28.dp)
            .size(52.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Gray950.copy(alpha = 0.92f))
            .border(1.5.dp, Red500.copy(alpha = 0.6f), CircleShape)
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = "💢", fontSize = 22.sp)
    }
}
