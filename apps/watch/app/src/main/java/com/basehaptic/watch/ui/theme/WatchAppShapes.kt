package com.basehaptic.watch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 워치 전용 모서리 반경 토큰.
 */
object WatchAppShapes {
    /** 2dp — 베이스 셀 */
    val xxs = RoundedCornerShape(2.dp)
    /** 8dp */
    val sm = RoundedCornerShape(8.dp)
    /** 10dp — 스코어 카드 내부 */
    val md10 = RoundedCornerShape(10.dp)
    /** 12dp — 카드 기본 */
    val md = RoundedCornerShape(12.dp)
    /** 14dp — 다이얼로그 */
    val lg = RoundedCornerShape(14.dp)
    /** 999dp — 완전 둥근 배지 */
    val pill = RoundedCornerShape(999.dp)
}
