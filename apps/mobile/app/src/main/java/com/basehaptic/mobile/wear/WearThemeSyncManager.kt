package com.basehaptic.mobile.wear

import android.content.Context
import android.util.Log
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.data.model.ThemeData
import com.basehaptic.mobile.data.model.ThemeStore
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearThemeSyncManager {
    private const val TAG = "WearThemeSync"
    private const val PATH_THEME = "/theme/current"
    private const val KEY_MY_TEAM = "my_team"
    private const val KEY_STORE_THEME_ID = "store_theme_id"
    private const val KEY_STORE_PRIMARY = "store_primary"
    private const val KEY_STORE_SECONDARY = "store_secondary"
    private const val KEY_STORE_ACCENT = "store_accent"
    private const val KEY_UPDATED_AT = "updated_at"

    fun syncThemeToWatch(context: Context, team: Team, storeThemeId: String? = null) {
        val theme = storeThemeId?.let { id -> ThemeStore.allThemes.find { it.id == id } }

        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val request = PutDataMapRequest.create(PATH_THEME).apply {
                    dataMap.putString(KEY_MY_TEAM, team.name)
                    dataMap.putString(KEY_STORE_THEME_ID, storeThemeId ?: "")
                    if (theme != null) {
                        dataMap.putInt(KEY_STORE_PRIMARY, theme.colors.primary.toArgb())
                        dataMap.putInt(KEY_STORE_SECONDARY, theme.colors.secondary.toArgb())
                        dataMap.putInt(KEY_STORE_ACCENT, theme.colors.accent.toArgb())
                    }
                    dataMap.putLong(KEY_UPDATED_AT, timestamp)
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                Log.d(TAG, "Theme sync queued: team=${team.name}, storeTheme=$storeThemeId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync theme to watch", e)
            }
        }.start()
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        val a = (alpha * 255).toInt()
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
