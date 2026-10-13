package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.WidgetId
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.RetroButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.RetroTextField
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.retroShadow
import dev.retrobit.desktop.ui.components.rememberAppIcon
import dev.retrobit.desktop.ui.theme.LocalPalette

/**
 * "Add apps or widgets to the desktop": one panel that fills the home screen.
 *
 * The app list is searchable and works like a desktop icon list — tap a row to put that app on the
 * desktop, tap it again to take it off. The widget row below toggles the widgets the same way, so
 * a fresh home screen can be put together without leaving this panel. Nothing here launches an app.
 */
@Composable
fun AddToDesktopDialog(vm: LauncherViewModel) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val apps = remember(query, vm.apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) vm.apps else vm.apps.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.ink.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                vm.appPicker = false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(dp(430f))
                .heightIn(max = dp(560f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(end = dp(4f), bottom = dp(4f))
                .retroShadow(dp(4f))
                .background(p.paper)
                .border(dp(2f), p.ink),
        ) {
            // ── title bar ─────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().background(p.soft).padding(horizontal = dp(8f), vertical = dp(5f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                RetroText("add to desktop", size = 12f, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RetroButton(label = "close", minWidth = dp(56f)) { vm.appPicker = false }
            }
            Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))

            // ── widgets ───────────────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(dp(8f)),
                verticalArrangement = Arrangement.spacedBy(dp(5f)),
            ) {
                RetroText("widgets", size = 11f, weight = FontWeight.Bold)
                WidgetId.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(dp(5f))) {
                        pair.forEach { id ->
                            val on = vm.settings.isWidgetEnabled(id)
                            RetroButton(
                                label = if (on) "${id.label} *" else id.label,
                                active = on,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (on) vm.settings.removeWidget(id) else vm.settings.addWidget(id)
                                },
                            )
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))

            // ── apps ──────────────────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(dp(8f)),
                verticalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dp(6f)),
                ) {
                    Box(Modifier.weight(1f)) {
                        RetroTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "search applications…",
                        )
                    }
                    RetroText("${apps.size}/${vm.apps.size}", size = 11f, color = p.mid)
                }
                RetroText(
                    "tap an app to put it on the desktop, tap it again to take it off",
                    size = 10f, color = p.mid, maxLines = 2,
                )
            }
            LazyColumn(Modifier.fillMaxWidth().height(dp(210f)).padding(horizontal = dp(6f))) {
                items(apps, key = { it.key }) { app -> AddRow(vm, app) }
            }
            Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))
            Row(
                Modifier.fillMaxWidth().padding(dp(8f)),
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RetroText(
                    "${vm.settings.pinnedDesktop.size} apps on the desktop",
                    size = 10.5f, color = p.mid, modifier = Modifier.weight(1f),
                )
                RetroButton(label = "arrange", minWidth = dp(66f)) {
                    vm.settings.clearIconCells()
                }
                RetroButton(label = "done", active = true, minWidth = dp(56f)) { vm.appPicker = false }
            }
        }
    }
}

/** One app line: icon, name, package and an Add / Remove button. */
@Composable
private fun AddRow(vm: LauncherViewModel, app: AppEntry) {
    val p = LocalPalette.current
    val onDesktop = vm.settings.pinnedDesktop.contains(app.pkg)
    val onDock = vm.settings.pinnedDock.contains(app.pkg)
    val icon = rememberAppIcon(app)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = dp(2f))
            .background(if (onDesktop) p.soft else p.paper)
            .border(dp(1f), p.ink)
            .padding(dp(4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
    ) {
        Box(Modifier.size(dp(30f)), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, app.label, Modifier.size(dp(28f)), filterQuality = FilterQuality.None)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dp(1f))) {
            RetroText(app.label, size = 11.5f, weight = FontWeight.Bold, maxLines = 1)
            RetroText(
                buildString {
                    append(app.pkg)
                    if (onDesktop) append("  [desktop]")
                    if (onDock) append("  [dock]")
                },
                size = 9.5f, color = p.mid, maxLines = 1,
            )
        }
        RetroButton(
            label = if (onDesktop) "remove" else "add",
            active = onDesktop,
            minWidth = dp(72f),
            onClick = {
                if (onDesktop) vm.toggleDesktopPin(app) else vm.addAppToDesktop(app)
            },
        )
        val inDock = vm.settings.pinnedDock.contains(app.pkg)
        RetroButton(
            label = if (inDock) "undock" else "dock",
            active = inDock,
            minWidth = dp(72f),
            onClick = { vm.toggleDockPin(app) },
        )
    }
}
