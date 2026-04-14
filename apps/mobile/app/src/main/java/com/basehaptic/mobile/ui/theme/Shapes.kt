package com.basehaptic.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * BaseHaptic 모서리 반경 토큰.
 * iOS `AppRadius`와 동일 스케일.
 */
object AppShapes {
    /** 8dp — 작은 버튼, 태그, 칩 */
    val sm = RoundedCornerShape(8.dp)
    /** 12dp — 카드 기본 */
    val md = RoundedCornerShape(12.dp)
    /** 16dp — 큰 카드, 모달 */
    val lg = RoundedCornerShape(16.dp)
    /** 20dp — 강조 컨테이너 */
    val xl = RoundedCornerShape(20.dp)
    /** 999dp — 완전히 둥근 배지 (pill) */
    val pill = RoundedCornerShape(999.dp)
}
