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
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create(PATH_GAME).apply {
                    dataMap.putString("game_id", gameId)
                    dataMap.putString("home_team", homeTeam)
                    dataMap.putString("away_team", awayTeam)
                    dataMap.putInt("home_score", homeScore)
                    dataMap.putInt("away_score", awayScore)
                    dataMap.putString("status", status)
                    dataMap.putString("inning", inning)
                    dataMap.putInt("ball", ball)
                    dataMap.putInt("strike", strike)
                    dataMap.putInt("out", out)
                    dataMap.putBoolean("base_first", baseFirst)
                    dataMap.putBoolean("base_second", baseSecond)
                    dataMap.putBoolean("base_third", baseThird)
                    dataMap.putString("pitcher", pitcher)
                    dataMap.putString("batter", batter)
                    dataMap.putString("my_team", myTeam)
                    dataMap.putLong(KEY_UPDATED_AT, timestamp)
                    if (eventType != null) {
                        dataMap.putString("event_type", eventType)
                    }
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Game data sent: inning=$inning, score=$homeScore:$awayScore, event=$eventType")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send game data", e)
            }
        }.start()
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
