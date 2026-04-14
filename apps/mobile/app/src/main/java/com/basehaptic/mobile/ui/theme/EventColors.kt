package com.basehaptic.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 게임 이벤트 타입별 시맨틱 색상.
 * 화면·플랫폼 간 일관성을 위해 항상 이 헬퍼를 사용한다.
 * 매핑 정의는 openspec/specs/design-system/spec.md 참고.
 * iOS `AppEventColors`와 동일 그룹 매핑.
 */
object AppEventColors {
    fun eventColor(eventType: String): Color = when (eventType.uppercase()) {
        "HOMERUN", "SCORE", "SAC_FLY_SCORE", "VICTORY" -> Yellow500
        "HIT", "WALK", "STEAL" -> Green500
        "DOUBLE_PLAY", "TRIPLE_PLAY", "STRIKE" -> Orange500
        "OUT", "TAG_UP_ADVANCE" -> Red500
        "BALL" -> Gray400
        else -> Gray500
    }
}
