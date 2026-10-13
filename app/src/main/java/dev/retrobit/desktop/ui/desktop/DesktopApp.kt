package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.WallPattern
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.LocalViewModel
import dev.retrobit.desktop.ui.boot.BootScreen
import dev.retrobit.desktop.ui.components.ContextMenu
import dev.retrobit.desktop.ui.components.DragGhost
import dev.retrobit.desktop.ui.components.LocalUiScale
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.Scanlines
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalDesktopStyle
import dev.retrobit.desktop.ui.theme.LocalIconMode
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.Palettes
import dev.retrobit.desktop.ui.theme.rememberWallpaperBrush
import dev.retrobit.desktop.ui.input.LocalPointerRouter
import dev.retrobit.desktop.ui.input.PointerRouter
import dev.retrobit.desktop.ui.input.StylusCursorOverlay
import dev.retrobit.desktop.ui.input.watchPointer
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState

@Composable
fun DesktopApp(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings = vm.settings
    val inverted = vm.invertRequested % 2 == 1
    val palette = remember(settings.palette, inverted) { Palettes.of(settings.palette).let { if (inverted) it.invert() else it } }
    val drag = remember { DragState() }
    val focus = remember { FocusRequester() }
    val pointer = remember { PointerRouter() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // pen / mouse behaviour follows the settings section "input & devices"
    LaunchedEffect(settings.penRightClick, settings.showCursor) {
        pointer.penRightClick = settings.penRightClick
        pointer.showCursor = settings.showCursor
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalIconMode provides settings.iconMode,
        LocalDesktopStyle provides settings.style,
        LocalUiScale provides settings.uiScale,
        LocalViewModel provides vm,
        LocalDragState provides drag,
        LocalPointerRouter provides pointer,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(palette.paper)
                .focusRequester(focus)
                .focusable()
                .watchPointer(pointer)
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.T -> { vm.openWindow(WinKind.TERMINAL); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.S -> { vm.openWindow(WinKind.SETTINGS); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.M -> { vm.wm.minimizeAll(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.W -> { vm.wm.focused?.let { vm.wm.close(it.id) }; true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.D -> { vm.desktopMenu(Offset(120f, 160f)); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.K -> { vm.toggleKeyboard?.invoke(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.Right -> { vm.wm.cycleFocus(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.Left -> { vm.wm.cycleFocus(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.Up -> {
                            vm.wm.focused?.let { vm.wm.toggleMaximize(it.id) }; true
                        }
                        ev.key == Key.Tab -> { vm.wm.cycleFocus(); true }
                        ev.key == Key.MetaLeft || ev.key == Key.MetaRight -> { vm.spotlight = true; true }
                        ev.key == Key.Escape -> {
                            vm.menu = null; vm.spotlight = false; vm.startMenu = false; vm.launchpad = false; true
                        }
                        else -> false
                    }
                },
        ) {
            Wallpaper(settings.wallpaper, Modifier.fillMaxSize())

            when (settings.style) {
                DesktopStyle.LINUX -> LinuxShell(vm)
                DesktopStyle.MACOS -> MacosShell(vm)
                DesktopStyle.WINDOWS -> WindowsShell(vm)
            }

            vm.menu?.let { m ->
                ContextMenu(items = m.items, anchor = m.anchors, onDismiss = { vm.menu = null })
            }

            if (vm.spotlight) Spotlight(vm)

            DragGhost(dp(64f))

            if (vm.booting) BootScreen(vm) { vm.booting = false }

            // e-paper pointer for the M-Pencil / mouse, drawn above everything else
            StylusCursorOverlay(pointer)
        }
    }
}

/** E-paper wallpaper: paper base, ordered-dither pattern, registration marks and a panel label. */
@Composable
fun Wallpaper(pattern: WallPattern, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val brush = rememberWallpaperBrush(pattern, p.pattern, dp(3f))
    Box(modifier.background(p.paper)) {
        if (brush != null) {
            Box(Modifier.fillMaxSize().background(brush))
        }
        Canvas(Modifier.fillMaxSize()) {
            val m = 26f
            val len = 30f
            val c = p.ink.copy(alpha = 0.55f)
            val corners = listOf(
                Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height),
            )
            corners.forEachIndexed { i, o ->
                val sx = if (i % 2 == 0) 1f else -1f
                val sy = if (i < 2) 1f else -1f
                drawLine(c, Offset(o.x + sx * m, o.y + sy * m), Offset(o.x + sx * (m + len), o.y + sy * m), 1.5f)
                drawLine(c, Offset(o.x + sx * m, o.y + sy * m), Offset(o.x + sx * m, o.y + sy * (m + len)), 1.5f)
            }
        }
        RetroText(
            "RETROBIT E-PAPER · ${p.id.label.uppercase()} · ${pattern.label.uppercase()}",
            size = 9.5f,
            color = p.ink.copy(alpha = 0.45f),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(dp(14f)),
        )
        Scanlines(alpha = 0.035f)
    }
}
