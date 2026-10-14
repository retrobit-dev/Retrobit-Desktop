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
import androidx.compose.foundation.layout.fillMaxHeight
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
import dev.retrobit.desktop.ui.components.GlyphTile
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.shellPanel
import dev.retrobit.desktop.ui.input.KeyboardTrayButton
import dev.retrobit.desktop.ui.components.px
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.rememberPanelTexture
import dev.retrobit.desktop.ui.wm.DockSlots
import dev.retrobit.desktop.ui.wm.LocalDragState
import dev.retrobit.desktop.ui.wm.ShellMetrics
import dev.retrobit.desktop.ui.wm.WindowLayer
import dev.retrobit.desktop.ui.wm.WinState
import dev.retrobit.desktop.ui.wm.glyphFor
import java.util.Date

/**
 * One taskbar button: the window's own glyph and title. Tapping raises the window (and brings a
 * minimized one back), double tap maximizes / restores, and the little box closes it.
 */
@Composable
private fun TaskButton(vm: LauncherViewModel, win: WinState) {
    val p = LocalPalette.current
    val active = !win.minimized && (win.maximized || vm.wm.focusedId == win.id)
    Row(
        Modifier
            .height(dp(34f))
            .background(if (active) p.plate else p.paper)
            .border(dp(1.4f), p.edge)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                vm.showWindow(win)
            }
            .padding(horizontal = dp(6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        GlyphIcon(glyphFor(win.kind), dp(14f))
        RetroText(
            win.title,
            size = 11f,
            maxLines = 1,
            color = when {
                active -> p.paper
                win.minimized -> p.mid
                else -> p.ink
            },
        )
    }
}

/**
 * Windows 95 style shell: taskbar with Start button and tray at the bottom,
 * start menu, desktop icons stacked on the left and widgets on the right.
 */
@Composable
fun WindowsShell(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val drag = LocalDragState.current
    val dockSlots = remember { DockSlots(horizontal = true) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val taskH = px(44f)
    val edge = px(6f)
    val winMargin = px(8f)
    val cascade = px(24f)
    val minW = px(360f)
    val minH = px(240f)
    val area = remember(size, edge, taskH) {
        val m = edge
        Rect(m, m, (size.width - m).coerceAtLeast(m), (size.height - taskH - m).coerceAtLeast(m))
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Box(Modifier.fillMaxSize().padding(bottom = dp(44f))) {
            DesktopIcons(vm, Modifier.fillMaxSize())
            if (vm.settings.showWidgets) {
                DesktopWidgets(vm, Modifier.align(Alignment.TopEnd).padding(dp(12f)))
            }
        }

        
        // ── floating windows of our own apps ─────────────────────────────────────
        // The desktop frame never moves: a window keeps its place when the keyboard opens, and the
        // panel / dock stay drawn over it, the way a desktop taskbar always does.
        LaunchedEffect(area, winMargin, cascade, minW, minH) {
            vm.wm.relayout(area, ShellMetrics(winMargin, cascade, minW, minH))
        }
        WindowLayer(vm.wm)

        // ── start menu ────────────────────────────────────────────────────────────
        if (vm.startMenu) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = dp(8f), bottom = dp(50f))
                    .width(dp(420f))
                    .fillMaxHeight(0.62f)
                    .shellPanel(borderDp = 2.4f, shadowDp = 6f, radiusCap = 10f)
                    .onGloballyPositioned { drag.sidebarBounds = it.boundsInRoot() },
            ) {
                Column(Modifier.fillMaxSize()) {
                    // the banner of the start menu: printed ink on e-paper, the desktop's own
                    // highlight on a native theme (the real Windows 95 start menu is navy too)
                    Row(Modifier.fillMaxWidth().height(dp(38f)).background(p.plate).padding(horizontal = dp(10f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
                        GlyphIcon(Glyph.LOGO, dp(18f), tint = p.plateText, clear = p.plate)
                        RetroText("Retrobit 95", size = 15f, display = true, weight = FontWeight.Bold, color = p.plateText)
                        Spacer(Modifier.weight(1f))
                        RetroText("${vm.settings.userName}", size = 11f, color = p.plateText)
                    }
                    Row(Modifier.weight(1f)) {
                        Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(dp(8f)), verticalArrangement = Arrangement.spacedBy(dp(6f))) {
                            RetroText("Applications", size = 11f, color = p.mid, weight = FontWeight.Bold)
                            LazyVerticalGrid(columns = GridCells.Adaptive(dp(96f)), modifier = Modifier.height(dp(220f))) {
                                // same order as the app drawer: most used first, then A-Z
                                items(vm.appsByUsage().take(24), key = { it.key }) { app ->
                                    AppIconTile(
                                        app = app,
                                        openOnDoubleClick = false,   // start menu / taskbar list
                                        iconSize = dp(34f),
                                        labelSize = 10f,
                                        onOpen = { vm.launch(it) },
                                        onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                                    )
                                }
                            }
                        }
                        Box(Modifier.width(dp(1.4f)).fillMaxHeight().background(p.ink.copy(alpha = 0.4f)))
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(dp(8f)), verticalArrangement = Arrangement.spacedBy(dp(6f))) {
                            StartItem(Glyph.GRID, "Applications") { vm.openWindow(WinKind.APPS) }
                            StartItem(Glyph.TERMINAL, "Terminal") { vm.openWindow(WinKind.TERMINAL) }
                            StartItem(Glyph.SYSMON, "System monitor") { vm.openWindow(WinKind.SYSMON) }
                            StartItem(Glyph.NOTES, "Notes") { vm.openWindow(WinKind.NOTES) }
                            StartItem(Glyph.CALENDAR, "Calendar") { vm.openWindow(WinKind.CALENDAR) }
                            StartItem(Glyph.SETTINGS, "Settings") { vm.openWindow(WinKind.SETTINGS) }
                            StartItem(Glyph.SEARCH, "Find (spotlight)") { vm.spotlight = true }
                            StartItem(Glyph.ABOUT, "About") { vm.openWindow(WinKind.ABOUT) }
                            Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink.copy(alpha = 0.35f)))
                            StartItem(Glyph.GRID, "Desktops / workspaces") { vm.startMenu = false; vm.workspaceMenu(Offset(24f, 300f)) }
                        }
                    }
                }
            }
        }

        // ── taskbar ───────────────────────────────────────────────────────────────
        val hatch = rememberPanelTexture()
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(dp(44f))
                .background(p.paper)
                .border(dp(1.6f), p.edge)
                .onGloballyPositioned { drag.dockBounds = it.boundsInRoot() }
                .padding(dp(5f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            Row(
                Modifier
                    .height(dp(34f))
                    .background(if (vm.startMenu) p.plate else p.paper)
                    .border(dp(1.6f), p.edge)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.startMenu = !vm.startMenu }
                    .padding(horizontal = dp(10f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(7f)),
            ) {
                GlyphIcon(Glyph.LOGO, dp(18f))
                RetroText("Start", size = 13f, display = true, weight = FontWeight.Bold, color = if (vm.startMenu) p.paper else p.ink)
            }
            Box(Modifier.width(dp(1.4f)).height(dp(26f)).background(p.ink.copy(alpha = 0.4f)))
            // quick launch: the taskbar's own dock, reorderable by dragging an icon along it
            Row(
                Modifier
                    .height(dp(38f))
                    .border(dp(1.2f), p.edge.copy(alpha = 0.45f))
                    .padding(horizontal = dp(3f)),
                horizontalArrangement = Arrangement.spacedBy(dp(2f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val quick = vm.dockApps(6)
                dockSlots.trim(quick.size)
                quick.forEachIndexed { index, app ->
                    Box(Modifier.onGloballyPositioned { pos -> dockSlots.record(index, pos.boundsInRoot().center) }) {
                        AppIconTile(
                            app = app,
                            iconSize = dp(24f),
                            showLabel = false,
                            openOnDoubleClick = false,   // quick launch: one click, like the taskbar
                            onOpen = { vm.launch(it) },
                            onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                            onDrop = { a, target -> dockDrop(vm, a, dockSlots, drag.lastPosition, target) },
                        )
                    }
                }
                DockInsertMarker(dockSlots, horizontal = true)
            }
            // ── task buttons ───────────────────────────────────────────────────────
            // one button per floating window of our own apps: tap raises it (and un-minimizes it),
            // minimized windows are printed faint, exactly like the taskbar this is imitating
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dp(4f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                vm.openWindows.sortedByDescending { it.z }.forEach { win ->
                    TaskButton(vm, win)
                }
            }
            WorkspacePager(vm, boxSize = dp(30f))
            StyleChip(vm, compact = true)
            Row(
                Modifier
                    .then(
                        if (hatch != null) Modifier.background(hatch)
                        else Modifier.background(p.soft)
                    )
                    .padding(horizontal = dp(8f), vertical = dp(3f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(8f)),
            ) {
                TrayStatus(vm)
                KeyboardTrayButton(boxSize = dp(24f))
                TrayButton(Glyph.SEARCH) { vm.spotlight = true }
                TrayButton(Glyph.SYSMON) { vm.openWindow(WinKind.SYSMON) }
                RetroText(
                    rememberClockFormat(if (vm.settings.use24h) "HH:mm" else "hh:mm a").format(Date(vm.now)),
                    size = 12f, weight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StartItem(glyph: Glyph, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.paper)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = dp(4f), vertical = dp(6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(8f)),
    ) {
        GlyphTile(glyph, dp(15f))
        RetroText(label, size = 12f, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

/** 0 from the widget settings means "as tall as the content needs". */
private val Int.dpOrNull: androidx.compose.ui.unit.Dp?
    get() = if (this > 0) androidx.compose.ui.unit.Dp(this.toFloat()) else null
