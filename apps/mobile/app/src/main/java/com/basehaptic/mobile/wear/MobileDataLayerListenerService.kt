package com.basehaptic.mobile.wear

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.basehaptic.mobile.MainActivity
import com.basehaptic.mobile.R
import com.basehaptic.mobile.push.NotificationChannels
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

            // 워치에서 수락 시에도 보상형 광고 게이트를 통과해야 한다.
            // 앱이 떠 있으면 broadcast 로 처리되고, 백그라운드/종료 상태면 알림 탭으로 이어간다.
            if (accepted) {
                notifyPhoneAdRequired(gameId)
            }
            sendBroadcast(Intent(WearWatchSyncBridge.ACTION_WATCH_SYNC_RESPONSE))
        }
    }

    private fun notifyPhoneAdRequired(gameId: String) {
        NotificationChannels.ensureCreated(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            gameId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.GAME_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("워치 관람 광고 확인")
            .setContentText("휴대폰에서 광고 확인 후 자동 관람됩니다.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(gameId.hashCode(), notification)
            Log.d(TAG, "Posted phone ad confirmation notification: gameId=$gameId")
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification permission missing; pending watch response stored", error)
        }
    }
}
