package com.basehaptic.mobile.data

import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object BackendGamesRepository {
    private const val TIMEOUT_MS = 5000

    fun fetchGames(selectedTeam: Team): List<Game>? {
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/games?limit=20"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }

        return runCatching {
            connection.inputStream.bufferedReader().use { reader ->
                val body = reader.readText()
                parseGames(body, selectedTeam)
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun parseGames(body: String, selectedTeam: Team): List<Game> {
        val array = JSONArray(body)
        val items = ArrayList<Game>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            items.add(item.toGame(selectedTeam))
        }
        return items
    }

    private fun JSONObject.toGame(selectedTeam: Team): Game {
        val homeTeamName = optString("homeTeam")
        val awayTeamName = optString("awayTeam")
        val homeTeamId = teamFromBackend(homeTeamName)
        val awayTeamId = teamFromBackend(awayTeamName)
        val status = statusFromBackend(optString("status"))
        val inning = optString("inning").ifBlank { defaultInningFor(status) }

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
            time = extractTime(inning, status),
            isMyTeam = selectedTeam != Team.NONE && (homeTeamId == selectedTeam || awayTeamId == selectedTeam),
            homePitcher = null,
            awayPitcher = null
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
            GameStatus.LIVE -> "진행중"
            GameStatus.FINISHED -> "종료"
            GameStatus.SCHEDULED -> "예정"
        }
    }

    private fun extractTime(inning: String, status: GameStatus): String? {
        if (status != GameStatus.SCHEDULED) return null
        return if (Regex("^\\d{2}:\\d{2}$").matches(inning)) inning else null
    }

    private fun teamFromBackend(value: String): Team {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return Team.NONE

        return when {
            normalized.contains("doosan") || normalized.contains("두산") -> Team.DOOSAN
            normalized.contains("lg") || normalized.contains("엘지") -> Team.LG
            normalized.contains("kiwoom") || normalized.contains("키움") -> Team.KIWOOM
            normalized.contains("samsung") || normalized.contains("삼성") -> Team.SAMSUNG
            normalized.contains("lotte") || normalized.contains("롯데") -> Team.LOTTE
            normalized.contains("ssg") || normalized.contains("랜더") || normalized.contains("lander") -> Team.SSG
            normalized.contains("kt") || normalized.contains("wiz") || normalized.contains("위즈") -> Team.KT
            normalized.contains("hanwha") || normalized.contains("한화") -> Team.HANWHA
            normalized.contains("kia") || normalized.contains("기아") -> Team.KIA
            normalized.contains("nc") || normalized.contains("dinos") || normalized.contains("다이노스") -> Team.NC
            else -> Team.NONE
        }
    }
}
