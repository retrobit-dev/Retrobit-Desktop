package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Size

/**
 * Pure maths for manually sizing desktop widgets.
 *
 * Widgets can be resized by dragging their right edge (width), bottom edge (height) or the
 * bottom-right corner (both). Sizes snap to a coarse step so every widget lines up with the others,
 * and are clamped so a widget never gets smaller than its content or wider than the desktop.
 * Android-free so it can be unit tested on the JVM.
 */
object WidgetMath {

    /** Snap step in px: widget sizes move in whole 8 px blocks. */
    const val STEP = 8f

    /** Smallest usable widget. */
    const val MIN_WIDTH = 160f
    const val MIN_HEIGHT = 48f

    /** Rounds a size to the snap grid. */
    fun snap(value: Float, step: Float = STEP): Float {
        if (step <= 0f) return value
        val steps = kotlin.math.round(value / step)
        return (steps * step).coerceAtLeast(0f)
    }

    /**
     * Applies a width drag: [dx] px of horizontal drag, snapped and clamped between [minW] and
     * [maxW] (the desktop edge).
     */
    fun width(current: Float, dx: Float, minW: Float = MIN_WIDTH, maxW: Float = Float.MAX_VALUE): Float =
        snap(current + dx).coerceIn(minW, maxOf(minW, maxW))

    /** Applies a height drag: [dy] px, snapped and clamped. */
    fun height(current: Float, dy: Float, minH: Float = MIN_HEIGHT, maxH: Float = Float.MAX_VALUE): Float =
        snap(current + dy).coerceIn(minH, maxOf(minH, maxH))

    /**
     * Applies a corner drag to both axes at once (bottom-right grip).
     */
    fun size(
        current: Size,
        dx: Float,
        dy: Float,
        minW: Float = MIN_WIDTH,
        minH: Float = MIN_HEIGHT,
        maxW: Float = Float.MAX_VALUE,
        maxH: Float = Float.MAX_VALUE,
    ): Size = Size(width(current.width, dx, minW, maxW), height(current.height, dy, minH, maxH))

    /** Where the pointer sits on a widget: right edge, bottom edge, corner or nowhere. */
    fun handleAt(pointX: Float, pointY: Float, width: Float, height: Float, grab: Float): WidgetHandle? {
        if (width <= 0f || height <= 0f) return null
        val onRight = pointX >= width - grab
        val onBottom = pointY >= height - grab
        return when {
            onRight && onBottom -> WidgetHandle.CORNER
            onRight -> WidgetHandle.RIGHT
            onBottom -> WidgetHandle.BOTTOM
            else -> null
        }
    }

    enum class WidgetHandle { RIGHT, BOTTOM, CORNER }
}
