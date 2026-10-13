package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.WidgetId
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.RetroButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.WidgetMath
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Grab width of the widget resize edges, in dp. */
private const val GrabDp = 22f

/** Minimum widget height in dp (about two text lines). */
private const val MinHeightDp = 56f

/** How long a press has to rest before the widget enters edit / resize mode. */
private const val HoldMs = 380L

/**
 * One desktop widget: content plus manual sizing.
 *
 * UX rules:
 *  · **hold** anywhere on the widget → it enters resize mode (the whole widget turns into a handle,
 *    the nearest edge or corner follows the pointer) and the desktop enters widget editing;
 *  · drag the right edge / bottom edge / bottom-right grip to size it;
 *  · while editing, the widget shows a dashed frame, its name, a ✕ to remove it, arrows to move it
 *    in the column, and a reset button — and its content stops reacting to taps.
 */
@Composable
fun WidgetSlot(
    vm: LauncherViewModel,
    id: WidgetId,
    content: @Composable (widthDp: Int, heightDp: Int) -> Unit,
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val editing = vm.widgetEdit
    // the stored size is the source of truth; the local copy only makes the live drag smooth, and it
    // re-reads the stored value whenever that changes (preset width, reset size)
    val storedWidth = vm.settings.widgetWidth(id)
    val storedHeight = vm.settings.widgetHeight(id)
    var width by remember(id, storedWidth) { mutableStateOf(storedWidth.toFloat()) }
    var height by remember(id, storedHeight) { mutableStateOf(storedHeight.toFloat()) }
    var dragging by remember(id) { mutableStateOf(false) }

    val grab = with(density) { GrabDp.dp.toPx() }
    val minH = with(density) { MinHeightDp.dp.toPx() }
    val holdPx = with(density) { 14.dp.toPx() }

    BoxWithConstraints {
        val maxW = with(density) { maxWidth.toPx() }

        Box(
            Modifier
                .width(width.dp)
                .then(if (height > 0f) Modifier.height(height.dp) else Modifier)
                // hold → edit + resize; drag from an edge/grip → resize
                .pointerInput(id, maxW, grab, minH, holdPx, storedWidth, storedHeight, density) {
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var moved = false
                            // the size at the start of this gesture, not the one this block captured
                            val widthPx = with(density) { width.dp.toPx() }
                            val heightPx = with(density) { height.dp.toPx() }
                            val handle = WidgetMath.handleAt(
                                down.position.x, down.position.y, widthPx, heightPx, grab,
                            )
                            var drag = handle != null
                            // a hold anywhere on the widget arms the resize, so every widget can be
                            // sized with one gesture instead of hunting for the 22 dp edge strip
                            val holdJob = launch {
                                delay(HoldMs)
                                if (!moved) {
                                    vm.widgetEdit = true
                                    drag = true
                                    dragging = true
                                }
                            }
                            var last = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // the widget content (calendar day, list, button) has priority
                                if (change.isConsumed) {
                                    holdJob.cancel()
                                    return@awaitEachGesture
                                }
                                if (!change.pressed) break
                                val delta = change.position - last
                                last = change.position
                                if (!moved && (change.position - down.position).getDistance() > holdPx) {
                                    moved = true
                                    holdJob.cancel()
                                    // a plain drag inside the widget belongs to the content (scroll / tap)
                                    if (handle == null) return@awaitEachGesture
                                }
                                if (drag) {
                                    change.consume()
                                    val active = handle ?: WidgetMath.WidgetHandle.CORNER
                                    if (active != WidgetMath.WidgetHandle.BOTTOM) width = WidgetMath.width(width, delta.x, maxW = maxW)
                                    if (active != WidgetMath.WidgetHandle.RIGHT) height = WidgetMath.height(height, delta.y, minH = minH)
                                }
                            }
                            holdJob.cancel()
                            if (dragging) vm.settings.setWidgetSize(id, width.roundToInt(), height.roundToInt())
                            dragging = false
                        }
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

            // editing chrome: dashed frame, name, remove / move / reset
            if (editing) {
                Canvas(Modifier.matchParentSize()) {
                    drawRect(
                        color = p.ink,
                        style = Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                        ),
                    )
                }
                // while editing, taps must not reach the widget content
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { },
                )
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(y = (-20).dp)
                        .background(p.ink)
                        .padding(horizontal = dp(4f), vertical = dp(1f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dp(3f)),
                ) {
                    RetroText(id.label, size = 10f, weight = FontWeight.Bold, color = p.paper, maxLines = 1)
                    RetroText(
                        if (height > 0f) "${width.roundToInt()}x${height.roundToInt()}" else "${width.roundToInt()}w",
                        size = 9f, color = p.paper, maxLines = 1,
                    )
                }
            }

            // printed corner grip, so the resize handle is discoverable
            if (!editing && width > 120f && height > 60f) {
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
                        .align(Alignment.BottomStart)
                        .offset(y = 16.dp)
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

/**
 * The desktop widget column: whatever the user enabled, in their order, each in a [WidgetSlot].
 * Called by every shell, so adding or removing a widget works the same on Linux, macOS and Windows.
 */
@Composable
fun DesktopWidgets(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val widgets = vm.settings.widgets
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dp(12f)),
        horizontalAlignment = Alignment.End,
    ) {
        widgets.forEach { id ->
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(dp(3f))) {
                Box(Modifier.padding(top = if (vm.widgetEdit) dp(22f) else dp(0f))) {
                    WidgetSlot(vm, id) { w, h -> WidgetContent(vm, id, w, h) }
                }
                if (vm.widgetEdit) WidgetToolbar(vm, id)
            }
        }
        if (vm.widgetEdit) {
            AddWidgetBar(vm, Modifier.padding(end = dp(2f)))
        } else if (widgets.isEmpty()) {
            Box(
                Modifier
                    .background(p.paper)
                    .border(dp(1.4f), p.ink)
                    .padding(dp(8f)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(dp(3f))) {
                    RetroText("no widgets", size = 11f, weight = FontWeight.Bold, maxLines = 1)
                    RetroText("right click or hold the desktop, then add", size = 10f, color = p.mid, maxLines = 2)
                }
            }
        }
        if (!vm.widgetEdit && widgets.isNotEmpty()) {
            RetroText("hold a widget to edit or resize", size = 9f, color = p.mid, maxLines = 1)
        }
    }
}

