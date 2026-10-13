package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Screen-edge snap targets, like Aero Snap / GNOME edge tiling. */
enum class SnapZone { LEFT, RIGHT, MAXIMIZE }

/**
 * Which side or corner of a window is being dragged. Every edge is a resize handle, so windows can
 * be sized manually like on a desktop — left/right/top/bottom plus the four corners.
 */
enum class ResizeEdge {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    val movesLeft: Boolean get() = this == LEFT || this == TOP_LEFT || this == BOTTOM_LEFT
    val movesRight: Boolean get() = this == RIGHT || this == TOP_RIGHT || this == BOTTOM_RIGHT
    val movesTop: Boolean get() = this == TOP || this == TOP_LEFT || this == TOP_RIGHT
    val movesBottom: Boolean get() = this == BOTTOM || this == BOTTOM_LEFT || this == BOTTOM_RIGHT
}

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
     *
     * The side edges take priority: dragging a window into the top-left corner fills the left half
     * (like Aero Snap), while dragging it to the top *centre* maximizes.
     */
    fun zoneFor(area: Rect, window: Rect, edge: Float = EDGE, top: Float = TOP): SnapZone? {
        if (area.width <= 16f || area.height <= 16f) return null
        val atTop = window.top <= area.top + top
        val centered = kotlin.math.abs(window.center.x - area.center.x) < area.width * 0.22f
        return when {
            window.left <= area.left + edge -> SnapZone.LEFT
            window.right >= area.right - edge -> SnapZone.RIGHT
            atTop && centered -> SnapZone.MAXIMIZE
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
     * Applies a manual resize. [bounds] keeps the window on the desktop, [minW]/[minH] keep it
     * usable — the same rules a desktop window manager follows for every edge and corner handle.
     */
    fun resize(
        rect: Rect,
        edge: ResizeEdge,
        dx: Float,
        dy: Float,
        minW: Float,
        minH: Float,
        bounds: Rect,
    ): Rect {
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        if (edge.movesLeft) left = (left + dx).coerceIn(bounds.left, maxOf(bounds.left, right - minW))
        if (edge.movesRight) right = (right + dx).coerceIn(minOf(bounds.right, left + minW), bounds.right)
        if (edge.movesTop) top = (top + dy).coerceIn(bounds.top, maxOf(bounds.top, bottom - minH))
        if (edge.movesBottom) bottom = (bottom + dy).coerceIn(minOf(bounds.bottom, top + minH), bounds.bottom)
        return Rect(left, top, right, bottom)
    }

    /** Which resize handle (if any) sits under [point]; [handle] is the grab width in px. */
    fun edgeAt(point: Offset, rect: Rect, handle: Float): ResizeEdge? {
        if (rect.width <= 0f || rect.height <= 0f) return null
        val corner = (handle * 2f).coerceAtMost(rect.width / 2f).coerceAtMost(rect.height / 2f)
        val left = point.x <= rect.left + handle
        val right = point.x >= rect.right - handle
        val top = point.y <= rect.top + handle
        val bottom = point.y >= rect.bottom - handle
        val inX = point.x >= rect.left - handle && point.x <= rect.right + handle
        val inY = point.y >= rect.top - handle && point.y <= rect.bottom + handle
        val nearLeft = point.x <= rect.left + corner
        val nearRight = point.x >= rect.right - corner
        val nearTop = point.y <= rect.top + corner
        val nearBottom = point.y >= rect.bottom - corner
        return when {
            nearTop && nearLeft && inX && inY -> ResizeEdge.TOP_LEFT
            nearTop && nearRight && inX && inY -> ResizeEdge.TOP_RIGHT
            nearBottom && nearLeft && inX && inY -> ResizeEdge.BOTTOM_LEFT
            nearBottom && nearRight && inX && inY -> ResizeEdge.BOTTOM_RIGHT
            left && inY -> ResizeEdge.LEFT
            right && inY -> ResizeEdge.RIGHT
            top && inX -> ResizeEdge.TOP
            bottom && inX -> ResizeEdge.BOTTOM
            else -> null
        }
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
