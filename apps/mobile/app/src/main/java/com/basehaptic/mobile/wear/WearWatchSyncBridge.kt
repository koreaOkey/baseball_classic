package com.basehaptic.mobile.wear

import android.content.Context

data class WatchSyncResponse(
    val gameId: String,
    val accepted: Boolean
)

object WearWatchSyncBridge {
    const val ACTION_WATCH_SYNC_RESPONSE = "com.basehaptic.mobile.ACTION_WATCH_SYNC_RESPONSE"

    private const val PREFS_NAME = "wear_watch_sync_bridge"
    private const val KEY_PENDING_GAME_ID = "pending_game_id"
    private const val KEY_PENDING_ACCEPTED = "pending_accepted"

    fun savePendingResponse(context: Context, gameId: String, accepted: Boolean) {
        if (gameId.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_GAME_ID, gameId)
            .putBoolean(KEY_PENDING_ACCEPTED, accepted)
            .apply()
    }

    fun consumePendingResponse(context: Context): WatchSyncResponse? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gameId = prefs.getString(KEY_PENDING_GAME_ID, "").orEmpty()
        if (gameId.isBlank()) return null
        val accepted = prefs.getBoolean(KEY_PENDING_ACCEPTED, false)
        prefs.edit()
            .remove(KEY_PENDING_GAME_ID)
            .remove(KEY_PENDING_ACCEPTED)
            .apply()
        return WatchSyncResponse(gameId = gameId, accepted = accepted)
    }
}
