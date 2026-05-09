package com.basehaptic.mobile.push

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.basehaptic.mobile.MainActivity
import com.basehaptic.mobile.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BaseHapticMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed: ${token.take(16)}...")
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
        TeamSubscriptionRegistrar.syncIfNeeded(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val notification = message.notification
        val data = message.data
        val title = notification?.title ?: data["title"] ?: return
        val body = notification?.body ?: data["body"] ?: ""
        val gameId = data["game_id"]
        val homeTeam = data["home_team"]
        val awayTeam = data["away_team"]

        showNotification(
            title = title,
            body = body,
            gameId = gameId,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        gameId: String?,
        homeTeam: String?,
        awayTeam: String?,
    ) {
        NotificationChannels.ensureCreated(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            gameId?.let { putExtra(EXTRA_GAME_ID, it) }
            homeTeam?.let { putExtra(EXTRA_HOME_TEAM, it) }
            awayTeam?.let { putExtra(EXTRA_AWAY_TEAM, it) }
        }
        val baseRequestCode = gameId?.hashCode() ?: 0
        val pendingIntent = PendingIntent.getActivity(
            this,
            baseRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 액션 버튼용 별도 PendingIntent (request code 만 다르게 — 동일 intent 라도 액션 식별 분리)
        val actionPendingIntent = PendingIntent.getActivity(
            this,
            baseRequestCode xor 0x1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, NotificationChannels.GAME_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "관람하기", actionPendingIntent)

        val notificationId = gameId?.hashCode() ?: System.currentTimeMillis().toInt()
        try {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification post denied (POST_NOTIFICATIONS permission missing)", se)
        }
    }

    companion object {
        private const val TAG = "BaseHapticFCM"
        const val PREFS_NAME = "fcm_prefs"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val EXTRA_GAME_ID = "extra_game_id"
        const val EXTRA_HOME_TEAM = "extra_home_team"
        const val EXTRA_AWAY_TEAM = "extra_away_team"
    }
}
