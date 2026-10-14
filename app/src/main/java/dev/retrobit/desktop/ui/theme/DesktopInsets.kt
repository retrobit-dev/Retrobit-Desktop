package dev.retrobit.desktop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * How far the desktop chrome must stay away from the physical edges of the tablet, in px.
 *
 * Two things get in the way on a modern tablet: the rounded corners (every corner on the MatePad
 * Mini) and the punch-hole camera in the top-left corner. The launcher draws edge to edge, so the
 * panel, dock, widgets and windows are inset by the larger of the display-cutout inset and the
 * corner radius the user dialled in.
 */
data class DesktopInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    val vertical: Float get() = top + bottom
    val horizontal: Float get() = left + right
    val isEmpty: Boolean get() = left == 0f && top == 0f && right == 0f && bottom == 0f

    companion object {
        val Zero = DesktopInsets()
    }
}

/** The safe area the shells must honour (camera hole + rounded corners). */
val LocalDesktopInsets = staticCompositionLocalOf { DesktopInsets.Zero }

/**
 * Display-cutout insets in px, as `[left, top, right, bottom]`, filled in by [MainActivity] from
 * the window insets. Empty on devices without a cutout (or below Android 9).
 */
val LocalCutoutInsets = staticCompositionLocalOf { IntArray(0) }

/**
 * Combines the punch-hole / cutout insets reported by the system with the rounded-corner margin.
 * [enabled] mirrors the "keep clear of the camera and corners" setting, [cornerDp] is the radius of
 * the display corner the user picked.
 */
@Composable
fun rememberDesktopInsets(enabled: Boolean, cornerDp: Int): DesktopInsets {
    if (!enabled) return DesktopInsets.Zero
    val density = LocalDensity.current
    val cutout = LocalCutoutInsets.current
    val corner = with(density) { cornerDp.dp.toPx() }
    fun side(index: Int): Float = (cutout.getOrNull(index) ?: 0).toFloat()
    return DesktopInsets(
        left = maxOf(side(0), corner),
        top = maxOf(side(1), corner),
        right = maxOf(side(2), corner),
        bottom = maxOf(side(3), corner),
    )
}
