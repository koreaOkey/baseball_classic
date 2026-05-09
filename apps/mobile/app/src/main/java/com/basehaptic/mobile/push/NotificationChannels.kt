package com.basehaptic.mobile.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val GAME_ALERTS_ID = "game_alerts"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(GAME_ALERTS_ID) != null) return
        val channel = NotificationChannel(
            GAME_ALERTS_ID,
            "경기 알림",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "응원팀 경기 시작 등 주요 경기 알림"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
