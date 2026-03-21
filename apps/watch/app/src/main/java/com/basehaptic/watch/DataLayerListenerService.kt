package com.basehaptic.watch

import android.content.Context
import android.content.Intent
import android.os.PowerManager
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
        const val ACTION_WATCH_SYNC_PROMPT = "com.basehaptic.watch.ACTION_WATCH_SYNC_PROMPT"

        const val PATH_GAME = "/game"
        const val PATH_THEME = "/theme"
        const val PATH_HAPTIC = "/haptic"
        const val PATH_WATCH_PROMPT = "/watch/prompt"
        
        const val KEY_GAME_ID = "game_id"
        const val KEY_HOME_TEAM = "home_team"
        const val KEY_AWAY_TEAM = "away_team"
        const val KEY_HOME_SCORE = "home_score"
        const val KEY_AWAY_SCORE = "away_score"
        const val KEY_STATUS = "status"
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
        const val KEY_EVENT_CURSOR = "event_cursor"
        const val KEY_LAST_EVENT_TYPE = "last_event_type"
        const val KEY_LAST_EVENT_AT = "last_event_at"
        const val KEY_LAST_EVENT_CURSOR = "last_event_cursor"
        const val KEY_PENDING_SYNC_GAME_ID = "pending_sync_game_id"
        const val KEY_PENDING_SYNC_HOME_TEAM = "pending_sync_home_team"
        const val KEY_PENDING_SYNC_AWAY_TEAM = "pending_sync_away_team"
        const val KEY_PENDING_SYNC_MY_TEAM = "pending_sync_my_team"

        private const val WAKE_LOCK_TIMEOUT_MS = 3_000L
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
                    path?.startsWith(PATH_WATCH_PROMPT) == true -> handleWatchSyncPrompt(item)
                }
            }
        }
    }
    
    /**
     * 경기 데이터 수신 → UI 업데이트
     */
    private fun handleGameData(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val rawStatus = dataMap.getString(KEY_STATUS, "")
        val incomingInning = dataMap.getString(KEY_INNING, "") ?: ""
        val isFinished = rawStatus.equals("FINISHED", ignoreCase = true) ||
            incomingInning.contains("FINAL", ignoreCase = true) ||
            incomingInning.contains("경기 종료")
        val rawOut = dataMap.getInt(KEY_OUT, 0).coerceAtLeast(0)
        val normalizedOut = if (isFinished) 0 else rawOut
        val normalizedBall = if (isFinished || rawOut >= 3) 0 else dataMap.getInt(KEY_BALL, 0).coerceAtLeast(0)
        val normalizedStrike = if (isFinished || rawOut >= 3) 0 else dataMap.getInt(KEY_STRIKE, 0).coerceAtLeast(0)
        val normalizedInning = if (isFinished) "경기 종료" else incomingInning

        getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GAME_ID, dataMap.getString(KEY_GAME_ID, ""))
            .putString(KEY_HOME_TEAM, dataMap.getString(KEY_HOME_TEAM, ""))
            .putString(KEY_AWAY_TEAM, dataMap.getString(KEY_AWAY_TEAM, ""))
            .putInt(KEY_HOME_SCORE, dataMap.getInt(KEY_HOME_SCORE, 0))
            .putInt(KEY_AWAY_SCORE, dataMap.getInt(KEY_AWAY_SCORE, 0))
            .putString(KEY_STATUS, rawStatus)
            .putString(KEY_INNING, normalizedInning)
            .putInt(KEY_BALL, normalizedBall)
            .putInt(KEY_STRIKE, normalizedStrike)
            .putInt(KEY_OUT, normalizedOut)
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

        val eventType = dataMap.getString(KEY_EVENT_TYPE, "")
        if (eventType.isNotBlank()) {
            saveLatestEvent(eventType, null)
            triggerHapticFeedback(eventType)
        }

        sendBroadcast(Intent(ACTION_GAME_UPDATED))
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
        if (eventType.isBlank()) return
        val eventCursor = if (dataMap.containsKey(KEY_EVENT_CURSOR)) {
            dataMap.getLong(KEY_EVENT_CURSOR, -1L)
        } else {
            -1L
        }
        if (eventCursor > 0L) {
            val lastCursor = getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_EVENT_CURSOR, 0L)
            if (eventCursor <= lastCursor) {
                return
            }
        }

        saveLatestEvent(eventType, eventCursor.takeIf { it > 0L })
        triggerHapticFeedback(eventType)
        sendBroadcast(Intent(ACTION_GAME_UPDATED))
    }

    private fun handleWatchSyncPrompt(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val gameId = dataMap.getString(KEY_GAME_ID, "")
        if (gameId.isBlank()) return

        val homeTeam = dataMap.getString(KEY_HOME_TEAM, "")
        val awayTeam = dataMap.getString(KEY_AWAY_TEAM, "")
        val myTeam = dataMap.getString(KEY_MY_TEAM, "")

        getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_SYNC_GAME_ID, gameId)
            .putString(KEY_PENDING_SYNC_HOME_TEAM, homeTeam)
            .putString(KEY_PENDING_SYNC_AWAY_TEAM, awayTeam)
            .putString(KEY_PENDING_SYNC_MY_TEAM, myTeam)
            .apply()

        if (myTeam.isNotBlank()) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_TEAM_NAME, myTeam)
                .apply()
            sendBroadcast(Intent(ACTION_THEME_UPDATED))
        }

        sendBroadcast(Intent(ACTION_WATCH_SYNC_PROMPT))
        wakeScreenForPrompt(gameId)
    }

    private fun saveLatestEvent(eventType: String, eventCursor: Long?) {
        getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_EVENT_TYPE, eventType.uppercase())
            .putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis())
            .apply {
                if (eventCursor != null) {
                    putLong(KEY_LAST_EVENT_CURSOR, eventCursor)
                }
            }
            .apply()
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
            "DOUBLE_PLAY" -> longArrayOf(0, 120, 80, 120) to
                    intArrayOf(0, 210, 0, 210)
            "TRIPLE_PLAY" -> longArrayOf(0, 120, 80, 120, 80, 120) to
                    intArrayOf(0, 230, 0, 230, 0, 230)
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

        wakeScreenForEvent(eventType.uppercase())

        Log.d(TAG, "Haptic feedback: $eventType")
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator.vibrate(effect)
    }

    private fun wakeScreenForEvent(eventType: String) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == true) return

        acquireWakeLock(powerManager)
        launchMainActivity(extraKey = "wake_event_type", extraValue = eventType) { error ->
            Log.e(TAG, "Failed to open watch screen for event: $eventType", error)
        }
    }

    private fun wakeScreenForPrompt(gameId: String) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive != true) {
            acquireWakeLock(powerManager)
        }
        launchMainActivity(extraKey = "sync_prompt_game_id", extraValue = gameId) { error ->
            Log.e(TAG, "Failed to open watch screen for sync prompt: $gameId", error)
        }
    }

    private fun acquireWakeLock(powerManager: PowerManager?) {
        if (powerManager == null) return
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "$TAG:EventWakeLock"
        )
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun launchMainActivity(
        extraKey: String,
        extraValue: String,
        onFailure: (Throwable) -> Unit
    ) {
        val wakeIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(extraKey, extraValue)
        }
        runCatching { startActivity(wakeIntent) }
            .onFailure(onFailure)
    }
}
