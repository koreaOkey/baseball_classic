package com.basehaptic.mobile.venting

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * 분풀이 룸 흔들기 감지기.
 *
 * 선형가속도(중력 제거) 크기가 임계값을 넘는 순간을 "흔들기 버스트" 1회로 보고,
 * 세기에 비례한 타격 수(1~3)를 콜백으로 전달한다 — 세게 흔들수록 데미지가 크다.
 * 룸 화면이 보이는 동안만 start/stop 으로 센서를 점유한다.
 */
class VentingShakeDetector(
    context: Context,
    private val onShakeBurst: (hitCount: Int) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // 선형가속도 센서가 없는 기기는 가속도계 + 저역통과 중력 제거로 폴백
    private val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravity = FloatArray(3)
    private var lastBurstElapsedMs = 0L

    fun start() {
        val sensor = linearSensor ?: rawSensor ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            Triple(event.values[0], event.values[1], event.values[2])
        } else {
            // 저역통과로 중력 성분 추정 후 제거
            for (i in 0..2) gravity[i] = gravity[i] * GRAVITY_FILTER + event.values[i] * (1 - GRAVITY_FILTER)
            Triple(
                event.values[0] - gravity[0],
                event.values[1] - gravity[1],
                event.values[2] - gravity[2]
            )
        }

        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < SHAKE_THRESHOLD) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBurstElapsedMs < BURST_DEBOUNCE_MS) return
        lastBurstElapsedMs = now

        // 세기 → 타격 수: 임계값 초과분 8m/s² 당 +1, 최대 3타
        val hits = (1 + ((magnitude - SHAKE_THRESHOLD) / 8f).toInt()).coerceIn(1, 3)
        onShakeBurst(hits)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** 흔들기 인정 최소 선형가속도 (m/s²). 일상 동작 오탐 방지 수준. */
        const val SHAKE_THRESHOLD = 12f
        /** 버스트 간 최소 간격 — 한 번의 왕복 흔들기가 다중 인식되는 것 방지. */
        const val BURST_DEBOUNCE_MS = 220L
        const val GRAVITY_FILTER = 0.8f
    }
}
