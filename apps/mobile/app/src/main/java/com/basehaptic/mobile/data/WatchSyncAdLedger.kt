package com.basehaptic.mobile.data

import android.content.Context

object WatchSyncAdLedger {

    private const val PREFS_NAME = "watch_sync_ad_ledger"
    private const val KEY_PREFIX = "watch_sync_ad_viewed_"

    fun hasViewed(context: Context, gameId: String): Boolean {
        if (gameId.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFIX + gameId, false)
    }

    fun markViewed(context: Context, gameId: String) {
        if (gameId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREFIX + gameId, true).apply()
    }
}
