package dev.retrobit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import dev.retrobit.desktop.ui.theme.LocalPalette
import kotlin.math.roundToInt

data class MenuItem(
    val label: String,
    val glyph: Glyph? = null,
    val enabled: Boolean = true,
    val shortcut: String? = null,
    val onSelect: () -> Unit,
)

/** Win95/GTK style popup menu, dithered shadow, no animation. */
@Composable
fun ContextMenu(
    items: List<MenuItem>,
    anchor: Offset,
    onDismiss: () -> Unit,
    width: Int = 260,
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val screen = LocalConfiguration.current
    val screenW = with(density) { screen.screenWidthDp.dp.toPx() }
    val screenH = with(density) { screen.screenHeightDp.dp.toPx() }
    val wPx = with(density) { width.dp.toPx() }
    var size by remember { mutableStateOf(IntSize(0, 0)) }
    val x = anchor.x.coerceIn(0f, (screenW - wPx).coerceAtLeast(0f))
    val y = anchor.y.coerceIn(0f, (screenH - 100f).coerceAtLeast(0f))

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(width.dp)
                .onSizeChanged { size = it }
                .padding(end = dp(4f), bottom = dp(4f))
                .retroShadow(dp(4f)),
        ) {
            // a native desktop rounds its menus (Windows 11, Aqua, GNOME) and frames them with the
            // theme border; the e-paper palettes keep the square printed card with the ink frame
            val menuShape = androidx.compose.foundation.shape.RoundedCornerShape(
                dp(if (p.isNative) p.cornerDp.coerceAtMost(8f) else 0f),
            )
            Column(
                Modifier
                    .clip(menuShape)
                    .background(p.paper)
                    .border(dp(if (p.isNative) 1f else 2f), p.edge, menuShape)
                    .padding(vertical = dp(5f)),
            ) {
                items.forEach { item ->
                    Row(
                        Modifier
                            .width(dp(width.toFloat()))
                            .background(p.paper)
                            .then(
                                if (item.enabled) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { item.onSelect(); onDismiss() } else Modifier
                            )
                            .padding(horizontal = dp(10f), vertical = dp(9f)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dp(8f)),
                    ) {
                        if (item.glyph != null) GlyphIcon(item.glyph, dp(15f))
                        RetroText(
                            item.label,
                            size = 13f,
                            color = if (item.enabled) p.ink else p.mid,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.shortcut != null) RetroText(item.shortcut, size = 10f, color = p.mid)
                    }
                }
            }
        }
    }
}

/** Simple vertical separator row inside menus. */
@Composable
fun MenuSeparator() {
    val p = LocalPalette.current
    Spacer(Modifier.height(dp(6f)))
    Box(Modifier.widthIn(min = 0.dp).fillMaxWidth().height(dp(1f)).background(p.ink.copy(alpha = 0.35f)))
    Spacer(Modifier.height(dp(6f)))
}
