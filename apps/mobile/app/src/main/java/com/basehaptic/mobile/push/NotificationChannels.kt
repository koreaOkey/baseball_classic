package com.basehaptic.mobile.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val GAME_ALERTS_ID = "game_alerts"
    const val LIVE_SCORE_ID = "live_score_card"
    const val TEST_PUSH_ID = "test_push"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(GAME_ALERTS_ID) == null) {
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
        if (manager.getNotificationChannel(LIVE_SCORE_ID) == null) {
            val channel = NotificationChannel(
                LIVE_SCORE_ID,
                "라이브 스코어",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "라이브 경기 진행 중 스코어·이닝·BSO 진행 상태 표시"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(TEST_PUSH_ID) == null) {
            val channel = NotificationChannel(
                TEST_PUSH_ID,
                "푸시 시뮬레이션 테스트",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "워치 테스트 메뉴에서 발사하는 로컬 푸시 (잠금화면 노출)"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}
