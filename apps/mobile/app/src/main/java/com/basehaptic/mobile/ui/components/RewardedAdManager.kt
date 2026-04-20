package com.basehaptic.mobile.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import com.basehaptic.mobile.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "RewardedAdManager"

// 테스트용 광고 단위 ID (디버그 빌드에서 사용)
private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
// 프로덕션 광고 단위 ID
private const val PROD_AD_UNIT_ID = "ca-app-pub-7935544989894266/3246911798"

private val AD_UNIT_ID = if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID

object RewardedAdManager {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAndShowAd(context: Context, onRewardEarned: () -> Unit) {
        if (_isLoading.value) return
        _isLoading.value = true

        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Load failed: ${error.message}")
                _isLoading.value = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                _isLoading.value = false
                val activity = context as? Activity
                if (activity == null) {
                    Log.e(TAG, "Context is not an Activity")
                    return
                }
                ad.show(activity) {
                    Log.d(TAG, "User earned reward")
                    onRewardEarned()
                }
            }
        })
    }
}
