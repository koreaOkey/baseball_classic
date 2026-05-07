package com.basehaptic.mobile.wear

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object WatchInstallLauncher {
    private const val TAG = "WatchInstallLauncher"
    private const val PACKAGE_NAME = "com.basehaptic.mobile"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val WEAR_OS_APP_PACKAGE = "com.google.android.wearable.app"

    fun openWearOsCompanion(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(WEAR_OS_APP_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Wear OS companion launch failed", e)
            }
        }
        // Fallback: Play Store 의 Wear OS 앱 페이지로 보냄
        val webIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=$WEAR_OS_APP_PACKAGE")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(webIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No fallback for Wear OS companion launch", e)
        }
    }

    fun openPlayStoreForWatch(context: Context) {
        val marketIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$PACKAGE_NAME")
            setPackage(PLAY_STORE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(marketIntent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Play Store app not available, falling back to web", e)
        }

        val webIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(webIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No browser available for Play Store fallback", e)
        }
    }
}
