package com.basehaptic.mobile.venting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.Red400
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

    // 딥링크(패배 푸시) 탭 즉시 방을 로딩 상태로 띄우기 위한 플래그.
    // 데이터(state·boxscore·regret) 로드 전 홈이 잠깐 보이는 플래시를 없앤다.
    var loading by mutableStateOf(false)
        private set

    /** 탭 즉시 호출 — 데이터 로드 동안 로딩 오버레이를 띄운다. */
    fun showLoading() {
        loading = true
    }

    /** 로드 실패 등으로 진입을 접을 때 (오버레이만 내린다). */
    fun dismissLoading() {
        loading = false
    }

    fun open(context: VentingGameContext, backLabel: String = "홈", entrySource: String = "unknown") {
        loading = false
        request = Request(context, backLabel, entrySource)
    }

    fun close() {
        request = null
        loading = false
    }
}

/**
 * MainActivity 루트 Box에 배치되는 분풀이 플로우 오버레이.
 * 요청이 있으면 플로우를, (요청 전) 로딩 중이면 스피너 오버레이를 그린다. 둘 다 아니면 미표시.
 */
@Composable
fun VentingFlowHost() {
    val request = VentingFlowController.request
    if (request == null) {
        if (VentingFlowController.loading) {
            VentingLoadingOverlay()
        }
        return
    }
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

/** 딥링크 탭 직후 데이터 로드 동안 표시되는 풀스크린 로딩 오버레이 (💢 + 스피너). */
@Composable
private fun VentingLoadingOverlay() {
    BackHandler { VentingFlowController.dismissLoading() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .background(Gray950)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(text = "💢", fontSize = 48.sp)
            CircularProgressIndicator(color = Red400)
        }
    }
}
