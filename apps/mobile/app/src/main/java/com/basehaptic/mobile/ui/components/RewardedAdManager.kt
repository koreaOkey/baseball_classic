package com.basehaptic.mobile.ui.components

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.basehaptic.mobile.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "RewardedAdManager"

enum class RewardedAdFormat {
    REWARDED,
    REWARDED_INTERSTITIAL,
}

/**
 * 보상형 광고 결과.
 *
 * 광고 게이트 정책:
 * - REWARD_EARNED: 보상 획득 → 혜택 지급 + 시청 기록 저장.
 * - LOAD_FAILED: 광고 자체가 로드/표시 불가(no-fill, 네트워크, Activity 소멸 등)
 *   → 사용자 귀책이 아니므로 혜택은 지급하되 시청 기록은 남기지 않는다.
 * - DISMISSED_WITHOUT_REWARD: 사용자가 보상 전에 광고를 닫음 → 혜택 지급 거부.
 * - BUSY: 이미 다른 광고 요청 진행 중(중복 탭) → 아무 것도 하지 않는다
 *   (혜택 지급도, pending 상태 소비도 없음).
 */
enum class RewardedAdResult {
    REWARD_EARNED,
    LOAD_FAILED,
    DISMISSED_WITHOUT_REWARD,
    BUSY,
}

object RewardedAdManager {

    private const val REWARDED_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"

    private const val THEME_STORE_AD_UNIT_PROD = "ca-app-pub-7935544989894266/3246911798"
    private const val WATCH_SYNC_AD_UNIT_PROD = "ca-app-pub-7935544989894266/8231864339"
    private const val LIVE_SCORE_AD_UNIT_PROD = "ca-app-pub-7935544989894266/5260195991"
    private const val VENTING_RETRY_AD_UNIT_PROD = "ca-app-pub-7935544989894266/9256313538"

    val THEME_STORE_AD_UNIT: String =
        if (BuildConfig.DEBUG) REWARDED_TEST_AD_UNIT_ID else THEME_STORE_AD_UNIT_PROD

    val WATCH_SYNC_AD_UNIT: String =
        if (BuildConfig.DEBUG) REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID else WATCH_SYNC_AD_UNIT_PROD

    val LIVE_SCORE_AD_UNIT: String =
        if (BuildConfig.DEBUG) REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID else LIVE_SCORE_AD_UNIT_PROD

    /** 빠따존 재도전 게이트 (Rewarded) */
    val VENTING_RETRY_AD_UNIT: String =
        if (BuildConfig.DEBUG) REWARDED_TEST_AD_UNIT_ID else VENTING_RETRY_AD_UNIT_PROD

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 로드 콜백이 유실됐을 때 isLoading·전역 오버레이가 영구히 남지 않도록 하는 워치독 시한. */
    private const val LOAD_TIMEOUT_MS = 60_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadAndShowAd(
        context: Context,
        adUnitId: String,
        format: RewardedAdFormat = RewardedAdFormat.REWARDED,
        onComplete: (RewardedAdResult) -> Unit
    ) {
        if (_isLoading.value) {
            onComplete(RewardedAdResult.BUSY)
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            Log.e(TAG, "Context is not an Activity")
            onComplete(RewardedAdResult.LOAD_FAILED)
            return
        }
        _isLoading.value = true

        // 비동기 로드 동안 Activity 를 강참조로 붙잡지 않도록 WeakReference 사용.
        // 로드 중에는 applicationContext 만 사용한다.
        val activityRef = WeakReference(activity)
        val appContext = context.applicationContext
        val adRequest = AdRequest.Builder().build()

        // isLoading 은 어떤 경로로든 반드시 해제하고, 종단 콜백은 정확히 한 번만 전달한다
        // (중복 콜백이 그대로 흐르면 호출부 게이트가 이중 지급된다).
        // AdMob 콜백과 워치독 모두 메인 스레드에서 실행되므로 플래그에 별도 동기화는 불필요.
        var settled = false
        var loadFinished = false
        val complete: (RewardedAdResult) -> Unit = { result ->
            if (!settled) {
                settled = true
                _isLoading.value = false
                onComplete(result)
            }
        }
        // 로드 콜백 도착 표시. 워치독이 이미 마감했으면 false — 늦게 온 광고는 표시하지 않는다.
        val markLoadFinished: () -> Boolean = {
            loadFinished = true
            !settled
        }
        mainHandler.postDelayed({
            if (!loadFinished) {
                Log.w(TAG, "Ad load watchdog fired (${LOAD_TIMEOUT_MS}ms) — treating as LOAD_FAILED")
                complete(RewardedAdResult.LOAD_FAILED)
            }
        }, LOAD_TIMEOUT_MS)

        when (format) {
            RewardedAdFormat.REWARDED ->
                loadAndShowRewardedAd(appContext, activityRef, adUnitId, adRequest, markLoadFinished, complete)
            RewardedAdFormat.REWARDED_INTERSTITIAL ->
                loadAndShowRewardedInterstitialAd(
                    appContext, activityRef, adUnitId, adRequest, markLoadFinished, complete
                )
        }
    }

