package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.AppIconTile
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.RetroTextField
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.WinState

/** The "Applications" window — an app drawer with search, usable in every shell style. */
@Composable
fun AppsView(vm: LauncherViewModel, win: WinState) {
    val p = LocalPalette.current
    var query by remember(win.id) { mutableStateOf("") }
    val apps: List<AppEntry> = remember(query, vm.apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) vm.apps else vm.apps.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }
    }

    Column(Modifier.fillMaxSize().background(p.paper)) {
        Row(
            Modifier.fillMaxWidth().padding(dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            Box(Modifier.weight(1f)) {
                RetroTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "search applications…",
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
                GlyphIcon(Glyph.GRID, dp(14f))
                RetroText("${apps.size}/${vm.apps.size}", size = 12f, color = p.mid)
            }
        }
        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = dp(104f)),
            modifier = Modifier.fillMaxSize().padding(dp(6f)),
            verticalArrangement = Arrangement.spacedBy(dp(4f)),
            horizontalArrangement = Arrangement.spacedBy(dp(2f)),
        ) {
            items(apps, key = { it.key }) { app ->
                AppIconTile(
                    app = app,
                    iconSize = dp(48f),
                    labelSize = 11f,
                    onOpen = { vm.launch(it) },
                    onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                    onDrop = { a, target ->
                        when (target) {
                            dev.retrobit.desktop.ui.wm.DragState.Drop.DOCK -> vm.toggleDockPin(a)
                            dev.retrobit.desktop.ui.wm.DragState.Drop.SIDEBAR -> vm.openAppWindow(a)
                            else -> Unit
                        }
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(p.soft).padding(dp(6f)),
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetroText("${vm.apps.size} apps · long-press for options", size = 11f, color = p.mid)
        }
    }
}
