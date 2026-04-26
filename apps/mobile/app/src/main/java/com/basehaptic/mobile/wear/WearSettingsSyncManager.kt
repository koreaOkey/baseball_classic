package com.basehaptic.mobile.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearSettingsSyncManager {
    private const val TAG = "WearSettingsSync"
    private const val PATH_SETTINGS = "/settings/current"
    private const val KEY_EVENT_VIDEO_ENABLED = "event_video_enabled"
    private const val KEY_LIVE_HAPTIC_ENABLED = "live_haptic_enabled"
    private const val KEY_UPDATED_AT = "updated_at"

    fun syncEventVideoEnabledToWatch(context: Context, enabled: Boolean) {
        putBool(context, KEY_EVENT_VIDEO_ENABLED, enabled)
    }

    fun syncLiveHapticEnabledToWatch(context: Context, enabled: Boolean) {
        putBool(context, KEY_LIVE_HAPTIC_ENABLED, enabled)
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
}
