package dev.retrobit.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.retrobit.desktop.data.IconCache
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.ui.theme.LocalIconMode
import dev.retrobit.desktop.ui.theme.LocalIconTheme
import dev.retrobit.desktop.ui.input.desktopClick
import dev.retrobit.desktop.ui.input.penOrMouseDrag
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The icon bitmap of [app] in the current palette, mode, shape and accent.
 *
 * Every input that changes the picture is part of the render key **and** the producer always
 * resolves that key: a cached bitmap is only ever used for the exact same key. The previous version
 * kept the old bitmap alive when the palette or mode changed (produceState does not reset its value
 * for a new key), which is why icons used to look "unchanged" after switching a palette.
 */
@Composable
fun rememberAppIcon(app: AppEntry, inverted: Boolean = false): ImageBitmap? {
    val p = LocalPalette.current
    val mode = LocalIconMode.current
    val theme = LocalIconTheme.current
    val options = dev.retrobit.desktop.ui.theme.LocalIconOptions.current
    val epoch = dev.retrobit.desktop.ui.theme.LocalIconEpoch.current
    val ink = if (inverted) p.paper else p.ink
    val paper = if (inverted) p.ink else p.paper
    val accent = if (inverted) null else options.accent
    val label = app.label
    val keys = arrayOf<Any?>(app.key, mode, theme, ink, paper, accent, options.monogram, epoch)
    val state by produceState(IconCache.peek(app, mode, theme, ink, paper, accent, options.monogram), *keys) {
        // resolve for *this* key, whether or not a bitmap from another key is already in `value`
        value = withContext(Dispatchers.Default) {
            IconCache.get(app, mode, theme, ink, paper, accent, options.monogram, label)
        }
    }
    return state
}

/**
 * An app icon on the desktop / app drawer / launchpad / dock / sidebar.
 *
 * Desktop style: a tap **selects** the icon ([onSelect] + `selected`), a **double tap / double click
 * opens** it when [openOnDoubleClick] is on, and a **hold** opens its context menu (touch long-press,
 * pen hold, mouse or pen right click). Docks and quick-launch strips pass
 * `openOnDoubleClick = false` so they stay one-click, like every desktop taskbar.
 * Long-press + move still drags the icon onto the dock, the desktop or the app drawer.
 */
@Composable
fun AppIconTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    iconSize: Dp = dp(56f),
    labelSize: Float = 11f,
    showLabel: Boolean = true,
    selected: Boolean = false,
    openOnDoubleClick: Boolean = true,
    onOpen: (AppEntry) -> Unit,
    onSelect: (AppEntry) -> Unit = {},
    onContextMenu: ((AppEntry, Offset) -> Unit)? = null,
    onDrop: ((AppEntry, DragState.Drop) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val drag = LocalDragState.current
    val pointer = dev.retrobit.desktop.ui.input.LocalPointerRouter.current
    val labelsEnabled = dev.retrobit.desktop.ui.theme.LocalIconOptions.current.labels
    var origin by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableStateOf(0f) }
    val icon = rememberAppIcon(app, inverted = selected)

    fun finishDrag() {
        val target = drag.dropTarget()
        drag.clear()
        onDrop?.invoke(app, target)
    }

    val tileModifier = modifier
        .width(iconSize + dp(18f))
        .onGloballyPositioned { origin = it.positionInRoot() }
        // touch: long-press to pick the icon up, release without moving for the context menu
        .pointerInput(app.key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { pos -> moved = 0f; drag.start(app, origin + pos) },
                onDragEnd = {
                    val target = drag.dropTarget()
                    drag.clear()
                    if (moved > 28f) {
                        onDrop?.invoke(app, target)
                    } else {
                        onContextMenu?.invoke(app, origin + Offset(iconSize.value / 2f, iconSize.value / 2f))
                    }
                },
                onDragCancel = { drag.clear() },
                onDrag = { change, amount ->
                    change.consume()
                    moved += abs(amount.x) + abs(amount.y)
                    drag.move(origin + change.position)
                },
            )
        }
        // pen / mouse: drag straight away, single tap = open, double tap = context menu
        .penOrMouseDrag(
            onStart = { pos -> drag.start(app, origin + pos) },
            onMove = { pos -> drag.move(origin + pos) },
            onEnd = { completed -> if (completed) finishDrag() else drag.clear() },
            router = pointer,
        )
        .desktopClick(
            key = app.key,
            primary = { onOpen(app) },
            secondary = { pos -> onContextMenu?.invoke(app, origin + pos) },
            select = { onSelect(app) },
            doubleClick = openOnDoubleClick,
            holdTouch = false,   // the tile already turns a long press into a context menu
        )
        .padding(vertical = dp(5f), horizontal = dp(2f))

    Column(
        modifier = tileModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        // selected: the e-paper palettes invert the icon (ink plate), a native theme paints the
        // selection colour of its desktop behind it (navy on Windows 95, system blue on Aqua)
        val plate = p.skin?.highlight ?: p.ink
        val plateEdge = p.skin?.highlight ?: p.ink
        Box(
            Modifier
                .size(iconSize + dp(6f))
                .background(if (selected) plate else Color.Transparent)
                .then(if (selected) Modifier.border(dp(1.5f), plateEdge) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(iconSize),
                    filterQuality = FilterQuality.None,
                )
            } else {
                GlyphIcon(Glyph.FOLDER, iconSize * 0.78f)
            }
            if (app.isWorkProfile) {
                Box(Modifier.align(Alignment.BottomEnd).padding(dp(1f))) {
                    Tag("W", inverted = !selected)
                }
            }
        }
        if (showLabel && labelsEnabled) {
            val skin = p.skin
            if (skin != null) {
                // native desktops print a label straight on the desktop and highlight the whole
                // plate when the icon is selected (Windows 95: white on teal, navy when picked)
                RetroText(
                    text = app.label,
                    size = labelSize,
                    color = if (selected) skin.highlightText else skin.deskText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(if (selected) skin.highlight else Color.Transparent)
                        .padding(horizontal = dp(4f), vertical = dp(1f)),
                )
            } else {
                // name chip: on e-paper a printed plate behind the name is far easier to read
                RetroText(
                    text = app.label,
                    size = labelSize,
                    color = if (selected) p.plateText else p.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(if (selected) p.plate else p.paper)
                        .border(dp(1f), if (selected) p.plate else p.ink.copy(alpha = 0.45f))
                        .padding(horizontal = dp(4f), vertical = dp(1f)),
                )
            }
        }
    }
}

