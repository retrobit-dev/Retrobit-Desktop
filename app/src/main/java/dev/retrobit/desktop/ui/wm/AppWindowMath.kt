package dev.retrobit.desktop.ui.wm

import android.graphics.Rect

/**
 * A screen-pixel rectangle, kept free of the Android framework so the placement maths can be unit
 * tested on the JVM (the project's unit tests run without Robolectric).
 */
data class Placement(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isUsable: Boolean get() = width > 0 && height > 0

    fun toRect(): Rect = Rect(left, top, right, bottom)

    override fun toString(): String = "$left,$top $width" + "x" + "$height"
}

/**
 * Where a floating app should be put so that it lands *inside* its desktop window frame.
 *
 * The desktop frame is drawn inside the safe-area padding of the launcher, the frame has a border and
 * a title bar, and the system wants the rectangle in **screen pixels** — four conversions that are
 * easy to get subtly wrong, which is why they live here as pure maths with unit tests.
 *
 *  frame(x, y, w, h)  → desktop coordinates of the window (relative to the padded shell)
 *  insets             → padding the launcher applies for the camera hole and rounded corners
 *  titlePx, borderPx  → the frame's own chrome that the app must not cover
 */
object AppWindowMath {

    /** Windows below this size are not worth sending to the system. */
    const val MIN_SIDE = 120

    /**
     * The rectangle the app should occupy, in screen pixels: the frame's content area — everything
     * below the title bar and inside the border — moved by the safe-area insets.
     *
     * Returns null when the frame is too small to hold an app window, so the caller can fall back to
     * a plain launch.
     */
    fun contentRect(
        frameX: Float,
        frameY: Float,
        frameW: Float,
        frameH: Float,
        titlePx: Float,
        borderPx: Float,
        insetLeft: Float,
        insetTop: Float,
        screenW: Int,
        screenH: Int,
    ): Placement? {
        if (frameW <= 0f || frameH <= 0f) return null
        val wantedLeft = Math.round(frameX + insetLeft + borderPx)
        val wantedTop = Math.round(frameY + insetTop + titlePx + borderPx)
        val wantedRight = Math.round(frameX + insetLeft + frameW - borderPx)
        val wantedBottom = Math.round(frameY + insetTop + frameH - borderPx)

        // a frame that has no room for an app is refused instead of being silently enlarged
        val wantedW = wantedRight - wantedLeft
        val wantedH = wantedBottom - wantedTop
        if (wantedW < MIN_SIDE || wantedH < MIN_SIDE) return null

        // keep the app on the physical screen even if the frame hangs over an edge
        val w = minOf(wantedW, screenW)
        val h = minOf(wantedH, screenH)
        val left = wantedLeft.coerceIn(0, maxOf(0, screenW - w))
        val top = wantedTop.coerceIn(0, maxOf(0, screenH - h))
        val right = left + w
        val bottom = top + h
        val placement = Placement(left, top, right, bottom)
        return if (placement.isUsable) placement else null
    }

    /** Height of the window title bar in px for a given density and UI scale. */
    fun titleHeightPx(baseDp: Float, uiScale: Float, density: Float): Float =
        baseDp * uiScale.coerceIn(0.5f, 2f) * density

    /** Width of the frame border in px. */
    fun borderPx(borderDp: Float, density: Float): Float = borderDp * density

    /**
     * True when [placement] is a sensible request: it fits inside the screen and is smaller than the
     * whole display, because a "window" that covers everything is just a full-screen launch.
     */
    fun isWindowed(placement: Placement?, screenW: Int, screenH: Int): Boolean {
        if (placement == null) return false
        if (placement.left < 0 || placement.top < 0) return false
        if (placement.right > screenW || placement.bottom > screenH) return false
        return placement.width < screenW || placement.height < screenH
    }
}
