package com.basehaptic.watch

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 워치에서 독립적으로 백엔드를 폴링하여 내 팀 경기가 LIVE가 되면 팝업 표시.
 * 1) 앱 시작 시 경기 목록 조회 → 내 팀 경기 시작 시간 파악
 * 2) 시작 5분 전부터 30초 간격 폴링
 * 3) 이미 LIVE인 경기가 있으면 즉시 팝업
 *
 * 종료 조건 (무한 폴링 방지):
 * - 오늘 내 팀 경기가 모두 FINISHED/CANCELED 이면 중단 (다음 날 / 앱 포그라운드 복귀 시 재무장)
 * - KST 날짜가 바뀌면 중단
 * - 조회 실패 시 30초 고정 재시도 대신 지수 백오프
 * - MainActivity onStop / 라이브 동기화 세션 시작 시 stopPolling() 호출
 */
object WatchGamePoller {

    private const val TAG = "WatchGamePoller"
    private const val TIMEOUT_MS = 10_000
    private const val POLL_INTERVAL_MS = 30_000L

    // 조회 실패 시 백오프 (30초 → 1분 → 2분 → 5분 유지)
    private val failureBackoffMs = listOf(30_000L, 60_000L, 120_000L, 300_000L)

    private val finalStatuses = setOf("FINISHED", "CANCELED", "CANCELLED")

    private val scope = CoroutineScope(Dispatchers.Default)
    private var pollingJob: Job? = null
    private val promptedGameIds = mutableSetOf<String>()
    private var promptedDate: String = ""
    private val kst = TimeZone.getTimeZone("Asia/Seoul")

