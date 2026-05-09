package com.basehaptic.mobile.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 푸시 알림 탭으로 인한 Activity 진입 시 ComposeUI 에 게임 정보를 전달하는 작은 버스.
 *
 * Android 의 onNewIntent 흐름은 Activity-level 콜백이라 Compose state 와 직결되지 않는다.
 * MainActivity 에서 [post] 로 emit 하고, BaseHapticApp Composable 이 [pending] 을
 * collect 한 뒤 [consume] 으로 비운다.
 */
object NotificationIntentBus {
    data class PendingIntent(
        val gameId: String,
        val homeTeam: String?,
        val awayTeam: String?,
    )

    private val _pending = MutableStateFlow<PendingIntent?>(null)
    val pending: StateFlow<PendingIntent?> = _pending.asStateFlow()

    fun post(gameId: String, homeTeam: String?, awayTeam: String?) {
        if (gameId.isBlank()) return
        _pending.value = PendingIntent(gameId, homeTeam, awayTeam)
    }

    fun consume() {
        _pending.value = null
    }
}
