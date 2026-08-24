package com.basehaptic.mobile.venting

import android.content.Context
import com.basehaptic.mobile.ui.components.RewardedAdFormat
import com.basehaptic.mobile.ui.components.RewardedAdManager
import com.basehaptic.mobile.ui.components.RewardedAdResult
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 재도전 게이트 판정 결과 (iOS VentingRetryVerdict 포팅). */
enum class VentingRetryVerdict {
    /** Rewarded 광고 보상 획득 → 재도전 허용 (지표: retry_ad_complete) */
    AD_REWARDED,

    /** 광고 없이 허용 (광고 로드 실패 폴백 — 사용자 귀책 아님) */
    ALLOWED_FREE,

    /** 광고 중도 이탈·중복 요청 → 재도전 거부 */
    DENIED,
}

/**
 * 분풀이 재도전 Rewarded 광고 게이트 (iOS RewardedAdGate 포팅).
 *
 * 경기당 첫 완파는 무료(진입 게이트 없음), 재도전(재파괴)은 매번 광고 1회 시청 후 허용.
 * 광고 로드 실패(no-fill·네트워크 등)는 사용자 귀책이 아니므로 재도전을 허용한다
 * (테마 스토어·워치 동기화 게이트와 동일 정책).
 */
object VentingRetryGate {

    suspend fun requestRetry(activityContext: Context): VentingRetryVerdict =
        suspendCancellableCoroutine { cont ->
            RewardedAdManager.loadAndShowAd(
                context = activityContext,
                adUnitId = RewardedAdManager.VENTING_RETRY_AD_UNIT,
                format = RewardedAdFormat.REWARDED
            ) { result ->
                val verdict = when (result) {
                    RewardedAdResult.REWARD_EARNED -> VentingRetryVerdict.AD_REWARDED
                    RewardedAdResult.LOAD_FAILED -> VentingRetryVerdict.ALLOWED_FREE
                    RewardedAdResult.DISMISSED_WITHOUT_REWARD,
                    RewardedAdResult.BUSY -> VentingRetryVerdict.DENIED
                }
                if (cont.isActive) cont.resume(verdict)
            }
        }
}
