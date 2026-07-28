package com.basehaptic.mobile.venting

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 분풀이 룸 진동 재생기 (iOS UIKitVentingHapticPlayer 포팅).
 *
 * destruction_stage 전이별 진동 패턴:
 * - CRACKED  (균열): 중강도 40ms
 * - BURST    (터짐): 강강도 55ms
 * - DESTROYED(완파): 성공 웨이브폼 (짧게 2번 + 길게 1번)
 * - 일반 탭        : 경강도 20ms
 *
 * Phase 2에서 패턴·강도 조정 시 이 파일만 수정한다.
 */
class VentingHapticPlayer(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun playStageTransition(stage: DestructionStage) {
        when (stage) {
            DestructionStage.IDLE -> Unit // 대기 상태는 전이 대상 아님
            DestructionStage.CRACKED -> oneShot(40, 200)
            DestructionStage.BURST -> oneShot(55, 230)
            DestructionStage.DESTROYED -> {
                val timings = longArrayOf(0, 40, 60, 40, 60, 120)
                val amplitudes = intArrayOf(0, 200, 0, 220, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        }
    }

    /** 단계 전이 없는 일반 탭 경진동. */
    fun playTapFeedback() {
        oneShot(20, 150)
    }

    private fun oneShot(durationMs: Long, amplitude: Int) {
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    }
}
