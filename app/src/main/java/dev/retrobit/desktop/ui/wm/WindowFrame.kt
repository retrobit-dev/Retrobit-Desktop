package dev.retrobit.desktop.ui.wm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.ui.apps.WindowContent
import dev.retrobit.desktop.ui.components.DitherTitleBar
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.LocalUiScale
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.ditherGradient
import dev.retrobit.desktop.ui.theme.rememberChromeBrush
import dev.retrobit.desktop.ui.theme.ChromePattern
import kotlin.math.roundToInt


/** Double tap / double click on a title bar maximizes the window (or restores it). */
private fun Modifier.maximizeOnDoubleTap(win: WinState, wm: WindowManager): Modifier =
    this.pointerInput(win.id) {
        detectTapGestures(onDoubleTap = { wm.toggleMaximize(win.id) })
    }

/** Click-to-focus helper: any press on a title bar raises and focuses that window. */
private fun Modifier.raiseOnPress(win: WinState, wm: WindowManager): Modifier = this.pointerInput(win.id) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        wm.focus(win.id)
    }
}

@Composable
fun WindowLayer(wm: WindowManager, modifier: Modifier = Modifier) {
    val windows = wm.visible
    val preview = wm.snapPreview
    val p = LocalPalette.current
    Box(modifier.fillMaxSize()) {
        // edge-snap preview: dashed ink outline, drawn under the windows
        if (preview != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = p.ink,
                    topLeft = Offset(preview.left, preview.top),
                    size = Size(preview.width, preview.height),
                    style = Stroke(
                        width = 3f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
                drawRect(
                    color = p.ink.copy(alpha = 0.10f),
                    topLeft = Offset(preview.left, preview.top),
                    size = Size(preview.width, preview.height),
                )
            }
        }
        for (w in windows) {
            key(w.id) {
                WindowFrame(w, wm)
            }
        }
    }
}

