package com.basehaptic.mobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * BaseHaptic 타이포그래피 토큰.
 * 새 코드는 raw `fontSize = N.sp` 대신 이 토큰을 사용한다.
 * 상세 기준은 openspec/specs/design-system/spec.md 참고.
 * iOS `AppFont`와 1:1 매칭.
 */
object AppFont {
    private val defaultFamily = FontFamily.Default

    private fun style(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle = TextStyle(
        fontFamily = defaultFamily,
        fontWeight = weight,
        fontSize = size.sp
    )

    // Display (Onboarding)
    /** 56sp — 온보딩 이모지/심볼 */
    val display = style(56)

    // Headings
    /** 36sp bold — 온보딩 대제목 */
    val h1 = style(36, FontWeight.Bold)
    /** 28sp bold — 섹션 대제목, 스코어 */
    val h2 = style(28, FontWeight.Bold)
    /** 24sp — 아이콘·심볼용 */
    val h3 = style(24)
    /** 24sp bold — 섹션 제목 */
    val h3Bold = style(24, FontWeight.Bold)
    /** 20sp — 아이콘 크기 */
    val h4 = style(20)
    /** 20sp bold — 서브 제목 */
    val h4Bold = style(20, FontWeight.Bold)
    /** 18sp bold — 카드 제목 */
    val h5Bold = style(18, FontWeight.Bold)

    // Body Large (16sp)
    /** 16sp — 기본 본문 */
    val bodyLg = style(16)
    /** 16sp medium — 버튼 라벨, 본문 강조 */
    val bodyLgMedium = style(16, FontWeight.Medium)
    /** 16sp bold */
    val bodyLgBold = style(16, FontWeight.Bold)

    // Label (15sp)
    val label = style(15)
    val labelMedium = style(15, FontWeight.Medium)
    val labelBold = style(15, FontWeight.Bold)

    // Body (14sp)
    /** 14sp — 본문 */
    val body = style(14)
    val bodyMedium = style(14, FontWeight.Medium)
    val bodySemibold = style(14, FontWeight.SemiBold)
    val bodyBold = style(14, FontWeight.Bold)

    // Caption (13sp)
    val caption = style(13)
    val captionMedium = style(13, FontWeight.Medium)
    val captionSemibold = style(13, FontWeight.SemiBold)
    val captionBold = style(13, FontWeight.Bold)

    // Micro (12sp)
    val micro = style(12)
    val microMedium = style(12, FontWeight.Medium)
    val microBold = style(12, FontWeight.Bold)

    // Tiny (11sp)
    val tiny = style(11)
    val tinyBold = style(11, FontWeight.Bold)
}
