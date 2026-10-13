package dev.retrobit.desktop.ui.wm

import kotlin.math.roundToInt

/**
 * What **maximize** and **restore** mean for a desktop window, as pure maths.
 *
 * Maximize makes the frame as large as the desktop area — the whole screen minus the shell's own
 * panel / dock / taskbar, which stay visible — and remembers the geometry the window had before, so
 * pressing maximize again brings it back **exactly** where the user left it instead of dropping it
 * somewhere in the middle of the screen.
 *
 * Kept free of the Android framework so it can be unit tested on the JVM.
 */
object MaximizeMath {

    /** Size of a window restored without a remembered rectangle: a bit larger than a new window. */
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
}
