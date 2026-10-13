package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.WidgetId
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.WidgetMath
import kotlin.math.roundToInt

/** Grab width of the widget resize edges, in dp. */
private const val GrabDp = 22f

/** Minimum widget height in dp (about two text lines). */
private const val MinHeightDp = 56f

/**
 * A desktop widget that can be resized by hand.
 *
 * The size comes from Settings, where every widget defaults to the calendar width so the whole
 * column lines up. Drag the right edge for the width, the bottom edge for the height, or the
 * bottom-right grip for both; the new size is snapped to the widget grid and remembered.
 * A height of 0 means "as tall as the content needs", anything else scrolls the content inside.
 */
@Composable
fun WidgetSlot(
    vm: LauncherViewModel,
    id: WidgetId,
    content: @Composable (widthDp: Int, heightDp: Int) -> Unit,
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    var width by remember(id) { mutableStateOf(vm.settings.widgetWidth(id).toFloat()) }
    var height by remember(id) { mutableStateOf(vm.settings.widgetHeight(id).toFloat()) }
    var dragging by remember(id) { mutableStateOf(false) }

    val grab = with(density) { GrabDp.dp.toPx() }
    val minH = with(density) { MinHeightDp.dp.toPx() }

    BoxWithConstraints {
        // the widget lives in a column on the desktop: it may never grow past that column
        val maxW = with(density) { maxWidth.toPx() }
        val widthPx = with(density) { width.dp.toPx() }
        val heightPx = with(density) { height.dp.toPx() }

        Box(
            Modifier
                .width(width.dp)
                .then(if (height > 0f) Modifier.height(height.dp) else Modifier)
                .pointerInput(id, maxW) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val handle = WidgetMath.handleAt(
                            down.position.x, down.position.y,
                            widthPx, heightPx, grab,
                        ) ?: return@awaitEachGesture
                        dragging = true
                        down.consume()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - last
                            last = change.position
                            change.consume()
                            if (handle != WidgetMath.WidgetHandle.BOTTOM) {
                                width = WidgetMath.width(width, delta.x, maxW = maxW)
                            }
                            if (handle != WidgetMath.WidgetHandle.RIGHT) {
                                height = WidgetMath.height(height, delta.y, minH = minH)
                            }
                        }
                        vm.settings.setWidgetSize(id, width.roundToInt(), height.roundToInt())
                        dragging = false
                    }
                },
        ) {
            if (height > 0f) {
                Column(Modifier.height(height.dp).verticalScroll(rememberScrollState())) {
                    content(width.roundToInt(), height.roundToInt())
                }
            } else {
                content(width.roundToInt(), 0)
            }

            // printed corner grip, so the resize handle is discoverable
            if (width > 120f && height > 60f) {
                Canvas(Modifier.align(Alignment.BottomEnd).size(dp(13f))) {
                    for (i in 0 until 3) {
                        val o = i * 4f
                        drawLine(p.ink, Offset(size.width - 13f + o, size.height - 1f), Offset(size.width - 1f, size.height - 13f + o), 1.6f)
                    }
                }
            }

            // live size readout while dragging
            if (dragging) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(y = (-18).dp)
                        .background(p.paper)
                        .border(dp(1.2f), p.ink)
                        .padding(horizontal = dp(4f)),
                ) {
                    RetroText(
                        if (height > 0f) "${width.roundToInt()}x${height.roundToInt()} dp" else "${width.roundToInt()} dp wide",
                        size = 10f, weight = FontWeight.Bold, maxLines = 1,
                    )
                }
            }
        }
    }
}
