package dev.retrobit.desktop.ui.wm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import dev.retrobit.desktop.data.Settings
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LocalViewModel
import dev.retrobit.desktop.ui.components.DitherTitleBar
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.retroShadow
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.desktop.ShellContent
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.NativeSkin
import dev.retrobit.desktop.ui.theme.ditherGradient
import kotlin.math.roundToInt

/**
 * Draws every floating window of the current desktop, back to front, plus the dashed preview of an
 * edge snap while a window is being dragged against the side of the screen.
 *
 * Maximized windows are **not** drawn here: they own the screen and are rendered by
 * [dev.retrobit.desktop.ui.desktop.ShellHost], the same full-screen frame the cyberdeck chrome draws
 * over somebody else's app.
 */
@Composable
fun WindowLayer(wm: WindowManager, modifier: Modifier = Modifier) {
    val windows = wm.visible
    val preview = wm.snapPreview
    val p = LocalPalette.current
    Box(modifier.fillMaxSize()) {
        if (preview != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = p.ink.copy(alpha = 0.10f),
                    topLeft = Offset(preview.left, preview.top),
                    size = Size(preview.width, preview.height),
                )
                drawRect(
                    color = p.ink,
                    topLeft = Offset(preview.left, preview.top),
                    size = Size(preview.width, preview.height),
                    style = Stroke(
                        width = 3f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
            }
        }
        for (w in windows) {
            key(w.id) { WindowFrame(w, wm) }
        }
    }
}

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

/** Drag the window by its title bar; releasing against a screen edge snaps it (see [WindowManager.endDrag]). */
private fun Modifier.dragByTitleBar(win: WinState, wm: WindowManager): Modifier = this.pointerInput(win.id) {
    detectDragGestures(
        onDragStart = { wm.focus(win.id) },
        onDragCancel = { wm.cancelDrag() },
        onDragEnd = { wm.endDrag(win.id) },
        onDrag = { change, amount -> change.consume(); wm.move(win.id, amount) },
    )
}

/**
 * One desktop window: a framed box with a title bar you can drag, eight resize handles and a live
 * app inside.
 *
 * The title bar follows the desktop style the user picked (Linux solid, macOS traffic lights,
 * Windows 95 dithered gradient), so a window looks like it belongs to the shell it sits on.
 */
@Composable
fun WindowFrame(win: WinState, wm: WindowManager) {
    val p = LocalPalette.current
    val style = dev.retrobit.desktop.ui.theme.LocalDesktopStyle.current
    val skin = p.skin
    val focused = wm.focusedId == win.id
    val density = LocalDensity.current
    // real desktops have their own title bar height: Windows 95 is short and square, macOS and
    // GNOME are taller, and the e-paper palettes keep the print height
    val titleH = when {
        skin == null -> dp(24f + 6f)
        skin.trafficLights -> dp(28f)
        skin.isFlatTitle -> dp(32f)
        else -> dp(24f)
    }
    // The printed windows (e-paper palettes) keep their two rounded top corners - the classic
    // print look. A native theme with rounded corners rounds all four, the way its own windows are
    // drawn (Aqua, Windows 11, Ubuntu, GNOME), while Windows 95 stays a hard square.
    val shape = if (skin != null && skin.cornerDp > 0f) {
        androidx.compose.foundation.shape.RoundedCornerShape(dp(p.cornerDp))
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = dp(p.cornerDp), topEnd = dp(p.cornerDp),
        )
    }

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
                .then(if (focused) Modifier.retroShadow(dp(if (skin != null) 6f else 5f)) else Modifier)
                .clip(shape)
                .background(p.paper)
                .border(
                    width = when {
                        skin == null -> dp(if (focused) 2.4f else 1.4f)
                        // Windows 95 frames its windows with a hard black box, the modern ones
                        // barely draw a line at all
                        skin.cornerDp == 0f -> dp(2f)
                        else -> dp(1f)
                    },
                    color = if (skin == null && !focused) p.mid else p.edge,
                    shape = shape,
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                when {
                    skin != null -> NativeTitleBar(skin, win, wm, focused, titleH)
                    style == DesktopStyle.MACOS -> MacTitleBar(win, wm, focused, titleH)
                    style == DesktopStyle.WINDOWS -> Win95TitleBar(win, wm, focused, titleH)
                    else -> LinuxTitleBar(win, wm, focused, titleH)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(p.paper)
                        // a tap on the body focuses the window and is *consumed*: nothing falls
                        // through to the desktop icons behind the box
                        .pointerInput(win.id) { detectTapGestures { wm.focus(win.id) } }
                        .padding(bottom = keyboardPadInside(win.height, density)),
                ) {
                    ShellContent(win.kind)
                }
            }
        }
        ResizeHandles(win, wm, grab)
    }
}

