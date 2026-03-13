package com.basehaptic.mobile.data

import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

object BackendGamesRepository {
    private const val TIMEOUT_MS = 5000
    private val hhmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

    fun fetchGames(selectedTeam: Team): List<Game>? {
        val today = LocalDate.now().toString()
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/games?date=$today&limit=20"
        return getJson(endpoint) { body ->
            val array = JSONArray(body)
            val items = ArrayList<Game>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                items.add(item.toGame(selectedTeam))
            }
            items
        }
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
