package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basehaptic.mobile.ui.components.BannerAd
import com.basehaptic.mobile.data.model.EventType
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.theme.AppEventColors
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray600
import com.basehaptic.mobile.ui.theme.Gray700
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Green400
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import com.basehaptic.mobile.ui.theme.Red400
import com.basehaptic.mobile.ui.theme.Yellow400
import com.basehaptic.mobile.wear.WearGameSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SimGameState(
    val homeTeam: String = "KIA",
    val awayTeam: String = "SSG",
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val inning: String = "1회초",
    val ball: Int = 0,
    val strike: Int = 0,
    val out: Int = 0,
    val baseFirst: Boolean = false,
    val baseSecond: Boolean = false,
    val baseThird: Boolean = false,
    val pitcher: String = "김광현",
    val batter: String = "추신수"
)

data class SimEvent(
    val eventType: EventType,
    val description: String,
    val stateUpdate: (SimGameState) -> SimGameState,
    val delayMs: Long = 2000L
)

// 10분+ 시뮬레이션 시나리오 (백그라운드 데이터 전송 테스트용)
// 이벤트 간 간격: 기본 5초, 주요 이벤트 7초
private val simulationScenario = listOf(
    // --- 1회초 (SSG 공격) ---
    SimEvent(EventType.BALL, "1회초 초구 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 2) }, 5000L),
    SimEvent(EventType.HIT, "추신수 좌전 안타", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "김재환") }, 7000L),
    SimEvent(EventType.STRIKE, "김재환에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "연속 스트라이크", { it.copy(strike = 2) }, 5000L),
    SimEvent(EventType.HOMERUN, "김재환 투런 홈런", { it.copy(homeScore = it.homeScore + 2, ball = 0, strike = 0, baseFirst = false, batter = "최정") }, 7000L),
    SimEvent(EventType.BALL, "최정에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "최정 뜬공 아웃", { it.copy(ball = 0, strike = 0, out = 1, batter = "최지훈") }, 5000L),
    SimEvent(EventType.STRIKE, "최지훈에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.HIT, "최지훈 중전 안타", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "박성한") }, 7000L),
    SimEvent(EventType.OUT, "박성한 1루 땅볼 아웃", { it.copy(ball = 0, strike = 0, out = 2, batter = "이재원") }, 5000L),
    SimEvent(EventType.DOUBLE_PLAY, "이재원 병살타", { it.copy(ball = 0, strike = 0, out = 0, baseFirst = false, baseSecond = false, baseThird = false, inning = "1회말", pitcher = "김건우", batter = "나성범") }, 7000L),
    // --- 1회말 (KIA 공격) ---
    SimEvent(EventType.STRIKE, "나성범에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.HIT, "나성범 우전 안타", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "김선빈") }, 7000L),
    SimEvent(EventType.HOMERUN, "김선빈 역전 투런 홈런", { it.copy(awayScore = it.awayScore + 2, ball = 0, strike = 0, baseFirst = false, batter = "최형우") }, 7000L),
    SimEvent(EventType.BALL, "최형우에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "최형우 삼진 아웃", { it.copy(ball = 0, strike = 0, out = 1, batter = "이창진") }, 5000L),
    SimEvent(EventType.BALL, "이창진에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 2) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "이창진 땅볼 아웃", { it.copy(ball = 0, strike = 0, out = 2, batter = "류지혁") }, 5000L),
    SimEvent(EventType.STRIKE, "류지혁에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "연속 스트라이크", { it.copy(strike = 2) }, 5000L),
    SimEvent(EventType.OUT, "류지혁 삼진", { it.copy(ball = 0, strike = 0, out = 0, inning = "2회초", pitcher = "김광현", batter = "추신수") }, 5000L),
    // --- 2회초 (SSG 공격) ---
    SimEvent(EventType.BALL, "추신수에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 2) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 3) }, 5000L),
    SimEvent(EventType.WALK, "추신수 볼넷", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "김재환") }, 7000L),
    SimEvent(EventType.STRIKE, "김재환에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.HIT, "김재환 좌전 안타", { it.copy(ball = 0, strike = 0, baseFirst = true, baseSecond = true, batter = "최정") }, 7000L),
    SimEvent(EventType.BALL, "최정에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.HIT, "최정 적시타", { it.copy(homeScore = it.homeScore + 1, ball = 0, strike = 0, baseFirst = true, baseSecond = false, baseThird = true, batter = "최지훈") }, 7000L),
    SimEvent(EventType.SCORE, "SSG 1점 추가", { it }, 7000L),
    SimEvent(EventType.BALL, "최지훈에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "연속 스트라이크", { it.copy(strike = 2) }, 5000L),
    SimEvent(EventType.OUT, "최지훈 삼진", { it.copy(ball = 0, strike = 0, out = 1, batter = "박성한") }, 5000L),
    SimEvent(EventType.STRIKE, "박성한에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "박성한 플라이 아웃", { it.copy(ball = 0, strike = 0, out = 2, batter = "이재원") }, 5000L),
    SimEvent(EventType.BALL, "이재원에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "이재원 땅볼 아웃", { it.copy(ball = 0, strike = 0, out = 0, baseFirst = false, baseThird = false, inning = "2회말", pitcher = "김건우", batter = "나성범") }, 5000L),
    // --- 2회말 (KIA 공격) ---
    SimEvent(EventType.STRIKE, "나성범에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.OUT, "나성범 플라이 아웃", { it.copy(ball = 0, strike = 0, out = 1, batter = "김선빈") }, 5000L),
    SimEvent(EventType.BALL, "김선빈에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 2) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.STEAL, "김선빈 도루 시도", { it.copy(baseSecond = true) }, 5000L),
    SimEvent(EventType.HIT, "최형우 우전 안타", { it.copy(ball = 0, strike = 0, baseFirst = true, baseSecond = true, batter = "이창진") }, 7000L),
    SimEvent(EventType.STRIKE, "이창진에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "연속 스트라이크", { it.copy(strike = 2) }, 5000L),
    SimEvent(EventType.OUT, "이창진 삼진", { it.copy(ball = 0, strike = 0, out = 2, batter = "류지혁") }, 5000L),
    SimEvent(EventType.BALL, "류지혁에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.HIT, "류지혁 적시타", { it.copy(awayScore = it.awayScore + 1, ball = 0, strike = 0, baseFirst = true, baseSecond = false, baseThird = false, batter = "박찬호") }, 7000L),
    SimEvent(EventType.SCORE, "KIA 동점", { it }, 7000L),
    SimEvent(EventType.BALL, "박찬호에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "박찬호 땅볼 아웃", { it.copy(ball = 0, strike = 0, out = 0, baseFirst = false, inning = "3회초", pitcher = "김광현", batter = "추신수") }, 5000L),
    // --- 3회초 (SSG 공격) ---
    SimEvent(EventType.STRIKE, "추신수에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 2) }, 5000L),
    SimEvent(EventType.HIT, "추신수 중전 안타", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "김재환") }, 7000L),
    SimEvent(EventType.BALL, "김재환에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 2) }, 5000L),
    SimEvent(EventType.STRIKE, "연속 스트라이크", { it.copy(strike = 2) }, 5000L),
    SimEvent(EventType.HIT, "김재환 2루타", { it.copy(homeScore = it.homeScore + 1, ball = 0, strike = 0, baseFirst = false, baseSecond = true, baseThird = true, batter = "최정") }, 7000L),
    SimEvent(EventType.SCORE, "SSG 역전", { it }, 7000L),
    SimEvent(EventType.BALL, "최정에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 2) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 3) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 4) }, 5000L),
    SimEvent(EventType.WALK, "최정 고의사구", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "최지훈") }, 5000L),
    SimEvent(EventType.STRIKE, "최지훈에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "최지훈 삼진", { it.copy(ball = 0, strike = 0, out = 1, batter = "박성한") }, 5000L),
    SimEvent(EventType.OUT, "박성한 플라이 아웃", { it.copy(out = 2, batter = "이재원") }, 5000L),
    SimEvent(EventType.OUT, "이재원 땅볼 아웃", { it.copy(out = 0, baseFirst = false, baseSecond = false, baseThird = false, inning = "3회말", pitcher = "김건우", batter = "나성범") }, 5000L),
    // --- 3회말 (KIA 공격) ---
    SimEvent(EventType.BALL, "나성범에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.OUT, "나성범 땅볼 아웃", { it.copy(ball = 0, strike = 0, out = 1, batter = "김선빈") }, 5000L),
    SimEvent(EventType.STRIKE, "김선빈에게 스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.OUT, "김선빈 플라이 아웃", { it.copy(ball = 0, strike = 0, out = 2, batter = "최형우") }, 5000L),
    SimEvent(EventType.BALL, "최형우에게 볼", { it.copy(ball = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "스트라이크", { it.copy(strike = 1) }, 5000L),
    SimEvent(EventType.STRIKE, "연속 스트라이크", { it.copy(strike = 2) }, 5000L),
    SimEvent(EventType.OUT, "최형우 삼진", { it.copy(ball = 0, strike = 0, out = 0, inning = "경기 종료") }, 5000L),
    // --- 경기 종료 ---
    SimEvent(EventType.VICTORY, "SSG 승리!", { it }, 7000L)
)

@Composable
fun WatchTestScreen(
    selectedTeam: Team,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val teamTheme = LocalTeamTheme.current
    val scope = rememberCoroutineScope()

    var gameState by remember { mutableStateOf(SimGameState()) }
    var logMessages by remember { mutableStateOf(listOf<String>()) }
    var isSimulating by remember { mutableStateOf(false) }
    var simIndex by remember { mutableIntStateOf(0) }

    fun addLog(msg: String) {
        logMessages = (listOf(msg) + logMessages).take(30)
    }

    fun shouldSendEvent(eventType: String?): Boolean {
        val type = eventType?.uppercase() ?: return true
        if (type == "BALL" || type == "STRIKE") {
            return context.getSharedPreferences("basehaptic_user_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("ball_strike_haptic_enabled", true)
        }
        return true
    }

    fun sendCurrentState(eventType: String?) {
        val filteredEventType = if (shouldSendEvent(eventType)) eventType else null
        WearGameSyncManager.sendGameData(
            context = context,
            gameId = "test_001",
            homeTeam = gameState.homeTeam,
            awayTeam = gameState.awayTeam,
            homeScore = gameState.homeScore,
            awayScore = gameState.awayScore,
            inning = gameState.inning,
            ball = gameState.ball,
            strike = gameState.strike,
            out = gameState.out,
            baseFirst = gameState.baseFirst,
            baseSecond = gameState.baseSecond,
            baseThird = gameState.baseThird,
            pitcher = gameState.pitcher,
            batter = gameState.batter,
            myTeam = selectedTeam.teamName,
            eventType = filteredEventType
        )
    }

    fun sendCheerTest() {
        val team = if (selectedTeam == Team.NONE) Team.DOOSAN else selectedTeam
        val cheerText = "${team.teamName} 팬들, 지금 함께 응원해요!"
        WearGameSyncManager.sendCheerTrigger(
            context = context,
            teamCode = team.name,
            stadiumCode = "TEST",
            cheerText = cheerText,
            primaryColorHex = team.color.toRgbHex(),
            hapticPatternId = "watch_test_v1",
            fireAtUnixMs = System.currentTimeMillis()
        )
        addLog("[CHEER] ${team.teamName} 응원 화면 테스트 전송")
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Gray950)
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
                }
                Text(
                    text = "워치 테스트",
                    style = AppFont.h4Bold,
                    color = Color.White
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Gray950)
                .padding(padding)
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Ad Banner
            item {
                BannerAd(
                    modifier = Modifier.padding(vertical = AppSpacing.sm)
                )
            }

            item {
                Surface(
                    shape = AppShapes.lg,
                    color = Gray900,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(gameState.inning, style = AppFont.bodyBold, color = teamTheme.primary)
                        Spacer(Modifier.height(AppSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(gameState.homeTeam, style = AppFont.bodyLgBold, color = Color.White)
                                Text("${gameState.homeScore}", style = AppFont.h1, color = Color.White)
                            }
                            Text(":", style = AppFont.h2, color = Gray500)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(gameState.awayTeam, style = AppFont.bodyLgBold, color = Color.White)
                                Text("${gameState.awayScore}", style = AppFont.h1, color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                            Text("B ${gameState.ball}", style = AppFont.caption, color = Green400)
                            Text("S ${gameState.strike}", style = AppFont.caption, color = Yellow400)
                            Text("O ${gameState.out}", style = AppFont.caption, color = Red400)
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                        val bases = listOfNotNull(
                            if (gameState.baseFirst) "1루" else null,
                            if (gameState.baseSecond) "2루" else null,
                            if (gameState.baseThird) "3루" else null
                        )
                        Text(
                            if (bases.isEmpty()) "주자 없음" else "주자: ${bases.joinToString(", ")}",
                            style = AppFont.micro,
                            color = Gray400
                        )
                        Text("투수: ${gameState.pitcher}  타자: ${gameState.batter}", style = AppFont.micro, color = Gray400)
                    }
                }
            }

            item {
                Surface(
                    shape = AppShapes.md,
                    color = Gray900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.lg)) {
                        Text("자동 시뮬레이션", style = AppFont.bodyBold, color = Gray300)
                        Spacer(Modifier.height(AppSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            Button(
                                onClick = {
                                    if (!isSimulating) {
                                        isSimulating = true
                                        simIndex = 0
                                        gameState = SimGameState()
                                        logMessages = emptyList()
                                        addLog("자동 시뮬레이션 시작")

                                        scope.launch {
                                            for (i in simulationScenario.indices) {
                                                if (!isSimulating) break
                                                simIndex = i
                                                val event = simulationScenario[i]
                                                gameState = event.stateUpdate(gameState)
                                                addLog("[${event.eventType}] ${event.description}")
                                                sendCurrentState(event.eventType.name)
                                                delay(event.delayMs)
                                            }
                                            isSimulating = false
                                            addLog("자동 시뮬레이션 종료")
                                        }
                                    }
                                },
                                enabled = !isSimulating,
                                colors = ButtonDefaults.buttonColors(containerColor = teamTheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(AppSpacing.xs))
                                Text("시작")
                            }
                            OutlinedButton(
                                onClick = {
                                    isSimulating = false
                                    addLog("자동 시뮬레이션 중단")
                                },
                                enabled = isSimulating
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp), tint = Red400)
                                Spacer(Modifier.width(AppSpacing.xs))
                                Text("중단", color = Color.White)
                            }
                        }
                        if (isSimulating) {
                            Spacer(Modifier.height(AppSpacing.sm))
                            LinearProgressIndicator(
                                progress = { (simIndex + 1).toFloat() / simulationScenario.size },
                                modifier = Modifier.fillMaxWidth(),
                                color = teamTheme.primary,
                                trackColor = Gray700
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = AppShapes.md,
                    color = Gray900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.lg)) {
                        Text("현장 응원 테스트", style = AppFont.bodyBold, color = Gray300)
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            text = "워치에 풀스크린 응원 문구와 팀 컬러, 햅틱을 즉시 전송합니다.",
                            style = AppFont.caption,
                            color = Gray500
                        )
                        Spacer(Modifier.height(AppSpacing.sm))
                        Button(
                            onClick = { sendCheerTest() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AppSpacing.buttonHeight),
                            colors = ButtonDefaults.buttonColors(containerColor = teamTheme.primary),
                            shape = AppShapes.sm
                        ) {
                            Text("응원 화면 테스트", style = AppFont.bodyBold)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = AppShapes.md,
                    color = Gray900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.lg)) {
                        Text("수동 이벤트 전송", style = AppFont.bodyBold, color = Gray300)
                        Spacer(Modifier.height(AppSpacing.sm))

                        // HOMERUN은 팀 테마 primary를 강조색으로 사용, 나머지는 AppEventColors 매핑.
                        val events = listOf(
                            Triple(EventType.HOMERUN, "홈런", teamTheme.primary),
                            Triple(EventType.HIT, "안타", AppEventColors.eventColor("HIT")),
                            Triple(EventType.WALK, "볼넷", AppEventColors.eventColor("WALK")),
                            Triple(EventType.STEAL, "도루", AppEventColors.eventColor("STEAL")),
                            Triple(EventType.SCORE, "득점", AppEventColors.eventColor("SCORE")),
                            Triple(EventType.DOUBLE_PLAY, "병살", AppEventColors.eventColor("DOUBLE_PLAY")),
                            Triple(EventType.TRIPLE_PLAY, "삼중살", AppEventColors.eventColor("TRIPLE_PLAY")),
                            Triple(EventType.OUT, "아웃", AppEventColors.eventColor("OUT")),
                            Triple(EventType.STRIKE, "스트라이크", AppEventColors.eventColor("STRIKE")),
                            Triple(EventType.BALL, "볼", AppEventColors.eventColor("BALL")),
                            Triple(EventType.VICTORY, "승리", AppEventColors.eventColor("VICTORY"))
                        )

                        events.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                row.forEach { (type, label, color) ->
                                    Button(
                                        onClick = {
                                            addLog("[$type] 수동 전송")
                                            sendCurrentState(type.name)
                                            if (shouldSendEvent(type.name)) {
                                                WearGameSyncManager.sendHapticEvent(context, type.name)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(AppSpacing.buttonHeight),
                                        colors = ButtonDefaults.buttonColors(containerColor = color),
                                        shape = AppShapes.sm
                                    ) {
                                        Text(label, style = AppFont.bodyBold)
                                    }
                                }
                                if (row.size < 2) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(AppSpacing.sm))
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = AppShapes.md,
                    color = Gray900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("전송 로그", style = AppFont.bodyBold, color = Gray300)
                            TextButton(onClick = { logMessages = emptyList() }) {
                                Text("지우기", style = AppFont.micro, color = Gray500)
                            }
                        }
                        if (logMessages.isEmpty()) {
                            Text(
                                "이벤트를 전송하면 여기에 표시됩니다.",
                                style = AppFont.caption,
                                color = Gray600,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.lg)
                            )
                        } else {
                            logMessages.forEach { msg ->
                                Text(msg, style = AppFont.caption, color = Gray400, modifier = Modifier.padding(vertical = AppSpacing.xxs))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(AppSpacing.bottomSafeSpacer)) }
        }
    }
}

private fun Color.toRgbHex(): String {
    return "#%06X".format(toArgb() and 0x00FFFFFF)
}
