package dev.retrobit.desktop.shell

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * The drawing vocabulary the chrome overlays use for a **native** palette.
 *
 * The e-paper palettes *print*: square paper surfaces, ink rules and a hard checker shadow — that
 * path is untouched and lives in [RailView] / [PanelView] as it always did. A native theme instead
 * draws what its own desktop draws: a rounded surface, the gradient of its title bar and a real
 * soft shadow, so the rails over a full-screen app read as the window chrome of that desktop rather
 * than as a printed sheet. Every caller branches on [CyberdeckChrome.Skin], so nothing here runs on
 * the printed path.
 *
 * The stepped shadow mirrors `Modifier.retroShadow` in the Compose UI on purpose: the shadows of the
 * desktop and of the chrome have to look like the same light source.
 */
internal object SkinPaint {

    /** A surface: a flat face when `top == bottom`, a title-strip gradient when it is not. */
    fun face(canvas: Canvas, paint: Paint, rect: RectF, radius: Float, top: Int, bottom: Int) {
        paint.style = Paint.Style.FILL
        if (top == bottom) {
            paint.shader = null
            paint.color = top
        } else {
            paint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom, top, bottom, Shader.TileMode.CLAMP,
            )
        }
        if (radius > 0.5f) canvas.drawRoundRect(rect, radius, radius, paint)
        else canvas.drawRect(rect, paint)
        paint.shader = null
    }

    /**
     * Hairline frame of a surface. Unlike the printed frame (four filled rects, so it stays one
     * crisp pixel wide) this is a stroked rounded outline: a native border is a hairline, not ink,
     * and it has to follow the corners.
     */
    fun frame(canvas: Canvas, paint: Paint, rect: RectF, radius: Float, width: Float, color: Int) {
        if (rect.width() <= 1f || rect.height() <= 1f) return
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        val half = width / 2f
        val inner = RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
        if (radius > 0.5f) canvas.drawRoundRect(inner, radius, radius, paint)
        else canvas.drawRect(inner, paint)
        paint.style = Paint.Style.FILL
    }

    /** The soft shadow behind a floating surface, drawn *before* the surface itself. */
    fun shadow(
        canvas: Canvas,
        paint: Paint,
        rect: RectF,
        radius: Float,
        spread: Float,
        color: Int = 0xFF000000.toInt(),
    ) {
        if (spread <= 0f) return
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        // the same three steps (and the same alphas) retroShadow draws in the launcher
        listOf(1.00f to 0.16f, 0.75f to 0.10f, 0.50f to 0.07f).forEach { (share, alpha) ->
            val off = spread * share
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            val r = RectF(rect.left + off, rect.top + off, rect.right + off, rect.bottom + off)
            if (radius > 0.5f) canvas.drawRoundRect(r, radius, radius, paint)
            else canvas.drawRect(r, paint)
        }
        paint.alpha = 255
    }

    /** A washed-out version of [color], for the hairline of a part that is not switched on. */
    fun hairline(color: Int, alpha: Float): Int {
        val a = (android.graphics.Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(
            a,
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color),
        )
    }
}
