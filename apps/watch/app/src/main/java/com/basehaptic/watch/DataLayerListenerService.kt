package com.basehaptic.watch

import android.content.Intent
import com.google.android.gms.wearable.*

/**
 * 모바일 앱에서 전달되는 데이터를 수신하는 서비스
 * - 경기 데이터 (점수, 이닝, BSO 등)
 * - 팀 테마 정보 (팀 이름 → 워치 테마 변경)
 * - 햅틱 이벤트 트리거
 */
class DataLayerListenerService : WearableListenerService() {
    
    companion object {
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
        
        // 경기 데이터 추출
        val gameId = dataMap.getString(KEY_GAME_ID, "")
        val homeTeam = dataMap.getString(KEY_HOME_TEAM, "")
        val awayTeam = dataMap.getString(KEY_AWAY_TEAM, "")
        val homeScore = dataMap.getInt(KEY_HOME_SCORE, 0)
        val awayScore = dataMap.getInt(KEY_AWAY_SCORE, 0)
        val inning = dataMap.getString(KEY_INNING, "")
        val ball = dataMap.getInt(KEY_BALL, 0)
        val strike = dataMap.getInt(KEY_STRIKE, 0)
        val out = dataMap.getInt(KEY_OUT, 0)
        val pitcher = dataMap.getString(KEY_PITCHER, "")
        val batter = dataMap.getString(KEY_BATTER, "")
        val myTeam = dataMap.getString(KEY_MY_TEAM, "")
        
        // TODO: 수신된 데이터를 ViewModel이나 SharedPreferences에 저장하여 UI 업데이트
        // 예: GameRepository.updateGameData(...)
        
        // 이벤트 타입이 있으면 햅틱 피드백
        val eventType = dataMap.getString(KEY_EVENT_TYPE, null)
        if (eventType != null) {
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
        
        // TODO: SharedPreferences에 teamName 저장
        // → MainActivity에서 읽어서 BaseHapticWatchTheme(teamName = ...)에 전달
        
        // 예시:
        // getSharedPreferences("theme", MODE_PRIVATE)
        //     .edit()
        //     .putString("team_name", teamName)
        //     .apply()
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
        // TODO: 이벤트 유형에 따른 진동 패턴 구현
        // HOMERUN → 강한 연속 진동
        // SCORE → 중간 진동
        // STRIKE → 짧은 진동
        // etc.
    }
}