/**
 * Room for the on-screen keyboard **inside** a floating window.
 *
 * With the default keyboard mode (`overlay`) the keyboard simply floats above the desktop, so the
 * padding is zero: the window, its frame and everything on the desktop keep their exact place. In
 * "room inside apps" mode the content of the focused window shrinks a little - at most 45% of the
 * window height - so the line being typed is not hidden, while the **frame still does not move**.
 */
@Composable
private fun keyboardPadInside(windowHeight: Float, density: Density): Dp {
    val settings = LocalViewModel.current.settings
    if (settings.keyboardMode != Settings.KEYBOARD_CONTENT) return dp(0f)
    val ime = WindowInsets.ime.getBottom(density)
    return with(density) { minOf(ime.toFloat(), windowHeight * 0.45f).toDp() }
}

@Composable
private fun TitleLabel(win: WinState, focused: Boolean, color: Color? = null) {
    val p = LocalPalette.current
    RetroText(
        text = win.title,
        size = 12f,
        weight = if (focused) FontWeight.Bold else FontWeight.Normal,
        color = color ?: p.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The glyph a window shows in its title bar and in the task list. */
fun glyphFor(kind: WinKind): Glyph = when (kind) {
    WinKind.APPS -> Glyph.GRID
    WinKind.NOTES -> Glyph.NOTES
    WinKind.CALENDAR -> Glyph.CALENDAR
    WinKind.SETTINGS -> Glyph.SETTINGS
    WinKind.SYSMON -> Glyph.SYSMON
    WinKind.ABOUT -> Glyph.ABOUT
    WinKind.TERMINAL -> Glyph.TERMINAL
}

/**
 * The title bar of a **native** theme, in the colours of the desktop it copies:
 *
 *  · Windows 95 - the navy→blue gradient, bold white title, three beveled grey boxes on the right;
 *  · Windows 11 - a flat Mica bar, white title and three small flat buttons;
 *  · Aqua / Aqua dark - a light or graphite gradient with the red/amber/green traffic lights;
 *  · Ubuntu / Adwaita - a dark or flat headerbar with round buttons, Ubuntu's close button in a
 *    red circle. Everything the e-paper bars do (drag, double tap, raise, live size) still works.
 */
@Composable
private fun NativeTitleBar(
    skin: NativeSkin,
    win: WinState,
    wm: WindowManager,
    focused: Boolean,
    height: Dp,
) {
    val gradient = remember(skin, focused) {
        val top = if (focused) skin.titleTop else lerp(skin.titleTop, skin.window, 0.55f)
        val bottom = if (focused) skin.titleBottom else lerp(skin.titleBottom, skin.window, 0.55f)
        if (top == bottom) androidx.compose.ui.graphics.SolidColor(top)
        else Brush.verticalGradient(listOf(top, bottom))
    }
    val textColor = if (focused) skin.titleText else skin.titleText.copy(alpha = 0.65f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(gradient)
            .raiseOnPress(win, wm)
            .maximizeOnDoubleTap(win, wm)
            .dragByTitleBar(win, wm),
    ) {
        if (skin.trafficLights) {
            Row(
                Modifier.align(Alignment.CenterStart).padding(start = dp(10f)),
                horizontalArrangement = Arrangement.spacedBy(dp(8f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrafficLight(Sym.CLOSE, tone = TrafficTone.CLOSE, onClick = { wm.close(win.id) })
                TrafficLight(Sym.MIN, tone = TrafficTone.MIN, onClick = { wm.minimize(win.id) })
                TrafficLight(Sym.MAX, tone = TrafficTone.MAX, onClick = { wm.toggleMaximize(win.id) })
            }
            Box(Modifier.fillMaxSize().padding(horizontal = dp(84f)), contentAlignment = Alignment.Center) {
                TitleLabel(win, focused, textColor)
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(horizontal = dp(6f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                GlyphIcon(glyphFor(win.kind), dp(12f))
                Box(Modifier.weight(1f)) { TitleLabel(win, focused, textColor) }
                if (wm.resizingId == win.id) {
                    RetroText("${win.width.toInt()}x${win.height.toInt()}", size = 9.5f, color = textColor, maxLines = 1)
                }
                if (skin.flatButtons) {
                    // Windows 11 / GNOME: three small flat discs instead of beveled boxes
                    FlatWindowButton(Sym.MIN, skin, onClick = { wm.minimize(win.id) })
                    FlatWindowButton(Sym.MAX, skin, onClick = { wm.toggleMaximize(win.id) })
                    FlatWindowButton(Sym.CLOSE, skin, onClick = { wm.close(win.id) })
                } else {
                    // Windows 95: silver boxes with a bevelled edge and black glyphs
                    BevelButton(Sym.MIN, skin, onClick = { wm.minimize(win.id) })
                    BevelButton(Sym.MAX, skin, onClick = { wm.toggleMaximize(win.id) })
                    BevelButton(Sym.CLOSE, skin, onClick = { wm.close(win.id) })
                }
            }
        }
        // the status line under the title bar that GNOME / macOS draw
        Canvas(Modifier.fillMaxWidth().height(dp(1f)).align(Alignment.BottomCenter)) {
            drawRect(skin.border.copy(alpha = if (focused) 0.85f else 0.45f))
        }
    }
}

/** Which of the three traffic lights this is: macOS colours them red, amber and green. */
enum class TrafficTone { CLOSE, MIN, MAX }

@Composable
private fun TrafficLight(sym: Sym, tone: TrafficTone, onClick: () -> Unit) {
    val colour = when (tone) {
        TrafficTone.CLOSE -> Color(0xFFFF5F57)
        TrafficTone.MIN -> Color(0xFFFEBC2E)
        TrafficTone.MAX -> Color(0xFF28C840)
    }
    Box(
        Modifier
            .size(dp(13f))
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(colour)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension * 0.20f
            val ink = Color.Black.copy(alpha = 0.65f)
            when (sym) {
                Sym.CLOSE -> {
                    drawLine(ink, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), 1.5f)
                    drawLine(ink, Offset(c.x + r, c.y - r), Offset(c.x - r, c.y + r), 1.5f)
                }
                Sym.MIN -> drawRect(ink, Offset(c.x - r * 1.4f, c.y - 0.8f), Size(r * 2.8f, 1.6f))
                Sym.MAX -> drawRect(ink, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = Stroke(1.4f))
            }
        }
    }
}

/** Windows 95 / classic: a grey box with a light top-left and a dark bottom-right bevel. */
@Composable
private fun BevelButton(sym: Sym, skin: NativeSkin, onClick: () -> Unit) {
    Box(
        Modifier
            .size(dp(17f))
            .background(skin.buttonFace)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val light = Color.White
            val dark = Color.Black.copy(alpha = 0.55f)
            drawLine(light, Offset(0f, 0f), Offset(size.width, 0f), 1.2f)
            drawLine(light, Offset(0f, 0f), Offset(0f, size.height), 1.2f)
            drawLine(dark, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.2f)
            drawLine(dark, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1.2f)
            val c = center
            val r = size.minDimension * 0.22f
            when (sym) {
                Sym.CLOSE -> {
                    drawLine(skin.buttonText, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), 1.7f)
                    drawLine(skin.buttonText, Offset(c.x + r, c.y - r), Offset(c.x - r, c.y + r), 1.7f)
                }
                Sym.MIN -> drawRect(skin.buttonText, Offset(c.x - r * 1.2f, c.y + r * 0.6f), Size(r * 2.4f, 1.8f))
                Sym.MAX -> drawRect(skin.buttonText, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = Stroke(1.7f))
            }
        }
    }
}

/** Windows 11 / GNOME: a small flat disc that lights up under the glyph. */
@Composable
private fun FlatWindowButton(sym: Sym, skin: NativeSkin, onClick: () -> Unit) {
    val tint = if (sym == Sym.CLOSE) Color(0xFFE81123) else skin.highlight
    Box(
        Modifier
            .size(dp(22f))
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.20f
            val c = center
            when (sym) {
                Sym.CLOSE -> {
                    drawLine(skin.buttonText, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), 1.5f)
                    drawLine(skin.buttonText, Offset(c.x + r, c.y - r), Offset(c.x - r, c.y + r), 1.5f)
                }
                Sym.MIN -> drawRect(skin.buttonText, Offset(c.x - r * 1.2f, c.y - 0.8f), Size(r * 2.4f, 1.6f))
                Sym.MAX -> drawRect(skin.buttonText, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = Stroke(1.5f))
            }
            // the accent dot every GNOME window button keeps in the corner
            drawCircle(tint.copy(alpha = 0.9f), radius = 1.6f, center = Offset(size.width - 4f, size.height - 4f))
        }
    }
}

