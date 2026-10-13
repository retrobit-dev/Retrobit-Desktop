package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextOverflow
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.RetroTextField
import dev.retrobit.desktop.ui.components.Tag
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.rememberAppIcon
import dev.retrobit.desktop.ui.input.desktopClick
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.WinState

/**
 * The "Applications" window — the app drawer.
 *
 * A **scrollable list** instead of a full-screen icon sheet: one row per app, ordered by how often
 * you actually open it (most used first, then A-Z), with the launch count on the right so the order
 * is never a mystery. The list, the search field and the sorting are shared with the macOS
 * launchpad ([AppDrawerList]), so both drawers always behave the same way.
 */
@Composable
fun AppsView(vm: LauncherViewModel, win: WinState) {
    val p = LocalPalette.current
    var query by remember(win.id) { mutableStateOf("") }
    val shown = remember(query, vm.apps, vm.settings.appUsage) { matchingApps(vm, query) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.paper)
            // tapping the drawer's empty space drops the icon selection
            .desktopClick(primary = { vm.clearSelection() }),
    ) {
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
                RetroText("${shown.size}/${vm.apps.size}", size = 12f, color = p.mid)
            }
        }
        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))
        AppDrawerList(vm, query, Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().background(p.soft).padding(dp(6f)),
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetroText(
                "most used first, then a-z · " +
                    if (vm.settings.doubleClickOpen) "double tap to open · hold for options"
                    else "tap to open · hold for options",
                size = 11f, color = p.mid, maxLines = 1,
            )
        }
    }
}

/**
 * The app list itself: most used first, then alphabetical, filtered by [query].
 *
 * Shared by the Applications window and the launchpad panel so the drawer looks and behaves the same
 * everywhere — and never grows into a full-screen sheet.
 */
@Composable
fun AppDrawerList(
    vm: LauncherViewModel,
    query: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val apps = remember(query, vm.apps, vm.settings.appUsage) { matchingApps(vm, query) }
    val counts = remember(vm.settings.appUsage) { vm.usageCounts() }
    val buckets = remember(apps, counts) { bucketsOf(apps, counts) }
    val showHeads = query.isBlank() && buckets.used.isNotEmpty() && buckets.rest.isNotEmpty()

    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RetroText("no app matches this search", size = 12f, color = p.mid)
        }
        return
    }

    LazyColumn(modifier, contentPadding = PaddingValues(vertical = dp(3f))) {
        if (showHeads) item(key = "head-used") { DrawerHead("most used") }
        items(buckets.used, key = { it.key }) { app -> DrawerRow(vm, app, counts[app.pkg] ?: 0) }
        if (showHeads) item(key = "head-rest") { DrawerHead("all apps, a-z") }
        items(buckets.rest, key = { it.key }) { app -> DrawerRow(vm, app, counts[app.pkg] ?: 0) }
    }
}

/** One line of the drawer: icon, name, package and how often it has been opened. */
@Composable
private fun DrawerRow(vm: LauncherViewModel, app: AppEntry, uses: Int) {
    val p = LocalPalette.current
    val selected = vm.selectedApp?.key == app.key
    val icon = rememberAppIcon(app, inverted = selected)

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) p.ink else Color.Transparent)
            .desktopClick(
                key = app.key,
                primary = { vm.launch(app) },
                secondary = { pos -> vm.appMenu(app, pos) },
                select = { vm.selectIcon(app) },
                doubleClick = vm.settings.doubleClickOpen,
            )
            .padding(horizontal = dp(7f), vertical = dp(3f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(8f)),
    ) {
        Box(Modifier.size(dp(26f)), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, app.label, Modifier.size(dp(22f)), filterQuality = FilterQuality.None)
            } else {
                GlyphIcon(Glyph.FOLDER, dp(18f))
            }
        }
        Column(Modifier.weight(1f)) {
            RetroText(
                app.label,
                size = 12.5f,
                color = if (selected) p.paper else p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RetroText(
                app.pkg,
                size = 9.5f,
                color = if (selected) p.paper else p.mid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (app.isWorkProfile) Tag("W", inverted = selected)
        if (uses > 0) {
            RetroText(
                "x" + uses.toString(),
                size = 10.5f,
                color = if (selected) p.paper else p.mid,
                maxLines = 1,
            )
        }
    }
}

/** Small label that separates "most used" from the alphabetical rest of the drawer. */
@Composable
private fun DrawerHead(label: String) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = dp(7f), vertical = dp(4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
    ) {
        RetroText(label, size = 10f, color = p.mid, maxLines = 1)
        Box(Modifier.weight(1f).height(dp(1f)).background(p.mid.copy(alpha = 0.5f)))
    }
}

private class Buckets(val used: List<AppEntry>, val rest: List<AppEntry>)

/** Splits the sorted drawer into "opened at least once" and "never opened", keeping both orders. */
private fun bucketsOf(apps: List<AppEntry>, counts: Map<String, Int>): Buckets =
    Buckets(
        used = apps.filter { (counts[it.pkg] ?: 0) > 0 },
        rest = apps.filter { (counts[it.pkg] ?: 0) <= 0 },
    )

/** The apps matching the search box, in drawer order (most used first, then A-Z). */
private fun matchingApps(vm: LauncherViewModel, query: String): List<AppEntry> {
    val q = query.trim().lowercase()
    val ordered = vm.appsByUsage()
    if (q.isEmpty()) return ordered
    return ordered.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }
}