@Composable
private fun key(id: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(id) { content() }

@Composable
fun WindowFrame(win: WinState, wm: WindowManager) {
    val p = LocalPalette.current
    val style = dev.retrobit.desktop.ui.theme.LocalDesktopStyle.current
    val focused = wm.focusedId == win.id
    val scale = LocalUiScale.current
    val density = LocalDensity.current
    val titleH = dp(24f + 6f)

    // the outer box is the window plus a grab margin, so every edge can be dragged from outside
    val grab = rawPx(HandleGrabDp, density)
    Box(
        Modifier
            .offset { IntOffset((win.x - grab).roundToInt(), (win.y - grab).roundToInt()) }
            .size(
                with(density) { (win.width + grab * 2f).toDp() },
                with(density) { (win.height + grab * 2f).toDp() },
            ),
    ) {
        Box(
            Modifier
                .offset { IntOffset(grab.roundToInt(), grab.roundToInt()) }
                .size(with(density) { win.width.toDp() }, with(density) { win.height.toDp() })
                .then(if (focused) Modifier.retroShadowOffset(dp(5f)) else Modifier)
                .background(p.paper)
                .border(dp(if (focused) 2.4f else 1.4f), if (focused) p.ink else p.mid),
        ) {
        Column(Modifier.fillMaxSize()) {
            when (style) {
                DesktopStyle.MACOS -> MacTitleBar(win, wm, focused, titleH)
                DesktopStyle.WINDOWS -> Win95TitleBar(win, wm, focused, titleH)
                DesktopStyle.LINUX -> LinuxTitleBar(win, wm, focused, titleH)
            }
            Box(Modifier.fillMaxSize().background(p.paper)) {
                WindowContent(win, wm)
            }
        }
        }
        if (!win.maximized) ResizeHandles(win, wm, grab)
    }
}

@Composable
private fun Modifier.retroShadowOffset(offset: androidx.compose.ui.unit.Dp): Modifier {
    val p = LocalPalette.current
    val brush = rememberChromeBrush(ChromePattern.CHECKER, p.ink.copy(alpha = 0.85f), dp(1f))
    return this.drawBehindBrush(brush, offset)
}

private fun Modifier.drawBehindBrush(brush: androidx.compose.ui.graphics.Brush, offset: androidx.compose.ui.unit.Dp): Modifier =
    this.drawBehind {
        val o = offset.toPx()
        drawRect(brush, topLeft = Offset(o, o), size = Size(size.width, size.height))
    }


@Composable
private fun TitleLabel(win: WinState, focused: Boolean, color: Color? = null) {
    val p = LocalPalette.current
    RetroText(
        text = win.title,
        size = 12f,
        weight = if (focused) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
        color = color ?: p.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LinuxTitleBar(win: WinState, wm: WindowManager, focused: Boolean, height: androidx.compose.ui.unit.Dp) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (focused) p.ink else p.soft)
            .raiseOnPress(win, wm)
            .pointerInput(win.id, focused) {
                detectDragGestures(
                    onDragStart = { wm.focus(win.id) },
                    onDragCancel = { wm.cancelDrag() },
                    onDragEnd = { wm.endDrag(win.id) },
                    onDrag = { change, amount -> change.consume(); wm.move(win.id, amount) },
                )
            }
            .padding(horizontal = dp(6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            GlyphIcon(if (win.isApp) Glyph.GRID else Glyph.TERMINAL, dp(12f))
            TitleLabel(win, focused, if (focused) p.paper else p.ink)
        }
        TitleButton(Sym.MIN, onClick = { wm.minimize(win.id) })
        TitleButton(Sym.MAX, onClick = { wm.toggleMaximize(win.id) })
        TitleButton(Sym.CLOSE, onClick = { wm.close(win.id) })
    }
}

@Composable
private fun Win95TitleBar(win: WinState, wm: WindowManager, focused: Boolean, height: androidx.compose.ui.unit.Dp) {
    val p = LocalPalette.current
    val cells = with(LocalDensity.current) { (win.width / 3f).roundToInt().coerceAtLeast(8) }
    val hCells = with(LocalDensity.current) { height.toPx().roundToInt().coerceAtLeast(6) }
    val bmp: ImageBitmap = remember(cells, hCells, p.ink, focused) {
        ditherGradient(cells, hCells, p.ink, if (focused) 1.0f else 0.45f)
    }
    DitherTitleBar(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(dp(2f)),
        bmp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .raiseOnPress(win, wm)
                .maximizeOnDoubleTap(win, wm)
                .pointerInput(win.id) {
                    detectDragGestures(
                        onDragStart = { wm.focus(win.id) },
                        onDragCancel = { wm.cancelDrag() },
                        onDragEnd = { wm.endDrag(win.id) },
                        onDrag = { change, amount -> change.consume(); wm.move(win.id, amount) },
                    )
                }
                .padding(horizontal = dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(4f)),
        ) {
            GlyphIcon(if (win.isApp) Glyph.GRID else Glyph.TERMINAL, dp(12f))
            Box(Modifier.weight(1f)) { TitleLabel(win, focused, p.paper) }
            if (wm.resizingId == win.id) {
                RetroText("${win.width.toInt()}x${win.height.toInt()}", size = 9.5f, color = p.paper, maxLines = 1)
            }
            TitleButton(Sym.FLOAT, inverted = true, active = win.floating, onClick = { wm.toggleFloat(win.id) })
            TitleButton(Sym.MIN, inverted = true, onClick = { wm.minimize(win.id) })
            TitleButton(Sym.MAX, inverted = true, onClick = { wm.toggleMaximize(win.id) })
            TitleButton(Sym.CLOSE, inverted = true, onClick = { wm.close(win.id) })
        }
    }
}

