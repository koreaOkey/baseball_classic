package com.basehaptic.watch

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.wear.tiles.TileService
import com.basehaptic.watch.tile.GameTileService
import com.basehaptic.watch.ui.StadiumCheerOverlayCoordinator
import com.basehaptic.watch.ui.StadiumCheerPayload
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
        const val PREF_KEY_STORE_THEME_ID = "store_theme_id"
        const val PREF_KEY_STORE_PRIMARY = "store_primary"
        const val PREF_KEY_STORE_SECONDARY = "store_secondary"
        const val PREF_KEY_STORE_ACCENT = "store_accent"
        const val ACTION_THEME_UPDATED = "com.basehaptic.watch.ACTION_THEME_UPDATED"

        const val GAME_PREFS_NAME = "watch_game_prefs"
        const val ACTION_GAME_UPDATED = "com.basehaptic.watch.ACTION_GAME_UPDATED"
        const val ACTION_WATCH_SYNC_PROMPT = "com.basehaptic.watch.ACTION_WATCH_SYNC_PROMPT"
        const val ACTION_SETTINGS_UPDATED = "com.basehaptic.watch.ACTION_SETTINGS_UPDATED"

        const val SETTINGS_PREFS_NAME = "watch_user_prefs"
        const val PREF_KEY_EVENT_VIDEO_ENABLED = "event_video_enabled"
        const val PREF_KEY_LIVE_HAPTIC_ENABLED = "live_haptic_enabled"

        const val PATH_GAME = "/game"
        const val PATH_THEME = "/theme"
        const val PATH_HAPTIC = "/haptic"
        const val PATH_WATCH_PROMPT = "/watch/prompt"
        const val PATH_SETTINGS = "/settings"
        // TODO(stadium-cheer): 활성화 시 phone WearGameSyncManager.PATH_CHEER_TRIGGER 와 일치 유지.
        const val PATH_CHEER_TRIGGER = "/cheer/trigger"
        
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
        const val KEY_GAME_UPDATED_AT = "game_updated_at"
        const val KEY_PENDING_SYNC_GAME_ID = "pending_sync_game_id"
        const val KEY_PENDING_SYNC_HOME_TEAM = "pending_sync_home_team"
        const val KEY_PENDING_SYNC_AWAY_TEAM = "pending_sync_away_team"
        const val KEY_PENDING_SYNC_MY_TEAM = "pending_sync_my_team"

        private const val WAKE_LOCK_TIMEOUT_MS = 3_000L
        private const val STALE_EVENT_THRESHOLD_MS = 10_000L
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
                    path?.startsWith(PATH_SETTINGS) == true -> handleSettingsUpdate(item)
                    // TODO(stadium-cheer): 활성화 시 아래 분기 주석 해제. 다크 단계에서는 미전달이라 도달 X.
                    // path?.startsWith(PATH_CHEER_TRIGGER) == true -> handleCheerTrigger(item)
                }
            }
        }
    }
    
    /**
     * 경기 데이터 수신 → UI 업데이트
     */
    private fun handleGameData(item: DataItem) {
        val liveHapticEnabled = getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY_LIVE_HAPTIC_ENABLED, true)
        if (!liveHapticEnabled) {
            Log.d(TAG, "live_haptic_enabled=false, freezing game_data")
            return
        }
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
        // 1~8회 3out → 다음 이닝으로 즉시 전진. 9회 이상은 점수/연장 판정이 필요해 백엔드 값 사용.
        val normalizedInning = when {
            isFinished -> "경기 종료"
            rawOut >= 3 -> advanceInningBeforeNinth(incomingInning)
            else -> incomingInning
        }

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
            .putLong(KEY_GAME_UPDATED_AT, System.currentTimeMillis())
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

        // 모바일에서 이미 경기 관람을 시작한 경우 → 워치 팝업 자동 수락
        val gameId = dataMap.getString(KEY_GAME_ID, "")
        val pendingPromptGameId = getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_SYNC_GAME_ID, "")
        if (!gameId.isNullOrBlank() && gameId == pendingPromptGameId) {
            WatchSyncResponseSender.send(this, gameId, accepted = true)
            getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_SYNC_GAME_ID)
                .remove(KEY_PENDING_SYNC_HOME_TEAM)
                .remove(KEY_PENDING_SYNC_AWAY_TEAM)
                .remove(KEY_PENDING_SYNC_MY_TEAM)
                .apply()
        }

        val eventType = dataMap.getString(KEY_EVENT_TYPE, "")
        if (eventType.isNotBlank()) {
            // 오래된 이벤트는 햅틱 무시 (워치 재시작 시 이벤트 폭주 방지)
            val isStale = if (dataMap.containsKey("updated_at")) {
                System.currentTimeMillis() - dataMap.getLong("updated_at", 0L) > STALE_EVENT_THRESHOLD_MS
            } else false
            saveLatestEvent(eventType, null)
            if (!isStale) {
                triggerHapticFeedback(eventType)
            }
        }

        if (isFinished) {
            GameForegroundService.stop(this)
        } else {
            GameForegroundService.start(this)
        }

        sendBroadcast(Intent(ACTION_GAME_UPDATED))
        TileService.getUpdater(this).requestUpdate(GameTileService::class.java)
    }
    
    /**
     * 팀 테마 변경 수신 → 워치 테마 즉시 변경
     * 사용자가 모바일에서 응원팀을 변경하면 워치도 자동 변경
     */
    private fun handleThemeData(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val teamName = dataMap.getString(KEY_MY_TEAM, "DEFAULT")
        val storeThemeId = dataMap.getString("store_theme_id", "")

        val editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putString(PREF_KEY_TEAM_NAME, teamName)
        editor.putString(PREF_KEY_STORE_THEME_ID, storeThemeId)

        if (storeThemeId.isNotBlank() && dataMap.containsKey("store_primary")) {
            editor.putInt(PREF_KEY_STORE_PRIMARY, dataMap.getInt("store_primary"))
            editor.putInt(PREF_KEY_STORE_SECONDARY, dataMap.getInt("store_secondary"))
            editor.putInt(PREF_KEY_STORE_ACCENT, dataMap.getInt("store_accent"))
        } else {
            editor.remove(PREF_KEY_STORE_PRIMARY)
            editor.remove(PREF_KEY_STORE_SECONDARY)
            editor.remove(PREF_KEY_STORE_ACCENT)
        }

        editor.apply()
        sendBroadcast(Intent(ACTION_THEME_UPDATED))
    }
    
    /**
     * 햅틱 이벤트 트리거
     */
    private fun handleHapticEvent(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val eventType = dataMap.getString(KEY_EVENT_TYPE, "")
        if (eventType.isBlank()) return

        // 오래된 이벤트는 햅틱 무시 (워치 재시작 시 이벤트 폭주 방지)
        val eventTimestamp = dataMap.getLong("updated_at", 0L)
            .takeIf { it > 0L }
            ?: item.uri.path
                ?.substringAfterLast("/")
                ?.toLongOrNull()
        if (eventTimestamp != null) {
            val ageMs = System.currentTimeMillis() - eventTimestamp
            if (ageMs > STALE_EVENT_THRESHOLD_MS) {
                Log.d(TAG, "Skipping stale haptic event: $eventType (age=${ageMs}ms)")
                return
            }
        }

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

        val prefs = getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
        // 이미 해당 경기 데이터를 수신 중이면 팝업 무시
        val currentGameId = prefs.getString("game_id", "") ?: ""
        val isLive = prefs.getBoolean("is_live", false)
        if (currentGameId == gameId && isLive) return
        // 이미 같은 경기 팝업이 떠있으면 무시
        val existingPromptId = prefs.getString(KEY_PENDING_SYNC_GAME_ID, "") ?: ""
        if (existingPromptId == gameId) return

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

    /**
     * 사용자 설정 수신 → SharedPreferences 저장 + 브로드캐스트
     */
    private fun handleSettingsUpdate(item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val prefs = getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        var changed = false
        if (dataMap.containsKey(PREF_KEY_EVENT_VIDEO_ENABLED)) {
            val enabled = dataMap.getBoolean(PREF_KEY_EVENT_VIDEO_ENABLED, true)
            prefs.edit().putBoolean(PREF_KEY_EVENT_VIDEO_ENABLED, enabled).apply()
            Log.d(TAG, "event_video_enabled = $enabled")
            changed = true
        }
        if (dataMap.containsKey(PREF_KEY_LIVE_HAPTIC_ENABLED)) {
            val enabled = dataMap.getBoolean(PREF_KEY_LIVE_HAPTIC_ENABLED, true)
            prefs.edit().putBoolean(PREF_KEY_LIVE_HAPTIC_ENABLED, enabled).apply()
            Log.d(TAG, "live_haptic_enabled = $enabled")
            changed = true
        }
        if (changed) {
            sendBroadcast(Intent(ACTION_SETTINGS_UPDATED))
        }
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
        // 마스터 스위치 OFF 시 햅틱·화면 깨우기 모두 차단
        val liveHapticEnabled = getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY_LIVE_HAPTIC_ENABLED, true)
        if (!liveHapticEnabled) {
            Log.d(TAG, "live_haptic_enabled=false, skipping: $eventType")
            return
        }

        @Suppress("DEPRECATION")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "Vibrator not available")
            return
        }

        val (timings, amplitudes) = when (eventType.uppercase()) {
            "VICTORY" -> {
                // 4초간 반복 진동 (200ms on, 100ms off × 13회 = 3900ms)
                val count = 13
                val t = LongArray(count * 2).also { arr ->
                    for (i in 0 until count) { arr[i * 2] = if (i == 0) 0L else 100L; arr[i * 2 + 1] = 200L }
                }
                val a = IntArray(count * 2).also { arr ->
                    for (i in 0 until count) { arr[i * 2] = 0; arr[i * 2 + 1] = 255 }
                }
                t to a
            }
            "HOMERUN" -> longArrayOf(0, 200, 150, 200, 150, 200) to
                    intArrayOf(0, 255, 0, 255, 0, 255)
            "HIT" -> longArrayOf(0, 150, 100, 150) to
                    intArrayOf(0, 180, 0, 180)
            "WALK" -> longArrayOf(0, 150, 100, 150) to
                    intArrayOf(0, 180, 0, 180)
            "STEAL", "TAG_UP_ADVANCE" -> longArrayOf(0, 150, 100, 150) to
                    intArrayOf(0, 180, 0, 180)
            "PITCHER_CHANGE" -> longArrayOf(0, 150, 100, 150) to
                    intArrayOf(0, 180, 0, 180)
            "MOUND_VISIT" -> {
                Log.d(TAG, "MOUND_VISIT: skip haptic (overlay only)")
                return
            }
            "OUT" -> longArrayOf(0, 100) to
                    intArrayOf(0, 210)
            "DOUBLE_PLAY" -> longArrayOf(0, 100) to
                    intArrayOf(0, 210)
            "TRIPLE_PLAY" -> longArrayOf(0, 100) to
                    intArrayOf(0, 210)
            "SCORE" -> longArrayOf(0, 200, 150, 200, 150, 200) to
                    intArrayOf(0, 255, 0, 255, 0, 255)
            "STRIKE" -> longArrayOf(0, 50) to
                    intArrayOf(0, 80)
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

    /**
     * 3out 시 워치에서 이닝을 즉시 전진. 9회말은 점수/연장 판정 필요해 그대로 두고 백엔드 대기.
     * "N회초" → "N회말", "N회말" (N<9) → "(N+1)회초". 그 외는 원본 유지.
     */
    private fun advanceInningBeforeNinth(inning: String): String {
        val match = Regex("^(\\d+)회(초|말)$").matchEntire(inning) ?: return inning
        val number = match.groupValues[1].toIntOrNull() ?: return inning
        val half = match.groupValues[2]
        return when {
            half == "초" -> "${number}회말"
            half == "말" && number < 9 -> "${number + 1}회초"
            else -> inning
        }
    }

    // TODO(stadium-cheer): 활성화 시 onDataChanged when 분기 주석 해제 + StadiumCheerOverlay UI 트리거.
    // 다크 머지 단계에서는 함수 정의만 두고 호출되지 않음.
    @Suppress("unused")
    private fun handleCheerTrigger(item: com.google.android.gms.wearable.DataItem) {
        val prefs = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_KEY_LIVE_HAPTIC_ENABLED, true)) return

        val map = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
        val teamCode = map.getString("team_code") ?: return
        val cheerText = map.getString("cheer_text") ?: return
        val primaryColorHex = map.getString("primary_color_hex") ?: "#3B82F6"
        val hapticPatternId = map.getString("haptic_pattern_id") ?: "default"
        val fireAtMs = map.getLong("fire_at_unix_ms", 0L)

        val delay = (fireAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        android.os.Handler(mainLooper).postDelayed({
            StadiumCheerOverlayCoordinator.dispatch(
                applicationContext,
                StadiumCheerPayload(teamCode, cheerText, primaryColorHex, hapticPatternId)
            )
        }, delay)
    }
}
