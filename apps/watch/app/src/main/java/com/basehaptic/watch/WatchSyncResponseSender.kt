package com.basehaptic.watch

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WatchSyncResponseSender {
    private const val TAG = "WatchSyncResponse"
    private const val PATH_SYNC_RESPONSE = "/watch/sync-response"

    fun send(context: Context, gameId: String, accepted: Boolean) {
        if (gameId.isBlank()) return

        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create("$PATH_SYNC_RESPONSE/$timestamp").apply {
                    dataMap.putString("game_id", gameId)
                    dataMap.putBoolean("accepted", accepted)
                    dataMap.putLong("responded_at", timestamp)
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Watch sync response sent: game=$gameId accepted=$accepted")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to send watch sync response", error)
            }
        }.start()
    }
}