/** Solid framed icon used by the sidebar (Linux) and taskbar lists. */
@Composable
fun AppIconRow(
    app: AppEntry,
    modifier: Modifier = Modifier,
    iconSize: Dp = dp(30f),
    active: Boolean = false,
    showLabel: Boolean = true,
    onOpen: (AppEntry) -> Unit,
    onContextMenu: ((AppEntry, Offset) -> Unit)? = null,
    onDrop: ((AppEntry, DragState.Drop) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val drag = LocalDragState.current
    val pointer = dev.retrobit.desktop.ui.input.LocalPointerRouter.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableStateOf(0f) }
    val icon = rememberAppIcon(app, inverted = active)
    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(app.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos -> moved = 0f; drag.start(app, origin + pos) },
                    onDragEnd = {
                        val target = drag.dropTarget()
                        drag.clear()
                        if (moved > 28f) onDrop?.invoke(app, target) else onContextMenu?.invoke(app, origin)
                    },
                    onDragCancel = { drag.clear() },
                    onDrag = { change, amount ->
                        change.consume()
                        moved += abs(amount.x) + abs(amount.y)
                        drag.move(origin + change.position)
                    },
                )
            }
            .penOrMouseDrag(
                onStart = { pos -> drag.start(app, origin + pos) },
                onMove = { pos -> drag.move(origin + pos) },
                onEnd = { completed ->
                    val target = drag.dropTarget()
                    drag.clear()
                    if (completed) onDrop?.invoke(app, target) else Unit
                },
                router = pointer,
            )
            .desktopClick(
                key = app.key,
                primary = { onOpen(app) },
                secondary = { pos -> onContextMenu?.invoke(app, origin + pos) },
                holdTouch = false,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.background(if (active) p.plate else Color.Transparent).padding(dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(7f)),
        ) {
            if (icon != null) {
                Image(icon, null, Modifier.size(iconSize), filterQuality = FilterQuality.None)
            } else {
                GlyphIcon(Glyph.FOLDER, iconSize * 0.85f)
            }
            if (showLabel) {
                RetroText(app.label, size = 12f, color = if (active) p.paper else p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Ghost preview that follows the pointer while dragging an icon. */
@Composable
fun DragGhost(size: Dp = dp(60f)) {
    val drag = LocalDragState.current
    val p = LocalPalette.current
    val info = drag.info ?: return
    // e-paper drags an inverted print of the icon on an ink plate; a native theme drags the real
    // colour icon on a plate in its own selection colour
    val native = p.skin != null
    val icon = rememberAppIcon(info.app, inverted = !native)
    Box(
        Modifier
            .offsetTo(info.position.x - with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() } / 2f,
                info.position.y - with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() } / 2f)
            .size(size)
            .background(p.plate)
            .border(dp(2f), if (native) p.plate else p.plateText),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.size(size * 0.7f), filterQuality = FilterQuality.None)
        }
    }
}

@Composable
private fun Modifier.offsetTo(x: Float, y: Float): Modifier =
    this.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