@Composable
private fun LinuxTitleBar(win: WinState, wm: WindowManager, focused: Boolean, height: Dp) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (focused) p.plate else p.soft)
            .raiseOnPress(win, wm)
            .maximizeOnDoubleTap(win, wm)
            .dragByTitleBar(win, wm)
            .padding(horizontal = dp(6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            GlyphIcon(glyphFor(win.kind), dp(12f))
            TitleLabel(win, focused, if (focused) p.paper else p.ink)
        }
        if (wm.resizingId == win.id) {
            RetroText("${win.width.toInt()}x${win.height.toInt()}", size = 9.5f, color = p.mid, maxLines = 1)
        }
        TitleButton(Sym.MIN, onClick = { wm.minimize(win.id) })
        TitleButton(Sym.MAX, onClick = { wm.toggleMaximize(win.id) })
        TitleButton(Sym.CLOSE, onClick = { wm.close(win.id) })
    }
}

@Composable
private fun Win95TitleBar(win: WinState, wm: WindowManager, focused: Boolean, height: Dp) {
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
                .dragByTitleBar(win, wm)
                .padding(horizontal = dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(4f)),
        ) {
            GlyphIcon(glyphFor(win.kind), dp(12f))
            Box(Modifier.weight(1f)) { TitleLabel(win, focused, p.paper) }
            if (wm.resizingId == win.id) {
                RetroText("${win.width.toInt()}x${win.height.toInt()}", size = 9.5f, color = p.paper, maxLines = 1)
            }
            TitleButton(Sym.MIN, onClick = { wm.minimize(win.id) })
            TitleButton(Sym.MAX, onClick = { wm.toggleMaximize(win.id) })
            TitleButton(Sym.CLOSE, onClick = { wm.close(win.id) })
        }
    }
}

