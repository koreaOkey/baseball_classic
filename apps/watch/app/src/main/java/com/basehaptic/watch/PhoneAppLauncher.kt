package com.basehaptic.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper

/**
 * 워치에서 폰 앱을 원격 실행하는 헬퍼.
 *
 * 동기화 수락 시 광고 게이트가 폰에서 진행되므로, 시스템 Wear 프레임워크의
 * "Open on phone" 경로(RemoteActivityHelper)로 폰 앱을 광고 플로우 딥링크로 띄운다.
 * 실행 실패 시에도 폰 쪽 MobileDataLayerListenerService 가 게시하는 고우선 알림이
 * 폴백으로 남아 있어 사용자는 알림 탭으로 이어갈 수 있다.
 */
object PhoneAppLauncher {
    private const val TAG = "PhoneAppLauncher"
    private const val WATCH_SYNC_DEEP_LINK = "basehaptic://watch-sync"

    fun launchForWatchSyncAd(context: Context) {
        runCatching {
            RemoteActivityHelper(context).startRemoteActivity(
                Intent(Intent.ACTION_VIEW)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setData(Uri.parse(WATCH_SYNC_DEEP_LINK))
            )
            Log.d(TAG, "Requested phone app launch for watch-sync ad")
        }.onFailure {
            Log.w(TAG, "Failed to launch phone app remotely (fallback: phone notification)", it)
        }
    }
}
