package com.basehaptic.mobile.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

object PushSetup {
    private const val TAG = "BaseHapticFCM"

    fun initialize(activity: ComponentActivity) {
        NotificationChannels.ensureCreated(activity)
        ensureNotificationPermission(activity)
        fetchTokenAndSync(activity.applicationContext)
    }

    private fun ensureNotificationPermission(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
        }
        launcher.launch(permission)
    }

    /**
     * FCM 토큰을 직접 fetch 해서 SharedPreferences 에 저장하고 백엔드 동기화 시도.
     *
     * onNewToken 은 토큰이 변경될 때만 호출되므로 (재설치/이미 발급된 토큰) 첫
     * 등록 흐름에 의존할 수 없다. 매 앱 시작 시 명시적으로 fetch + 저장 + sync 한다.
     */
    private fun fetchTokenAndSync(context: Context) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to fetch FCM token", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.i(TAG, "FCM token: $token")
            context.getSharedPreferences(BaseHapticMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(BaseHapticMessagingService.KEY_FCM_TOKEN, token)
                .apply()
            TeamSubscriptionRegistrar.syncIfNeeded(context)
        }
    }
}
