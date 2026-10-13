package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LocalViewModel
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.apps.AboutView
import dev.retrobit.desktop.ui.apps.AppsView
import dev.retrobit.desktop.ui.apps.CalendarView
import dev.retrobit.desktop.ui.apps.NotesView
import dev.retrobit.desktop.ui.apps.SettingsView
import dev.retrobit.desktop.ui.apps.SystemMonitorView
import dev.retrobit.desktop.ui.apps.TerminalView
import dev.retrobit.desktop.ui.components.AppIconRow
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.LocalUiScale
import dev.retrobit.desktop.ui.components.RetroIconButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.input.keyboardRoom
import dev.retrobit.desktop.ui.theme.LocalPalette
import java.util.Date

/**
 * One of **our own apps, full screen** - the terminal, settings, the drawer, notes...
 *
 * This is what a desktop window used to be, and it is deliberately shaped like the cyberdeck chrome
 * that is drawn over somebody else's app: a black frame around the whole screen, a title rail along
 * the top (name on the left, live clock and keys on the right) and the dock strip along the bottom.
 * Whichever app owns the screen - ours or a third-party one - the user sees the same window.
 */
@Composable
fun ShellHost(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val kind = vm.shellOpen ?: return
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxSize()
            .background(p.paper)
            // the same 3.5 dp black frame the chrome draws over full-screen apps
            .border(dp(3.5f), Color.Black),
    ) {
        ShellRail(vm, kind)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The window frame (border, title rail, dock) never moves: when the keyboard opens, only
                // the app's own content makes room for it, so the terminal's input line and the notes /
                // settings fields stay visible instead of hiding behind the keyboard.
                .keyboardRoom(vm.settings),
        ) {
            ShellContent(kind)
        }
        ShellDock(vm)
    }
}

/** Which view belongs to a [WinKind]: our apps are full-screen views, never windows. */
@Composable
fun ShellContent(kind: WinKind) {
    val vm = LocalViewModel.current
    when (kind) {
        WinKind.APPS -> AppsView(vm)
        WinKind.TERMINAL -> TerminalView(vm)
        WinKind.SETTINGS -> SettingsView(vm)
        WinKind.SYSMON -> SystemMonitorView(vm)
        WinKind.NOTES -> NotesView(vm)
        WinKind.ABOUT -> AboutView(vm)
        WinKind.CALENDAR -> CalendarView(vm)
    }
}

private fun titleOf(kind: WinKind): String = when (kind) {
    WinKind.APPS -> "applications"
    WinKind.TERMINAL -> "terminal"
    WinKind.SETTINGS -> "settings"
    WinKind.SYSMON -> "system monitor"
    WinKind.NOTES -> "notes"
    WinKind.ABOUT -> "about"
    WinKind.CALENDAR -> "calendar"
}

/** The title rail: app name, live clock and the two keys that matter. */
@Composable
private fun ShellRail(vm: LauncherViewModel, kind: WinKind) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(dp(40f))
            .background(p.paper)
            .border(dp(1.6f), Color.Black)
            .padding(horizontal = dp(8f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(8f)),
    ) {
        Box(Modifier.width(dp(9f)).height(dp(9f)).background(p.accent))
        RetroText(titleOf(kind), size = 14f, display = true, weight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        RetroText(
            rememberClockFormat("HH:mm:ss").format(Date(vm.now)),
            size = 12f,
            maxLines = 1,
        )
        RetroIconButton(glyph = Glyph.SETTINGS, label = "panel", boxSize = dp(28f)) {
            vm.openPanel()
        }
        RetroIconButton(glyph = Glyph.HOME, label = "desktop", boxSize = dp(28f)) {
            vm.closeShell()
        }
    }
}

/**
 * The dock strip along the bottom of our own full-screen apps: the desktop key, the apps this
 * tablet is set up to open, and the quick panel. Tapping an app here opens it (full screen, with the
 * chrome frame); holding an icon opens its menu, exactly like the desktop icons do.
 */
@Composable
private fun ShellDock(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val scale = LocalUiScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(dp(54f))
            .background(p.paper)
            .border(dp(1.6f), Color.Black)
            .padding(horizontal = dp(7f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
    ) {
        Column(
            Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    vm.closeShell()
                }
                .padding(horizontal = dp(4f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dp(3f)),
        ) {
            GlyphIcon(Glyph.HOME, dp(24f) * scale)
            RetroText("desktop", size = 10f, color = p.mid, maxLines = 1)
        }
        Box(Modifier.width(dp(1.4f)).height(dp(34f)).background(Color.Black))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            vm.dockApps(12).forEach { app ->
                AppIconRow(
                    app = app,
                    iconSize = dp(26f) * scale,
                    showLabel = true,
                    onOpen = { vm.launch(it) },
                    onContextMenu = { a, pos -> vm.appMenu(a, pos) },
                    onDrop = { _, _ -> },
                )
            }
        }
        RetroIconButton(glyph = Glyph.SETTINGS, label = "panel", boxSize = dp(30f)) { vm.openPanel() }
    }
}
