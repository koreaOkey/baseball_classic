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

object RewardedAdManager {

    private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private const val THEME_STORE_AD_UNIT_PROD = "ca-app-pub-7935544989894266/3246911798"
    private const val WATCH_SYNC_AD_UNIT_PROD = "ca-app-pub-7935544989894266/8231864339"

    val THEME_STORE_AD_UNIT: String =
        if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else THEME_STORE_AD_UNIT_PROD

    val WATCH_SYNC_AD_UNIT: String =
        if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else WATCH_SYNC_AD_UNIT_PROD

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAndShowAd(
        context: Context,
        adUnitId: String,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        if (_isLoading.value) {
            onComplete(false)
            return
        }
        _isLoading.value = true

        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Load failed: ${error.message}")
                _isLoading.value = false
                onComplete(false)
            }

            override fun onAdLoaded(ad: RewardedAd) {
                _isLoading.value = false
                val activity = context as? Activity
                if (activity == null) {
                    Log.e(TAG, "Context is not an Activity")
                    onComplete(false)
                    return
                }
                var rewardEarned = false
                ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        onComplete(rewardEarned)
                    }

                    override fun onAdFailedToShowFullScreenContent(
                        adError: com.google.android.gms.ads.AdError
                    ) {
                        Log.e(TAG, "Show failed: ${adError.message}")
                        onComplete(false)
                    }
                }
                ad.show(activity) {
                    Log.d(TAG, "User earned reward")
                    rewardEarned = true
                }
            }
        })
    }
}
