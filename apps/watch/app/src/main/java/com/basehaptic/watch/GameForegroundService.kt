package com.basehaptic.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 경기 관람 중 Doze 모드에서도 햅틱 이벤트 수신을 보장하는 포그라운드 서비스.
 * 경기가 시작되면 start(), 종료되면 stop()을 호출한다.
 */
class GameForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "game_foreground_channel"
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GameForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameForegroundService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BaseHaptic")
            .setContentText("경기 관람 중")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "경기 진행 중",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "경기 실시간 동기화 알림"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
