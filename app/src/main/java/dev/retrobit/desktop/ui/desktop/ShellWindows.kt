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
import dev.retrobit.desktop.ui.components.AppIconTile
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.retroShadow
import dev.retrobit.desktop.ui.input.KeyboardTrayButton
import dev.retrobit.desktop.ui.components.px
import dev.retrobit.desktop.ui.theme.ChromePattern
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.rememberChromeBrush
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState
import dev.retrobit.desktop.ui.wm.ShellMetrics
import dev.retrobit.desktop.ui.wm.WindowLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Windows 95 style shell: taskbar with Start button and tray at the bottom,
 * start menu, desktop icons stacked on the left and widgets on the right.
 */
@Composable
fun WindowsShell(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val drag = LocalDragState.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    val taskH = px(44f)
    val edge = px(6f)
    val winMargin = px(8f)
    val cascade = px(24f)
    val minW = px(360f)
    val minH = px(240f)
    val area = remember(size) {
        val m = edge
        Rect(m, m, (size.width - m).coerceAtLeast(m), (size.height - taskH - m).coerceAtLeast(m))
    }
    LaunchedEffect(size, vm.settings.tiling) {
        if (size.width > 0) {
            vm.wm.relayout(
                area,
                ShellMetrics(0f, taskH, winMargin, cascade, minW, minH),
                DesktopStyle.WINDOWS,
                vm.settings.tiling,
            )
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Box(Modifier.fillMaxSize().padding(bottom = dp(44f))) {
            DesktopIcons(vm, IconLayout.COLUMN_LEFT, Modifier.fillMaxSize())
            if (vm.settings.showWidgets) {
                Column(
                    Modifier.align(Alignment.TopEnd).padding(dp(12f)).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dp(10f)),
                ) {
                    ClockWidget(vm)
                    SystemWidget(vm)
                }
            }
        }

        WindowLayer(vm.wm, Modifier.fillMaxSize())

        // ── start menu ────────────────────────────────────────────────────────────
        if (vm.startMenu) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = dp(8f), bottom = dp(50f))
                    .width(dp(420f))
                    .fillMaxHeight(0.62f)
                    .background(p.paper)
                    .border(dp(2.4f), p.ink)
                    .retroShadow(dp(6f))
                    .onGloballyPositioned { drag.sidebarBounds = it.boundsInRoot() },
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(dp(38f)).background(p.ink).padding(horizontal = dp(10f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
                        GlyphIcon(Glyph.LOGO, dp(18f))
                        RetroText("Retrobit 95", size = 15f, display = true, weight = FontWeight.Bold, color = p.paper)
                        Spacer(Modifier.weight(1f))
                        RetroText("${vm.settings.userName}", size = 11f, color = p.paper)
                    }
                    Row(Modifier.weight(1f)) {
                        Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(dp(8f)), verticalArrangement = Arrangement.spacedBy(dp(6f))) {
                            RetroText("Applications", size = 11f, color = p.mid, weight = FontWeight.Bold)
                            LazyVerticalGrid(columns = GridCells.Adaptive(dp(96f)), modifier = Modifier.height(dp(220f))) {
                                items(vm.apps.take(24), key = { it.key }) { app ->
                                    AppIconTile(
                                        app = app,
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
                            StartItem(Glyph.SETTINGS, "Settings") { vm.openWindow(WinKind.SETTINGS) }
                            StartItem(Glyph.SEARCH, "Find (spotlight)") { vm.spotlight = true }
                            StartItem(Glyph.ABOUT, "About") { vm.openWindow(WinKind.ABOUT) }
                            Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink.copy(alpha = 0.35f)))
                            StartItem(Glyph.COMPUTER, "Minimize all windows") { vm.wm.minimizeAll(); vm.startMenu = false }
                            StartItem(Glyph.POWER, "Close all windows") { vm.wm.closeAll(); vm.startMenu = false }
                        }
                    }
                }
            }
        }

        // ── taskbar ───────────────────────────────────────────────────────────────
        val hatch = rememberChromeBrush(ChromePattern.STRIPES_H, p.pattern, dp(1f))
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(dp(44f))
                .background(p.paper)
                .border(dp(1.6f), p.ink)
                .onGloballyPositioned { drag.dockBounds = it.boundsInRoot() }
                .padding(dp(5f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            Row(
                Modifier
                    .height(dp(34f))
                    .background(if (vm.startMenu) p.ink else p.paper)
                    .border(dp(1.6f), p.ink)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.startMenu = !vm.startMenu }
                    .padding(horizontal = dp(10f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(7f)),
            ) {
                GlyphIcon(Glyph.LOGO, dp(18f))
                RetroText("Start", size = 13f, display = true, weight = FontWeight.Bold, color = if (vm.startMenu) p.paper else p.ink)
            }
            Box(Modifier.width(dp(1.4f)).height(dp(26f)).background(p.ink.copy(alpha = 0.4f)))
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(dp(5f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                vm.wm.windows.take(8).forEach { w ->
                    val active = vm.wm.focusedId == w.id && !w.minimized
                    Row(
                        Modifier
                            .width(dp(190f))
                            .height(dp(32f))
                            .background(if (active) p.ink else p.paper)
                            .border(dp(1.4f), p.ink)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (active) vm.wm.minimize(w.id) else vm.wm.focus(w.id)
                            }
                            .padding(horizontal = dp(6f)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
                    ) {
                        GlyphIcon(if (w.isApp) Glyph.GRID else Glyph.TERMINAL, dp(12f))
                        RetroText(w.title, size = 11.5f, color = if (active) p.paper else p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
            }
            StyleChip(vm, compact = true)
            Row(Modifier.background(hatch).padding(horizontal = dp(8f), vertical = dp(3f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
                TrayStatus(vm)
                KeyboardTrayButton(boxSize = dp(24f))
                TrayButton(Glyph.SEARCH) { vm.spotlight = true }
                TrayButton(Glyph.SYSMON) { vm.openWindow(WinKind.SYSMON) }
                RetroText(
                    SimpleDateFormat(if (vm.settings.use24h) "HH:mm" else "hh:mm a", Locale.getDefault()).format(Date(vm.now)),
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
        GlyphIcon(glyph, dp(15f))
        RetroText(label, size = 12f, maxLines = 2, modifier = Modifier.weight(1f))
    }
}
