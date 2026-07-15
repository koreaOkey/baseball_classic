package com.basehaptic.mobile.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import com.basehaptic.mobile.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "RewardedAdManager"

enum class RewardedAdFormat {
    REWARDED,
    REWARDED_INTERSTITIAL,
}

object RewardedAdManager {

    private const val REWARDED_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"

    private const val THEME_STORE_AD_UNIT_PROD = "ca-app-pub-7935544989894266/3246911798"
    private const val WATCH_SYNC_AD_UNIT_PROD = "ca-app-pub-7935544989894266/8231864339"
    private const val LIVE_SCORE_AD_UNIT_PROD = "ca-app-pub-7935544989894266/5260195991"

    val THEME_STORE_AD_UNIT: String =
        if (BuildConfig.DEBUG) REWARDED_TEST_AD_UNIT_ID else THEME_STORE_AD_UNIT_PROD

    val WATCH_SYNC_AD_UNIT: String =
        if (BuildConfig.DEBUG) REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID else WATCH_SYNC_AD_UNIT_PROD

    val LIVE_SCORE_AD_UNIT: String =
        if (BuildConfig.DEBUG) REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID else LIVE_SCORE_AD_UNIT_PROD

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAndShowAd(
        context: Context,
        adUnitId: String,
        format: RewardedAdFormat = RewardedAdFormat.REWARDED,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        if (_isLoading.value) {
            onComplete(false)
            return
        }
        _isLoading.value = true

        val adRequest = AdRequest.Builder().build()

        when (format) {
            RewardedAdFormat.REWARDED -> loadAndShowRewardedAd(context, adUnitId, adRequest, onComplete)
            RewardedAdFormat.REWARDED_INTERSTITIAL -> loadAndShowRewardedInterstitialAd(context, adUnitId, adRequest, onComplete)
        }
    }

    private fun loadAndShowRewardedAd(
        context: Context,
        adUnitId: String,
        adRequest: AdRequest,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Rewarded load failed: ${error.message}")
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
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        onComplete(rewardEarned)
                    }

                    override fun onAdFailedToShowFullScreenContent(
                        adError: com.google.android.gms.ads.AdError
                    ) {
                        Log.e(TAG, "Rewarded show failed: ${adError.message}")
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

    private fun loadAndShowRewardedInterstitialAd(
        context: Context,
        adUnitId: String,
        adRequest: AdRequest,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded interstitial load failed: ${error.message}")
                    _isLoading.value = false
                    onComplete(false)
                }

                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    _isLoading.value = false
                    val activity = context as? Activity
                    if (activity == null) {
                        Log.e(TAG, "Context is not an Activity")
                        onComplete(false)
                        return
                    }
                    var rewardEarned = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onComplete(rewardEarned)
                        }

                        override fun onAdFailedToShowFullScreenContent(
                            adError: com.google.android.gms.ads.AdError
                        ) {
                            Log.e(TAG, "Rewarded interstitial show failed: ${adError.message}")
                            onComplete(false)
                        }
                    }
                    ad.show(activity) {
                        Log.d(TAG, "User earned rewarded interstitial reward")
                        rewardEarned = true
                    }
                }
            }
        )
    }
}
