package dev.retrobit.desktop.ui.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.ui.LocalViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.RetroIconButton
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import kotlin.math.roundToInt

/**
 * E-paper pointer for pen and mouse input: a 1-bit arrow with a paper halo so it stays readable on
 * top of any dither pattern. It inverts while the button is pressed, like a real cursor.
 */
@Composable
fun StylusCursorOverlay(router: PointerRouter = LocalPointerRouter.current, size: Dp = dp(17f)) {
    if (!router.showCursor) return
    // inside a text field the pen is a pen again: no arrow, the field shows its own I-beam
    if (router.textInput && router.source == InputSource.PEN) return
    val position = router.cursor ?: return
    val pressed = router.cursorPressed
    val p = LocalPalette.current
    val density = LocalDensity.current
    val fg = if (pressed) p.paper else p.ink
    val bg = if (pressed) p.ink else p.paper

    Box(
        Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .size(size),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = this.size.minDimension / 16f
            fun drawGlyph(color: androidx.compose.ui.graphics.Color, shrink: Float) {
                Glyph.CURSOR.rows.forEachIndexed { y, row ->
                    row.forEachIndexed { x, ch ->
                        if (ch != ' ') {
                            drawRect(
                                color,
                                topLeft = Offset(x * cell - shrink, y * cell - shrink),
                                size = Size(cell + shrink * 2 + 0.35f, cell + shrink * 2 + 0.35f),
                            )
                        }
                    }
                }
            }
            drawGlyph(bg, shrink = cell * 0.30f)
            drawGlyph(fg, shrink = 0f)
        }
    }
}

/**
 * Room for the on-screen keyboard on a surface the launcher owns, following the "keyboard lifts the
 * layout" preference: by default the surface itself makes room for the keyboard (the desktop and the
 * window frame never move); with the old slide-up mode the whole desktop already moved, so padding
 * here would push the content up twice.
 */
@Composable
fun Modifier.keyboardRoom(settings: dev.retrobit.desktop.data.Settings): Modifier =
    if (settings.keyboardLiftsLayout) this else this.imePadding()

/** Tray / panel button that summons or hides the on-screen (internal) keyboard. */
@Composable
fun KeyboardTrayButton(boxSize: Dp = dp(24f)) {
    val kb = LocalKeyboardRequester.current
    val vm = LocalViewModel.current
    RetroIconButton(glyph = Glyph.KEYBOARD, active = kb.visible, boxSize = boxSize) {
        vm.toggleKeyboard?.invoke()
    }
}
