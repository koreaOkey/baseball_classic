package com.basehaptic.mobile.venting.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.venting.MockRegretProvider
import com.basehaptic.mobile.venting.VentingEventReporter
import com.basehaptic.mobile.venting.VentingFeatureFlag
import com.basehaptic.mobile.venting.VentingGameContext
import com.basehaptic.mobile.venting.VentingOpenConditionChecker
import com.basehaptic.mobile.venting.VentingRoomState
import com.basehaptic.mobile.venting.VentingTarget
import com.basehaptic.mobile.wear.WatchCompanionStatus
import com.basehaptic.mobile.wear.WatchCompanionStatusRepository
import com.basehaptic.mobile.wear.WearGameSyncManager

/** 분풀이 모드 화면 흐름 상태 (iOS VentingFlowState 포팅). */
private sealed class VentingFlowState {
    data object Selection : VentingFlowState()
    data class Room(val state: VentingRoomState) : VentingFlowState()
    data class Destroyed(val state: VentingRoomState) : VentingFlowState()

    /** 워치로 분풀이 시작 후 안내 화면 (폰 룸 미진입). */
    data object WatchHandoff : VentingFlowState()
}

/**
 * 분풀이 모드 화면 흐름 조율자 (iOS VentingFlowCoordinator 포팅).
 * VentingTargetSelectionScreen → VentingRoomScreen → VentingDestroyedScreen
 * 사이의 화면 전환을 관리한다.
 */
@Composable
fun VentingFlowCoordinator(
    context: VentingGameContext,
    onClose: () -> Unit,
    backLabel: String = "홈",
    entrySource: String = "unknown"
) {
    val appContext = LocalContext.current.applicationContext
    var flowState by remember { mutableStateOf<VentingFlowState>(VentingFlowState.Selection) }

    // 워치 연동(앱 설치) 여부 — installed일 때만 "워치로 분풀이 시작하기" 노출.
    // 캐시값으로 초기화해 깜빡임을 줄이고, 실제 조회 결과로 갱신한다.
    var watchAvailable by remember {
        mutableStateOf(
            WatchCompanionStatusRepository.getCachedStatus(appContext) == WatchCompanionStatus.Installed
        )
    }
    LaunchedEffect(Unit) {
        watchAvailable =
            WatchCompanionStatusRepository.getStatus(appContext) == WatchCompanionStatus.Installed
    }

    when (val current = flowState) {
        is VentingFlowState.Selection -> VentingTargetSelectionScreen(
            context = context,
            onBack = onClose,
            backLabel = backLabel,
            onSelectTarget = { target: VentingTarget ->
                // 6.1 지표: 룸 진입 순간 = room_enter
                VentingEventReporter.report(
                    context = appContext,
                    eventType = "room_enter",
                    team = context.myTeamId,
                    entrySource = entrySource,
                    gameId = context.gameId
                )
                flowState = VentingFlowState.Room(
                    VentingRoomState(
                        context = appContext,
                        gameContext = context,
                        selectedTarget = target
                    )
                )
            },
            showWatchOption = watchAvailable,
            onSelectTargetOnWatch = { target: VentingTarget ->
                // 워치 룸 트리거 발송 (테스트 도구와 동일 경로). 폰은 룸으로 진입하지 않는다.
                // 선수는 역할 라벨(실명 금지), 감독/직접입력은 라벨 그대로 전송.
                WearGameSyncManager.sendVentingTrigger(
                    context = appContext,
                    gameId = context.gameId,
                    targetLabel = target.roleLabel,
                    eventDescription = target.eventDescription
                )
                // 6.1 지표: 워치 진입 = watch_room_enter
                VentingEventReporter.report(
                    context = appContext,
                    eventType = "watch_room_enter",
                    team = context.myTeamId,
                    entrySource = entrySource,
                    gameId = context.gameId
                )
                flowState = VentingFlowState.WatchHandoff
            }
        )

        is VentingFlowState.Room -> VentingRoomScreen(
            state = current.state,
            entrySource = entrySource,
            onBack = { flowState = VentingFlowState.Selection },
            onDestroyed = { flowState = VentingFlowState.Destroyed(current.state) }
        )

        is VentingFlowState.Destroyed -> VentingDestroyedScreen(
            state = current.state,
            entrySource = entrySource,
            onRetry = {
                current.state.reset()
                flowState = VentingFlowState.Room(current.state)
            },
            onClose = onClose
        )

        is VentingFlowState.WatchHandoff -> VentingWatchHandoffScreen(onClose = onClose)
    }
}

/**
 * HomeScreen에 삽입되는 분풀이 카드 컨테이너 (iOS VentingHomeCardContainer 포팅).
 *
 * - 목업 경기 컨텍스트를 로드하고, 오픈 조건을 판정한다.
 * - 조건 미충족 시 아무것도 렌더링하지 않는다.
 * - DEBUG + venting_mode_enabled 이중 게이트 뒤에서만 동작한다.
 * - 진입 시 [VentingFlowController]로 루트 오버레이 플로우를 연다.
 */
@Composable
fun VentingHomeCardContainer(myTeam: Team) {
    val androidContext = LocalContext.current

    val ventingContext = remember(myTeam) {
        if (!VentingFeatureFlag.isEnabled(androidContext)) return@remember null
        val loaded = MockRegretProvider.fetchVentingContext(androidContext)
        if (VentingOpenConditionChecker.isOpen(loaded, myTeam.name)) loaded else null
    } ?: return

    VentingHomeCard(
        context = ventingContext,
        onEnterVenting = { VentingFlowController.open(ventingContext, entrySource = "home_card") }
    )
}
