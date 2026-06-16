package com.basehaptic.mobile.wear

import android.content.Context
import android.util.Log
import com.basehaptic.mobile.data.model.TeamDisplayNameStyle
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearSettingsSyncManager {
    private const val TAG = "WearSettingsSync"
    private const val PATH_SETTINGS = "/settings/current"
    private const val KEY_EVENT_VIDEO_ENABLED = "event_video_enabled"
    private const val KEY_LIVE_HAPTIC_ENABLED = "live_haptic_enabled"
    private const val KEY_STADIUM_CHEER_ENABLED = "stadium_cheer_enabled"
    private const val KEY_TEAM_DISPLAY_NAME_STYLE = "team_display_name_style"
    private const val KEY_UPDATED_AT = "updated_at"

    fun syncEventVideoEnabledToWatch(context: Context, enabled: Boolean) {
        putBool(context, KEY_EVENT_VIDEO_ENABLED, enabled)
    }

    fun syncLiveHapticEnabledToWatch(context: Context, enabled: Boolean) {
        putBool(context, KEY_LIVE_HAPTIC_ENABLED, enabled)
    }

    fun syncStadiumCheerEnabledToWatch(context: Context, enabled: Boolean) {
        putBool(context, KEY_STADIUM_CHEER_ENABLED, enabled)
    }

    fun syncTeamDisplayNameStyleToWatch(context: Context, style: TeamDisplayNameStyle) {
        putString(context, KEY_TEAM_DISPLAY_NAME_STYLE, style.name)
    }

    fun syncEventFiltersToWatch(context: Context, filters: Map<String, Boolean>) {
        Thread {
            try {
                val request = PutDataMapRequest.create(PATH_SETTINGS).apply {
                    filters.forEach { (key, value) -> dataMap.putBoolean(key, value) }
                    dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Event filters sync queued: ${filters.size} keys")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync event filters to watch", e)
            }
        }.start()
    }

    private fun putBool(context: Context, key: String, enabled: Boolean) {
        Thread {
            try {
                val request = PutDataMapRequest.create(PATH_SETTINGS).apply {
                    dataMap.putBoolean(key, enabled)
                    dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Settings sync queued: $key=$enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync settings to watch ($key)", e)
            }
        }.start()
    }

    private fun putString(context: Context, key: String, value: String) {
        Thread {
            try {
                val request = PutDataMapRequest.create(PATH_SETTINGS).apply {
                    dataMap.putString(key, value)
                    dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Settings sync queued: $key=$value")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync settings to watch ($key)", e)
            }
        }.start()
    }
}
