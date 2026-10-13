package dev.retrobit.desktop.ui.apps

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

        Section("e-paper palette")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            PaletteId.values().forEach { id ->
                val pal = Palettes.of(id)
                Column(
                    Modifier
                        .weight(1f)
                        .border(dp(if (s.palette == id) 2.4f else 1.2f), if (s.palette == id) p.ink else p.mid)
                        .background(pal.paper)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            vm.setPalette(id)
                        }
                        .padding(dp(6f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dp(5f)),
                ) {
                    Box(Modifier.fillMaxWidth().height(dp(14f)).background(pal.ink))
                    RetroText(id.label, size = 10f, color = pal.ink, maxLines = 1)
                }
            }
        }

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
                val brush = rememberWallpaperBrush(w, p.pattern, dp(2f))
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
                    Box(Modifier.fillMaxWidth().height(dp(16f)).background(p.paper).then(brush?.let { Modifier.background(it) } ?: Modifier))
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

        Section("apps & windows")
        ToggleRow("Open apps as floating windows", s.floatingApps, onGlyph = Glyph.GRID) { s.floatingApps = it }
        RetroText(
            dev.retrobit.desktop.data.WindowedLaunch.statusLine(ctx),
            size = 10.5f, color = p.mid, maxLines = 2,
        )
        RetroText(
            "Every app you open gets a desktop window you can move, resize, minimize, snap and send to " +
                "another workspace. Where the system supports floating (freeform) windows the app is " +
                "launched into exactly that rectangle; elsewhere it opens full screen while the window " +
                "stays as its taskbar entry. To enable floating windows: developer options -> force " +
                "activities to be resizable, or: adb shell settings put global enable_freeform_support 1",
            size = 10f, color = p.mid, maxLines = 8,
        )

        Section("display widgets (home screen)")
        RetroText(
            "Add or remove widgets here, or right click / hold the desktop and use the widget entries. " +
                "Hold a widget on the home screen to edit it: it turns into a resize handle, shows its " +
                "name, and gets buttons to remove it, move it up or down in the column and reset its size.",
            size = 10.5f, color = p.mid, maxLines = 5,
        )
        dev.retrobit.desktop.model.WidgetId.entries.forEach { id ->
            val on = s.isWidgetEnabled(id)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
                RetroText(
                    (if (on) "[x] " else "[ ] ") + id.label,
                    size = 11.5f,
                    weight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1.4f),
                    maxLines = 1,
                )
                RetroText(
                    "${s.widgetWidth(id)}x${s.widgetHeight(id)} dp",
                    size = 10f, color = p.mid, maxLines = 1, modifier = Modifier.weight(1f),
                )
                RetroButton(
                    label = if (on) "remove" else "add",
                    active = on,
                    modifier = Modifier.weight(0.9f),
                    onClick = { if (on) s.removeWidget(id) else s.addWidget(id) },
                )
                RetroButton(label = "^", modifier = Modifier.weight(0.4f), onClick = { s.moveWidget(id, -1) })
                RetroButton(label = "v", modifier = Modifier.weight(0.4f), onClick = { s.moveWidget(id, 1) })
                RetroButton(label = "size", modifier = Modifier.weight(0.8f), onClick = { s.resetWidgetSize(id) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            listOf(220, 250, 300, 360).forEach { w ->
                RetroButton(
                    label = "all ${w}dp",
                    active = s.widgetWidth == w,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        s.widgetWidth = w
                        s.resetWidgets()
                    },
                )
            }
        }
        ToggleRow("Hold a widget on the home screen to edit & resize it", s.widgetHoldEdit, onGlyph = Glyph.GRID) {
            s.widgetHoldEdit = it
        }
        RetroText(
            "With this off, widget editing starts from the desktop menu or the button below, and the " +
                "right edge, bottom edge and corner grip still resize by dragging. Tapping the desktop " +
                "always leaves edit mode.",
            size = 10f, color = p.mid, maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            RetroButton(
                label = if (vm.widgetEdit) "done editing widgets" else "edit widgets on desktop",
                glyph = Glyph.GRID,
                active = vm.widgetEdit,
                modifier = Modifier.weight(1.6f),
                onClick = { vm.widgetEdit = !vm.widgetEdit },
            )
            RetroButton(
                label = "reset layout",
                modifier = Modifier.weight(1f),
                onClick = { s.resetWidgetList() },
            )
        }
        RetroText(
            "on the home screen: ${dev.retrobit.desktop.ui.desktop.widgetSummary(vm)}",
            size = 10f, color = p.mid, maxLines = 2,
        )

        Section("display corners & camera hole")
        RetroText(
            "The tablet has rounded corners and a punch-hole camera in the top-left corner. The desktop " +
                "chrome stops short of both so nothing important is ever hidden.",
            size = 10.5f, color = p.mid, maxLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            RetroButton(
                label = if (s.safeArea) "safe area: ON" else "safe area: OFF",
                active = s.safeArea,
                modifier = Modifier.weight(1f),
                onClick = { s.safeArea = !s.safeArea },
            )
            listOf(0, 12, 20, 28).forEach { radius ->
                RetroButton(
                    label = if (radius == 0) "flat" else "${radius}dp",
                    active = s.cornerInset == radius,
                    modifier = Modifier.weight(1f),
                    onClick = { s.cornerInset = radius },
                )
            }
        }

        Section("app icon look (live preview)")
        RetroText(
            "These are the real icons of your apps, rendered with the settings below and the active " +
                "palette — the desktop, dock and app drawer use exactly the same pipeline.",
            size = 10.5f, color = p.mid, maxLines = 3,
        )
        AppIconPreview(vm)
        Section("app icon readability")
        RetroText(
            "Icons are redrawn for e-paper: the frame follows the active palette, an optional letter " +
                "plate marks the app, and a printed name chip sits under every desktop icon.",
            size = 10.5f, color = p.mid, maxLines = 4,
        )
        ToggleRow("Name chips under icons", s.iconLabels, onGlyph = Glyph.GRID) { s.iconLabels = it }
        ToggleRow("First-letter plate on icons", s.iconMonogram, onGlyph = Glyph.ABOUT) {
            s.iconMonogram = it
            vm.reinkIcons()
        }
        ToggleRow("Palette accent colour on icon frames", s.iconAccent, onGlyph = Glyph.SETTINGS) {
            s.iconAccent = it
            vm.reinkIcons()
        }
        RetroButton(
            label = "Reset icon look (mono + accent + letter plate)",
            glyph = Glyph.LOGO,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                s.iconMode = dev.retrobit.desktop.model.IconMode.MONO
                s.iconTheme = dev.retrobit.desktop.model.IconTheme.AUTO.name
                s.iconAccent = true
                s.iconMonogram = true
                s.iconLabels = true
                vm.reinkIcons()
            },
        )

        Section("app icon shape")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            IconTheme.values().forEach { theme ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dp(3f))) {
                    RetroButton(
                        label = theme.label,
                        active = s.iconThemeEnum == theme,
                        onClick = {
                            s.iconTheme = theme.name
                            vm.reinkIcons()
                        },
                    )
                    RetroText(
                        if (theme == IconTheme.AUTO) "follows ${s.style.label}" else theme.hint,
                        size = 9.5f, color = p.mid, maxLines = 2,
                    )
                }
            }
        }

        Section("interface scale")
        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            listOf(0.8f, 0.9f, 1.0f, 1.15f, 1.3f).forEach { v ->
                RetroButton(
                    label = "${(v * 100).toInt()}%",
                    active = kotlin.math.abs(s.uiScale - v) < 0.01f,
                    modifier = Modifier.weight(1f),
                    onClick = { s.uiScale = v },
                )
            }
        }

        Section("behaviour")
        ToggleRow("24-hour clock", s.use24h, onGlyph = Glyph.ABOUT) { s.use24h = it }
        ToggleRow("Show seconds in the clock", s.showSeconds, onGlyph = Glyph.SYSMON) { s.showSeconds = it }
        ToggleRow("Show the placement grid while dragging icons", s.showGrid, onGlyph = Glyph.GRID) { s.showGrid = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            RetroButton(
                label = "Auto arrange desktop icons",
                glyph = Glyph.GRID,
                modifier = Modifier.weight(1f),
                onClick = { s.clearIconCells(); vm.shell.print("desktop icons auto arranged") },
            )
            RetroButton(
                label = "Calendar",
                glyph = Glyph.CALENDAR,
                modifier = Modifier.weight(1f),
                onClick = { vm.openWindow(dev.retrobit.desktop.model.WinKind.CALENDAR) },
            )
        }
        ToggleRow("desktop widgets", s.showWidgets, onGlyph = Glyph.GRID) { s.showWidgets = it }
        ToggleRow("boot sequence animation", s.bootSequence, onGlyph = Glyph.POWER) { s.bootSequence = it }
        ToggleRow("hide android system bars", s.hideSystemBars, onGlyph = Glyph.SYSMON) { s.hideSystemBars = it }

        Section("windows & workspaces")
        ToggleRow("Snap windows to screen edges (drag a window to the left/right edge, or to the top to maximize)",
            s.snapWindows, onGlyph = Glyph.COMPUTER) { s.snapWindows = it }
        ToggleRow("Tiling window layout (master + stack, Linux style)", s.tiling, onGlyph = Glyph.GRID) { vm.setTiling(it) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            RetroText("virtual desktops", size = 12f, modifier = Modifier.weight(1f), maxLines = 1)
            listOf(1, 2, 3, 4).forEach { count ->
                RetroButton(
                    label = "$count",
                    active = s.workspaceCount == count,
                    modifier = Modifier.width(dp(52f)),
                    onClick = { vm.setWorkspaceCount(count) },
                )
            }
        }
        RetroText(
            "Tap the numbered squares in the panel / menu bar / taskbar to switch desktop " +
                "(long-press one for the desktop menu). Shortcuts: Ctrl+Alt+1..4, Ctrl+Alt+Shift+1..4 " +
                "sends the focused window to that desktop, terminal: `workspace 2`, `wsmove 3`.",
            size = 11f, color = p.mid, maxLines = 6,
        )

        Section("input & devices — pen, mouse, keyboard")
        ToggleRow("Hold the pen = right click (M-Pencil: rest it on a widget, icon or the desktop)",
            s.penRightClick, onGlyph = Glyph.PEN) { s.penRightClick = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            RetroText("hold time", size = 11f, modifier = Modifier.weight(1f), maxLines = 1)
            listOf(250, 380, 500, 700).forEach { ms ->
                RetroButton(
                    label = "${ms}ms",
                    active = s.penHoldMs == ms,
                    modifier = Modifier.weight(1f),
                    onClick = { s.penHoldMs = ms },
                )
            }
        }
        RetroText(
            "Tap = left click (opens apps, moves focus). Hold = right click: the context menu opens " +
                "where you rested the pen, exactly like right-clicking a desktop. Holding a widget " +
                "also starts editing it (resize, remove, add). The barrel button still works as right click.",
            size = 10f, color = p.mid, maxLines = 5,
        )
        ToggleRow("Show pointer cursor for pen & mouse", s.showCursor, onGlyph = Glyph.CURSOR) { s.showCursor = it }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).border(dp(1.6f), p.ink).padding(dp(9f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dp(4f))) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(7f))) {
                        GlyphIcon(Glyph.PEN, dp(16f))
                        RetroText("M-Pencil", size = 12f, weight = FontWeight.Bold)
                    }
                    RetroText("1 tap = left click\n2 taps = right click\nbarrel button = right click\ndrag = move icons & windows", size = 10f, color = p.mid, maxLines = 4)
                }
            }
            Box(
                Modifier.weight(1f).border(dp(1.6f), p.ink).padding(dp(9f)),
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
        RetroText(
            "Android's own keyboard is kept: it appears when you tap a text field and the desktop slides " +
                "up so nothing is covered. Use the keyboard button in the panel/taskbar (or Ctrl+Alt+K) to " +
                "summon or hide it manually, which is handy with an external mouse.\n" +
                "External keyboards: Ctrl+Alt+T terminal · Ctrl+Alt+S settings · Ctrl+Alt+W close window · " +
                "Ctrl+Alt+M minimize all · Ctrl+Alt+D desktop menu · Ctrl+Alt+left/right or Tab cycles windows · " +
                "Ctrl+Alt+up maximizes · Super opens Spotlight.",
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
                label = "Home key test",
                glyph = Glyph.POWER,
                modifier = Modifier.weight(1f),
                onClick = { vm.wm.minimizeAll() },
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
                    s.tiling = false
                    vm.wm.retile(false)
                },
            )
            RetroButton(label = "About", modifier = Modifier.weight(1f), onClick = { vm.openWindow(dev.retrobit.desktop.model.WinKind.ABOUT) })
        }
        RetroText("retrobit desktop · build ${dev.retrobit.desktop.BuildConfig.VERSION_NAME}", size = 10f, color = p.mid)
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
                .border(dp(1.6f), p.ink)
                .background(if (checked) p.ink else p.paper),
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
