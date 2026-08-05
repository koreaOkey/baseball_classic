package com.basehaptic.watch.venting

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.basehaptic.watch.R

/**
 * 워치 분풀이 lite 코어 (폰 venting/Destruction.kt 축소 포팅).
 *
 * 폰과 동일한 게이지 밸런스(40히트 완파, 33%/66% 단계 전환)를 유지하되,
 * 워치 배터리·모터 특성에 맞춰 경진동은 히트 3회당 1회만 재생한다.
 */
object WatchVentingConstants {
    const val THRESHOLD1 = 0.33
    const val THRESHOLD2 = 0.66
    const val COMPLETE = 1.0

    /** 히트(탭·로터리 환산) 1회당 게이지 증가량 — 폰 TAP_INCREMENT 동일 */
    const val HIT_INCREMENT = 1.0 / 40.0

    /** 로터리(베젤/크라운) 스크롤 픽셀 → 히트 1회 환산 단위 */
    const val ROTARY_PX_PER_HIT = 36f

    /** 경진동 재생 간격 (히트 수 기준) */
    const val TAP_HAPTIC_EVERY_HITS = 3
}

enum class WatchDestructionStage(val dollRes: Int) {
    IDLE(R.drawable.venting_doll_normal),
    CRACKED(R.drawable.venting_doll_crack),
    BURST(R.drawable.venting_doll_burst),
    DESTROYED(R.drawable.venting_doll_destroyed);

    companion object {
        fun fromGauge(gauge: Double): WatchDestructionStage = when {
            gauge >= WatchVentingConstants.COMPLETE -> DESTROYED
            gauge >= WatchVentingConstants.THRESHOLD2 -> BURST
            gauge >= WatchVentingConstants.THRESHOLD1 -> CRACKED
            else -> IDLE
        }
    }
}

/** 폰 → 워치 분풀이 트리거 요청 (테스트 도구 발송 payload). */
data class WatchVentingRequest(
    val gameId: String,
    val targetLabel: String,
    val eventDescription: String,
    val requestedAtMs: Long
)

/** 워치 분풀이 진동 재생기 — 폰 VentingHapticPlayer 패턴의 워치판. */
class WatchVentingHapticPlayer(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun playStageTransition(stage: WatchDestructionStage) {
        when (stage) {
            WatchDestructionStage.IDLE -> Unit
            WatchDestructionStage.CRACKED -> oneShot(40, 200)
            WatchDestructionStage.BURST -> oneShot(55, 230)
            WatchDestructionStage.DESTROYED -> {
                val timings = longArrayOf(0, 40, 60, 40, 60, 120)
                val amplitudes = intArrayOf(0, 200, 0, 220, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        }
    }

    fun playHitFeedback() {
        oneShot(15, 130)
    }

    private fun oneShot(durationMs: Long, amplitude: Int) {
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    }
}

/**
 * 워치 분풀이 룸 상태 홀더 (폰 VentingRoomState 축소 포팅).
 * 탭·로터리 입력을 히트 수로 통일해 받는다.
 */
class WatchVentingState(context: Context, private val gameId: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("watch_user_prefs", Context.MODE_PRIVATE)
    private val hapticPlayer = WatchVentingHapticPlayer(context)

    var gauge by mutableStateOf(0.0)
        private set
    var stage by mutableStateOf(WatchDestructionStage.IDLE)
        private set
    var isDestroyed by mutableStateOf(false)
        private set

    private var hitsSinceLightHaptic = 0

    fun recordHits(hits: Int) {
        if (isDestroyed) return

        var transition: WatchDestructionStage? = null
        repeat(hits.coerceIn(1, 5)) {
            if (gauge >= WatchVentingConstants.COMPLETE) return@repeat
            val oldStage = stage
            gauge = minOf(gauge + WatchVentingConstants.HIT_INCREMENT, WatchVentingConstants.COMPLETE)
            stage = WatchDestructionStage.fromGauge(gauge)
            if (stage != oldStage) transition = stage
        }

        val newStage = transition
        if (newStage != null) {
            hapticPlayer.playStageTransition(newStage)
            hitsSinceLightHaptic = 0
            if (newStage == WatchDestructionStage.DESTROYED) {
                isDestroyed = true
                recordFirstDestructionIfNeeded()
            }
        } else {
            hitsSinceLightHaptic += hits
            if (hitsSinceLightHaptic >= WatchVentingConstants.TAP_HAPTIC_EVERY_HITS) {
                hitsSinceLightHaptic = 0
                hapticPlayer.playHitFeedback()
            }
        }
    }

    fun reset() {
        gauge = 0.0
        stage = WatchDestructionStage.IDLE
        isDestroyed = false
        hitsSinceLightHaptic = 0
    }

    private fun recordFirstDestructionIfNeeded() {
        val key = "venting_first_destruction_$gameId"
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
    }
}
