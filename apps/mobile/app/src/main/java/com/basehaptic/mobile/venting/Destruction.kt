package com.basehaptic.mobile.venting

import android.content.Context
import com.basehaptic.mobile.R

/**
 * 분풀이 룸 파괴 상태머신 상수 (iOS DestructionConstants 포팅).
 * Phase 2에서 밸런스 조정 시 이 object 안의 값만 수정한다.
 */
object DestructionConstants {
    /** 균열 단계 진입 게이지 임계값 (33%) */
    const val THRESHOLD1 = 0.33
    /** 터짐 단계 진입 게이지 임계값 (66%) */
    const val THRESHOLD2 = 0.66
    /** 완파 게이지 값 (100%) */
    const val COMPLETE = 1.0
    /** 탭 1회당 게이지 증가량 (= 1/40). 40탭에 완파. */
    const val TAP_INCREMENT = 1.0 / 40.0
}

/**
 * 분풀이 룸 파괴 단계. 게이지 임계값에 따라 자동 결정된다:
 * idle(0~0.33) → cracked(0.33~0.66) → burst(0.66~1.0) → destroyed(1.0)
 */
enum class DestructionStage(val dollDrawableRes: Int) {
    IDLE(R.drawable.venting_doll_normal),
    CRACKED(R.drawable.venting_doll_crack),
    BURST(R.drawable.venting_doll_burst),
    DESTROYED(R.drawable.venting_doll_destroyed);

    companion object {
        fun fromGauge(gauge: Double): DestructionStage = when {
            gauge >= DestructionConstants.COMPLETE -> DESTROYED
            gauge >= DestructionConstants.THRESHOLD2 -> BURST
            gauge >= DestructionConstants.THRESHOLD1 -> CRACKED
            else -> IDLE
        }
    }
}

/**
 * 분풀이 룸 파괴 상태머신 (iOS DestructionStateMachine 포팅).
 *
 * 탭 1회 = 게이지 [DestructionConstants.TAP_INCREMENT] 증가.
 * 완파(gauge = 1.0) 시 경기당 첫 완파 여부를 SharedPreferences에 기록한다.
 * reset() 후에도 SharedPreferences 기록은 유지된다(Phase 2 광고 게이트 재사용).
 */
class DestructionStateMachine(context: Context, private val gameId: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("basehaptic_user_prefs", Context.MODE_PRIVATE)

    var gauge: Double = 0.0
        private set

    var stage: DestructionStage = DestructionStage.IDLE
        private set

    private val firstDestructionKey: String
        get() = "venting_first_destruction_$gameId"

    /**
     * 탭 1회를 처리한다.
     * @return 단계가 전환된 경우 새 단계, 아닌 경우 null. 완파 상태에서 탭하면 즉시 null.
     */
    fun tap(): DestructionStage? {
        if (stage == DestructionStage.DESTROYED) return null

        val oldStage = stage
        gauge = minOf(gauge + DestructionConstants.TAP_INCREMENT, DestructionConstants.COMPLETE)
        stage = DestructionStage.fromGauge(gauge)

        if (stage == oldStage) return null

        if (stage == DestructionStage.DESTROYED) {
            recordFirstDestructionIfNeeded()
        }
        return stage
    }

    /** 게이지와 단계를 초기 상태(0, IDLE)로 되돌린다. SharedPreferences 기록은 유지. */
    fun reset() {
        gauge = 0.0
        stage = DestructionStage.IDLE
    }

    val hasRecordedFirstDestruction: Boolean
        get() = prefs.getBoolean(firstDestructionKey, false)

    private fun recordFirstDestructionIfNeeded() {
        if (prefs.getBoolean(firstDestructionKey, false)) return
        prefs.edit().putBoolean(firstDestructionKey, true).apply()
    }
}
