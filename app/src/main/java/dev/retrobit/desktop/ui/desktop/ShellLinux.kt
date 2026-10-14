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
import dev.retrobit.desktop.ui.components.AppIconRow
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.px
import dev.retrobit.desktop.ui.input.KeyboardTrayButton
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.DockSlots
import dev.retrobit.desktop.ui.wm.LocalDragState
import dev.retrobit.desktop.ui.wm.ShellMetrics
import dev.retrobit.desktop.ui.wm.WindowLayer
import java.util.Date

/**
 * GNU/Linux style shell: top panel with a system menu + window buttons, a left side dock,
 * desktop icon grid and widgets in the top-right corner.
 */
@Composable
fun LinuxShell(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val drag = LocalDragState.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    val dockSlots = remember { DockSlots(horizontal = false) }
    val panelH = px(30f)
    val dockW = px(78f)
    val winMargin = px(10f)
    val cascade = px(34f)
    val minW = px(360f)
    val minH = px(260f)
    // keyed on the shell's own metrics too: a change of UI scale moves the panel / dock
    val area = remember(size, dockW, panelH) {
        Rect(dockW, panelH, size.width.toFloat(), size.height.toFloat())
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        // ── desktop surface ───────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize().padding(start = dp(78f), top = dp(30f))) {
            DesktopIcons(vm, Modifier.fillMaxSize())
            if (vm.settings.showWidgets) {
                DesktopWidgets(
                    vm,
                    Modifier.align(Alignment.TopEnd).padding(dp(10f)),
                )
            }
        }


        // ── floating windows of our own apps ─────────────────────────────────────
        // The desktop frame never moves: a window keeps its place when the keyboard opens, and the
        // panel / dock stay drawn over it, the way a desktop taskbar always does.
        LaunchedEffect(area, winMargin, cascade, minW, minH) {
            vm.wm.relayout(area, ShellMetrics(winMargin, cascade, minW, minH))
        }
        WindowLayer(vm.wm)

        // ── top panel ─────────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .height(dp(30f))
                .background(p.paper)
                .border(dp(1.4f), p.edge)
                .padding(horizontal = dp(6f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            Row(
                Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.desktopMenu(Offset(10f, 60f)) }
                    .padding(horizontal = dp(6f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                GlyphIcon(Glyph.LOGO, dp(14f))
                RetroText("retrOS", size = 12f, weight = FontWeight.Bold)
            }
            PanelMenu("system") { vm.openWindow(WinKind.SETTINGS) }
            PanelMenu("apps") { vm.openWindow(WinKind.APPS) }
            PanelMenu("term") { vm.openWindow(WinKind.TERMINAL) }
            // the task list a panel always has: the floating windows of our own apps
            PanelMenu("windows") { vm.windowsMenu(Offset(10f, 60f)) }
            // no task list any more: an app owns the whole screen, and the panel is only the
            // desktop's own bar (the cyberdeck rail covers apps that are running)
            Spacer(Modifier.weight(1f))
            WorkspacePager(vm, boxSize = dp(20f))
            StyleChip(vm, compact = true)
            TrayStatus(vm)
            KeyboardTrayButton(boxSize = dp(22f))
            TrayButton(Glyph.SEARCH) { vm.spotlight = true }
            RetroText(
                rememberClockFormat(if (vm.settings.use24h) "HH:mm" else "hh:mm a").format(Date(vm.now)),
                size = 12f, weight = FontWeight.Bold,
            )
        }

        // ── left dock / sidebar ───────────────────────────────────────────────────
        Column(
            Modifier
                .padding(top = dp(30f))
                .width(dp(78f))
                .fillMaxHeight()
                .background(p.paper)
                .border(dp(1.4f), p.edge)
                .onGloballyPositioned { drag.sidebarBounds = it.boundsInRoot() }
                .padding(vertical = dp(8f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            DockGlyph(Glyph.GRID, "apps") { vm.openWindow(WinKind.APPS) }
            DockGlyph(Glyph.SEARCH, "find") { vm.spotlight = true }
            DockGlyph(Glyph.TERMINAL, "shell") { vm.openWindow(WinKind.TERMINAL) }
            Box(Modifier.fillMaxWidth().padding(horizontal = dp(10f)).height(dp(1.2f)).background(p.ink.copy(alpha = 0.4f)))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                val pinnedDock = vm.dockApps(14)
                dockSlots.trim(pinnedDock.size)
                pinnedDock.forEachIndexed { index, app ->
                    Box(
                        Modifier.onGloballyPositioned { pos ->
                            dockSlots.record(index, pos.boundsInRoot().center)
                        },
                    ) {
                        AppIconRow(
                            app = app,
                            iconSize = dp(34f),
                            showLabel = false,
                            onOpen = { vm.launch(it) },
                            onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                            onDrop = { a, target -> dockDrop(vm, a, dockSlots, drag.lastPosition, target) },
                        )
                    }
                }
                DockInsertMarker(dockSlots, horizontal = false)
            }
            DockGlyph(Glyph.SYSMON, "mon") { vm.openWindow(WinKind.SYSMON) }
            DockGlyph(Glyph.NOTES, "note") { vm.openWindow(WinKind.NOTES) }
            DockGlyph(Glyph.SETTINGS, "setup") { vm.openWindow(WinKind.SETTINGS) }
        }
    }
}

@Composable
private fun PanelMenu(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = dp(6f), vertical = dp(2f)),
    ) {
        RetroText(label, size = 12f, color = p.ink)
    }
}

@Composable
private fun DockGlyph(glyph: Glyph, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .width(dp(64f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = dp(3f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dp(3f)),
    ) {
        GlyphIcon(glyph, dp(26f))
        RetroText(label, size = 10f, color = p.mid, maxLines = 1)
    }
}

/** 0 from the widget settings means "as tall as the content needs". */
private val Int.dpOrNull: androidx.compose.ui.unit.Dp?
    get() = if (this > 0) androidx.compose.ui.unit.Dp(this.toFloat()) else null
