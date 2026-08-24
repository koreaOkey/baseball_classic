package com.basehaptic.mobile.venting

import android.content.Context

/**
 * 분풀이 모드 피처 플래그: 릴리즈 포함 기본 ON (iOS VentingFeatureFlag 포팅).
 * 토글은 설정의 DEBUG 섹션에서만 노출되는 로컬 킬스위치 — 릴리즈 사용자는 항상 ON이다.
 */
object VentingFeatureFlag {

    private const val PREFS_NAME = "basehaptic_user_prefs"
    // v2: 이전 테스트에서 저장된 stale false 값을 무시하기 위해 키를 올린다.
    // (구 키 "venting_mode_enabled"에 false가 남아 기본 ON이 덮여 안 보이던 문제 회피)
    private const val KEY = "venting_mode_enabled_v2"

    fun isEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY, true)  // 기본 ON
    }

    /** 로컬 토글 On/Off (DEBUG 설정 섹션 전용). */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }
}
