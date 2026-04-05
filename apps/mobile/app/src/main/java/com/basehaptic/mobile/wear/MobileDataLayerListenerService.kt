package com.basehaptic.mobile.wear

import android.content.Context
import android.content.Intent
import android.util.Log
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.service.GameSyncForegroundService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class MobileDataLayerListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "MobileDataLayerListener"
        private const val PATH_WATCH_SYNC_RESPONSE = "/watch/sync-response"
        private const val KEY_GAME_ID = "game_id"
        private const val KEY_ACCEPTED = "accepted"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            val path = item.uri.path ?: return@forEach
            if (!path.startsWith(PATH_WATCH_SYNC_RESPONSE)) return@forEach

            val dataMap = DataMapItem.fromDataItem(item).dataMap
            val gameId = dataMap.getString(KEY_GAME_ID, "")
            if (gameId.isBlank()) return@forEach
            val accepted = dataMap.getBoolean(KEY_ACCEPTED, false)
            WearWatchSyncBridge.savePendingResponse(
                context = this,
                gameId = gameId,
                accepted = accepted
            )
            sendBroadcast(Intent(WearWatchSyncBridge.ACTION_WATCH_SYNC_RESPONSE))

            // 워치에서 수락 시 → 앱이 백그라운드/종료 상태여도 스트리밍 서비스 직접 시작
            if (accepted) {
                startStreamingFromWatch(gameId)
            }
        }
    }

    private fun startStreamingFromWatch(gameId: String) {
        val prefs = getSharedPreferences("basehaptic_user_prefs", Context.MODE_PRIVATE)
        val teamName = prefs.getString("selected_team", null).orEmpty()
        val team = Team.fromString(teamName)
        if (team == Team.NONE) {
            Log.w(TAG, "No saved team, cannot start streaming from watch")
            return
        }

        Log.d(TAG, "Starting streaming from watch accept: gameId=$gameId, team=$teamName")
        val intent = Intent(this, GameSyncForegroundService::class.java).apply {
            action = GameSyncForegroundService.ACTION_START_STREAMING
            putExtra(GameSyncForegroundService.EXTRA_GAME_ID, gameId)
            putExtra(GameSyncForegroundService.EXTRA_SELECTED_TEAM, teamName)
        }
        startForegroundService(intent)
    }
}
