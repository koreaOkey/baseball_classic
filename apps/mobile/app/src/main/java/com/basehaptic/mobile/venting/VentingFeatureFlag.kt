package com.basehaptic.mobile.venting

import android.content.Context
import com.basehaptic.mobile.BuildConfig

/**
 * 분풀이 모드 피처 플래그: DEBUG 빌드 + 로컬 토글의 이중 게이트 (iOS VentingFeatureFlag 포팅).
 * 릴리즈 빌드에서는 [isEnabled]가 항상 false — 프로덕션 동작에 영향 없음.
 *
 * DEBUG 빌드에서는 기본 ON(토글을 명시적으로 끄지 않는 한) — 테스트 빌드에서 별도 토글 없이
 * 경기 상세 💢 진입점이 바로 보이도록. 설정 토글로 언제든 끌 수 있다.
 */
object VentingFeatureFlag {

    private const val PREFS_NAME = "basehaptic_user_prefs"
    private const val KEY = "venting_mode_enabled"

    fun isEnabled(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY, true)  // DEBUG 기본 ON
    }

    /** 로컬 토글 On/Off (DEBUG 전용). */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }
}
