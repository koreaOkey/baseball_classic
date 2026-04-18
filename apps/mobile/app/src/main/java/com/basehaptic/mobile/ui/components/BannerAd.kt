package com.basehaptic.mobile.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private val BANNER_AD_UNIT_ID = if (com.basehaptic.mobile.BuildConfig.DEBUG) {
    "ca-app-pub-3940256099942544/6300978111" // Google 테스트용
} else {
    "ca-app-pub-7935544989894266/1331426409" // 프로덕션
}

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = BANNER_AD_UNIT_ID
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