/** The widget body, chosen by id — shared by every shell. */
@Composable
private fun WidgetContent(vm: LauncherViewModel, id: WidgetId, widthDp: Int, heightDp: Int) {
    val w = widthDp.dp
    val h = if (heightDp > 0) heightDp.dp else null
    when (id) {
        WidgetId.CLOCK -> ClockWidget(vm, width = w, height = h)
        WidgetId.CALENDAR -> CalendarWidget(vm, width = w, height = h)
        WidgetId.SYSTEM -> SystemWidget(vm, width = w, height = h)
        WidgetId.NOTES -> NotesWidget(vm, width = w, height = h)
    }
}

/** Edit mode footer: add any widget the user removed, or leave edit mode. */
@Composable
private fun AddWidgetBar(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .width(dp(250f))
            .background(p.paper)
            .border(dp(1.6f), p.ink)
            .padding(dp(7f)),
        verticalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        RetroText("add or remove widgets", size = 11f, weight = FontWeight.Bold, maxLines = 1)
        WidgetId.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(dp(5f))) {
                pair.forEach { id ->
                    val on = vm.settings.isWidgetEnabled(id)
                    RetroButton(
                        label = if (on) "${id.label} *" else id.label,
                        active = on,
                        modifier = Modifier.weight(1f),
                        onClick = { if (on) vm.settings.removeWidget(id) else vm.settings.addWidget(id) },
                    )
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        RetroText("marked * are on the desktop", size = 9.5f, color = p.mid, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(dp(5f))) {
            RetroButton(label = "done", modifier = Modifier.weight(1f)) { vm.widgetEdit = false }
            RetroButton(label = "reset sizes", modifier = Modifier.weight(1f)) { vm.settings.resetWidgets() }
            RetroButton(label = "reset layout", modifier = Modifier.weight(1f)) { vm.settings.resetWidgetList() }
        }
    }
}

/** Per-widget actions while the home screen is in widget-editing mode. */
@Composable
private fun WidgetToolbar(vm: LauncherViewModel, id: WidgetId) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(dp(4f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RetroButton(label = "^", minWidth = dp(24f)) { vm.settings.moveWidget(id, -1) }
        RetroButton(label = "v", minWidth = dp(24f)) { vm.settings.moveWidget(id, 1) }
        RetroButton(label = "fit", minWidth = dp(34f)) { vm.settings.resetWidgetSize(id) }
        RetroButton(
            label = "remove",
            minWidth = dp(58f),
            active = true,
            onClick = { vm.settings.removeWidget(id) },
        )
    }
}

/** Small helper so shells can show the widget count in their menus. */
fun widgetSummary(vm: LauncherViewModel): String {
    val list = vm.settings.widgets
    return if (list.isEmpty()) "no widgets" else list.joinToString(", ") { it.label }
}
