package com.basehaptic.watch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 워치 게임 이벤트 타입별 시맨틱 색상.
 * Mobile `AppEventColors`와 동일 매핑 원칙.
 * overlayStyle은 워치 전용 세분화 (STEAL=cyan 등).
 */
object WatchEventColors {

    /** 기본 매핑 (iPhone/Android Mobile과 동일 그룹) */
    fun eventColor(eventType: String): Color = when (eventType.uppercase()) {
        "HOMERUN", "SCORE", "SAC_FLY_SCORE", "VICTORY" -> Yellow500
        "HIT", "WALK", "STEAL", "TAG_UP_ADVANCE" -> Green500
        "DOUBLE_PLAY", "TRIPLE_PLAY", "STRIKE" -> Orange500
        "OUT" -> Red500
        "BALL" -> Gray400
        else -> Gray500
    }

    /** 워치 이벤트 오버레이 전용 스타일 (label, icon, 강조색) */
    data class OverlayStyle(val label: String, val icon: String, val color: Color)

    fun overlayStyle(eventType: String): OverlayStyle? = when (eventType.uppercase()) {
        "HIT" -> OverlayStyle("HIT", "bolt.fill", Green500)
        "WALK" -> OverlayStyle("WALK", "bolt.fill", Green400)
        "STEAL" -> OverlayStyle("STEAL", "bolt.fill", Cyan500)
        "TAG_UP_ADVANCE" -> OverlayStyle("STEAL", "bolt.fill", Cyan500)
        "SCORE" -> OverlayStyle("SCORE", "trophy.fill", Yellow500)
        "HOMERUN" -> OverlayStyle("HOMERUN", "trophy.fill", Yellow500)
        "OUT" -> OverlayStyle("OUT", "xmark.circle.fill", Red500)
        "DOUBLE_PLAY" -> OverlayStyle("DOUBLE PLAY", "xmark.circle.fill", Orange500)
        "TRIPLE_PLAY" -> OverlayStyle("TRIPLE PLAY", "xmark.circle.fill", Red600)
        else -> null
    }
}
