package com.basehaptic.mobile.wear

import android.content.Intent
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class MobileDataLayerListenerService : WearableListenerService() {
    companion object {
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
        }
    }
}
