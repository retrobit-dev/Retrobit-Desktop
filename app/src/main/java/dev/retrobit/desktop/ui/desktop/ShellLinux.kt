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
import dev.retrobit.desktop.model.AppEntry
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
import dev.retrobit.desktop.ui.wm.WindowLayer
import dev.retrobit.desktop.ui.wm.ShellMetrics
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GNU/Linux style shell: top panel with a system menu + window buttons, a left side dock,
 * desktop icon grid, widgets in the top-right corner and (optionally) tiling windows.
 */
@Composable
fun LinuxShell(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val drag = LocalDragState.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    val panelH = px(30f)
    val dockW = px(78f)
    val winMargin = px(10f)
    val cascade = px(34f)
    val minW = px(360f)
    val minH = px(260f)
    val area = remember(size) {
        Rect(dockW, panelH, size.width.toFloat(), size.height.toFloat())
    }
    LaunchedEffect(size, vm.settings.tiling) {
        if (size.width > 0) {
            vm.wm.relayout(
                area,
                ShellMetrics(topInset = panelH, bottomInset = 0f, margin = winMargin,
                    cascade = cascade, minW = minW, minH = minH),
                DesktopStyle.LINUX,
                vm.settings.tiling,
            )
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        // ── desktop surface ───────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize().padding(start = dp(78f), top = dp(30f))) {
            DesktopIcons(vm, IconLayout.GRID, Modifier.fillMaxSize())
            if (vm.settings.showWidgets) {
                Column(
                    Modifier.align(Alignment.TopEnd).padding(dp(10f)).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dp(10f)),
                ) {
                    ClockWidget(vm)
                    SystemWidget(vm)
                }
            }
        }

        // ── windows ───────────────────────────────────────────────────────────────
        WindowLayer(vm.wm, Modifier.fillMaxSize())

        // ── top panel ─────────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .height(dp(30f))
                .background(p.paper)
                .border(dp(1.4f), p.ink)
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
            Spacer(Modifier.weight(1f))
            // window buttons — the "task list" of the panel (current workspace only)
            Row(horizontalArrangement = Arrangement.spacedBy(dp(4f)), verticalAlignment = Alignment.CenterVertically) {
                vm.wm.onScreen.take(7).forEach { w ->
                    Box(
                        Modifier
                            .border(dp(1.1f), p.ink)
                            .background(if (vm.wm.focusedId == w.id && !w.minimized) p.ink else p.paper)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (w.minimized) vm.wm.focus(w.id) else if (vm.wm.focusedId == w.id) vm.wm.minimize(w.id) else vm.wm.focus(w.id)
                            }
                            .padding(horizontal = dp(7f), vertical = dp(2f)),
                    ) {
                        RetroText(
                            w.title.take(18),
                            size = 11f,
                            color = if (vm.wm.focusedId == w.id && !w.minimized) p.paper else p.ink,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            WorkspacePager(vm, boxSize = dp(20f))
            StyleChip(vm, compact = true)
            TrayStatus(vm)
            KeyboardTrayButton(boxSize = dp(22f))
            TrayButton(Glyph.SEARCH) { vm.spotlight = true }
            RetroText(
                SimpleDateFormat(if (vm.settings.use24h) "HH:mm" else "hh:mm a", Locale.getDefault()).format(Date(vm.now)),
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
                .border(dp(1.4f), p.ink)
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
                vm.appsByPackages(vm.settings.pinnedDock).forEach { app ->
                    AppIconRow(
                        app = app,
                        iconSize = dp(34f),
                        showLabel = false,
                        onOpen = { vm.launch(it) },
                        onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                        onDrop = { a, target -> if (target == DragState.Drop.SIDEBAR || target == DragState.Drop.DOCK) vm.toggleDockPin(a) },
                    )
                }
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
