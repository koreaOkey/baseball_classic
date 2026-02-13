package com.basehaptic.mobile.wear

import android.content.Context
import android.util.Log
import com.basehaptic.mobile.data.model.Team
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearThemeSyncManager {
    private const val TAG = "WearThemeSync"
    private const val PATH_THEME = "/theme"
    private const val KEY_MY_TEAM = "my_team"
    private const val KEY_UPDATED_AT = "updated_at"

    fun syncThemeToWatch(context: Context, team: Team) {
        Thread {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected Wear nodes. Skip theme sync.")
                    return@Thread
                }

                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create("$PATH_THEME/$timestamp").apply {
                    dataMap.putString(KEY_MY_TEAM, team.name)
                    dataMap.putLong(KEY_UPDATED_AT, timestamp)
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Theme synced to watch: ${team.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync theme to watch", e)
            }
        }.start()
    }
}
