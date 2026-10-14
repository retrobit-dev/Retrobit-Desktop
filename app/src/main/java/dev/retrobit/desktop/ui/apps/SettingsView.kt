package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.data.WallpaperSource
import dev.retrobit.desktop.data.WallpaperTone
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.IconTheme
import dev.retrobit.desktop.model.PaletteId
import dev.retrobit.desktop.model.WallPattern
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.Palettes
import dev.retrobit.desktop.ui.theme.rememberWallpaperBrush

@Composable
fun SettingsView(vm: LauncherViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = LocalPalette.current
    val s = vm.settings

    Column(
        Modifier
            .fillMaxSize()
            .background(p.paper)
            .verticalScroll(rememberScrollState())
            .padding(dp(12f)),
        verticalArrangement = Arrangement.spacedBy(dp(14f)),
    ) {
        RetroText("SETTINGS", size = 16f, display = true, weight = FontWeight.Bold)
        RetroText("system preferences · stored on device", size = 11f, color = p.mid)

        Section("desktop style")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            DesktopStyle.values().forEach { style ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dp(4f))) {
                    RetroButton(
                        label = style.label,
                        active = s.style == style,
                        onClick = { vm.setStyle(style) },
                    )
                    RetroText(style.osName, size = 10f, color = p.mid, maxLines = 2)
                    RetroText(style.tagline, size = 10f, color = p.mid, maxLines = 3)
                }
            }
        }

        Section("palette")
        RetroText(
            "**E-paper**: one paper tone and one ink tone, printed with the launcher's own dither " +
                "patterns, hard shadows and 1-bit title bars. These are the original palettes and " +
                "they fit every desktop style.\n" +
                "**Native desktop**: the real thing, not an e-paper toned version of it. Windows 95 " +
                "gets its silver chrome, the navy-to-blue title gradient, bevelled buttons and the " +
                "teal desk; Aqua gets snow chrome, the red/amber/green traffic lights and the blue " +
                "desk; Ubuntu gets the dark headerbar, orange accents and the aubergine wallpaper; " +
                "Windows 11 and Adwaita get their dark chrome (Windows 11's is flat, Adwaita's a subtle gradient) and rounded windows. Gradients " +
                "are real gradients here, shadows are real shadows, and the scanline overlay is off.\n" +
                "**Widgets follow the theme as well**: they wear the window face of that desktop, its " +
                "rounded corners, its border colour, a header bar in its own title gradient, and " +
                "meters filled with its accent colour - while the e-paper palettes keep printing the " +
                "hatched header and the ink cells.",
            size = 10.5f, color = p.mid, maxLines = 16,
        )
        RetroText(
            "Picking a native theme also switches to the desktop style it belongs to and shows " +
                "app icons in colour (both stay yours to change afterwards); the e-paper palettes " +
                "never touch them.",
            size = 10f, color = p.mid, maxLines = 4,
        )
        PaletteChips(Palettes.ePaper, s.palette) { vm.applyPalette(it) }
        RetroText("native desktop themes", size = 10f, color = p.mid)
        PaletteChips(Palettes.native, s.palette) { vm.applyPalette(it) }

        Section("wallpaper source")
        val pickImage = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) vm.setWallpaperUri(uri) }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            WallpaperSource.values().forEach { source ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dp(3f))) {
                    RetroButton(
                        label = source.label,
                        active = s.wallpaperSourceEnum == source,
                        onClick = { vm.setWallpaperSource(source) },
                    )
                    when (source) {
                        WallpaperSource.PATTERN -> RetroText("generated dither", size = 9.5f, color = p.mid, maxLines = 2)
                        WallpaperSource.DEVICE -> RetroButton(
                            label = "use device wallpaper",
                            modifier = Modifier,
                            onClick = { vm.setWallpaperSource(WallpaperSource.DEVICE) },
                        )
                        WallpaperSource.IMAGE -> RetroButton(
                            label = "pick image…",
                            onClick = { runCatching { pickImage.launch(arrayOf("image/*")) } },
                        )
                    }
                }
            }
        }
        if (s.wallpaperSourceEnum != WallpaperSource.PATTERN) {
            Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
                WallpaperTone.values().forEach { tone ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dp(3f))) {
                        RetroButton(
                            label = tone.label,
                            active = s.wallpaperToneEnum == tone,
                            onClick = { vm.setWallpaperTone(tone) },
                        )
                        RetroText(tone.hint, size = 9.5f, color = p.mid, maxLines = 2)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
                RetroButton(
                    label = if (vm.wallpaperBusy) "working…" else "re-render wallpaper",
                    glyph = Glyph.SETTINGS,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.refreshWallpaper() },
                )
                RetroText(
                    vm.wallpaperNote.ifEmpty {
                        if (s.wallpaperSourceEnum == WallpaperSource.IMAGE) "image selected" else "device wallpaper loaded"
                    },
                    size = 10f, color = p.mid, maxLines = 3, modifier = Modifier.weight(1.4f),
                )
            }
        }

        Section("wallpaper pattern")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            WallPattern.values().forEach { w ->
                // the sample is printed on the desk colour, exactly like the desktop itself
                val brush = rememberWallpaperBrush(w, p.deskPattern, dp(2f))
                Column(
                    Modifier
                        .weight(1f)
                        .border(dp(if (s.wallpaper == w) 2.4f else 1.2f), if (s.wallpaper == w) p.ink else p.mid)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            vm.setWallpaper(w)
                        }
                        .padding(dp(5f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dp(5f)),
                ) {
                    Box(Modifier.fillMaxWidth().height(dp(16f)).background(p.desk).then(brush?.let { Modifier.background(it) } ?: Modifier))
                    RetroText(w.label, size = 10f, maxLines = 1)
                }
            }
        }

        Section("app icon rendering")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            IconMode.values().forEach { m ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dp(3f))) {
                    RetroButton(label = m.label, active = s.iconMode == m, onClick = { vm.setIconMode(m) })
                    RetroText(
                        when (m) {
                            IconMode.COLOR -> "original artwork"
                            IconMode.MONO -> "grayscale on paper"
                            IconMode.DITHER -> "1-bit halftone"
                        },
                        size = 10f, color = p.mid, maxLines = 2,
                    )
                }
            }
        }

        Section("app icon shape")
        RetroText(
            "**Follow palette** (the default) gives every theme the icon shape that desktop really " +
                "uses: Windows 95 the square beveled button with its navy trim, Windows 11 the soft " +
                "rounded square, Aqua the glossy rounded tile with a pale edge, Ubuntu the rounded " +
                "square with the orange trim, GNOME the circle with a thin ring. The e-paper palettes " +
                "print a 1-bit tile and take their shape from the desktop style instead (Windows " +
                "square, macOS rounded, Linux flat).\n" +
                "The **shell's own icons** (launch, find, shell, mon, note, setup, and the keys " +
                "of the docks and menus) wear the very same tile, so the internal icons and the " +
                "app icons of a theme always match; **Plain** takes the tiles away and leaves the " +
                "bare printed glyph, on any palette.\n" +
                "Pinning a shape below overrides the theme until you pick another palette; " +
                "`icons shape <name>` does the same from the terminal.",
            size = 10.5f, color = p.mid, maxLines = 11,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            RetroButton(
                label = "Follow palette",
                active = s.iconThemeEnum == IconTheme.AUTO,
                modifier = Modifier.weight(1.6f),
                onClick = { vm.setIconTheme(IconTheme.AUTO) },
            )
            RetroText(
                "now: " + IconTheme.describe(s.iconThemeEnum, s.style, s.palette),
                size = 10f, color = p.mid, maxLines = 2, modifier = Modifier.weight(1.4f),
            )
        }
        IconThemeShapeChips(current = s.iconThemeEnum, palette = s.palette, onPick = { vm.setIconTheme(it) })
        AppIconPreview(vm)

        Section("apps & windows")
        RetroText(
            "**Our own apps open in a floating window** - terminal, settings, notes, applications, " +
                "system monitor, calendar and about. The window is a box on the desktop: drag it by " +
                "the title bar, resize it from any of the eight edges and corners, minimize it, " +
                "maximize it to fill the desktop and press maximize again to put it back, close it " +
                "with the x. Double tap (or double click) the title bar does the same as maximize.\n" +
                "Every app remembers the size and place you left it in, per desktop page, and opens " +
                "that way next time.\n" +
                "**Installed apps stay full screen**: the cyberdeck chrome draws the frame (border, " +
                "title rail, dock) around them, so a third-party app still gets the whole screen.",
            size = 10.5f, color = p.mid, maxLines = 12,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            RetroButton(
                label = "Reset window sizes",
                modifier = Modifier.weight(1f),
                onClick = { vm.resetWindowSizes() },
            )
            RetroButton(
                label = "Close all windows",
                modifier = Modifier.weight(1f),
                onClick = { vm.wm.closeAll() },
            )
        }
        RetroText(
            "Reset drops every window back to its default size and forgets the remembered geometry, " +
                "handy after a change of desktop style or rotation. Windows that were minimized come " +
                "back when you open the app again (from an icon, the dock, the panel's `windows` menu, " +
                "or the taskbar buttons). Ctrl+Alt+T terminal, Ctrl+Alt+W close, Ctrl+Alt+M minimize " +
                "all, Ctrl+Alt+Up maximize, Ctrl+Alt+Tab next window.",
            size = 10f, color = p.mid, maxLines = 6,
        )
        ToggleRow("Apps open with a double click / double tap (tap selects)", s.doubleClickOpen, onGlyph = Glyph.GRID) {
            s.doubleClickOpen = it
        }
        RetroText(
            "Desktop style: one tap marks the icon (it is printed inverted) and shows its name, the " +
                "second tap opens the app. A hold always opens the app menu, where an app can be added " +
                "to the desktop or the dock. Turn this off to open apps with a single tap again. Docks, " +
                "sidebars, the taskbar and the launcher widget stay one-click either way.",
            size = 10f, color = p.mid, maxLines = 5,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).border(dp(1.6f), p.edge).padding(dp(9f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dp(4f))) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(7f))) {
                        GlyphIcon(Glyph.PEN, dp(16f))
                        RetroText("M-Pencil", size = 12f, weight = FontWeight.Bold)
                    }
                    RetroText("1 tap = left click\nhold = right click\nbarrel button = right click\ndrag = move icons & widgets", size = 10f, color = p.mid, maxLines = 4)
                }
            }
            Box(
                Modifier.weight(1f).border(dp(1.6f), p.edge).padding(dp(9f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dp(4f))) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(7f))) {
                        GlyphIcon(Glyph.MOUSE, dp(16f))
                        RetroText("Mouse / trackpad", size = 12f, weight = FontWeight.Bold)
                    }
                    RetroText("left = click\nright = context menu\nwheel = scroll lists\nhover = move the pointer", size = 10f, color = p.mid, maxLines = 4)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            RetroButton(
                label = "Show internal keyboard",
                glyph = Glyph.KEYBOARD,
                modifier = Modifier.weight(1f),
                onClick = { vm.toggleKeyboard?.invoke() },
            )
            RetroButton(
                label = "Terminal",
                glyph = Glyph.TERMINAL,
                modifier = Modifier.weight(1f),
                onClick = { vm.openWindow(dev.retrobit.desktop.model.WinKind.TERMINAL) },
            )
        }
        // the pen is a mouse on the desktop, and a pen again inside a text field
        ToggleRow("M-Pencil: hold = right click (context menu)", s.penRightClick, onGlyph = Glyph.PEN) {
            s.penRightClick = it
        }
        ToggleRow("M-Pencil becomes a pen inside text fields", s.penTextMode, onGlyph = Glyph.PEN) {
            s.penTextMode = it
        }
        RetroText(
            "While a text field has the focus the arrow cursor steps aside and the stylus is a stylus " +
                "again: hold no longer opens a menu, a drag no longer moves icons, and text selection, " +
                "the selection handles and Huawei's own hand-writing input reach the field untouched. " +
                "Tap another surface, leave the app or turn this off to get the desktop mouse back.",
            size = 10.5f, color = p.mid, maxLines = 6,
        )
        ToggleRow("Draw the e-paper pointer arrow for pen / mouse", s.showCursor, onGlyph = Glyph.MOUSE) {
            s.showCursor = it
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f)), verticalAlignment = Alignment.CenterVertically) {
            RetroText("hold = right click after", size = 10.5f, color = p.mid, modifier = Modifier.weight(1.5f))
            listOf(250, 380, 500, 700).forEach { ms ->
                RetroButton(
                    label = "${ms}ms",
                    modifier = Modifier.weight(1f),
                    active = s.penHoldMs == ms,
                    onClick = { s.penHoldMs = ms },
                )
            }
        }
        // what the keyboard does to the layout: float over it (default), room inside our own apps,
        // or the old slide-up
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f)), verticalAlignment = Alignment.CenterVertically) {
            RetroText("keyboard", size = 10.5f, color = p.mid, modifier = Modifier.weight(0.8f))
            listOf(
                dev.retrobit.desktop.data.Settings.KEYBOARD_OVERLAY to "float over",
                dev.retrobit.desktop.data.Settings.KEYBOARD_CONTENT to "room inside apps",
                dev.retrobit.desktop.data.Settings.KEYBOARD_LIFT to "slide up",
            ).forEach { (mode, label) ->
                RetroButton(
                    label = label,
                    modifier = Modifier.weight(1.3f),
                    active = s.keyboardMode == mode,
                    onClick = { s.keyboardMode = mode },
                )
            }
        }
        RetroText(
            "Float over (default): the keyboard overlays the desktop and **nothing moves** - rail, icons, " +
                "widgets, panels and window frames all keep their place.\n" +
                "Room inside apps: the desktop still stays put, but the inside of our own apps (terminal, " +
                "notes, settings) makes room so the line you type is not hidden.\n" +
                "Slide up: the old behaviour, the whole desktop is pushed up by the height of the keyboard.",
            size = 10.5f, color = p.mid, maxLines = 8,
        )
        RetroText(
            "Android's own keyboard is kept: it appears when you tap a text field. Use the keyboard button " +
                "in the panel/taskbar (or Ctrl+Alt+K) to " +
                "summon or hide it manually, which is handy with an external mouse.\n" +
                "External keyboards: Ctrl+Alt+T terminal · Ctrl+Alt+S settings · Ctrl+Alt+A app drawer · " +
                "Ctrl+Alt+D desktop menu · Ctrl+Alt+left/right switches workspace · Ctrl+Alt+1..4 jumps to " +
                "one · Esc leaves a full-screen app · Super opens Spotlight.",
            size = 11f, color = p.mid, maxLines = 12,
        )

        Section("launcher")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            RetroButton(
                label = "Set as default launcher",
                glyph = Glyph.HOME,
                modifier = Modifier.weight(1f),
                onClick = { vm.requestDefaultLauncher?.invoke() },
            )
            RetroButton(
                label = "Back to desktop",
                glyph = Glyph.POWER,
                modifier = Modifier.weight(1f),
                onClick = { vm.closeShell() },
            )
        }
        RetroText(
            "Press the home button (or swipe up from the bottom edge on Huawei devices) and pick " +
                "\"Retrobit Desktop\" — tick \"always\" to replace the stock launcher. " +
                "You can switch back any time in Android Settings › Apps › Default apps › Home app.",
            size = 11f, color = p.mid, maxLines = 8,
        )

        Section("maintenance")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            RetroButton(label = "Replay boot screen", glyph = Glyph.POWER, modifier = Modifier.weight(1f), onClick = { vm.showBootScreenAgain() })
            RetroButton(
                label = "Refresh apps",
                glyph = Glyph.GRID,
                modifier = Modifier.weight(1f),
                onClick = {
                    vm.settings.defaultsSeeded = false
                    vm.refreshApps(force = true)
                    vm.shell.print("rescanned installed applications")
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            RetroButton(
                label = "Reset appearance",
                modifier = Modifier.weight(1f),
                onClick = {
                    s.style = DesktopStyle.LINUX
                    s.palette = PaletteId.PAPER
                    s.wallpaper = WallPattern.DOTS
                    s.iconMode = IconMode.MONO
                    s.uiScale = 1.0f
                },
            )
            RetroButton(label = "About", modifier = Modifier.weight(1f), onClick = { vm.openWindow(dev.retrobit.desktop.model.WinKind.ABOUT) })
        }
        RetroText("retrobit desktop · build ${dev.retrobit.desktop.BuildConfig.VERSION_NAME}", size = 10f, color = p.mid)
    }
}

