package com.basehaptic.watch

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.google.android.gms.wearable.*

/**
 * 모바일 앱에서 전달되는 데이터를 수신하는 서비스
 * - 경기 데이터 (점수, 이닝, BSO 등)
 * - 팀 테마 정보 (팀 이름 → 워치 테마 변경)
 * - 햅틱 이벤트 트리거
 */
class DataLayerListenerService : WearableListenerService() {
    
    companion object {
        private const val TAG = "DataLayerListener"
        const val PREFS_NAME = "watch_theme_prefs"
        const val PREF_KEY_TEAM_NAME = "team_name"
        const val ACTION_THEME_UPDATED = "com.basehaptic.watch.ACTION_THEME_UPDATED"

        const val GAME_PREFS_NAME = "watch_game_prefs"
        const val ACTION_GAME_UPDATED = "com.basehaptic.watch.ACTION_GAME_UPDATED"

        const val PATH_GAME = "/game"
        const val PATH_THEME = "/theme"
        const val PATH_HAPTIC = "/haptic"
        
        const val KEY_GAME_ID = "game_id"
        const val KEY_HOME_TEAM = "home_team"
        const val KEY_AWAY_TEAM = "away_team"
        const val KEY_HOME_SCORE = "home_score"
        const val KEY_AWAY_SCORE = "away_score"
        const val KEY_INNING = "inning"
        const val KEY_BALL = "ball"
        const val KEY_STRIKE = "strike"
        const val KEY_OUT = "out"
        const val KEY_BASE_FIRST = "base_first"
        const val KEY_BASE_SECOND = "base_second"
        const val KEY_BASE_THIRD = "base_third"
        const val KEY_PITCHER = "pitcher"
        const val KEY_BATTER = "batter"
        const val KEY_MY_TEAM = "my_team"
        const val KEY_EVENT_TYPE = "event_type"
    }
    
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                val path = item.uri.path
                
                when {
                    path?.startsWith(PATH_GAME) == true -> handleGameData(item)
                    path?.startsWith(PATH_THEME) == true -> handleThemeData(item)
                    path?.startsWith(PATH_HAPTIC) == true -> handleHapticEvent(item)
                }
            }
        }
    }
    
    /**
     * 경기 데이터 수신 → UI 업데이트
     */
    private fun handleGameData(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap

        getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GAME_ID, dataMap.getString(KEY_GAME_ID, ""))
            .putString(KEY_HOME_TEAM, dataMap.getString(KEY_HOME_TEAM, ""))
            .putString(KEY_AWAY_TEAM, dataMap.getString(KEY_AWAY_TEAM, ""))
            .putInt(KEY_HOME_SCORE, dataMap.getInt(KEY_HOME_SCORE, 0))
            .putInt(KEY_AWAY_SCORE, dataMap.getInt(KEY_AWAY_SCORE, 0))
            .putString(KEY_INNING, dataMap.getString(KEY_INNING, ""))
            .putInt(KEY_BALL, dataMap.getInt(KEY_BALL, 0))
            .putInt(KEY_STRIKE, dataMap.getInt(KEY_STRIKE, 0))
            .putInt(KEY_OUT, dataMap.getInt(KEY_OUT, 0))
            .putBoolean(KEY_BASE_FIRST, dataMap.getBoolean(KEY_BASE_FIRST))
            .putBoolean(KEY_BASE_SECOND, dataMap.getBoolean(KEY_BASE_SECOND))
            .putBoolean(KEY_BASE_THIRD, dataMap.getBoolean(KEY_BASE_THIRD))
            .putString(KEY_PITCHER, dataMap.getString(KEY_PITCHER, ""))
            .putString(KEY_BATTER, dataMap.getString(KEY_BATTER, ""))
            .putString(KEY_MY_TEAM, dataMap.getString(KEY_MY_TEAM, ""))
            .apply()

        // 테마도 같이 업데이트 (게임 데이터의 my_team 기준)
        val myTeam = dataMap.getString(KEY_MY_TEAM, "")
        if (myTeam.isNotBlank()) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_TEAM_NAME, myTeam)
                .apply()
            sendBroadcast(Intent(ACTION_THEME_UPDATED))
        }

        sendBroadcast(Intent(ACTION_GAME_UPDATED))

        // 이벤트 타입이 있으면 햅틱 피드백
        val eventType = dataMap.getString(KEY_EVENT_TYPE, "")
        if (eventType.isNotBlank()) {
            triggerHapticFeedback(eventType)
        }
    }
    
    /**
     * 팀 테마 변경 수신 → 워치 테마 즉시 변경
     * 사용자가 모바일에서 응원팀을 변경하면 워치도 자동 변경
     */
    private fun handleThemeData(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val teamName = dataMap.getString(KEY_MY_TEAM, "DEFAULT")
        
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_TEAM_NAME, teamName)
            .apply()

        sendBroadcast(Intent(ACTION_THEME_UPDATED))
    }
    
    /**
     * 햅틱 이벤트 트리거
     */
    private fun handleHapticEvent(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val eventType = dataMap.getString(KEY_EVENT_TYPE, "")
        triggerHapticFeedback(eventType)
    }
    
    private fun triggerHapticFeedback(eventType: String) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "Vibrator not available")
            return
        }

        val (timings, amplitudes) = when (eventType.uppercase()) {
            "HOMERUN" -> longArrayOf(0, 200, 150, 200, 150, 200) to
                    intArrayOf(0, 255, 0, 255, 0, 255)
            "HIT" -> longArrayOf(0, 150, 100, 150) to
                    intArrayOf(0, 180, 0, 180)
            "OUT" -> longArrayOf(0, 100) to
                    intArrayOf(0, 150)
            "SCORE" -> longArrayOf(0, 200, 200, 200) to
                    intArrayOf(0, 255, 0, 255)
            "STRIKE" -> longArrayOf(0, 80, 80, 80) to
                    intArrayOf(0, 120, 0, 120)
            "BALL" -> longArrayOf(0, 50) to
                    intArrayOf(0, 80)
            else -> {
                Log.d(TAG, "Unknown event type: $eventType")
                return
            }
        }

        Log.d(TAG, "Haptic feedback: $eventType")
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator.vibrate(effect)
    }
}
