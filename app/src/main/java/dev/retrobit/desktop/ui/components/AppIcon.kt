package dev.retrobit.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.retrobit.desktop.data.IconCache
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.ui.theme.LocalIconMode
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.DragState
import dev.retrobit.desktop.ui.wm.LocalDragState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun rememberAppIcon(app: AppEntry, inverted: Boolean = false): ImageBitmap? {
    val p = LocalPalette.current
    val mode = LocalIconMode.current
    val ink = if (inverted) p.paper else p.ink
    val paper = if (inverted) p.ink else p.paper
    val cached = IconCache.peek(app, mode, ink, paper)
    val state by produceState(cached, app.key, mode, ink, paper) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { IconCache.get(app, mode, ink, paper) }
        }
    }
    return state
}

/**
 * An app icon on the desktop / dock / sidebar.
 * Long-press opens the context menu; long-press + move drags the icon onto the dock or sidebar.
 */
@Composable
fun AppIconTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    iconSize: Dp = dp(56f),
    labelSize: Float = 11f,
    showLabel: Boolean = true,
    selected: Boolean = false,
    onOpen: (AppEntry) -> Unit,
    onContextMenu: ((AppEntry, Offset) -> Unit)? = null,
    onDrop: ((AppEntry, DragState.Drop) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val drag = LocalDragState.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableStateOf(0f) }
    val icon = rememberAppIcon(app, inverted = selected)

    val tileModifier = modifier
        .width(iconSize + dp(18f))
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(app.key) {
            detectTapGestures(
                onTap = { onOpen(app) },
                onLongPress = { pos -> onContextMenu?.invoke(app, origin + pos) },
            )
        }
        .pointerInput(app.key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { pos -> moved = 0f; drag.start(app, origin + pos) },
                onDragEnd = {
                    if (moved > 28f) {
                        val target = drag.dropTarget()
                        drag.clear()
                        onDrop?.invoke(app, target)
                    } else {
                        drag.clear()
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
        .padding(vertical = dp(5f), horizontal = dp(2f))

    Column(
        modifier = tileModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        Box(
            Modifier
                .size(iconSize + dp(6f))
                .background(if (selected) p.ink else Color.Transparent)
                .then(if (selected) Modifier.border(dp(1.5f), p.ink) else Modifier),
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
        if (showLabel) {
            RetroText(
                text = app.label,
                size = labelSize,
                color = if (selected) p.paper else p.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .then(if (selected) Modifier.background(p.ink) else Modifier)
                    .padding(horizontal = dp(3f)),
            )
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
    var origin by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableStateOf(0f) }
    val icon = rememberAppIcon(app, inverted = active)
    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = { onOpen(app) },
                    onLongPress = { pos -> onContextMenu?.invoke(app, origin + pos) },
                )
            }
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
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.background(if (active) p.ink else Color.Transparent).padding(dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(7f)),
        ) {
            if (icon != null) {
                Image(icon, null, Modifier.size(iconSize), filterQuality = FilterQuality.None)
            } else {
                GlyphIcon(Glyph.FOLDER, iconSize * 0.85f)
            }
            if (showLabel) {
                RetroText(app.label, size = 11.5f, color = if (active) p.paper else p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    val icon = rememberAppIcon(info.app, inverted = true)
    Box(
        Modifier
            .offsetTo(info.position.x - with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() } / 2f,
                info.position.y - with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() } / 2f)
            .size(size)
            .background(p.ink)
            .border(dp(2f), p.paper),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.size(size * 0.7f), filterQuality = FilterQuality.None)
        }
    }
}

@Composable
private fun Modifier.offsetTo(x: Float, y: Float): Modifier =
    this.then(androidx.compose.foundation.layout.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) })