/**
 * The palette picker. Every chip is a small print of what that palette does: the desk colour behind,
 * a window plate in paper, a title bar in the accent and the name printed in ink - which is exactly
 * the set of colours the desktop uses.
 */
@Composable
private fun PaletteChips(ids: List<PaletteId>, current: PaletteId, onPick: (PaletteId) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(dp(6f))) {
        ids.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
                row.forEach { id ->
                    val pal = Palettes.of(id)
                    val active = current == id
                    Column(
                        Modifier
                            .weight(1f)
                            .border(dp(if (active) 2.4f else 1.2f), if (active) p.ink else p.mid)
                            .background(pal.paper)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onPick(id)
                            }
                            .padding(dp(5f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dp(4f)),
                    ) {
                        // a print of the desk with a little window sitting on it
                        Box(Modifier.fillMaxWidth().height(dp(22f)).background(pal.desk)) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(0.72f)
                                    .height(dp(15f))
                                    .background(pal.paper)
                                    .border(dp(1f), pal.ink),
                            ) {
                                Box(Modifier.fillMaxWidth().height(dp(4f)).background(pal.accent))
                            }
                        }
                        RetroText(id.label, size = 9.5f, color = pal.ink, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The icon shapes as a row of chips: each one shows the **active palette's own colours** (paper
 * tile, ink artwork, accent trim, theme border) so picking a shape is a decision about that theme,
 * not about a generic preview.
 */
@Composable
private fun IconThemeShapeChips(current: IconTheme, palette: PaletteId, onPick: (IconTheme) -> Unit) {
    val p = LocalPalette.current
    val shapes = IconTheme.entries.filter { it != IconTheme.AUTO }
    Column(verticalArrangement = Arrangement.spacedBy(dp(6f))) {
        shapes.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
                row.forEach { theme ->
                    val active = current == theme
                    // the shape of the palette itself is the one "follow palette" would use
                    val fromPalette = IconTheme.forPalette(palette) == theme
                    Column(
                        Modifier
                            .weight(1f)
                            .border(dp(if (active) 2.4f else 1.2f), if (active) p.ink else p.mid)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onPick(theme)
                            }
                            .padding(dp(6f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dp(4f)),
                    ) {
                        Canvas(Modifier.size(dp(30f))) {
                            val w = size.width
                            val inset = when (theme) {
                                IconTheme.CLASSIC -> w * 0.06f
                                IconTheme.ROUNDED -> w * 0.05f
                                else -> w * 0.03f
                            }
                            val l = inset
                            val r = w - inset
                            val radius = when (theme) {
                                IconTheme.CLASSIC -> 0f
                                IconTheme.ROUNDED -> w * 0.20f
                                IconTheme.FLAT -> w * 0.06f
                                IconTheme.SQUIRCLE -> w * 0.24f
                                IconTheme.AQUA -> w * 0.26f
                                IconTheme.YARU -> w * 0.16f
                                else -> 0f
                            }
                            val rect = androidx.compose.ui.geometry.Rect(l, l, r, r)
                            val corner = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                            if (theme == IconTheme.CIRCLE) {
                                drawCircle(p.paper, radius = (r - l) / 2f, center = rect.center)
                                drawCircle(p.ink, radius = w * 0.16f, center = rect.center)
                                drawCircle(p.edge, radius = (r - l) / 2f, center = rect.center, style = Stroke(w * 0.04f))
                            } else {
                                drawRoundRect(p.paper, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner)
                                if (theme == IconTheme.CLASSIC) {
                                    drawRect(p.ink, topLeft = rect.topLeft, size = rect.size, style = Stroke(w * 0.07f))
                                    drawRect(
                                        p.accent,
                                        topLeft = androidx.compose.ui.geometry.Offset(rect.left + w * 0.08f, rect.bottom - w * 0.13f),
                                        size = androidx.compose.ui.geometry.Size(rect.width - w * 0.16f, w * 0.07f),
                                    )
                                } else if (theme == IconTheme.YARU) {
                                    drawRoundRect(p.ink, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = Stroke(w * 0.05f))
                                    drawRoundRect(
                                        p.accent,
                                        topLeft = androidx.compose.ui.geometry.Offset(rect.left + w * 0.18f, rect.bottom - w * 0.12f),
                                        size = androidx.compose.ui.geometry.Size(rect.width - w * 0.36f, w * 0.06f),
                                        cornerRadius = corner,
                                    )
                                } else if (theme == IconTheme.SQUIRCLE) {
                                    drawRoundRect(p.edge, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = Stroke(w * 0.03f))
                                } else if (theme == IconTheme.AQUA) {
                                    drawRoundRect(p.edge, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = Stroke(w * 0.035f))
                                } else {
                                    drawRoundRect(p.ink, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = Stroke(w * 0.05f))
                                }
                            }
                        }
                        RetroText(theme.label, size = 9.5f, maxLines = 1)
                        RetroText(
                            if (fromPalette) "this theme" else theme.hint.split(",").first(),
                            size = 8.5f, color = if (fromPalette) p.ink else p.mid, maxLines = 1,
                        )
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
        RetroText(title, size = 12f, color = p.mid, weight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(dp(1f)).background(p.ink.copy(alpha = 0.35f)))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onGlyph: Glyph, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .padding(vertical = dp(5f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(8f)),
    ) {
        Box(
            Modifier
                .size(dp(20f))
                .border(dp(1.6f), p.edge)
                .background(if (checked) p.plate else p.paper),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(dp(4f))) {
                    drawLine(p.paper, androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f), androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.85f), 2.4f)
                    drawLine(p.paper, androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.85f), androidx.compose.ui.geometry.Offset(size.width, size.height * 0.1f), 2.4f)
                }
            }
        }
        RetroText(label, size = 12f, modifier = Modifier.weight(1f), maxLines = 2)
        GlyphIcon(onGlyph, dp(14f))
    }
}
