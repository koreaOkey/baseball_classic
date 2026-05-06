package com.basehaptic.watch.ui

// TODO(stadium-cheer): 활성화 시 MainActivity 또는 NavHost에 overlay 트리거 연결.
// 다크 머지 단계에서는 화면 + Coordinator 정의만 두고 호출되지 않음.

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class StadiumCheerPayload(
    val teamCode: String,
    val cheerText: String,
    val primaryColorHex: String,
    val hapticPatternId: String,
)

object StadiumCheerOverlayCoordinator {
    val current = mutableStateOf<StadiumCheerPayload?>(null)

    fun dispatch(context: Context, payload: StadiumCheerPayload) {
        current.value = payload
        playHaptic(context, payload.hapticPatternId)
        android.os.Handler(context.mainLooper).postDelayed({
            if (current.value == payload) current.value = null
        }, 6_000L)
    }

    private fun playHaptic(context: Context, patternId: String) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val (pattern, amplitudes) = cheerPattern(patternId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun cheerPattern(patternId: String): Pair<LongArray, IntArray> {
        return when {
            patternId.contains("silent", ignoreCase = true) -> {
                longArrayOf(0) to intArrayOf(0)
            }
            else -> {
                longArrayOf(0, 200, 150, 200, 150, 200) to
                    intArrayOf(0, 255, 0, 255, 0, 255)
            }
        }
    }
}

@Composable
fun StadiumCheerScreen(payload: StadiumCheerPayload) {
    val bg = parseHex(payload.primaryColorHex)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = payload.teamCode,
                color = Color.White.copy(alpha = 0.85f),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = payload.cheerText,
                color = Color.White,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun parseHex(hex: String): Color {
    val sanitized = if (hex.startsWith("#")) hex.drop(1) else hex
    val value = sanitized.toLongOrNull(16) ?: 0x3B82F6L
    val r = ((value shr 16) and 0xFF) / 255f
    val g = ((value shr 8) and 0xFF) / 255f
    val b = (value and 0xFF) / 255f
    return Color(r, g, b)
}
