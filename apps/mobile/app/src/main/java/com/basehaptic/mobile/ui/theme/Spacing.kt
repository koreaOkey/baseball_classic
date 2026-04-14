package com.basehaptic.mobile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * BaseHaptic 스페이싱 토큰 (padding/margin/spacing 공통).
 * 4의 배수 기반. 새 코드는 raw `.dp` 직접 호출 대신 이 토큰을 사용한다.
 * iOS `AppSpacing`과 동일 스케일.
 */
object AppSpacing {
    /** 2dp — 미세 조정 */
    val xxs = 2.dp
    /** 4dp — 아이콘 gap, 라벨 간격 */
    val xs = 4.dp
    /** 8dp — 기본 요소 간격 */
    val sm = 8.dp
    /** 12dp — 카드 내부 보조 여백 */
    val md = 12.dp
    /** 16dp — 카드 내부 기본 여백 */
    val lg = 16.dp
    /** 20dp — 큰 카드 여백 */
    val xl = 20.dp
    /** 24dp — 화면 가장자리 여백 */
    val xxl = 24.dp
    /** 32dp — 섹션 간격 */
    val xxxl = 32.dp
    /** 48dp — 버튼 높이, 큰 아이콘 프레임 */
    val buttonHeight = 48.dp
    /** 80dp — ScrollView 하단 안전 영역 */
    val bottomSafeSpacer = 80.dp
}
