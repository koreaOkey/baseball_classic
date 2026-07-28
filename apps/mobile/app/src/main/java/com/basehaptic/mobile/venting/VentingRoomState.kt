package com.basehaptic.mobile.venting

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 분풀이 룸 상태 홀더 (iOS VentingRoomViewModel 포팅).
 *
 * [DestructionStateMachine]을 Compose 상태에 연결하고,
 * [VentingHapticPlayer]를 통해 단계별 진동을 구동한다.
 *
 * 진동 결속 규칙 (AC 4.1):
 * - 탭 (stage 전이 없음)    → playTapFeedback() (스로틀 50ms)
 * - IDLE→CRACKED 등 단계 전이 → playStageTransition(새 단계)
 * - 완파 후 탭               → 무시
 */
class VentingRoomState(
    context: Context,
    val gameContext: VentingGameContext,
    val selectedTarget: VentingTarget
) {
    private val machine = DestructionStateMachine(context, gameContext.gameId)
    private val hapticPlayer = VentingHapticPlayer(context)

    var gauge by mutableStateOf(0.0)
        private set
    var stage by mutableStateOf(DestructionStage.IDLE)
        private set
    var isDestroyed by mutableStateOf(false)
        private set

    /** 탭 경진동 최소 간격 (50ms). 스로틀은 tapFeedback에만 적용. */
    private var lastTapHapticElapsedMs = 0L

    fun recordTap() {
        if (isDestroyed) return

        val transitioned = machine.tap()
        gauge = machine.gauge
        stage = machine.stage

        if (transitioned != null) {
            hapticPlayer.playStageTransition(transitioned)
            if (transitioned == DestructionStage.DESTROYED) {
                isDestroyed = true
            }
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTapHapticElapsedMs >= 50) {
                hapticPlayer.playTapFeedback()
                lastTapHapticElapsedMs = now
            }
        }
    }

    /** 게이지를 0으로 초기화한다. SharedPreferences 기록은 유지된다. */
    fun reset() {
        machine.reset()
        gauge = 0.0
        stage = DestructionStage.IDLE
        isDestroyed = false
    }

    /** 현재 경기에서 첫 완파 기록 여부. */
    val isFirstDestructionRecorded: Boolean
        get() = machine.hasRecordedFirstDestruction
}
