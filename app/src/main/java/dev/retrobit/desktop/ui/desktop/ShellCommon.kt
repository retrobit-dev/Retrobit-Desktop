package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.AppIconTile
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.input.desktopClick
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.DockMath
import dev.retrobit.desktop.ui.wm.DockSlots
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.GridMath
import dev.retrobit.desktop.ui.wm.GridSpec
import dev.retrobit.desktop.ui.wm.LocalDragState
import kotlin.math.roundToInt

/**
 * Desktop icon surface with free placement on a fixed grid.
 *
 * Icons live in grid cells (col,row) that are stored per package in Settings, so a user can drop an
 * icon anywhere; dropping onto an occupied cell swaps the two icons. When no position has been set
 * yet the icons fall back to the classic auto arrangement (top → bottom, then the next column).
 */
@Composable
fun DesktopIcons(
    vm: LauncherViewModel,
    modifier: Modifier = Modifier,
    mirrorRight: Boolean = false,
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val drag = LocalDragState.current
    val pinned: List<AppEntry> = vm.appsByPackages(vm.settings.pinnedDesktop)

    var size by remember { mutableStateOf(IntSize.Zero) }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    val cellW = with(density) { dp(76f).toPx() }
    val cellH = with(density) { dp(94f).toPx() }
    val pad = with(density) { dp(6f).toPx() }
    val spec: GridSpec = remember(size, cellW, cellH, pad, mirrorRight) {
        GridMath.spec(size.width.toFloat(), size.height.toFloat(), cellW, cellH, pad, pad, mirrorRight)
    }

    // resolve the cell of every pinned icon (stored position, other icons get auto cells)
    val placements: List<Pair<AppEntry, Pair<Int, Int>>> = remember(pinned, spec, vm.settings.iconCells) {
        val auto = ArrayDeque(GridMath.autoCells(spec, pinned.size))
        val taken = HashSet<Pair<Int, Int>>()
        val fixed = ArrayList<Pair<AppEntry, Pair<Int, Int>>>()
        pinned.forEach { app ->
            val stored = vm.settings.iconCell(app.pkg)
            val cell = stored?.takeIf {
                it.first in 0 until spec.cols && it.second in 0 until spec.rows && it !in taken
            }
            if (cell != null) {
                taken += cell
                fixed += app to cell
            } else {
                fixed += app to (-1 to -1)
            }
        }
        fixed.map { (app, cell) ->
            if (cell.first >= 0) app to cell
            else {
                var next = auto.removeFirstOrNull() ?: (0 to 0)
                while (next in taken && auto.isNotEmpty()) next = auto.removeFirst()
                taken += next
                app to next
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onGloballyPositioned {
                drag.desktopBounds = it.boundsInRoot()
                rootOffset = it.positionInRoot()
            }
            // empty desktop: right click, a held pen, or a held finger opens the desktop menu —
            // this is the way into widget editing and adding widgets
            .desktopClick(
                // a tap on the empty desktop leaves widget editing and drops the icon selection
                primary = {
                    vm.widgetEdit = false
                    vm.clearSelection()
                },
                secondary = { pos -> vm.desktopMenu(rootOffset + pos) },
            ),
    ) {
        // placement grid, only while something is being dragged
        if (vm.settings.showGrid && drag.active) {
            Canvas(Modifier.fillMaxSize()) {
                val line = p.ink.copy(alpha = 0.25f)
                for (col in 0..spec.cols) {
                    val x = spec.originX + col * spec.cellW
                    if (x <= this.size.width) drawLine(line, Offset(x, spec.originY), Offset(x, spec.originY + spec.rows * spec.cellH), 1f)
                }
                for (row in 0..spec.rows) {
                    val y = spec.originY + row * spec.cellH
                    if (y <= this.size.height) drawLine(line, Offset(spec.originX, y), Offset(spec.originX + spec.cols * spec.cellW, y), 1f)
                }
                // highlight the cell the pointer is over
                drag.info?.let { info ->
                    val local = info.position - rootOffset
                    if (GridMath.contains(spec, local.x, local.y)) {
                        val cell = GridMath.cellAt(spec, local.x, local.y)
                        val origin = GridMath.offsetOf(spec, cell.first, cell.second)
                        drawRect(
                            p.ink.copy(alpha = 0.12f),
                            topLeft = origin,
                            size = Size(spec.cellW, spec.cellH),
                        )
                    }
                }
            }
        }

        placements.forEach { (app, cell) ->
            val origin = GridMath.offsetOf(spec, cell.first, cell.second)
            Box(
                Modifier
                    .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
                    .size(
                        with(density) { spec.cellW.toDp() },
                        with(density) { spec.cellH.toDp() },
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                DesktopIcon(
                    vm = vm,
                    app = app,
                    spec = spec,
                    rootOffset = { rootOffset },
                    placements = placements,
                    drag = drag,
                )
            }
        }
    }
}

@Composable
private fun DesktopIcon(
    vm: LauncherViewModel,
    app: AppEntry,
    spec: GridSpec,
    rootOffset: () -> Offset,
    placements: List<Pair<AppEntry, Pair<Int, Int>>>,
    drag: DragState,
) {
    AppIconTile(
        app = app,
        iconSize = dp(54f),
        selected = vm.selectedApp?.key == app.key,
        openOnDoubleClick = vm.settings.doubleClickOpen,
        onOpen = { vm.launch(it) },
        onSelect = { vm.selectIcon(it) },
        onContextMenu = { a, pos -> vm.appMenu(a, pos) },
        onDrop = { dropped, target ->
            when (target) {
                DragState.Drop.DOCK -> vm.toggleDockPin(dropped)
                DragState.Drop.SIDEBAR -> vm.toggleDockPin(dropped)
                DragState.Drop.DESKTOP -> placeAtPointer(vm, spec, rootOffset(), placements, dropped, drag.lastPosition)
                else -> Unit
            }
        },
    )
}

/** Moves [app] to the cell under the pointer, swapping with whatever icon is already there. */
private fun placeAtPointer(
    vm: LauncherViewModel,
    spec: GridSpec,
    rootOffset: Offset,
    placements: List<Pair<AppEntry, Pair<Int, Int>>>,
    app: AppEntry,
    dropPosition: Offset,
) {
    // an icon dragged out of the dock moves onto the grid (and vice versa)
    vm.moveToDesktop(app)
    val local = dropPosition - rootOffset
    if (!GridMath.contains(spec, local.x, local.y)) return
    val wanted = GridMath.cellAt(spec, local.x, local.y)
    val occupant = placements.firstOrNull { it.second == wanted && it.first.pkg != app.pkg }?.first
    val current = placements.firstOrNull { it.first.pkg == app.pkg }?.second
    if (occupant != null && current != null) {
        // swap the two icons: no icon is ever pushed off the desktop
        vm.settings.setIconCell(occupant.pkg, current.first, current.second)
        vm.settings.setIconCell(app.pkg, wanted.first, wanted.second)
    } else {
        val taken = placements.filter { it.first.pkg != app.pkg }.map { it.second }.toSet()
        val cell = GridMath.nearestFree(spec, taken, wanted)
        vm.settings.setIconCell(app.pkg, cell.first, cell.second)
    }
}

/**
 * Handles an icon dropped onto a dock / sidebar / taskbar: reorder it into the nearest slot, or
 * pin it there when it came from the desktop. Drops on the desktop pull the app out of the dock.
 */
fun dockDrop(vm: LauncherViewModel, app: AppEntry, slots: DockSlots, pointer: Offset, target: DragState.Drop) {
    when (target) {
        DragState.Drop.DOCK, DragState.Drop.SIDEBAR, DragState.Drop.TASKBAR ->
            vm.moveToDock(app, slots.indexFor(pointer))
        DragState.Drop.DESKTOP -> vm.moveToDesktop(app)
        DragState.Drop.NONE -> if (vm.settings.pinnedDock.contains(app.pkg)) vm.moveToDock(app, slots.indexFor(pointer))
    }
}

/** A thin bar showing where a dragged icon will land in the dock. */
@Composable
fun DockInsertMarker(slots: DockSlots, horizontal: Boolean) {
    val p = LocalPalette.current
    val drag = LocalDragState.current
    val info = drag.info ?: return
    if (!DockMath.inside(info.position, drag.dockBounds) && !DockMath.inside(info.position, drag.sidebarBounds)) return
    val index = slots.indexFor(info.position)
    val center = slots.centerOf(index) ?: return
    Box(
        Modifier
            .offset { IntOffset((center.x - 14f).toInt(), (center.y - 1.5f).toInt()) }
            .size(if (horizontal) dp(28f) else dp(3f), if (horizontal) dp(3f) else dp(30f))
            .background(p.ink),
    )
}

/**
 * Virtual desktop switcher: one square per workspace, the number of windows drawn as dots.
 * Tap a square to switch, tap the active one for the desktop menu.
 */
@Composable
fun WorkspacePager(vm: LauncherViewModel, modifier: Modifier = Modifier, boxSize: Dp = dp(22f)) {
    val p = LocalPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dp(4f)), verticalAlignment = Alignment.CenterVertically) {
        for (index in 0 until vm.wm.desktopCount) {
            val active = vm.wm.desktop == index
            val windowsHere = vm.wm.onScreen.count { it.desktop == index }
            Box(
                Modifier
                    .size(boxSize)
                    .background(if (active) p.ink else p.paper)
                    .border(dp(1.3f), p.ink)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { if (active) vm.workspaceMenu(Offset.Zero) else vm.switchDesktop(index) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    RetroText(
                        "${index + 1}",
                        size = 11f,
                        display = true,
                        weight = FontWeight.Bold,
                        color = if (active) p.paper else p.ink,
                    )
                    if (windowsHere > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(windowsHere.coerceAtMost(3)) {
                                Box(Modifier.size(3.dp).background(if (active) p.paper else p.ink))
                            }
                        }
                    }
                }
            }
        }
    }
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
