package com.basehaptic.mobile.venting

import android.content.Context
import com.basehaptic.mobile.BuildConfig

/**
 * 분풀이 모드 피처 플래그: DEBUG 빌드 + 로컬 토글의 이중 게이트 (iOS VentingFeatureFlag 포팅).
 * 릴리즈 빌드에서는 [isEnabled]가 항상 false — 프로덕션 동작에 영향 없음.
 */
object VentingFeatureFlag {

    private const val PREFS_NAME = "basehaptic_user_prefs"
    private const val KEY = "venting_mode_enabled"

    fun isEnabled(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    /** 로컬 토글 On/Off (DEBUG 전용). */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }
}
