package dev.retrobit.desktop.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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

val LocalDesktopInsets = staticCompositionLocalOf { DesktopInsets.Zero }

/**
 * Combines the punch-hole / cutout insets reported by the system with the rounded-corner margin.
 * [enabled] mirrors the "keep clear of the camera and corners" setting, [cornerDp] is the radius of
 * the display corner the user picked.
 */
@Composable
fun rememberDesktopInsets(enabled: Boolean, cornerDp: Int): DesktopInsets {
    if (!enabled) return DesktopInsets.Zero
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val corner = with(density) { cornerDp.dp.toPx() }
    val left = maxOf(cutout.getLeft(density, layoutDirection), corner)
    val top = maxOf(cutout.getTop(density), corner)
    val right = maxOf(cutout.getRight(density, layoutDirection), corner)
    val bottom = maxOf(cutout.getBottom(density), corner)
    return DesktopInsets(left, top, right, bottom)
}
