package dev.retrobit.desktop.ui.wm

import kotlin.math.roundToInt

/**
 * A screen-pixel rectangle, kept free of the Android framework so the window maths can be unit
 * tested on the JVM (the project's unit tests run without Robolectric).
 */
data class Placement(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isUsable: Boolean get() = width > 0 && height > 0

    override fun toString(): String = "$left,$top ${width}x$height"
}

/**
 * What **maximize** and **restore** mean for a desktop window, as pure maths.
 *
 * Maximize makes the window as large as the desktop and remembers the rectangle it had before, so
 * pressing maximize again brings it back **exactly** where the user left it instead of dropping it
 * somewhere in the middle of the screen. The same rectangle is what gets remembered per app in
 * `Settings.windowRects`, so a window reopens the size you gave it last time.
 *
 * Kept free of the Android framework so it can be unit tested on the JVM.
 */
object MaximizeMath {

    /** Size of a window that has no remembered rectangle: a bit larger than a fresh window. */
    const val DEFAULT_W = 0.62f
    const val DEFAULT_H = 0.60f

    /** A maximized window fills the usable desktop area, safe area already taken out by the shell. */
    fun maximized(area: Placement): Placement = area

    /**
     * Where a maximized window goes back to: [saved] when we remember it — clamped into [area], so a
     * rotation or a workspace change can never restore a window off screen — otherwise a default
     * window near the top-left corner of the desktop.
     */
    fun restore(
        saved: Placement?,
        area: Placement,
        margin: Float,
        cascade: Float,
        minW: Float,
        minH: Float,
    ): Placement {
        val remembered = saved?.takeIf { it.width > 0 && it.height > 0 }
        val maxW = area.width.coerceAtLeast(1)
        val maxH = area.height.coerceAtLeast(1)
        val minWidth = minW.roundToInt().coerceIn(1, maxW)
        val minHeight = minH.roundToInt().coerceIn(1, maxH)
        val wantW = remembered?.width ?: (area.width * DEFAULT_W).roundToInt()
        val wantH = remembered?.height ?: (area.height * DEFAULT_H).roundToInt()
        val w = wantW.coerceIn(minWidth, maxW)
        val h = wantH.coerceIn(minHeight, maxH)
        val wantX = remembered?.left ?: (area.left + margin + cascade).roundToInt()
        val wantY = remembered?.top ?: (area.top + margin + cascade).roundToInt()
        val x = wantX.coerceIn(area.left, (area.right - w).coerceAtLeast(area.left))
        val y = wantY.coerceIn(area.top, (area.bottom - h).coerceAtLeast(area.top))
        return Placement(x, y, x + w, y + h)
    }

    /**
     * The rectangle a window of [kind] opens with: the remembered one when the tablet has seen that
     * app before (clamped back into the desktop), otherwise a default that is a fraction of the
     * desktop — never the whole screen, because a window is meant to leave the desktop visible.
     */
    fun openRect(
        remembered: Placement?,
        area: Placement,
        fraction: Pair<Float, Float>,
        margin: Float,
        cascade: Float,
        minW: Float,
        minH: Float,
    ): Placement {
        val maxW = area.width.coerceAtLeast(1)
        val maxH = area.height.coerceAtLeast(1)
        val minWidth = minW.roundToInt().coerceIn(1, maxW)
        val minHeight = minH.roundToInt().coerceIn(1, maxH)
        val wantW = remembered?.width ?: (area.width * fraction.first).roundToInt()
        val wantH = remembered?.height ?: (area.height * fraction.second).roundToInt()
        val w = wantW.coerceIn(minWidth, maxW)
        val h = wantH.coerceIn(minHeight, maxH)
        val wantX = remembered?.left ?: (area.left + margin + cascade).roundToInt()
        val wantY = remembered?.top ?: (area.top + margin + cascade).roundToInt()
        val x = wantX.coerceIn(area.left, (area.right - w).coerceAtLeast(area.left))
        val y = wantY.coerceIn(area.top, (area.bottom - h).coerceAtLeast(area.top))
        return Placement(x, y, x + w, y + h)
    }
}
