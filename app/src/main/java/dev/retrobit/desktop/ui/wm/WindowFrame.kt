package dev.retrobit.desktop.ui.wm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.apps.WindowContent
import dev.retrobit.desktop.ui.components.DitherTitleBar
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.LocalUiScale
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.sp
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.ditherGradient
import dev.retrobit.desktop.ui.theme.rememberChromeBrush
import dev.retrobit.desktop.ui.theme.ChromePattern
import androidx.compose.foundation.Image
import kotlin.math.roundToInt


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
    Box(modifier.fillMaxSize()) {
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

    Box(
        Modifier
            .offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
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
        if (focused && !win.maximized) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dp(26f))
                    .pointerInput(win.id) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            wm.resize(win.id, amount)
                        }
                    },
            ) {
                Grippy()
            }
        }
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
private fun Grippy() {
    val p = LocalPalette.current
    Canvas(Modifier.fillMaxSize().padding(dp(2f))) {
        val step = size.width / 4f
        for (i in 1..3) for (j in 1..3) {
            if (i + j >= 3) {
                drawRect(p.ink.copy(alpha = 0.65f), Offset(i * step - 1f, j * step - 1f), Size(2f, 2f))
            }
        }
    }
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
                .pointerInput(win.id) {
                    detectDragGestures(
                        onDragStart = { wm.focus(win.id) },
                        onDrag = { change, amount -> change.consume(); wm.move(win.id, amount) },
                    )
                }
                .padding(horizontal = dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(4f)),
        ) {
            GlyphIcon(if (win.isApp) Glyph.GRID else Glyph.TERMINAL, dp(12f))
            Box(Modifier.weight(1f)) { TitleLabel(win, focused, p.paper) }
            TitleButton(Sym.MIN, inverted = true, onClick = { wm.minimize(win.id) })
            TitleButton(Sym.MAX, inverted = true, onClick = { wm.toggleMaximize(win.id) })
            TitleButton(Sym.CLOSE, inverted = true, onClick = { wm.close(win.id) })
        }
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
                .pointerInput(win.id) {
                    detectDragGestures(
                        onDragStart = { wm.focus(win.id) },
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
            }
        }
    }
}

enum class Sym { MIN, MAX, CLOSE }

@Composable
private fun TitleButton(sym: Sym, inverted: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val fg = if (inverted) p.ink else p.ink
    val bg = if (inverted) p.paper else p.paper
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
            }
        }
    }
}
