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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.key.isCtrlPressed
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

/** Ctrl+Alt+1..4 jump to a workspace page (icons and widgets). */
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
    androidx.compose.runtime.SideEffect { vm.desktopInsets = insets }
    LaunchedEffect(settings.penRightClick, settings.showCursor, settings.penHoldMs, settings.penTextMode) {
        pointer.penRightClick = settings.penRightClick
        pointer.showCursor = settings.showCursor
        pointer.penIsPen = settings.penTextMode
        pointer.holdMs = settings.penHoldMs.coerceIn(150, 1200).toLong()
    }
    // a different app / shell owns the screen now: whatever field was focused is gone, so the pen
    // is a mouse again until a field takes the focus back
    LaunchedEffect(vm.shellOpen, vm.frontApp) { pointer.resetTextFocus() }
    // load / re-render the background whenever the source, the picture, the tone or palette changes
    LaunchedEffect(settings.wallpaperSource, settings.wallpaperUri, settings.wallpaperTone, settings.palette, settings.style) {
        vm.refreshWallpaper()
    }
    // the icon cache key already carries ink/paper/theme/accent, but clearing on top of that rules
    // out a stale bitmap ever surviving a palette, mode, shape or size change
    LaunchedEffect(
        settings.iconTheme, settings.style, settings.iconAccent,
        settings.iconMonogram, settings.iconMode, settings.palette, settings.uiScale,
    ) {
        dev.retrobit.desktop.data.IconCache.clear()
    }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalIconMode provides settings.iconMode,
        LocalIconTheme provides IconTheme.resolve(settings.iconThemeEnum, settings.style),
        dev.retrobit.desktop.ui.theme.LocalIconEpoch provides vm.iconEpoch,
        dev.retrobit.desktop.ui.theme.LocalIconOptions provides dev.retrobit.desktop.ui.theme.IconOptions(
            accent = if (settings.iconAccent) palette.accentColor else null,
            monogram = settings.iconMonogram,
            labels = settings.iconLabels,
        ),
        LocalDesktopStyle provides settings.style,
        dev.retrobit.desktop.ui.theme.LocalDesktopInsets provides insets,
        // clamped: a value coming from an old install (or a corrupted preference) must never be
        // able to render the whole desktop unreadable
        LocalUiScale provides settings.uiScale.coerceIn(0.6f, 2.0f),
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
                // a tap on empty desktop ends text input mode: focus moves back to the desktop, so the
                // field loses it, the keyboard steps aside and the pen is a mouse again
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { runCatching { focus.requestFocus() } })
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.T -> { vm.openWindow(WinKind.TERMINAL); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.S -> { vm.openWindow(WinKind.SETTINGS); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.A -> { vm.openWindow(WinKind.APPS); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.D -> { vm.desktopMenu(Offset(120f, 160f)); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.K -> { vm.toggleKeyboard?.invoke(); true }
                        // window shortcuts, like a desktop window manager
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.W -> { vm.closeFocusedWindow(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.M -> { vm.minimizeAllWindows(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.DirectionUp -> { vm.maximizeFocused(); true }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.Tab -> { vm.wm.focusNext(); true }
                        // Ctrl+Alt+Left / Right walks the workspace pages (icons, widgets & windows)
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.DirectionRight -> {
                            vm.switchDesktop((vm.desktop + 1) % vm.desktopCount); true
                        }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key == Key.DirectionLeft -> {
                            vm.switchDesktop((vm.desktop - 1 + vm.desktopCount) % vm.desktopCount); true
                        }
                        ev.isCtrlPressed && ev.isAltPressed && ev.key in NUMBER_KEYS -> {
                            vm.switchDesktop(NUMBER_KEYS.indexOf(ev.key)); true
                        }
                        ev.key == Key.MetaLeft || ev.key == Key.MetaRight -> { vm.spotlight = true; true }
                        ev.key == Key.Enter -> {
                            vm.openSelected(); true
                        }
                        ev.key == Key.Escape -> {
                            // Esc shrinks a maximized window back to its box first, then the menus
                            if (vm.shellOpen != null) {
                                vm.closeShell()
                            } else {
                                vm.menu = null; vm.spotlight = false; vm.startMenu = false; vm.launchpad = false
                                vm.widgetEdit = false; vm.appPicker = false
                            }
                            true
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
                // the desktop: wallpaper, icons, widgets and the floating windows of our own apps
                when (settings.style) {
                    DesktopStyle.LINUX -> LinuxShell(vm)
                    DesktopStyle.MACOS -> MacosShell(vm)
                    DesktopStyle.WINDOWS -> WindowsShell(vm)
                }

                if (vm.shellOpen != null) {
                    // a maximized window owns the screen: the same full-screen frame the cyberdeck
                    // chrome draws over somebody else's app, covered with its own rail and dock
                    ShellHost(vm, Modifier.fillMaxSize())
                }

                vm.menu?.let { m ->
                    ContextMenu(items = m.items, anchor = m.anchors, onDismiss = { vm.menu = null })
                }

                if (vm.spotlight) Spotlight(vm)

                // "add apps or widgets to the desktop" panel
                if (vm.appPicker) dev.retrobit.desktop.ui.apps.AddToDesktopDialog(vm)

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
/**
 * The desktop of a **native** theme: the gradient (or flat colour) that desktop really paints, a
 * picture in its own colours when the user picked one, and none of the e-paper furniture — no dither
 * pattern, no registration marks, no printed caption and no scanlines, because a real desktop has
 * none of those either.
 */
@Composable
private fun NativeDesk(
    modifier: Modifier,
    pattern: WallPattern,
    source: WallpaperSource,
    image: androidx.compose.ui.graphics.ImageBitmap?,
    busy: Boolean,
    note: String,
) {
    val p = LocalPalette.current
    val skin = p.skin ?: return
    // a straight-down gradient, or corner to corner for the themes that use one (Ubuntu)
    val gradient = remember(skin.deskTop, skin.deskBottom, skin.deskDiagonal) {
        if (skin.deskDiagonal) {
            androidx.compose.ui.graphics.Brush.linearGradient(
                0f to skin.deskTop, 1f to skin.deskBottom,
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset.Infinite,
            )
        } else {
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(skin.deskTop, skin.deskBottom))
        }
    }
    Box(modifier.background(gradient)) {
        if (source != WallpaperSource.PATTERN && image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
            )
        } else if (source != WallpaperSource.PATTERN) {
            Column(
                Modifier.align(Alignment.Center).padding(dp(24f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dp(8f)),
            ) {
                RetroText(if (busy) "developing image…" else "wallpaper: ${source.label.lowercase()}", size = 16f, display = true)
                RetroText(note.ifEmpty { "choose a picture in Settings > wallpaper" }, size = 11f, color = p.mid, maxLines = 3)
            }
        }
    }
}

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
    val skin = p.skin
    if (skin != null) {
        NativeDesk(modifier, pattern, source, image, busy, note)
        return
    }
    // the pattern is printed on the desk, so both follow the palette's desk colour
    val brush = rememberWallpaperBrush(pattern, p.deskPattern, dp(3f))
    Box(modifier.background(p.desk)) {
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
                        note.ifEmpty { "choose a picture in Settings > wallpaper" },
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
            val c = p.onDesk.copy(alpha = 0.55f)
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
            "RETROBIT DESKTOP · ${p.id.label.uppercase()} · " +
                if (source == WallpaperSource.PATTERN) pattern.label.uppercase() else source.label.uppercase(),
            size = 10f,
            color = p.onDesk.copy(alpha = 0.45f),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(dp(14f)),
        )
        Scanlines(alpha = 0.035f)
    }
}