    /** 광고 표시가 가능한 살아있는 Activity 를 반환. 소멸/종료 중이면 null. */
    private fun resolveShowableActivity(activityRef: WeakReference<Activity>): Activity? {
        val activity = activityRef.get() ?: return null
        if (activity.isDestroyed || activity.isFinishing) return null
        return activity
    }

    private fun loadAndShowRewardedAd(
        appContext: Context,
        activityRef: WeakReference<Activity>,
        adUnitId: String,
        adRequest: AdRequest,
        markLoadFinished: () -> Boolean,
        onComplete: (RewardedAdResult) -> Unit
    ) {
        RewardedAd.load(appContext, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                markLoadFinished()
                Log.e(TAG, "Rewarded load failed: ${error.message}")
                onComplete(RewardedAdResult.LOAD_FAILED)
            }

            override fun onAdLoaded(ad: RewardedAd) {
                if (!markLoadFinished()) return
                val activity = resolveShowableActivity(activityRef)
                if (activity == null) {
                    // 로드 완료 시점에 Activity 가 이미 죽었으면 표시 불가 → 로드 실패와 동일 취급
                    Log.w(TAG, "Activity gone before rewarded show")
                    onComplete(RewardedAdResult.LOAD_FAILED)
                    return
                }
                var rewardEarned = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        onComplete(
                            if (rewardEarned) RewardedAdResult.REWARD_EARNED
                            else RewardedAdResult.DISMISSED_WITHOUT_REWARD
                        )
                    }

                    override fun onAdFailedToShowFullScreenContent(
                        adError: com.google.android.gms.ads.AdError
                    ) {
                        Log.e(TAG, "Rewarded show failed: ${adError.message}")
                        onComplete(RewardedAdResult.LOAD_FAILED)
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
        appContext: Context,
        activityRef: WeakReference<Activity>,
        adUnitId: String,
        adRequest: AdRequest,
        markLoadFinished: () -> Boolean,
        onComplete: (RewardedAdResult) -> Unit
    ) {
        RewardedInterstitialAd.load(
            appContext,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    markLoadFinished()
                    Log.e(TAG, "Rewarded interstitial load failed: ${error.message}")
                    onComplete(RewardedAdResult.LOAD_FAILED)
                }

                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    if (!markLoadFinished()) return
                    val activity = resolveShowableActivity(activityRef)
                    if (activity == null) {
                        Log.w(TAG, "Activity gone before rewarded interstitial show")
                        onComplete(RewardedAdResult.LOAD_FAILED)
                        return
                    }
                    var rewardEarned = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onComplete(
                                if (rewardEarned) RewardedAdResult.REWARD_EARNED
                                else RewardedAdResult.DISMISSED_WITHOUT_REWARD
                            )
                        }

                        override fun onAdFailedToShowFullScreenContent(
                            adError: com.google.android.gms.ads.AdError
                        ) {
                            Log.e(TAG, "Rewarded interstitial show failed: ${adError.message}")
                            onComplete(RewardedAdResult.LOAD_FAILED)
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
