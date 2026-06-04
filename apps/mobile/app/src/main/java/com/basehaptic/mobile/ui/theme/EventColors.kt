package com.basehaptic.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 게임 이벤트 타입별 시맨틱 색상.
 * 화면·플랫폼 간 일관성을 위해 항상 이 헬퍼를 사용한다.
 * 매핑 정의는 openspec/specs/design-system/spec.md 참고.
 * iOS `AppEventColors`와 동일 그룹 매핑.
 */
object AppEventColors {
    // 라이브 상세 PitchChip 가독성을 위해 S=노랑, B=초록, 안타=파랑으로 분리.
    fun eventColor(eventType: String): Color = when (eventType.uppercase()) {
        "STRIKE" -> Yellow500
        "BALL" -> Green500
        "HIT" -> Blue500
        "HOMERUN", "SCORE", "SAC_FLY_SCORE", "VICTORY", "MOUND_VISIT" -> Yellow500
        "WALK", "STEAL", "TAG_UP_ADVANCE", "PITCHER_CHANGE" -> Green500
        "DOUBLE_PLAY", "TRIPLE_PLAY" -> Orange500
        "OUT" -> Red500
        else -> Gray500
    }
}
