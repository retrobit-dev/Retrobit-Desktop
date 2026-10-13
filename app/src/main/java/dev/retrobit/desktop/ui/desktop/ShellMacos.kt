package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.AppIconTile
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.MenuItem
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.px
import dev.retrobit.desktop.ui.input.KeyboardTrayButton
import dev.retrobit.desktop.ui.input.desktopClick
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState
import dev.retrobit.desktop.ui.wm.ShellMetrics
import dev.retrobit.desktop.ui.wm.WindowLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Classic Mac style shell: top menu bar, bottom dock, Launchpad overlay,
 * desktop icons aligned to the right edge and widgets on the left.
 */
@Composable
fun MacosShell(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val drag = LocalDragState.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    val barH = px(26f)
    val dockH = px(86f)
    val edge = px(8f)
    val winMargin = px(12f)
    val cascade = px(26f)
    val minW = px(400f)
    val minH = px(280f)
    val area = remember(size) {
        val m = edge
        Rect(m, barH + m, (size.width - m).coerceAtLeast(m), (size.height - dockH).coerceAtLeast(barH))
    }
    LaunchedEffect(size, vm.settings.tiling) {
        if (size.width > 0) {
            vm.wm.relayout(
                area,
                ShellMetrics(barH, dockH, winMargin, cascade, minW, minH),
                DesktopStyle.MACOS,
                vm.settings.tiling,
            )
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        // desktop + icons (right aligned, like the real thing)
        Box(Modifier.fillMaxSize().padding(top = dp(26f), bottom = dp(86f))) {
            DesktopIcons(vm, IconLayout.COLUMN_RIGHT, Modifier.fillMaxSize())
            if (vm.settings.showWidgets) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(dp(12f)).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dp(10f)),
                ) {
                    ClockWidget(vm)
                    NotesWidget(vm)
                }
            }
        }

        WindowLayer(vm.wm, Modifier.fillMaxSize())

        // ── menu bar ──────────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .height(dp(26f))
                .background(p.paper)
                .padding(horizontal = dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(14f)),
        ) {
            MenuTitle(if (vm.wm.focused == null) "◆" else "◇") { vm.desktopMenu(Offset(12f, 40f)) }
            MenuTitle("File") {
                vm.openMenu(
                    dev.retrobit.desktop.ui.MenuRequest("File", null, Offset(40f, 40f), listOf(
                        MenuItem("New terminal window", Glyph.TERMINAL) { vm.openWindow(WinKind.TERMINAL) },
                        MenuItem("Open applications…", Glyph.GRID) { vm.openWindow(WinKind.APPS) },
                        MenuItem("New note", Glyph.NOTES) { vm.openWindow(WinKind.NOTES) },
                    ))
                )
            }
            MenuTitle("View") {
                vm.openMenu(
                    dev.retrobit.desktop.ui.MenuRequest("View", null, Offset(90f, 40f), listOf(
                        MenuItem(if (vm.settings.tiling) "Stop tiling windows" else "Tile windows", Glyph.GRID) { vm.setTiling(!vm.settings.tiling) },
                        MenuItem(if (vm.settings.showWidgets) "Hide widgets" else "Show widgets") { vm.settings.showWidgets = !vm.settings.showWidgets },
                        MenuItem("Minimize all windows", Glyph.COMPUTER) { vm.wm.minimizeAll() },
                    ))
                )
            }
            MenuTitle("Window") {
                val items = vm.wm.windows.map { w ->
                    MenuItem(w.title, Glyph.COMPUTER) { vm.wm.focus(w.id) }
                }.ifEmpty { listOf(MenuItem("no windows open", null, enabled = false) {}) }
                vm.openMenu(dev.retrobit.desktop.ui.MenuRequest("Window", null, Offset(140f, 40f), items))
            }
            MenuTitle("Help") {
                vm.openMenu(
                    dev.retrobit.desktop.ui.MenuRequest("Help", null, Offset(190f, 40f), listOf(
                        MenuItem("About Retrobit Desktop", Glyph.ABOUT) { vm.openWindow(WinKind.ABOUT) },
                        MenuItem("Terminal cheat sheet", Glyph.TERMINAL) { vm.openWindow(WinKind.TERMINAL) },
                        MenuItem("Settings", Glyph.SETTINGS) { vm.openWindow(WinKind.SETTINGS) },
                    ))
                )
            }
            Spacer(Modifier.weight(1f))
            StyleChip(vm, compact = true)
            TrayStatus(vm)
            KeyboardTrayButton(boxSize = dp(20f))
            RetroText(
                SimpleDateFormat("EEE dd MMM  " + if (vm.settings.use24h) "HH:mm" else "hh:mm a", Locale.getDefault()).format(Date(vm.now)),
                size = 12f, weight = FontWeight.Bold,
            )
        }

        // ── dock ──────────────────────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dp(10f))
                .onGloballyPositioned { drag.dockBounds = it.boundsInRoot() },
        ) {
            Row(
                Modifier
                    .background(p.paper)
                    .border(dp(2.2f), p.ink)
                    .padding(horizontal = dp(10f), vertical = dp(7f)),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                DockItem(Glyph.GRID, "launch") { vm.launchpad = true }
                Box(Modifier.width(dp(1.4f)).height(dp(52f)).background(p.ink.copy(alpha = 0.45f)))
                vm.appsByPackages(vm.settings.pinnedDock).take(9).forEach { app ->
                    AppIconTile(
                        app = app,
                        iconSize = dp(46f),
                        showLabel = false,
                        onOpen = { vm.launch(it) },
                        onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                        onDrop = { a, _ -> vm.toggleDockPin(a) },
                    )
                }
                Box(Modifier.width(dp(1.4f)).height(dp(52f)).background(p.ink.copy(alpha = 0.45f)))
                DockItem(Glyph.TERMINAL, "term") { vm.openWindow(WinKind.TERMINAL) }
                DockItem(Glyph.SETTINGS, "setup") { vm.openWindow(WinKind.SETTINGS) }
                DockItem(Glyph.SYSMON, "monitor") { vm.openWindow(WinKind.SYSMON) }
            }
        }

        // running window indicator strip above the dock
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = dp(68f)),
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            vm.wm.windows.take(8).forEach { w ->
                Box(
                    Modifier
                        .background(if (w.minimized) p.mid else p.ink)
                        .border(dp(1.1f), p.ink)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.wm.focus(w.id) }
                        .padding(horizontal = dp(6f), vertical = dp(1f)),
                ) {
                    RetroText(w.title.take(14), size = 10f, color = p.paper, maxLines = 1)
                }
            }
        }

        // ── launchpad overlay ─────────────────────────────────────────────────────
        if (vm.launchpad) {
            Box(
                Modifier.fillMaxSize().background(p.paper).padding(top = dp(40f), start = dp(30f), end = dp(30f), bottom = dp(30f)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(10f))) {
                        GlyphIcon(Glyph.GRID, dp(20f))
                        RetroText("LAUNCHPAD", size = 18f, display = true, weight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        RetroText("${vm.apps.size} apps", size = 11f, color = p.mid)
                    }
                    Box(Modifier.fillMaxWidth().height(dp(1.6f)).background(p.ink))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(dp(104f)),
                        modifier = Modifier.fillMaxSize().padding(dp(10f)),
                        verticalArrangement = Arrangement.spacedBy(dp(6f)),
                    ) {
                        items(vm.apps, key = { it.key }) { app ->
                            AppIconTile(
                                app = app,
                                iconSize = dp(46f),
                                onOpen = { vm.launch(it) },
                                onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                                onDrop = { a, _ -> vm.toggleDockPin(a) },
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .desktopClick(
                            primary = { vm.launchpad = false },
                            secondary = { pos -> vm.desktopMenu(pos) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun MenuTitle(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = dp(2f)),
    ) {
        RetroText(label, size = 12f, weight = FontWeight.Medium, color = p.ink)
    }
}

@Composable
private fun DockItem(glyph: Glyph, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = dp(4f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dp(2f)),
    ) {
        GlyphIcon(glyph, dp(36f))
        RetroText(label, size = 9f, color = p.mid, maxLines = 1)
    }
}
