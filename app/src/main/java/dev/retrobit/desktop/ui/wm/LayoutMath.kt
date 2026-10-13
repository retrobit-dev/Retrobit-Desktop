package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Rect

/** Screen-edge snap targets, like Aero Snap / GNOME edge tiling. */
enum class SnapZone { LEFT, RIGHT, MAXIMIZE }

/**
 * Pure layout maths for the window manager: tiling and edge snapping.
 *
 * Kept free of Android dependencies so it can be unit tested on the JVM (see
 * `app/src/test/java/dev/retrobit/desktop/wm/LayoutMathTest.kt`).
 */
object LayoutMath {

    /** How far the window may stick into a screen edge (px) before it snaps. */
    const val EDGE = 40f

    /** Distance from the top of the desktop that triggers maximize (px). */
    const val TOP = 20f

    /**
     * Which zone the window would snap to, or null for free placement.
     * Top of the screen (while horizontally centred) maximizes, left/right edges fill that half.
     */
    fun zoneFor(area: Rect, window: Rect, edge: Float = EDGE, top: Float = TOP): SnapZone? {
        if (area.width <= 16f || area.height <= 16f) return null
        val centered = kotlin.math.abs(window.center.x - area.center.x) < area.width * 0.28f
        return when {
            window.top <= area.top + top && centered -> SnapZone.MAXIMIZE
            window.left <= area.left + edge -> SnapZone.LEFT
            window.right >= area.right - edge -> SnapZone.RIGHT
            else -> null
        }
    }

    /** Result rectangle for a snap zone. */
    fun rectFor(area: Rect, zone: SnapZone, gap: Float = 0f): Rect = when (zone) {
        SnapZone.MAXIMIZE -> area
        SnapZone.LEFT -> Rect(area.left, area.top, area.left + area.width / 2f - gap, area.bottom)
        SnapZone.RIGHT -> Rect(area.left + area.width / 2f + gap, area.top, area.right, area.bottom)
    }

    /**
     * Master–stack tiling, used by the Linux shell's tiling mode:
     * one master window on the left, the rest stacked in a column on the right.
     */
    fun tilingRects(area: Rect, count: Int, margin: Float, masterRatio: Float = 0.55f): List<Rect> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(area)
        if (area.width <= 0f || area.height <= 0f) return List(count) { area }
        if (count == 2) {
            val half = area.width / 2f
            return listOf(
                Rect(area.left, area.top, (area.left + half - margin).coerceAtLeast(area.left), area.bottom),
                Rect((area.left + half + margin).coerceAtMost(area.right), area.top, area.right, area.bottom),
            )
        }
        val rects = ArrayList<Rect>(count)
        val masterWidth = (area.width * masterRatio - margin).coerceIn(1f, area.width)
        rects += Rect(area.left, area.top, area.left + masterWidth, area.bottom)
        val rest = count - 1
        val each = (area.height - margin * (rest - 1)) / rest
        val x = (area.left + masterWidth + margin * 2f).coerceAtMost(area.right)
        var y = area.top
        repeat(rest) {
            rects += Rect(x, y, area.right, (y + each).coerceAtMost(area.bottom))
            y += each + margin
        }
        return rects
    }
}
