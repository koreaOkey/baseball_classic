package com.basehaptic.mobile.push

import android.Manifest
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
        logCurrentToken()
        TeamSubscriptionRegistrar.syncIfNeeded(activity)
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

    private fun logCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.i(TAG, "FCM token: ${task.result}")
            } else {
                Log.w(TAG, "Failed to fetch FCM token", task.exception)
            }
        }
    }
}
