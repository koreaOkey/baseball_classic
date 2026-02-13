package com.basehaptic.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.data.model.EventType
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.ui.theme.*
import com.basehaptic.mobile.wear.WearGameSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SimGameState(
    val homeTeam: String = "SSG",
    val awayTeam: String = "KIA",
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val inning: String = "1회초",
    val ball: Int = 0,
    val strike: Int = 0,
    val out: Int = 0,
    val baseFirst: Boolean = false,
    val baseSecond: Boolean = false,
    val baseThird: Boolean = false,
    val pitcher: String = "양현종",
    val batter: String = "추신수"
)

data class SimEvent(
    val eventType: EventType,
    val description: String,
    val stateUpdate: (SimGameState) -> SimGameState,
    val delayMs: Long = 2000L
)

private val simulationScenario = listOf(
    SimEvent(EventType.BALL, "1회초 — 볼 원", { it.copy(ball = 1) }),
    SimEvent(EventType.STRIKE, "스트라이크!", { it.copy(strike = 1) }),
    SimEvent(EventType.BALL, "볼 투", { it.copy(ball = 2) }),
    SimEvent(EventType.HIT, "추신수, 좌전 안타!", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "김현수") }),
    SimEvent(EventType.STRIKE, "김현수에게 스트라이크", { it.copy(strike = 1) }),
    SimEvent(EventType.STRIKE, "연속 스트라이크!", { it.copy(strike = 2) }),
    SimEvent(EventType.HOMERUN, "김현수! 투런 홈런!!", {
        it.copy(
            homeScore = it.homeScore + 2, ball = 0, strike = 0,
            baseFirst = false, batter = "최정"
        )
    }, delayMs = 3000L),
    SimEvent(EventType.SCORE, "SSG 2점 리드!", { it }),
    SimEvent(EventType.BALL, "최정에게 볼", { it.copy(ball = 1) }),
    SimEvent(EventType.OUT, "최정, 플라이 아웃", { it.copy(ball = 0, strike = 0, out = 1, batter = "한유섬") }),
    SimEvent(EventType.STRIKE, "한유섬에게 스트라이크", { it.copy(strike = 1) }),
    SimEvent(EventType.HIT, "한유섬, 중전 안타!", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "박성한") }),
    SimEvent(EventType.OUT, "박성한, 삼진 아웃", { it.copy(ball = 0, strike = 0, out = 2, batter = "이재원") }),
    SimEvent(EventType.OUT, "이재원, 땅볼 아웃 — 체인지!", {
        it.copy(
            ball = 0, strike = 0, out = 0,
            baseFirst = false, baseSecond = false, baseThird = false,
            inning = "1회말", pitcher = "김광현", batter = "나성범"
        )
    }),
    SimEvent(EventType.STRIKE, "나성범에게 스트라이크", { it.copy(strike = 1) }),
    SimEvent(EventType.BALL, "볼", { it.copy(ball = 1) }),
    SimEvent(EventType.HIT, "나성범, 우전 안타!", { it.copy(ball = 0, strike = 0, baseFirst = true, batter = "김도영") }),
    SimEvent(EventType.HOMERUN, "김도영!! 역전 투런 홈런!!!", {
        it.copy(
            awayScore = it.awayScore + 2, ball = 0, strike = 0,
            baseFirst = false, batter = "최형우"
        )
    }, delayMs = 3000L),
    SimEvent(EventType.SCORE, "KIA 역전! 2:2 → 2:3!", { it.copy(inning = "1회말") }),
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

    fun sendCurrentState(eventType: String?) {
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
            myTeam = selectedTeam.name,
            eventType = eventType
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Gray950)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = Color.White)
                }
                Text(
                    text = "워치 테스트",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -- Game State Card --
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Gray900,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(gameState.inning, fontSize = 14.sp, color = teamTheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(gameState.homeTeam, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${gameState.homeScore}", fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text(":", fontSize = 28.sp, color = Gray500)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(gameState.awayTeam, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${gameState.awayScore}", fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("B ${gameState.ball}", fontSize = 13.sp, color = Green400)
                            Text("S ${gameState.strike}", fontSize = 13.sp, color = Yellow400)
                            Text("O ${gameState.out}", fontSize = 13.sp, color = Red400)
                        }
                        Spacer(Modifier.height(4.dp))
                        val bases = listOfNotNull(
                            if (gameState.baseFirst) "1루" else null,
                            if (gameState.baseSecond) "2루" else null,
                            if (gameState.baseThird) "3루" else null
                        )
                        Text(
                            if (bases.isEmpty()) "주자 없음" else "주자: ${bases.joinToString(", ")}",
                            fontSize = 12.sp, color = Gray400
                        )
                        Text("투: ${gameState.pitcher}  타: ${gameState.batter}", fontSize = 12.sp, color = Gray400)
                    }
                }
            }

            // -- Auto Simulation --
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Gray900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("자동 시뮬레이션", fontSize = 14.sp, color = Gray300, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (!isSimulating) {
                                        isSimulating = true
                                        simIndex = 0
                                        gameState = SimGameState()
                                        logMessages = emptyList()
                                        addLog("▶ 시뮬레이션 시작")

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
                                            addLog("■ 시뮬레이션 종료")
                                        }
                                    }
                                },
                                enabled = !isSimulating,
                                colors = ButtonDefaults.buttonColors(containerColor = teamTheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("시작")
                            }
                            OutlinedButton(
                                onClick = {
                                    isSimulating = false
                                    addLog("■ 시뮬레이션 중단")
                                },
                                enabled = isSimulating
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp), tint = Red400)
                                Spacer(Modifier.width(4.dp))
                                Text("중단", color = Color.White)
                            }
                        }
                        if (isSimulating) {
                            Spacer(Modifier.height(8.dp))
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

            // -- Manual Event Buttons --
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Gray900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("수동 이벤트 전송", fontSize = 14.sp, color = Gray300, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        val events = listOf(
                            Triple(EventType.HOMERUN, "홈런", teamTheme.primary),
                            Triple(EventType.HIT, "안타", Green500),
                            Triple(EventType.SCORE, "득점", Yellow500),
                            Triple(EventType.OUT, "아웃", Red500),
                            Triple(EventType.STRIKE, "스트라이크", Orange500),
                            Triple(EventType.BALL, "볼", Blue500),
                        )

                        // 2 columns
                        events.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { (type, label, color) ->
                                    Button(
                                        onClick = {
                                            addLog("[${type}] 수동 전송")
                                            WearGameSyncManager.sendHapticEvent(context, type.name)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = color),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                // fill empty space if odd count
                                if (row.size < 2) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // -- Log --
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Gray900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("전송 로그", fontSize = 14.sp, color = Gray300, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { logMessages = emptyList() }) {
                                Text("지우기", fontSize = 12.sp, color = Gray500)
                            }
                        }
                        if (logMessages.isEmpty()) {
                            Text(
                                "이벤트를 전송하면 여기에 표시됩니다",
                                fontSize = 13.sp, color = Gray600,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            )
                        } else {
                            logMessages.forEach { msg ->
                                Text(msg, fontSize = 13.sp, color = Gray400, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