/** One resize handle: which edge, where it sits in the grab ring, and how big it is (px). */
private data class ResizeStrip(
    val edge: ResizeEdge,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/** Grab width of every resize handle, in dp. */
private const val HandleGrabDp = 16f

/** Corner size of the resize handles, in dp. */
private const val HandleCornerDp = 20f

/** dp -> px without the scaled composable helper, so it can be used in plain functions. */
private fun rawPx(value: Float, density: androidx.compose.ui.unit.Density): Float =
    with(density) { androidx.compose.ui.unit.Dp(value).toPx() }

/**
 * Manual window sizing: eight invisible handles (four edges, four corners) that resize the window
 * under the pointer. A small grip is printed in the bottom-right corner like on a classic desktop.
 */
@Composable
private fun BoxScope.ResizeHandles(win: WinState, wm: WindowManager, grab: Float) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val corner = rawPx(HandleCornerDp, density)
    val w = win.width
    val h = win.height

    val strips = listOf(
        ResizeStrip(ResizeEdge.LEFT, 0f, grab + corner * 0.5f, grab, h - corner),
        ResizeStrip(ResizeEdge.RIGHT, grab + w, grab + corner * 0.5f, grab, h - corner),
        ResizeStrip(ResizeEdge.TOP, grab + corner * 0.5f, 0f, w - corner, grab),
        ResizeStrip(ResizeEdge.BOTTOM, grab + corner * 0.5f, grab + h, w - corner, grab),
        ResizeStrip(ResizeEdge.TOP_LEFT, 0f, 0f, grab + corner * 0.5f, grab + corner * 0.5f),
        ResizeStrip(ResizeEdge.TOP_RIGHT, grab + w - corner * 0.5f, 0f, grab + corner * 0.5f, grab + corner * 0.5f),
        ResizeStrip(ResizeEdge.BOTTOM_LEFT, 0f, grab + h - corner * 0.5f, grab + corner * 0.5f, grab + corner * 0.5f),
        ResizeStrip(ResizeEdge.BOTTOM_RIGHT, grab + w - corner * 0.5f, grab + h - corner * 0.5f, grab + corner * 0.5f, grab + corner * 0.5f),
    )
    strips.forEach { strip ->
        Box(
            Modifier
                .offset { IntOffset(strip.x.roundToInt(), strip.y.roundToInt()) }
                .size(with(density) { strip.w.toDp() }, with(density) { strip.h.toDp() })
                .resizeHandle(win, wm, strip.edge),
        )
    }

    // grip marks: a live size readout while resizing, a printed corner grip otherwise
    Box(
        Modifier
            .offset { IntOffset((grab + w - corner).roundToInt(), (grab + h - corner).roundToInt()) }
            .size(with(density) { corner.toDp() }, with(density) { corner.toDp() }),
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (wm.resizingId == win.id) {
            Box(Modifier.background(p.paper).border(dp(1.2f), p.ink).padding(horizontal = dp(3f))) {
                RetroText("${win.width.toInt()}x${win.height.toInt()}", size = 9f, color = p.ink, maxLines = 1)
            }
        } else {
            Canvas(Modifier.size(dp(14f))) {
                val ink = p.ink
                for (i in 0 until 3) {
                    val o = size.width - i * 4f - 2f
                    drawLine(ink, Offset(o, size.height - 1f), Offset(size.width - 1f, size.height - o), 1.6f)
                }
            }
        }
    }
}

/** Turns any composable into a resize grip for one window edge. */
private fun Modifier.resizeHandle(win: WinState, wm: WindowManager, edge: ResizeEdge): Modifier =
    this.pointerInput(win.id, edge) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            wm.beginResize(win.id, edge)
            down.consume()
            var last = down.position
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.position - last
                last = change.position
                change.consume()
                wm.updateResize(win.id, delta.x, delta.y)
            }
            wm.endResize()
        }
    }

