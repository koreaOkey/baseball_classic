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
 */
object WatchGamePoller {

    private const val TAG = "WatchGamePoller"
    private const val TIMEOUT_MS = 10_000

    private val scope = CoroutineScope(Dispatchers.Default)
    private var pollingJob: Job? = null
    private val promptedGameIds = mutableSetOf<String>()
    private val kst = TimeZone.getTimeZone("Asia/Seoul")

    fun startPolling(context: Context, myTeam: String) {
        stopPolling()
        if (myTeam.isBlank() || myTeam == "DEFAULT") return
        promptedGameIds.clear()

        pollingJob = scope.launch {
            // 1) 경기 목록 조회
            val games = fetchTodayGames()
            val myGames = games.filter { isMyTeamGame(it, myTeam) }

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

            // 예정된 경기 중 가장 빠른 시작 시간
            val earliestStart = myGames.mapNotNull { parseStartTime(it) }.minOrNull()

            if (earliestStart != null) {
                val waitUntil = earliestStart - 5 * 60 * 1000 // 5분 전
                val waitMs = waitUntil - System.currentTimeMillis()
                if (waitMs > 0) {
                    Log.d(TAG, "내 팀 경기 시작 ${waitMs / 1000}초 후 폴링 시작")
                    delay(waitMs)
                }
            } else if (myGames.isEmpty()) {
                Log.d(TAG, "오늘 내 팀 경기 없음, 폴링 중단")
                return@launch
            }

            // 2) 30초 간격 폴링
            Log.d(TAG, "폴링 시작")
            while (true) {
                pollOnce(context, myTeam)
                delay(30_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollOnce(context: Context, myTeam: String) {
        val games = fetchTodayGames()
        for (game in games) {
            val gameId = game["id"] ?: ""
            if (gameId.isBlank()) continue
            if (!isMyTeamGame(game, myTeam)) continue

            val status = (game["status"] ?: "").uppercase()
            if (status != "LIVE" && status != "IN_PROGRESS") continue
            if (!promptedGameIds.add(gameId)) continue

            showPrompt(context, gameId, game["homeTeam"] ?: "", game["awayTeam"] ?: "")
        }
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

        context.sendBroadcast(Intent(DataLayerListenerService.ACTION_WATCH_SYNC_PROMPT))
        Log.d(TAG, "LIVE 경기 감지 → 팝업: $awayTeam vs $homeTeam")
    }

    // MARK: - Network

    private suspend fun fetchTodayGames(): List<Map<String, String>> = withContext(Dispatchers.IO) {
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
                return@withContext emptyList()
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
            emptyList()
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