@Composable
private fun MacTitleBar(win: WinState, wm: WindowManager, focused: Boolean, height: Dp) {
    val p = LocalPalette.current
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(p.paper)
                .raiseOnPress(win, wm)
                .maximizeOnDoubleTap(win, wm)
                .dragByTitleBar(win, wm)
                .padding(horizontal = dp(8f)),
            contentAlignment = Alignment.Center,
        ) {
            TitleLabel(win, focused)
            Row(
                Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(dp(7f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrintTrafficLight(Sym.CLOSE, onClick = { wm.close(win.id) })
                PrintTrafficLight(Sym.MIN, onClick = { wm.minimize(win.id) })
                PrintTrafficLight(Sym.MAX, onClick = { wm.toggleMaximize(win.id) })
            }
        }
        Canvas(Modifier.fillMaxWidth().height(dp(1f))) {
            drawRect(p.ink.copy(alpha = if (focused) 0.75f else 0.35f))
        }
    }
}

@Composable
private fun PrintTrafficLight(sym: Sym, onClick: () -> Unit) {
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

/** The three window buttons: minimize, maximize (or restore), close. */
enum class Sym { MIN, MAX, CLOSE }

@Composable
private fun TitleButton(sym: Sym, onClick: () -> Unit) {
    val p = LocalPalette.current
    val fg = p.ink
    val bg = p.paper
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
private fun rawPx(value: Float, density: Density): Float = with(density) { Dp(value).toPx() }

/**
 * Manual window sizing: eight invisible handles (four edges, four corners) that resize the window
 * under the pointer. A live size readout appears while dragging, like on a classic desktop.
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

    // corner grip: a printed marker in the bottom-right, or the live size while resizing
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
                for (i in 0 until 3) {
                    val o = size.width - i * 4f - 2f
                    drawLine(p.ink, Offset(o, size.height - 1f), Offset(size.width - 1f, size.height - o), 1.6f)
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
