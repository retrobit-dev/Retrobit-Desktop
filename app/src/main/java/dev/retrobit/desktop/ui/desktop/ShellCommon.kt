package dev.retrobit.desktop.ui.desktop

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.AppIconTile
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.input.desktopClick
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState

enum class IconLayout { GRID, COLUMN_LEFT, COLUMN_RIGHT }

/** Desktop icon surface: pinned apps, drop handling, long-press menu, right-click menu. */
@Composable
fun DesktopIcons(
    vm: LauncherViewModel,
    layout: IconLayout,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val drag = LocalDragState.current
    val pinned: List<AppEntry> = vm.appsByPackages(vm.settings.pinnedDesktop)
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                drag.desktopBounds = it.boundsInRoot()
                rootOffset = it.positionInRoot()
            }
            // empty desktop: right click (mouse / pen barrel) or a pen double tap opens the menu
            .desktopClick(
                primary = { },
                secondary = { pos -> vm.desktopMenu(rootOffset + pos) },
            ),
    ) {
        if (pinned.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(dp(20f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                GlyphIcon(Glyph.GRID, dp(30f))
                RetroText("desktop is empty", size = 12f, color = p.mid, maxLines = 1)
                RetroText("open Applications and drag icons here, or long-press for the menu", size = 11f, color = p.mid, maxLines = 3)
            }
            return@Box
        }
        when (layout) {
            IconLayout.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = dp(108f)),
                modifier = Modifier.fillMaxSize().padding(dp(8f)),
                verticalArrangement = Arrangement.spacedBy(dp(4f)),
            ) {
                items(pinned, key = { it.key }) { app -> DesktopIcon(vm, app) }
            }
            IconLayout.COLUMN_LEFT, IconLayout.COLUMN_RIGHT -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(dp(10f))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = if (layout == IconLayout.COLUMN_LEFT) Alignment.Start else Alignment.End,
                verticalArrangement = Arrangement.spacedBy(dp(2f)),
            ) {
                pinned.forEach { app -> DesktopIcon(vm, app) }
            }
        }
    }
}

@Composable
private fun DesktopIcon(vm: LauncherViewModel, app: AppEntry) {
    AppIconTile(
        app = app,
        iconSize = dp(54f),
        onOpen = { vm.launch(it) },
        onContextMenu = { a, pos -> vm.appMenu(a, pos) },
        onDrop = { a, target ->
            when (target) {
                DragState.Drop.DOCK -> vm.toggleDockPin(a)
                DragState.Drop.SIDEBAR -> vm.toggleDockPin(a)
                DragState.Drop.DESKTOP -> if (!vm.settings.pinnedDesktop.contains(a.pkg)) vm.toggleDesktopPin(a)
                else -> Unit
            }
        },
    )
}

/** Style switcher chip used in every shell's status area. */
@Composable
fun StyleChip(vm: LauncherViewModel, compact: Boolean = false) {
    val p = LocalPalette.current
    val current = vm.settings.style
    Row(
        Modifier
            .border(dp(1.2f), p.ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                vm.openMenu(
                    dev.retrobit.desktop.ui.MenuRequest(
                        title = "Desktop style",
                        subtitle = "current: ${current.label}",
                        anchors = Offset(0f, 0f),
                        items = DesktopStyle.values().map { style ->
                            dev.retrobit.desktop.ui.components.MenuItem(
                                label = "${style.label} — ${style.tagline}",
                                glyph = if (style == current) Glyph.LOGO else null,
                                onSelect = { vm.setStyle(style) },
                            )
                        },
                    )
                )
            }
            .padding(horizontal = dp(6f), vertical = dp(2f))
            .background(p.paper),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        RetroText("[${current.label.uppercase()}]", size = 11f, weight = FontWeight.Bold, maxLines = 1)
        if (!compact) RetroText("switch", size = 10f, color = p.mid)
    }
}

@Composable
fun TrayStatus(vm: LauncherViewModel, modifier: Modifier = Modifier, showText: Boolean = true) {
    val p = LocalPalette.current
    val s = vm.snapshot
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
        if (showText) {
            RetroText(s.net, size = 10f, color = p.mid)
            RetroText(
                if (s.battery < 0) "--" else "${s.battery}%" + if (s.charging) "+" else "",
                size = 10f, color = p.mid,
            )
        }
    }
}

@Composable
fun TrayButton(glyph: Glyph, active: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(dp(24f))
            .background(if (active) p.ink else p.paper)
            .border(dp(1.2f), p.ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, dp(14f))
    }
}
