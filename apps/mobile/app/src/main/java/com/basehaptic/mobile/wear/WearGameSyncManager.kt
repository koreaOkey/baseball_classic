package com.basehaptic.mobile.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearGameSyncManager {
    private const val TAG = "WearGameSync"
    private const val PATH_GAME = "/game/current"
    private const val PATH_HAPTIC = "/haptic"
    private const val PATH_WATCH_PROMPT = "/watch/prompt/current"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val CACHE_PREFS = "basehaptic_user_prefs"
    private const val KEY_LAST_GAME_DATA = "last_watch_game_data"

    fun sendGameData(
        context: Context,
        gameId: String,
        homeTeam: String,
        awayTeam: String,
        homeScore: Int,
        awayScore: Int,
        status: String = "LIVE",
        inning: String,
        ball: Int,
        strike: Int,
        out: Int,
        baseFirst: Boolean,
        baseSecond: Boolean,
        baseThird: Boolean,
        pitcher: String,
        batter: String,
        myTeam: String,
        eventType: String? = null
    ) {
        val payload = GameDataPayload(
            gameId, homeTeam, awayTeam, homeScore, awayScore, status,
            inning, ball, strike, out, baseFirst, baseSecond, baseThird,
            pitcher, batter, myTeam, eventType
        )
        // 마스터 OFF여도 phone-side 캐시는 항상 갱신 (ON 복원 시 즉시 push 용)
        cacheLastGameData(context, payload)

        val liveHapticEnabled = context
            .getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean("live_haptic_enabled", true)
        if (!liveHapticEnabled) {
            Log.d(TAG, "live_haptic_enabled=false, skipping game_data send")
            return
        }
        deliverGameData(context, payload)
    }

    /// 토글 OFF→ON 복원 시 마지막 캐시된 game_data를 즉시 워치에 push
    fun resyncLastGameDataToWatch(context: Context) {
        val payload = readCachedGameData(context)
        if (payload == null) {
            Log.d(TAG, "No cached game_data to resync")
            return
        }
        deliverGameData(context, payload)
    }

    private data class GameDataPayload(
        val gameId: String,
        val homeTeam: String,
        val awayTeam: String,
        val homeScore: Int,
        val awayScore: Int,
        val status: String,
        val inning: String,
        val ball: Int,
        val strike: Int,
        val out: Int,
        val baseFirst: Boolean,
        val baseSecond: Boolean,
        val baseThird: Boolean,
        val pitcher: String,
        val batter: String,
        val myTeam: String,
        val eventType: String?
    )

    private fun deliverGameData(context: Context, p: GameDataPayload) {
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create(PATH_GAME).apply {
                    dataMap.putString("game_id", p.gameId)
                    dataMap.putString("home_team", p.homeTeam)
                    dataMap.putString("away_team", p.awayTeam)
                    dataMap.putInt("home_score", p.homeScore)
                    dataMap.putInt("away_score", p.awayScore)
                    dataMap.putString("status", p.status)
                    dataMap.putString("inning", p.inning)
                    dataMap.putInt("ball", p.ball)
                    dataMap.putInt("strike", p.strike)
                    dataMap.putInt("out", p.out)
                    dataMap.putBoolean("base_first", p.baseFirst)
                    dataMap.putBoolean("base_second", p.baseSecond)
                    dataMap.putBoolean("base_third", p.baseThird)
                    dataMap.putString("pitcher", p.pitcher)
                    dataMap.putString("batter", p.batter)
                    dataMap.putString("my_team", p.myTeam)
                    dataMap.putLong(KEY_UPDATED_AT, timestamp)
                    if (p.eventType != null) {
                        dataMap.putString("event_type", p.eventType)
                    }
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Game data sent: inning=${p.inning}, score=${p.homeScore}:${p.awayScore}, event=${p.eventType}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send game data", e)
            }
        }.start()
    }

    private fun cacheLastGameData(context: Context, p: GameDataPayload) {
        val json = org.json.JSONObject().apply {
            put("game_id", p.gameId)
            put("home_team", p.homeTeam)
            put("away_team", p.awayTeam)
            put("home_score", p.homeScore)
            put("away_score", p.awayScore)
            put("status", p.status)
            put("inning", p.inning)
            put("ball", p.ball)
            put("strike", p.strike)
            put("out", p.out)
            put("base_first", p.baseFirst)
            put("base_second", p.baseSecond)
            put("base_third", p.baseThird)
            put("pitcher", p.pitcher)
            put("batter", p.batter)
            put("my_team", p.myTeam)
            if (p.eventType != null) put("event_type", p.eventType)
        }
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_GAME_DATA, json.toString())
            .apply()
    }

    private fun readCachedGameData(context: Context): GameDataPayload? {
        val raw = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_GAME_DATA, null) ?: return null
        return try {
            val j = org.json.JSONObject(raw)
            GameDataPayload(
                gameId = j.optString("game_id"),
                homeTeam = j.optString("home_team"),
                awayTeam = j.optString("away_team"),
                homeScore = j.optInt("home_score"),
                awayScore = j.optInt("away_score"),
                status = j.optString("status", "LIVE"),
                inning = j.optString("inning"),
                ball = j.optInt("ball"),
                strike = j.optInt("strike"),
                out = j.optInt("out"),
                baseFirst = j.optBoolean("base_first"),
                baseSecond = j.optBoolean("base_second"),
                baseThird = j.optBoolean("base_third"),
                pitcher = j.optString("pitcher"),
                batter = j.optString("batter"),
                myTeam = j.optString("my_team"),
                eventType = if (j.has("event_type")) j.optString("event_type") else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached game_data", e)
            null
        }
    }

    fun sendHapticEvent(context: Context, eventType: String) {
        sendHapticEvent(context = context, eventType = eventType, cursor = null)
    }

    fun sendHapticEvent(context: Context, eventType: String, cursor: Long?) {
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create("$PATH_HAPTIC/$timestamp").apply {
                    dataMap.putString("event_type", eventType)
                    if (cursor != null) {
                        dataMap.putLong("event_cursor", cursor)
                    }
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Haptic event sent: $eventType")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send haptic event", e)
            }
        }.start()
    }

    fun sendWatchSyncPrompt(
        context: Context,
        gameId: String,
        homeTeam: String,
        awayTeam: String,
        myTeam: String
    ) {
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create(PATH_WATCH_PROMPT).apply {
                    dataMap.putString("game_id", gameId)
                    dataMap.putString("home_team", homeTeam)
                    dataMap.putString("away_team", awayTeam)
                    dataMap.putString("my_team", myTeam)
                    dataMap.putLong(KEY_UPDATED_AT, timestamp)
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Watch sync prompt sent for game: $gameId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send watch sync prompt", e)
            }
        }.start()
    }
}
