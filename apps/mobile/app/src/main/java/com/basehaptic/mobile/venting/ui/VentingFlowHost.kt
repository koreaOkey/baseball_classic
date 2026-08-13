package com.basehaptic.mobile.venting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.venting.VentingGameContext

/**
 * 분풀이 플로우 전역 요청 상태 (홈카드·라이브 진입 공용).
 *
 * 풀스크린 Dialog는 targetSdk 35 엣지-투-엣지 강제 환경에서 창이 상태바만큼
 * 밀려 하단이 잘리는 문제가 있어, 액티비티 루트에 직접 그리는 오버레이로 대체한다
 * (MainActivity 루트의 [VentingFlowHost]가 렌더링 — 앱 본체와 같은 윈도우라
 * 인셋 동작이 다른 화면과 동일하게 보장된다).
 */
object VentingFlowController {
    data class Request(
        val context: VentingGameContext,
        val backLabel: String,
        /** 진입 경로 지표 (room_enter 등 6.1 metrics의 entry_source). */
        val entrySource: String
    )

    var request by mutableStateOf<Request?>(null)
        private set

    fun open(context: VentingGameContext, backLabel: String = "홈", entrySource: String = "unknown") {
        request = Request(context, backLabel, entrySource)
    }

    fun close() {
        request = null
    }
}

/** MainActivity 루트 Box에 배치되는 분풀이 플로우 오버레이. 요청이 없으면 아무것도 그리지 않는다. */
@Composable
fun VentingFlowHost() {
    val request = VentingFlowController.request ?: return
    BackHandler { VentingFlowController.close() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 아래 화면으로 터치가 새지 않도록 전면 소비 (리플 없음)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .background(Gray950)
            .safeDrawingPadding()
    ) {
        VentingFlowCoordinator(
            context = request.context,
            onClose = { VentingFlowController.close() },
            backLabel = request.backLabel,
            entrySource = request.entrySource
        )
    }
}
