package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.IconTheme
import dev.retrobit.desktop.model.WallPattern
import dev.retrobit.desktop.data.WallpaperSource
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
import dev.retrobit.desktop.ui.theme.LocalIconTheme
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.Palettes
import dev.retrobit.desktop.ui.theme.rememberWallpaperBrush
import dev.retrobit.desktop.ui.input.LocalPointerRouter
import dev.retrobit.desktop.ui.input.PointerRouter
import dev.retrobit.desktop.ui.input.StylusCursorOverlay
import dev.retrobit.desktop.ui.input.watchPointer
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState

/** Ctrl+Alt+1..4 switch workspace, Ctrl+Alt+Shift+1..4 move the focused window. */
private val NUMBER_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four)

@Composable
fun DesktopApp(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings = vm.settings
    val inverted = vm.invertRequested % 2 == 1
    val palette = remember(settings.palette, inverted) { Palettes.of(settings.palette).let { if (inverted) it.invert() else it } }
    val drag = remember { DragState() }
    val density = LocalDensity.current
    // the tablet has rounded corners and a punch-hole camera: keep the chrome out of both
    val insets = dev.retrobit.desktop.ui.theme.rememberDesktopInsets(settings.safeArea, settings.cornerInset)
    val focus = remember { FocusRequester() }
    val pointer = remember { PointerRouter() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // pen / mouse behaviour follows the settings section "input & devices"
    LaunchedEffect(settings.penRightClick, settings.showCursor) {
        pointer.penRightClick = settings.penRightClick
        pointer.showCursor = settings.showCursor
    }
    // load / re-render the background whenever the source, the picture, the tone or palette changes
    LaunchedEffect(settings.wallpaperSource, settings.wallpaperUri, settings.wallpaperTone, settings.palette, settings.style) {
        vm.refreshWallpaper()
    }
    LaunchedEffect(settings.iconTheme, settings.style, settings.iconAccent, settings.iconMonogram) {
        dev.retrobit.desktop.data.IconCache.clear()
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalIconMode provides settings.iconMode,
        LocalIconTheme provides IconTheme.resolve(settings.iconThemeEnum, settings.style),
        LocalIconOptions provides dev.retrobit.desktop.ui.theme.IconOptions(
            accent = if (settings.iconAccent) palette.accentColor else null,
            monogram = settings.iconMonogram,
            labels = settings.iconLabels,
        ),
        LocalDesktopStyle provides settings.style,
        dev.retrobit.desktop.ui.theme.LocalDesktopInsets provides insets,
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
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.DirectionRight -> { vm.wm.cycleFocus(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.DirectionLeft -> { vm.wm.cycleFocus(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.DirectionUp -> {
                            vm.wm.focused?.let { vm.wm.toggleMaximize(it.id) }; true
                        }
                        ev.key == Key.Tab -> { vm.wm.cycleFocus(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key in NUMBER_KEYS -> {
                            val index = NUMBER_KEYS.indexOf(ev.key)
                            if (ev.isShiftPressed) vm.moveFocusedToDesktop(index) else vm.switchDesktop(index)
                            true
                        }
                        ev.key == Key.MetaLeft || ev.key == Key.MetaRight -> { vm.spotlight = true; true }
                        ev.key == Key.Escape -> {
                            vm.menu = null; vm.spotlight = false; vm.startMenu = false; vm.launchpad = false; true
                        }
                        else -> false
                    }
                },
        ) {
            Wallpaper(
                pattern = settings.wallpaper,
                source = settings.wallpaperSourceEnum,
                image = vm.wallpaperImage,
                busy = vm.wallpaperBusy,
                note = vm.wallpaperNote,
                modifier = Modifier.fillMaxSize(),
            )

            // everything except the wallpaper honours the safe area
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = with(density) { insets.left.toDp() },
                        top = with(density) { insets.top.toDp() },
                        end = with(density) { insets.right.toDp() },
                        bottom = with(density) { insets.bottom.toDp() },
                    ),
            ) {
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
            }

            // e-paper pointer for the M-Pencil / mouse, drawn above everything else
            StylusCursorOverlay(pointer)
        }
    }
}

/**
 * E-paper wallpaper. Either a generated dither pattern, the tablet's own wallpaper, or a picture the
 * user picked — the latter two are converted to ink/paper by [dev.retrobit.desktop.data.WallpaperRepository].
 */
@Composable
fun Wallpaper(
    pattern: WallPattern,
    modifier: Modifier = Modifier,
    source: WallpaperSource = WallpaperSource.PATTERN,
    image: androidx.compose.ui.graphics.ImageBitmap? = null,
    busy: Boolean = false,
    note: String = "",
) {
    val p = LocalPalette.current
    val brush = rememberWallpaperBrush(pattern, p.pattern, dp(3f))
    Box(modifier.background(p.paper)) {
        if (source != WallpaperSource.PATTERN) {
            if (image != null) {
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                )
            } else {
                Column(
                    Modifier.align(androidx.compose.ui.Alignment.Center).padding(dp(24f)),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dp(8f)),
                ) {
                    RetroText(
                        if (busy) "developing image…" else "wallpaper: ${source.label.lowercase()}",
                        size = 16f, display = true,
                    )
                    RetroText(
                        note.ifEmpty { "choose a picture in Settings → wallpaper" },
                        size = 11f, color = p.mid, maxLines = 3,
                    )
                }
                if (brush != null) Box(Modifier.fillMaxSize().background(brush))
            }
        } else if (brush != null) {
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
            "RETROBIT E-PAPER · ${p.id.label.uppercase()} · " +
                if (source == WallpaperSource.PATTERN) pattern.label.uppercase() else source.label.uppercase(),
            size = 10f,
            color = p.ink.copy(alpha = 0.45f),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(dp(14f)),
        )
        Scanlines(alpha = 0.035f)
    }
}
