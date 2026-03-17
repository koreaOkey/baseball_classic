package com.basehaptic.mobile.data

import android.content.Context
import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object BackendGamesRepository {
    private const val TIMEOUT_MS = 5000
    private const val CACHE_PREFS_NAME = "backend_games_cache"
    private const val KEY_TODAY_GAMES_DATE = "today_games_date"
    private const val KEY_TODAY_GAMES_PAYLOAD = "today_games_payload"
    private val hhmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val webSocketClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    data class LiveGameState(
        val gameId: String,
        val homeTeam: String,
        val awayTeam: String,
        val homeTeamId: Team,
        val awayTeamId: Team,
        val homeScore: Int,
        val awayScore: Int,
        val inning: String,
        val status: GameStatus,
        val ball: Int,
        val strike: Int,
        val out: Int,
        val baseFirst: Boolean,
        val baseSecond: Boolean,
        val baseThird: Boolean,
        val pitcher: String,
        val batter: String,
        val lastEventType: String?
    )

    data class LiveEvent(
        val cursor: Long,
        val id: String,
        val type: String,
        val description: String,
        val time: String
    )

    data class LiveEventsPage(
        val items: List<LiveEvent>,
        val nextCursor: Long?
    )

    data class TeamRecordStats(
        val teamId: String,
        val ranking: Int?,
        val wra: Double?,
        val updatedAt: String?
    )

    data class UpcomingGameSchedule(
        val gameDate: LocalDate,
        val game: Game
    )

    sealed interface LiveStreamMessage {
        data object Connected : LiveStreamMessage
        data class State(val state: LiveGameState) : LiveStreamMessage
        data class Events(val items: List<LiveEvent>) : LiveStreamMessage
        data class Pong(val at: String?) : LiveStreamMessage
        data class Error(val throwable: Throwable) : LiveStreamMessage
        data object Closed : LiveStreamMessage
    }

    fun fetchGames(selectedTeam: Team): List<Game>? {
        return fetchGamesByDate(selectedTeam = selectedTeam, targetDate = LocalDate.now())
    }

    fun fetchTodayGamesCached(context: Context, selectedTeam: Team): List<Game>? {
        val today = LocalDate.now().toString()
        val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        val cachedDate = prefs.getString(KEY_TODAY_GAMES_DATE, null)
        val cachedPayload = prefs.getString(KEY_TODAY_GAMES_PAYLOAD, null)

        if (cachedDate == today && !cachedPayload.isNullOrBlank()) {
            val cachedGames = parseGamesPayload(cachedPayload, selectedTeam)
            if (!cachedGames.isNullOrEmpty()) {
                return cachedGames
            }
        }

        val freshPayload = fetchGamesByDateRaw(LocalDate.now())
        if (!freshPayload.isNullOrBlank()) {
            val freshGames = parseGamesPayload(freshPayload, selectedTeam)
            if (freshGames != null) {
                if (freshGames.isNotEmpty()) {
                    prefs.edit()
                        .putString(KEY_TODAY_GAMES_DATE, today)
                        .putString(KEY_TODAY_GAMES_PAYLOAD, freshPayload)
                        .apply()
                }
                return freshGames
            }
        }

        if (!cachedPayload.isNullOrBlank()) {
            return parseGamesPayload(cachedPayload, selectedTeam)
        }

        return null
    }

    fun fetchUpcomingMyTeamGames(
        selectedTeam: Team,
        maxItems: Int = 3,
        daysAhead: Int = 30
    ): List<UpcomingGameSchedule>? {
        if (selectedTeam == Team.NONE) return emptyList()
        val normalizedMaxItems = maxItems.coerceAtLeast(1)
        val normalizedDaysAhead = daysAhead.coerceAtLeast(1)
        val now = LocalDate.now()
        val items = mutableListOf<UpcomingGameSchedule>()

        for (offset in 1..normalizedDaysAhead) {
            val targetDate = now.plusDays(offset.toLong())
            val dayGames = fetchGamesByDate(selectedTeam = selectedTeam, targetDate = targetDate).orEmpty()
            if (dayGames.isEmpty()) continue

            val upcomingMyTeamGames = dayGames
                .asSequence()
                .filter { it.isMyTeam && it.status == GameStatus.SCHEDULED }
                .sortedBy { parseGameTimeToSortKey(it.time) }
                .map { game -> UpcomingGameSchedule(gameDate = targetDate, game = game) }
                .toList()

            for (entry in upcomingMyTeamGames) {
                items.add(entry)
                if (items.size >= normalizedMaxItems) {
                    return items
                }
            }
        }

        return items
    }

    private fun fetchGamesByDate(selectedTeam: Team, targetDate: LocalDate): List<Game>? {
        val payload = fetchGamesByDateRaw(targetDate) ?: return null
        return parseGamesPayload(payload, selectedTeam)
    }

    private fun fetchGamesByDateRaw(targetDate: LocalDate): String? {
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/games?date=${targetDate}&limit=100"
        return getJson(endpoint) { body -> body }
    }

    private fun parseGamesPayload(payload: String, selectedTeam: Team): List<Game>? {
        return runCatching {
            val array = JSONArray(payload)
            val items = ArrayList<Game>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                items.add(item.toGame(selectedTeam))
            }
            items
        }.getOrNull()
    }

    fun fetchGameState(gameId: String): LiveGameState? {
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/games/$gameId/state"
        return getJson(endpoint) { body ->
            JSONObject(body).toLiveGameState()
        }
    }

    fun fetchGameEvents(gameId: String, after: Long, limit: Int = 50): LiveEventsPage? {
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/games/$gameId/events?after=$after&limit=$limit"
        return getJson(endpoint) { body ->
            val root = JSONObject(body)
            val array = root.optJSONArray("items") ?: JSONArray()
            val items = ArrayList<LiveEvent>(array.length())
            for (i in 0 until array.length()) {
                val event = array.optJSONObject(i) ?: continue
                items.add(event.toLiveEvent())
            }
            val nextCursor = if (root.has("nextCursor") && !root.isNull("nextCursor")) {
                root.optLong("nextCursor")
            } else {
                null
            }
            LiveEventsPage(items = items, nextCursor = nextCursor)
        }
    }

    fun streamGame(gameId: String): Flow<LiveStreamMessage> = callbackFlow {
        val endpoint = buildWebSocketEndpoint(gameId)
        val request = Request.Builder()
            .url(endpoint)
            .build()

        var webSocket: WebSocket? = null
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@callbackFlow.trySend(LiveStreamMessage.Connected)
                webSocket.send("ping")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = parseLiveStreamMessage(text) ?: return
                this@callbackFlow.trySend(parsed)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                this@callbackFlow.trySend(LiveStreamMessage.Closed)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@callbackFlow.trySend(LiveStreamMessage.Closed)
                this@callbackFlow.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@callbackFlow.trySend(LiveStreamMessage.Error(t))
                this@callbackFlow.close(t)
            }
        }

        webSocket = webSocketClient.newWebSocket(request, listener)
        val heartbeatJob = launch {
            while (true) {
                delay(15_000)
                webSocket?.send("ping")
            }
        }

        awaitClose {
            heartbeatJob.cancel()
            webSocket?.close(1000, "client closed")
        }
    }

    fun fetchTeamRecord(selectedTeam: Team): TeamRecordStats? {
        val teamId = selectedTeam.toKboTeamId() ?: return null
        val seasonCode = LocalDate.now().year.toString()
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/team-records/$teamId?categoryId=kbo&seasonCode=$seasonCode"
        return getJson(endpoint) { body ->
            JSONObject(body).toTeamRecordStats()
        }
    }

    private fun <T> getJson(endpoint: String, parser: (String) -> T): T? {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }

        val responseCode = runCatching { connection.responseCode }.getOrNull()
        if (responseCode == null || responseCode !in 200..299) {
            connection.disconnect()
            return null
        }

        return runCatching {
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parser(reader.readText())
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun buildWebSocketEndpoint(gameId: String): String {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://")}"
            baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://")}"
            baseUrl.startsWith("ws://") || baseUrl.startsWith("wss://") -> baseUrl
            else -> "ws://$baseUrl"
        }
        return "$wsBase/ws/games/$gameId"
    }

    private fun parseLiveStreamMessage(text: String): LiveStreamMessage? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return when (root.optString("type")) {
            "state" -> {
                val payload = root.optJSONObject("payload") ?: return null
                val state = runCatching { payload.toLiveGameState() }.getOrNull() ?: return null
                LiveStreamMessage.State(state)
            }

            "events" -> {
                val payload = root.optJSONObject("payload") ?: return null
                val items = payload.optJSONArray("items") ?: JSONArray()
                val parsed = ArrayList<LiveEvent>(items.length())
                for (i in 0 until items.length()) {
                    val event = items.optJSONObject(i) ?: continue
                    runCatching { event.toLiveEvent() }.getOrNull()?.let(parsed::add)
                }
                LiveStreamMessage.Events(parsed)
            }

            "pong" -> {
                val at = root.optJSONObject("payload")?.optString("at").orEmpty().ifBlank { null }
                LiveStreamMessage.Pong(at)
            }

            else -> null
        }
    }

    private fun JSONObject.toGame(selectedTeam: Team): Game {
        val homeTeamName = optString("homeTeam")
        val awayTeamName = optString("awayTeam")
        val homeTeamId = teamFromBackend(homeTeamName)
        val awayTeamId = teamFromBackend(awayTeamName)
        val status = statusFromBackend(optString("status"))
        val inning = optString("inning").ifBlank { defaultInningFor(status) }
        val startTime = optString("startTime").ifBlank { null }
            ?: extractTime(inning)
            ?: formatBackendTimeOrNull(optString("observedAt").ifBlank { null })

        return Game(
            id = optString("id"),
            homeTeam = homeTeamName,
            awayTeam = awayTeamName,
            homeTeamId = homeTeamId,
            awayTeamId = awayTeamId,
            homeScore = optInt("homeScore", 0),
            awayScore = optInt("awayScore", 0),
            inning = inning,
            status = status,
            time = startTime,
            isMyTeam = selectedTeam != Team.NONE && (homeTeamId == selectedTeam || awayTeamId == selectedTeam),
            homePitcher = null,
            awayPitcher = null
        )
    }

    private fun JSONObject.toLiveGameState(): LiveGameState {
        val homeTeamName = optString("homeTeam")
        val awayTeamName = optString("awayTeam")
        val bases = optJSONObject("bases") ?: JSONObject()
        val rawStatus = optString("status")
        return LiveGameState(
            gameId = optString("gameId"),
            homeTeam = homeTeamName,
            awayTeam = awayTeamName,
            homeTeamId = teamFromBackend(homeTeamName),
            awayTeamId = teamFromBackend(awayTeamName),
            homeScore = optInt("homeScore", 0),
            awayScore = optInt("awayScore", 0),
            inning = optString("inning").ifBlank { defaultInningFor(statusFromBackend(rawStatus)) },
            status = statusFromBackend(rawStatus),
            ball = optInt("ball", 0),
            strike = optInt("strike", 0),
            out = optInt("out", 0),
            baseFirst = bases.optBoolean("first", false),
            baseSecond = bases.optBoolean("second", false),
            baseThird = bases.optBoolean("third", false),
            pitcher = optString("pitcher"),
            batter = optString("batter"),
            lastEventType = optString("lastEventType").ifBlank { null }
        )
    }

    private fun JSONObject.toLiveEvent(): LiveEvent {
        val cursor = optLong("cursor", 0L)
        return LiveEvent(
            cursor = cursor,
            id = optString("id").ifBlank { cursor.toString() },
            type = optString("type").ifBlank { "OTHER" },
            description = optString("description"),
            time = formatBackendTime(optString("time"))
        )
    }

    private fun JSONObject.toTeamRecordStats(): TeamRecordStats {
        return TeamRecordStats(
            teamId = optString("teamId"),
            ranking = optNullableInt("ranking"),
            wra = optNullableDouble("wra"),
            updatedAt = optString("updatedAt").ifBlank { null }
        )
    }

    private fun statusFromBackend(raw: String): GameStatus {
        return when (raw.uppercase()) {
            "LIVE" -> GameStatus.LIVE
            "FINISHED" -> GameStatus.FINISHED
            else -> GameStatus.SCHEDULED
        }
    }

    private fun defaultInningFor(status: GameStatus): String {
        return when (status) {
            GameStatus.LIVE -> "LIVE"
            GameStatus.FINISHED -> "FINAL"
            GameStatus.SCHEDULED -> "SCHEDULED"
        }
    }

    private fun extractTime(value: String): String? {
        return if (Regex("^\\d{2}:\\d{2}$").matches(value)) value else null
    }

    private fun formatBackendTimeOrNull(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val formatted = formatBackendTime(raw)
        return if (formatted == "--:--") null else formatted
    }

    private fun formatBackendTime(raw: String): String {
        if (raw.isBlank()) return "--:--"
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalTime()
                .format(hhmmFormatter)
        }.getOrElse {
            val afterT = raw.substringAfter("T", missingDelimiterValue = "")
            if (afterT.length >= 5) afterT.take(5) else "--:--"
        }
    }

    private fun parseGameTimeToSortKey(raw: String?): Int {
        if (raw.isNullOrBlank()) return Int.MAX_VALUE
        return runCatching {
            val localTime = LocalTime.parse(raw, hhmmFormatter)
            localTime.hour * 60 + localTime.minute
        }.getOrDefault(Int.MAX_VALUE)
    }

    private fun teamFromBackend(value: String): Team {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return Team.NONE

        val repaired = repairPotentialMojibake(value).trim().lowercase()
        val candidates = listOf(normalized, repaired).distinct()

        val korea = "\uB300\uD55C\uBBFC\uAD6D"
        val japan = "\uC77C\uBCF8"

        return when {
            containsAny(candidates, "doosan", "\uB450\uC0B0", "\uBCA0\uC5B4\uC2A4") -> Team.DOOSAN
            containsAny(candidates, "lg", "\uC5D8\uC9C0", "\uD2B8\uC708\uC2A4") -> Team.LG
            containsAny(candidates, "kiwoom", "\uD0A4\uC6C0", "\uD790\uC5B4\uB85C\uC988", "\uB113\uC13C") -> Team.KIWOOM
            containsAny(candidates, "samsung", "\uC0BC\uC131", "\uB77C\uC774\uC628\uC988") -> Team.SAMSUNG
            containsAny(candidates, "lotte", "\uB86F\uB370", "\uC790\uC774\uC5B8\uCE20") -> Team.LOTTE
            containsAny(candidates, "ssg", "lander", "\uC5D0\uC2A4\uC5D0\uC2A4\uC9C0", "\uB79C\uB354\uC2A4") -> Team.SSG
            containsAny(candidates, "kt", "wiz", "\uCF00\uC774\uD2F0", "\uC704\uC988") -> Team.KT
            containsAny(candidates, "hanwha", "\uD55C\uD654", "\uC774\uAE00\uC2A4") -> Team.HANWHA
            containsAny(candidates, "kia", "\uAE30\uC544", "\uD0C0\uC774\uAC70\uC988") -> Team.KIA
            containsAny(candidates, "nc", "dinos", "\uC5D4\uC528", "\uB2E4\uC774\uB178\uC2A4") -> Team.NC
            containsAny(candidates, korea.lowercase(), "south korea", "korea") -> Team.SSG
            containsAny(candidates, japan.lowercase(), "japan") -> Team.SAMSUNG
            else -> Team.NONE
        }
    }

    private fun containsAny(candidates: List<String>, vararg tokens: String): Boolean {
        return candidates.any { source ->
            tokens.any { token -> source.contains(token) }
        }
    }

    private fun repairPotentialMojibake(value: String): String {
        return runCatching {
            String(value.toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
        }.getOrDefault(value)
    }

    private fun Team.toKboTeamId(): String? {
        return when (this) {
            Team.DOOSAN -> "OB"
            Team.LG -> "LG"
            Team.KIWOOM -> "WO"
            Team.SAMSUNG -> "SS"
            Team.LOTTE -> "LT"
            Team.SSG -> "SK"
            Team.KT -> "KT"
            Team.HANWHA -> "HH"
            Team.KIA -> "HT"
            Team.NC -> "NC"
            Team.NONE -> null
        }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getInt(key) }.getOrNull()
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull()
    }
}
