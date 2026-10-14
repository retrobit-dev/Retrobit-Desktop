package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import dev.retrobit.desktop.ui.components.RetroTextField
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.shellPanel
import dev.retrobit.desktop.ui.components.px
import dev.retrobit.desktop.ui.input.KeyboardTrayButton
import dev.retrobit.desktop.ui.input.desktopClick
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.DockSlots
import dev.retrobit.desktop.ui.wm.LocalDragState
import dev.retrobit.desktop.ui.wm.ShellMetrics
import dev.retrobit.desktop.ui.wm.WindowLayer
import java.util.Date

/**
 * Classic Mac style shell: top menu bar, bottom dock, Launchpad overlay,
 * desktop icons aligned to the right edge and widgets on the left.
 */
@Composable
fun MacosShell(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val drag = LocalDragState.current
    val dockSlots = remember { DockSlots(horizontal = true) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val barH = px(26f)
    val dockH = px(86f)
    val edge = px(8f)
    val winMargin = px(12f)
    val cascade = px(26f)
    val minW = px(400f)
    val minH = px(280f)
    val area = remember(size, edge, barH, dockH) {
        val m = edge
        Rect(m, barH + m, (size.width - m).coerceAtLeast(m), (size.height - dockH).coerceAtLeast(barH))
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        // desktop + icons (right aligned, like the real thing)
        Box(Modifier.fillMaxSize().padding(top = dp(26f), bottom = dp(86f))) {
            DesktopIcons(vm, Modifier.fillMaxSize(), mirrorRight = true)
            if (vm.settings.showWidgets) {
                DesktopWidgets(vm, Modifier.align(Alignment.TopStart).padding(dp(12f)))
            }
        }

        
        // ── floating windows of our own apps ─────────────────────────────────────
        // The desktop frame never moves: a window keeps its place when the keyboard opens, and the
        // panel / dock stay drawn over it, the way a desktop taskbar always does.
        LaunchedEffect(area, winMargin, cascade, minW, minH) {
            vm.wm.relayout(area, ShellMetrics(winMargin, cascade, minW, minH))
        }
        WindowLayer(vm.wm)

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
            // the retro logo doubles as the system menu (Pixel Operator has no ◆/◇ glyphs)
            Box(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    vm.desktopMenu(Offset(12f, 40f))
                },
            ) {
                GlyphIcon(Glyph.LOGO, dp(17f))
            }
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
                        MenuItem(if (vm.settings.showWidgets) "Hide widgets" else "Show widgets") { vm.settings.showWidgets = !vm.settings.showWidgets },
                        MenuItem("Desktops / workspaces…", Glyph.GRID) { vm.workspaceMenu(Offset(60f, 46f)) },
                    ))
                )
            }
            MenuTitle("Window") {
                // our own apps live in floating windows now, so this menu is a real task list
                vm.openMenu(
                    dev.retrobit.desktop.ui.MenuRequest(
                        "Window", "floating windows of our own apps", Offset(140f, 40f),
                        listOf(
                            MenuItem("Open the app drawer", Glyph.GRID) { vm.openWindow(WinKind.APPS) },
                            MenuItem("Desktops / workspaces…", Glyph.COMPUTER) { vm.workspaceMenu(Offset(60f, 46f)) },
                        ) + vm.windowMenuItems(),
                    ),
                )
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
            WorkspacePager(vm, boxSize = dp(19f))
            StyleChip(vm, compact = true)
            TrayStatus(vm)
            KeyboardTrayButton(boxSize = dp(20f))
            RetroText(
                rememberClockFormat("EEE dd MMM  " + if (vm.settings.use24h) "HH:mm" else "hh:mm a")
                    .format(Date(vm.now)),
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
                    .shellPanel(borderDp = 2.2f, shadowDp = 5f, radiusCap = 24f)
                    .padding(horizontal = dp(10f), vertical = dp(7f)),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                DockItem(Glyph.GRID, "launch") { vm.launchpad = true }
                Box(Modifier.width(dp(1.4f)).height(dp(52f)).background(p.ink.copy(alpha = 0.45f)))
                val dockApps = vm.dockApps(9)
                dockSlots.trim(dockApps.size)
                dockApps.forEachIndexed { index, app ->
                    Box(
                        Modifier.onGloballyPositioned { pos -> dockSlots.record(index, pos.boundsInRoot().center) },
                    ) {
                        AppIconTile(
                            app = app,
                            iconSize = dp(46f),
                            showLabel = false,
                            openOnDoubleClick = false,   // dock: one click opens, like macOS
                            onOpen = { vm.launch(it) },
                            onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                            onDrop = { a, target ->
                                dockDrop(vm, a, dockSlots, drag.lastPosition, target)
                            },
                        )
                    }
                }
                DockInsertMarker(dockSlots, horizontal = true)
                Box(Modifier.width(dp(1.4f)).height(dp(52f)).background(p.ink.copy(alpha = 0.45f)))
                DockItem(Glyph.TERMINAL, "term") { vm.openWindow(WinKind.TERMINAL) }
                DockItem(Glyph.SETTINGS, "setup") { vm.openWindow(WinKind.SETTINGS) }
                DockItem(Glyph.SYSMON, "monitor") { vm.openWindow(WinKind.SYSMON) }
            }
        }

        // ── launchpad: a panel, never a full-screen sheet ───────────────────────────
        // it shows the same scrollable, usage-sorted list as the Applications window, so the drawer
        // stays out of the way and the desktop keeps working behind it
        if (vm.launchpad) {
            var query by remember(vm.launchpad) { mutableStateOf("") }
            // clicking the dimmed desktop closes the panel
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.ink.copy(alpha = 0.22f))
                    .desktopClick(primary = { vm.launchpad = false }, secondary = { pos -> vm.desktopMenu(pos) }),
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(0.76f)
                    .shellPanel(borderDp = 2.2f, shadowDp = 4f, radiusCap = 14f)
                    // taps on the panel's own background stay in the panel (only the desktop closes it)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(dp(2f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = dp(7f), vertical = dp(5f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dp(8f)),
                ) {
                    GlyphIcon(Glyph.GRID, dp(16f))
                    RetroText("LAUNCHPAD", size = 13f, display = true, weight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    RetroText("${vm.apps.size} apps", size = 10.5f, color = p.mid)
                    Box(
                        Modifier
                            .border(dp(1.3f), p.edge)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.launchpad = false }
                            .padding(horizontal = dp(7f), vertical = dp(2f)),
                    ) {
                        RetroText("close", size = 10.5f, color = p.ink)
                    }
                }
                Box(Modifier.fillMaxWidth().height(dp(1.6f)).background(p.edge))
                Row(Modifier.fillMaxWidth().padding(dp(7f)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        RetroTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "search applications…",
                        )
                    }
                }
                dev.retrobit.desktop.ui.apps.AppDrawerList(
                    vm = vm,
                    query = query,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Row(
                    Modifier.fillMaxWidth().background(p.soft).padding(dp(6f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RetroText("most used first, then a-z · hold an app for options", size = 10f, color = p.mid, maxLines = 1)
                }
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

/** 0 from the widget settings means "as tall as the content needs". */
private val Int.dpOrNull: androidx.compose.ui.unit.Dp?
    get() = if (this > 0) androidx.compose.ui.unit.Dp(this.toFloat()) else null
