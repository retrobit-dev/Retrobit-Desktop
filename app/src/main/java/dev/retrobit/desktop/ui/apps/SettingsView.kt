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
        RetroText(
            "Apps fill the screen. There are no floating windows and no desktop boxes: opening " +
                "anything - an installed app, the terminal, these settings - opens it full screen, " +
                "and the cyberdeck chrome draws the frame (border, title rail, dock) around it.\n" +
                "Our own apps get the same frame drawn by the launcher itself (see the rail at the " +
                "top of this screen), so the desktop looks the same whichever app is in front.",
            size = 10.5f, color = p.mid, maxLines = 8,
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
                Modifier.weight(1f).border(dp(1.6f), p.ink).padding(dp(9f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dp(4f))) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(7f))) {
                        GlyphIcon(Glyph.PEN, dp(16f))
                        RetroText("M-Pencil", size = 12f, weight = FontWeight.Bold)
                    }
                    RetroText("1 tap = left click\n2 taps = right click\nbarrel button = right click\ndrag = move icons & widgets", size = 10f, color = p.mid, maxLines = 4)
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
