package com.basehaptic.mobile.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearGameSyncManager {
    private const val TAG = "WearGameSync"
    private const val PATH_GAME = "/game"
    private const val PATH_HAPTIC = "/haptic"

    fun sendGameData(
        context: Context,
        gameId: String,
        homeTeam: String,
        awayTeam: String,
        homeScore: Int,
        awayScore: Int,
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
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected Wear nodes. Skip game sync.")
                    return@Thread
                }

                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create("$PATH_GAME/$timestamp").apply {
                    dataMap.putString("game_id", gameId)
                    dataMap.putString("home_team", homeTeam)
                    dataMap.putString("away_team", awayTeam)
                    dataMap.putInt("home_score", homeScore)
                    dataMap.putInt("away_score", awayScore)
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
        Thread {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected Wear nodes. Skip haptic.")
                    return@Thread
                }

                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create("$PATH_HAPTIC/$timestamp").apply {
                    dataMap.putString("event_type", eventType)
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Haptic event sent: $eventType")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send haptic event", e)
            }
        }.start()
    }
}
