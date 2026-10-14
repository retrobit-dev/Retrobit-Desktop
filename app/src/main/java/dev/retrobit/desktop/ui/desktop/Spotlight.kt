package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.RetroTextField
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.shellPanel
import dev.retrobit.desktop.ui.components.rememberAppIcon
import dev.retrobit.desktop.ui.input.desktopClick
import dev.retrobit.desktop.ui.theme.LocalPalette
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

/** Spotlight / KRunner style launcher search. */
@Composable
fun Spotlight(vm: LauncherViewModel) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    val builtins = listOf(
        Triple("Terminal", WinKind.TERMINAL, Glyph.TERMINAL),
        Triple("Applications", WinKind.APPS, Glyph.GRID),
        Triple("Settings", WinKind.SETTINGS, Glyph.SETTINGS),
        Triple("System monitor", WinKind.SYSMON, Glyph.SYSMON),
        Triple("Notes", WinKind.NOTES, Glyph.NOTES),
        Triple("Calendar", WinKind.CALENDAR, Glyph.CALENDAR),
        Triple("About", WinKind.ABOUT, Glyph.ABOUT),
    )
    val q = query.trim().lowercase()
    // the same order as the app drawer: the apps you use most come first
    val appHits: List<AppEntry> = if (q.isEmpty()) vm.appsByUsage().take(6)
    else vm.apps.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }.take(8)
    val builtinHits = builtins.filter { q.isEmpty() || it.first.lowercase().contains(q) }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.ink.copy(alpha = 0.28f))
            .desktopClick(primary = { vm.spotlight = false }),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = dp(90f))
                .widthIn(max = dp(520f))
                .fillMaxWidth(0.8f)
                .shellPanel(borderDp = 2.4f, shadowDp = 5f, radiusCap = 12f)
                .padding(dp(12f)),
            verticalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
                GlyphIcon(Glyph.SEARCH, dp(16f))
                RetroText("SPOTLIGHT", size = 13f, display = true, weight = FontWeight.Bold)
                Box(Modifier.weight(1f))
                RetroText("esc to close", size = 10f, color = p.mid)
            }
            RetroTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "search apps and commands…",
                onSubmit = { if (!vm.launchResolved(query)) vm.shell.submit(query) },
                focusRequester = fr,
                modifier = Modifier.fillMaxWidth().onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) { vm.spotlight = false; true } else false
                },
            )
            LazyColumn(Modifier.fillMaxWidth().width(dp(480f))) {
                items(builtinHits) { (label, kind, glyph) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                vm.openWindow(kind)
                            }
                            .padding(vertical = dp(6f)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dp(10f)),
                    ) {
                        GlyphIcon(glyph, dp(16f))
                        RetroText(label, size = 13f, modifier = Modifier.weight(1f))
                        RetroText("command", size = 10f, color = p.mid)
                    }
                }
                items(appHits, key = { it.key }) { app ->
                    val icon = rememberAppIcon(app)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.launch(app) }
                            .padding(vertical = dp(6f)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dp(10f)),
                    ) {
                        if (icon != null) {
                            Image(icon, null, Modifier.width(dp(22f)), filterQuality = FilterQuality.None)
                        } else {
                            GlyphIcon(Glyph.FOLDER, dp(16f))
                        }
                        RetroText(app.label, size = 13f, modifier = Modifier.weight(1f))
                        RetroText(app.pkg, size = 10f, color = p.mid, maxLines = 1)
                    }
                }
            }
            if (appHits.isEmpty() && builtinHits.isEmpty()) {
                RetroText("no match — press enter to run as a shell command", size = 11f, color = p.mid)
            }
        }
    }
}
