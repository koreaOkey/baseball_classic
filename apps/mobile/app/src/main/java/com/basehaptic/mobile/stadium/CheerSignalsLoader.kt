package com.basehaptic.mobile.stadium

import android.content.Context
import android.util.Log
import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.data.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

data class StadiumInfo(
    val code: String,
    val name: String,
    val homeTeamCodes: List<String>,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val indoor: Boolean,
)

data class CheerSignal(
    val teamCode: String,
    val role: String,
    val cheerText: String,
    val primaryColorHex: String,
    val hapticPatternId: String,
)

data class CheerSignalEntry(
    val gameId: String,
    val stadiumCode: String,
    val fireAtIso: String,
    val signals: List<CheerSignal>,
)

data class TeamCheckinRank(
    val rank: Int,
    val team: Team,
    val count: Int,
)

object CheerSignalsLoader {
    private const val TAG = "CheerSignalsLoader"
    private const val PREFS_NAME = "stadium_cheer_cache"
    private const val KEY_STADIUMS = "stadiums_json"
    private const val KEY_SIGNALS = "cheer_signals_json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchStadiums(context: Context): List<StadiumInfo> = withContext(Dispatchers.IO) {
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/stadiums"
        val body = get(endpoint)
        if (body != null) {
            context.cachePrefs().edit().putString(KEY_STADIUMS, body).apply()
        }
        parseStadiums(body ?: context.cachePrefs().getString(KEY_STADIUMS, null).orEmpty())
    }

    suspend fun fetchCheerSignals(context: Context): List<CheerSignalEntry> = withContext(Dispatchers.IO) {
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/cheer-signals"
        val body = get(endpoint)
        if (body != null) {
            context.cachePrefs().edit().putString(KEY_SIGNALS, body).apply()
        }
        parseCheerSignals(body ?: context.cachePrefs().getString(KEY_SIGNALS, null).orEmpty())
    }

    suspend fun fetchTeamRankings(context: Context, period: String): List<TeamCheckinRank> = withContext(Dispatchers.IO) {
        val normalizedPeriod = if (period == "season") "season" else "weekly"
        val endpoint = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/rankings/teams?period=$normalizedPeriod"
        val body = get(endpoint) ?: return@withContext emptyList()
        parseTeamRankings(body)
    }

    suspend fun postCheckin(
        accessToken: String,
        team: Team,
        stadiumCode: String,
        gameId: String?,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Float?,
        mockLocation: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (team == Team.NONE) return@withContext false
        val payload = JSONObject().apply {
            put("team_code", team.name)
            put("stadium_code", stadiumCode)
            put("game_id", gameId)
            put("client_ts", Instant.now().toString())
            put("lat", latitude)
            put("lng", longitude)
            put("accuracy_m", accuracyMeters)
            put("mock_location", mockLocation)
            put("app_version", BuildConfig.VERSION_NAME)
            put("platform", "android")
        }
        val request = Request.Builder()
            .url("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/cheer-events")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse {
            Log.w(TAG, "postCheckin failed", it)
            false
        }
    }

    private fun get(endpoint: String): String? {
        val request = Request.Builder().url(endpoint).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GET $endpoint returned ${response.code}")
                    return null
                }
                response.body?.string()
            }
        }.getOrElse {
            Log.w(TAG, "GET $endpoint failed", it)
            null
        }
    }

    private fun parseStadiums(text: String): List<StadiumInfo> {
        if (text.isBlank()) return emptyList()
        return runCatching {
            val items = JSONObject(text).optJSONArray("items") ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val homeTeams = item.optJSONArray("home_team_codes") ?: JSONArray()
                    add(
                        StadiumInfo(
                            code = item.optString("code"),
                            name = item.optString("name"),
                            homeTeamCodes = buildList {
                                for (teamIndex in 0 until homeTeams.length()) {
                                    add(homeTeams.optString(teamIndex))
                                }
                            },
                            latitude = item.optDouble("latitude"),
                            longitude = item.optDouble("longitude"),
                            radiusMeters = item.optDouble("radius_meters").toFloat(),
                            indoor = item.optBoolean("indoor"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseCheerSignals(text: String): List<CheerSignalEntry> {
        if (text.isBlank()) return emptyList()
        return runCatching {
            val items = JSONObject(text).optJSONArray("items") ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val signals = item.optJSONArray("signals") ?: JSONArray()
                    add(
                        CheerSignalEntry(
                            gameId = item.optString("game_id"),
                            stadiumCode = item.optString("stadium_code"),
                            fireAtIso = item.optString("fire_at_iso"),
                            signals = buildList {
                                for (signalIndex in 0 until signals.length()) {
                                    val signal = signals.optJSONObject(signalIndex) ?: continue
                                    add(
                                        CheerSignal(
                                            teamCode = signal.optString("team_code"),
                                            role = signal.optString("role"),
                                            cheerText = signal.optString("cheer_text"),
                                            primaryColorHex = signal.optString("primary_color_hex"),
                                            hapticPatternId = signal.optString("haptic_pattern_id"),
                                        )
                                    )
                                }
                            },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseTeamRankings(text: String): List<TeamCheckinRank> {
        if (text.isBlank()) return emptyList()
        return runCatching {
            val items = JSONObject(text).optJSONArray("items") ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val team = Team.fromString(item.optString("team_code"))
                    if (team == Team.NONE) continue
                    add(
                        TeamCheckinRank(
                            rank = item.optInt("rank", index + 1),
                            team = team,
                            count = item.optInt("count"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun Context.cachePrefs() = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
