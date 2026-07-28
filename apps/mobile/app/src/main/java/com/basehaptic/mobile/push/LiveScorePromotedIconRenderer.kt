package com.basehaptic.mobile.push

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.basehaptic.mobile.data.BackendGamesRepository.LiveGameState

/**
 * Android 16+ promoted Live Update의 largeIcon 비트맵 렌더러.
 * promoted 노티는 커스텀 RemoteViews를 쓸 수 없으므로, 유일하게 자유로운 픽셀 영역인
 * largeIcon(약 48dp 정사각형)에 베이스 다이아몬드를 그린다. BSO는 본문 텍스트 줄로 표시.
 * 배경판 없이 투명 배경 — 도형이 "상자 속 그림"으로 축소되어 보이는 것을 방지.
 */
object LiveScorePromotedIconRenderer {

    // 표시 영역은 ~48dp지만 밀도별 축소에 대비해 고해상도로 그린다.
    private const val SIZE = 384f
    private const val BASE_OCCUPIED = 0xFFF2C14E.toInt()
    private const val BASE_EMPTY = 0xFF4B5563.toInt()

    fun render(state: LiveGameState): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // centerY 는 1·3루(가운데 행) 기준. 회전 사각형의 대각 확장(half*√2)까지 포함해
        // 상단(2루 꼭짓점)과 좌우가 캔버스 안에 들어오는 최대 크기.
        val baseSize = SIZE * 0.33f
        val centerY = SIZE * 0.62f
        val spread = baseSize * 0.76f
        val cx = SIZE / 2f
        drawBase(canvas, paint, cx, centerY - spread, baseSize, state.baseSecond)
        drawBase(canvas, paint, cx + spread, centerY, baseSize, state.baseFirst)
        drawBase(canvas, paint, cx - spread, centerY, baseSize, state.baseThird)
        return bitmap
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
}
