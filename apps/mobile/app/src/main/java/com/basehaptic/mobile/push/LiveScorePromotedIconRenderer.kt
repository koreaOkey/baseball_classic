package com.basehaptic.mobile.push

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.basehaptic.mobile.data.BackendGamesRepository.LiveGameState

/**
 * Android 16+ promoted Live Update의 largeIcon 비트맵 렌더러.
 * promoted 노티는 커스텀 RemoteViews를 쓸 수 없으므로, 유일하게 자유로운 픽셀 영역인
 * largeIcon(약 48dp 정사각형)에 베이스 다이아몬드와 BSO 카운트를 그린다.
 */
object LiveScorePromotedIconRenderer {

    /**
     * COMPOSITE: 다이아몬드(위) + BSO 점(아래) 합성 — 기본안.
     * DIAMOND_ONLY: 다이아몬드가 아이콘 전체를 사용, BSO는 본문 텍스트 이모지로 이동 —
     * 실기기에서 합성안의 점 가독성이 부족할 때의 폴백.
     */
    enum class Mode { COMPOSITE, DIAMOND_ONLY }

    // 표시 영역은 ~48dp지만 밀도별 축소에 대비해 고해상도로 그린다.
    private const val SIZE = 384f
    private const val BG_COLOR = 0xEE1F262C.toInt()
    private const val BASE_OCCUPIED = 0xFFF2C14E.toInt()
    private const val BASE_EMPTY = 0xFF4B5563.toInt()
    private const val DOT_BALL = 0xFF4ADE80.toInt()
    private const val DOT_STRIKE = 0xFFFACC15.toInt()
    private const val DOT_OUT = 0xFFF87171.toInt()
    private const val DOT_EMPTY = 0xFF4B5563.toInt()

    fun render(state: LiveGameState, mode: Mode): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = BG_COLOR
        canvas.drawRoundRect(RectF(0f, 0f, SIZE, SIZE), SIZE * 0.2f, SIZE * 0.2f, paint)

        when (mode) {
            Mode.COMPOSITE -> {
                drawDiamond(canvas, paint, state, centerY = SIZE * 0.38f, baseSize = SIZE * 0.24f)
                drawCountDots(canvas, paint, state, centerY = SIZE * 0.82f)
            }
            Mode.DIAMOND_ONLY -> {
                drawDiamond(canvas, paint, state, centerY = SIZE * 0.5f, baseSize = SIZE * 0.34f)
            }
        }
        return bitmap
    }

    private fun drawDiamond(
        canvas: Canvas,
        paint: Paint,
        state: LiveGameState,
        centerY: Float,
        baseSize: Float
    ) {
        val cx = SIZE / 2f
        val spread = baseSize * 0.82f
        drawBase(canvas, paint, cx, centerY - spread, baseSize, state.baseSecond)
        drawBase(canvas, paint, cx + spread, centerY, baseSize, state.baseFirst)
        drawBase(canvas, paint, cx - spread, centerY, baseSize, state.baseThird)
    }

    private fun drawBase(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        occupied: Boolean
    ) {
        paint.color = if (occupied) BASE_OCCUPIED else BASE_EMPTY
        canvas.save()
        canvas.rotate(45f, cx, cy)
        val half = size / 2f
        canvas.drawRoundRect(
            RectF(cx - half, cy - half, cx + half, cy + half),
            size * 0.18f,
            size * 0.18f,
            paint
        )
        canvas.restore()
    }

    private fun drawCountDots(canvas: Canvas, paint: Paint, state: LiveGameState, centerY: Float) {
        val radius = SIZE * 0.040f
        val innerGap = SIZE * 0.036f
        val groupGap = SIZE * 0.078f
        val groups = listOf(
            Triple(3, state.ball, DOT_BALL),
            Triple(2, state.strike, DOT_STRIKE),
            Triple(2, state.out, DOT_OUT)
        )
        val totalWidth = groups.sumOf { (slots, _, _) ->
            (slots * radius * 2 + (slots - 1) * innerGap).toDouble()
        }.toFloat() + groupGap * (groups.size - 1)
        var x = (SIZE - totalWidth) / 2f + radius
        groups.forEach { (slots, count, activeColor) ->
            repeat(slots) { index ->
                paint.color = if (index < count) activeColor else DOT_EMPTY
                canvas.drawCircle(x, centerY, radius, paint)
                x += radius * 2 + innerGap
            }
            x += groupGap - innerGap
        }
    }
}
