package com.basehaptic.mobile.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService

object NotificationChannels {
    const val GAME_ALERTS_ID = "game_alerts"
    const val LIVE_SCORE_ID = "live_score_card"
    const val LIVE_SCORE_ALERTS_ID = "live_score_alerts_v2"
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
        if (manager.getNotificationChannel(LIVE_SCORE_ALERTS_ID) == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                LIVE_SCORE_ALERTS_ID,
                "라이브 스코어 주요 이벤트",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "선택한 득점·홈런 등 주요 라이브 이벤트를 크게 표시"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 80, 180)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
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