@Composable
private fun MacTitleBar(win: WinState, wm: WindowManager, focused: Boolean, height: androidx.compose.ui.unit.Dp) {
    val p = LocalPalette.current
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(p.paper)
                .raiseOnPress(win, wm)
                .maximizeOnDoubleTap(win, wm)
                .pointerInput(win.id) {
                    detectDragGestures(
                        onDragStart = { wm.focus(win.id) },
                        onDragCancel = { wm.cancelDrag() },
                        onDragEnd = { wm.endDrag(win.id) },
                        onDrag = { change, amount -> change.consume(); wm.move(win.id, amount) },
                    )
                }
                .padding(horizontal = dp(8f)),
            contentAlignment = Alignment.Center,
        ) {
            TitleLabel(win, focused)
            Row(
                Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(dp(7f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrafficLight(Sym.CLOSE, onClick = { wm.close(win.id) })
                TrafficLight(Sym.MIN, onClick = { wm.minimize(win.id) })
                TrafficLight(Sym.MAX, onClick = { wm.toggleMaximize(win.id) })
                Spacer(Modifier.width(dp(6f)))
                if (win.isApp) GlyphIcon(Glyph.GRID, dp(11f))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(dp(1f))) {
            drawRect(p.ink.copy(alpha = if (focused) 0.75f else 0.35f))
        }
    }
}

@Composable
private fun TrafficLight(sym: Sym, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(dp(14f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f - 1f
            drawCircle(p.paper, radius = r, center = center)
            drawCircle(p.ink, radius = r, center = center, style = Stroke(width = 1.6f))
            when (sym) {
                Sym.CLOSE -> {
                    val d = r * 0.5f
                    drawLine(p.ink, Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), 1.6f)
                    drawLine(p.ink, Offset(center.x + d, center.y - d), Offset(center.x - d, center.y + d), 1.6f)
                }
                Sym.MIN -> drawLine(p.ink, Offset(center.x - r * 0.5f, center.y), Offset(center.x + r * 0.5f, center.y), 1.6f)
                Sym.MAX -> drawRect(p.ink, Offset(center.x - r * 0.5f, center.y - r * 0.5f), Size(r, r), style = Stroke(1.5f))
                Sym.FLOAT -> {
                    drawRect(p.ink, Offset(center.x - r * 0.7f, center.y - r * 0.3f), Size(r, r * 0.8f), style = Stroke(1.4f))
                    drawRect(p.ink, Offset(center.x - r * 0.3f, center.y - r * 0.7f), Size(r, r * 0.8f), style = Stroke(1.4f))
                }
            }
        }
    }
}

enum class Sym { MIN, MAX, CLOSE, FLOAT }

@Composable
private fun TitleButton(sym: Sym, inverted: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val fg = if (active) p.paper else p.ink
    val bg = if (active) p.ink else p.paper
    Box(
        Modifier
            .size(dp(18f))
            .background(bg)
            .border(dp(1.2f), p.ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension * 0.24f
            when (sym) {
                Sym.CLOSE -> {
                    drawLine(fg, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), 1.8f)
                    drawLine(fg, Offset(c.x + r, c.y - r), Offset(c.x - r, c.y + r), 1.8f)
                }
                Sym.MIN -> drawRect(fg, Offset(c.x - r * 1.2f, c.y - 1f), Size(r * 2.4f, 2f))
                Sym.MAX -> drawRect(fg, Offset(c.x - r * 1.2f, c.y - r * 1.2f), Size(r * 2.4f, r * 2.4f), style = Stroke(2f))
                Sym.FLOAT -> {
                    // two offset frames: "this window floats above the tiling layout"
                    drawRect(fg, Offset(c.x - r * 1.3f, c.y - r * 0.6f), Size(r * 1.8f, r * 1.5f), style = Stroke(1.6f))
                    drawRect(fg, Offset(c.x - r * 0.5f, c.y - r * 1.3f), Size(r * 1.8f, r * 1.5f), style = Stroke(1.6f))
                }
            }
        }
    }
}
