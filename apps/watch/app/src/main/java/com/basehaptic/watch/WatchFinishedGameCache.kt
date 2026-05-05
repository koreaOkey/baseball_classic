package com.basehaptic.watch

import android.content.SharedPreferences
import java.util.Calendar
import java.util.TimeZone

object WatchFinishedGameCache {
    private val kst: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

    fun isExpired(updatedAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (updatedAt <= 0L) return false
        val todayMidnight = Calendar.getInstance(kst).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return updatedAt < todayMidnight
    }

    fun clearGameData(prefs: SharedPreferences) {
        prefs.edit()
            .remove(DataLayerListenerService.KEY_GAME_ID)
            .remove(DataLayerListenerService.KEY_HOME_TEAM)
            .remove(DataLayerListenerService.KEY_AWAY_TEAM)
            .remove(DataLayerListenerService.KEY_HOME_SCORE)
            .remove(DataLayerListenerService.KEY_AWAY_SCORE)
            .remove(DataLayerListenerService.KEY_STATUS)
            .remove(DataLayerListenerService.KEY_INNING)
            .remove(DataLayerListenerService.KEY_BALL)
            .remove(DataLayerListenerService.KEY_STRIKE)
            .remove(DataLayerListenerService.KEY_OUT)
            .remove(DataLayerListenerService.KEY_BASE_FIRST)
            .remove(DataLayerListenerService.KEY_BASE_SECOND)
            .remove(DataLayerListenerService.KEY_BASE_THIRD)
            .remove(DataLayerListenerService.KEY_PITCHER)
            .remove(DataLayerListenerService.KEY_BATTER)
            .remove(DataLayerListenerService.KEY_MY_TEAM)
            .remove(DataLayerListenerService.KEY_EVENT_TYPE)
            .remove(DataLayerListenerService.KEY_EVENT_CURSOR)
            .remove(DataLayerListenerService.KEY_LAST_EVENT_TYPE)
            .remove(DataLayerListenerService.KEY_LAST_EVENT_AT)
            .remove(DataLayerListenerService.KEY_LAST_EVENT_CURSOR)
            .remove(DataLayerListenerService.KEY_GAME_UPDATED_AT)
            .apply()
    }
}