    fun startPolling(context: Context, myTeam: String) {
        stopPolling()
        if (myTeam.isBlank() || myTeam == "DEFAULT") return
        // 같은 날 재무장 시 이미 안내한 경기 팝업이 반복되지 않도록 날짜가 바뀔 때만 초기화
        val today = todayDateString()
        if (promptedDate != today) {
            promptedGameIds.clear()
            promptedDate = today
        }

        pollingJob = scope.launch {
            val startDate = todayDateString()

            // 1) 경기 목록 조회
            val games = fetchTodayGames()
            val myGames = games.orEmpty().filter { isMyTeamGame(it, myTeam) }

            // 이미 LIVE인 경기가 있으면 즉시 처리
            for (game in myGames) {
                val status = (game["status"] ?: "").uppercase()
                if (status == "LIVE" || status == "IN_PROGRESS") {
                    val gameId = game["id"] ?: ""
                    if (gameId.isNotBlank() && promptedGameIds.add(gameId)) {
                        showPrompt(context, gameId, game["homeTeam"] ?: "", game["awayTeam"] ?: "")
                        return@launch // LIVE 경기 발견 → 폴링 불필요
                    }
                }
            }

            // 오늘 내 팀 경기가 이미 전부 종료/취소 → 폴링 불필요
            if (games != null && myGames.isNotEmpty() && allGamesFinal(myGames)) {
                Log.d(TAG, "오늘 내 팀 경기 모두 종료/취소, 폴링 중단")
                return@launch
            }

            // 예정된 경기 중 가장 빠른 시작 시간
            val earliestStart = myGames.mapNotNull { parseStartTime(it) }.minOrNull()

            if (earliestStart != null) {
                val waitUntil = earliestStart - 5 * 60 * 1000 // 5분 전
                val waitMs = waitUntil - System.currentTimeMillis()
                if (waitMs > 0) {
                    Log.d(TAG, "내 팀 경기 시작 ${waitMs / 1000}초 후 폴링 시작")
                    delay(waitMs)
                }
            } else if (games != null && myGames.isEmpty()) {
                Log.d(TAG, "오늘 내 팀 경기 없음, 폴링 중단")
                return@launch
            }

            // 2) 30초 간격 폴링 (실패 시 백오프, 종료 조건 충족 시 중단)
            Log.d(TAG, "폴링 시작")
            var failureStreak = 0
            while (true) {
                if (todayDateString() != startDate) {
                    Log.d(TAG, "날짜 변경, 폴링 중단 (앱 재진입 시 재무장)")
                    break
                }

                when (pollOnce(context, myTeam)) {
                    PollResult.FETCH_FAILED -> {
                        val backoff = failureBackoffMs[failureStreak.coerceAtMost(failureBackoffMs.lastIndex)]
                        failureStreak += 1
                        Log.d(TAG, "조회 실패, ${backoff / 1000}초 후 재시도")
                        delay(backoff)
                        continue
                    }
                    PollResult.ALL_FINAL -> {
                        Log.d(TAG, "오늘 내 팀 경기 모두 종료/취소, 폴링 중단")
                        break
                    }
                    PollResult.CONTINUE -> {
                        failureStreak = 0
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private enum class PollResult { CONTINUE, ALL_FINAL, FETCH_FAILED }

    private fun allGamesFinal(myGames: List<Map<String, String>>): Boolean {
        return myGames.all { (it["status"] ?: "").uppercase() in finalStatuses }
    }

    private suspend fun pollOnce(context: Context, myTeam: String): PollResult {
        val games = fetchTodayGames() ?: return PollResult.FETCH_FAILED
        val myGames = games.filter { isMyTeamGame(it, myTeam) }
        for (game in myGames) {
            val gameId = game["id"] ?: ""
            if (gameId.isBlank()) continue

            val status = (game["status"] ?: "").uppercase()
            if (status != "LIVE" && status != "IN_PROGRESS") continue
            if (!promptedGameIds.add(gameId)) continue

            showPrompt(context, gameId, game["homeTeam"] ?: "", game["awayTeam"] ?: "")
        }
        if (myGames.isNotEmpty() && allGamesFinal(myGames)) return PollResult.ALL_FINAL
        return PollResult.CONTINUE
    }

    private fun showPrompt(context: Context, gameId: String, homeTeam: String, awayTeam: String) {
        val prefs = context.getSharedPreferences(
            DataLayerListenerService.GAME_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        // 이미 해당 경기 데이터를 수신 중이면 무시
        val currentGameId = prefs.getString("game_id", "") ?: ""
        val isLive = prefs.getBoolean("is_live", false)
        if (currentGameId == gameId && isLive) return

        // 이미 같은 경기 팝업이 떠있으면 무시
        val existingPromptId = prefs.getString(DataLayerListenerService.KEY_PENDING_SYNC_GAME_ID, "") ?: ""
        if (existingPromptId == gameId) return

        prefs.edit()
            .putString(DataLayerListenerService.KEY_PENDING_SYNC_GAME_ID, gameId)
            .putString(DataLayerListenerService.KEY_PENDING_SYNC_HOME_TEAM, homeTeam)
            .putString(DataLayerListenerService.KEY_PENDING_SYNC_AWAY_TEAM, awayTeam)
            .apply()

        // 내부 브로드캐스트 — 같은 앱 패키지로만 전달
        context.sendBroadcast(
            Intent(DataLayerListenerService.ACTION_WATCH_SYNC_PROMPT).setPackage(context.packageName)
        )
        Log.d(TAG, "LIVE 경기 감지 → 팝업: $awayTeam vs $homeTeam")
    }

    // MARK: - Network

    /** 성공 시 경기 목록, 실패 시 null (호출부에서 백오프 판단) */
    private suspend fun fetchTodayGames(): List<Map<String, String>>? = withContext(Dispatchers.IO) {
        try {
            val dateStr = todayDateString()
            val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
            val url = URL("$baseUrl/games?date=$dateStr&limit=100")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                connection.disconnect()
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val array = JSONArray(body)
            val result = mutableListOf<Map<String, String>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    mapOf(
                        "id" to obj.optString("id", ""),
                        "homeTeam" to obj.optString("homeTeam", ""),
                        "awayTeam" to obj.optString("awayTeam", ""),
                        "status" to obj.optString("status", ""),
                        "startTime" to obj.optString("startTime", ""),
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "경기 목록 조회 실패: ${e.message}")
            null
        }
    }

    // MARK: - Helpers

    private fun isMyTeamGame(game: Map<String, String>, myTeam: String): Boolean {
        val home = game["homeTeam"] ?: ""
        val away = game["awayTeam"] ?: ""
        return normalizeTeamName(home) == normalizeTeamName(myTeam)
                || normalizeTeamName(away) == normalizeTeamName(myTeam)
                || home.contains(myTeam) || away.contains(myTeam)
                || myTeam.contains(home) || myTeam.contains(away)
    }

    /** "14:00" → 오늘 KST 기준 epoch millis */
    private fun parseStartTime(game: Map<String, String>): Long? {
        val timeStr = game["startTime"] ?: return null
        if (timeStr.isBlank()) return null
        val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return null

        val cal = Calendar.getInstance(kst).apply {
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun todayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = kst
        return sdf.format(System.currentTimeMillis())
    }

    private fun normalizeTeamName(name: String): String {
        return name.split(" ").firstOrNull()?.uppercase() ?: name.uppercase()
    }
}
