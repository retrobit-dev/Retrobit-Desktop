package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.data.CalendarMath
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroIconButton
import dev.retrobit.desktop.ui.components.RetroProgress
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.rememberAppIcon
import dev.retrobit.desktop.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * The everyday widgets: what is coming up, what is left to do, the apps that get used the most and
 * how much battery is left. All of them are local — no account, no network.
 */

/** Next two weeks of entries, one line each. */
@Composable
fun AgendaWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f), height: Dp? = null) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val rows = remember(vm.calendar.entries.toList()) { vm.calendar.upcoming(today, days = 14) }

    WidgetCard(
        "agenda",
        Glyph.CALENDAR,
        modifier,
        width = width,
        height = height,
        onClick = { vm.openWindow(WinKind.CALENDAR) },
    ) {
        if (rows.isEmpty()) return@WidgetCard
        rows.take(5).forEach { (day, entry) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                RetroText(
                    if (day == today) "today" else CalendarMath.prettyDate(day).substringBefore(' '),
                    size = 9.5f,
                    color = p.mid,
                    maxLines = 1,
                )
                RetroText("${day.dayOfMonth}", size = 10.5f, weight = FontWeight.Bold, maxLines = 1)
                RetroText(
                    entry.title,
                    size = 10.5f,
                    color = if (entry.done) p.mid else p.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (entry.time != null) RetroText(CalendarMath.prettyTime(entry.time), size = 9.5f, color = p.mid, maxLines = 1)
            }
        }
        val more = rows.size - 5
        if (more > 0) RetroText("+$more more", size = 9.5f, color = p.mid, maxLines = 1)
    }
}

/** Open tasks, newest deadline last, with a one-tap done box. */
@Composable
fun TasksWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f), height: Dp? = null) {
    val p = LocalPalette.current
    val tasks = remember(vm.calendar.entries.toList()) { vm.calendar.openTasks() }
    val today = LocalDate.now()

    WidgetCard("tasks", Glyph.GRID, modifier, width = width, height = height) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            RetroText("${tasks.size} open", size = 11f, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            RetroIconButton(label = ">", boxSize = dp(19f)) { vm.openWindow(WinKind.CALENDAR) }
        }
        if (tasks.isEmpty()) return@WidgetCard
        tasks.take(5).forEach { task ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dp(6f)),
            ) {
                RetroIconButton(label = "-", boxSize = dp(17f)) { vm.calendar.toggleDone(task.id) }
                RetroText(task.title, size = 10.5f, maxLines = 1, modifier = Modifier.weight(1f))
                RetroText(
                    if (task.date == today) "today" else "${task.date.dayOfMonth} ${task.date.month.getDisplayName(TextStyle.SHORT, java.util.Locale.getDefault())}",
                    size = 9.5f,
                    color = p.mid,
                    maxLines = 1,
                )
            }
        }
        val more = tasks.size - 5
        if (more > 0) RetroText("+$more more", size = 9.5f, color = p.mid, maxLines = 1)
    }
}

/** Quick launch: the apps pinned to the dock, as a tap-to-open grid. */
@Composable
fun LauncherWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f), height: Dp? = null) {
    val apps = remember(vm.apps, vm.settings.pinnedDock) {
        vm.appsByPackages(vm.settings.pinnedDock).take(8)
    }
    WidgetCard("launcher", Glyph.HOME, modifier, width = width, height = height) {
        if (apps.isEmpty()) return@WidgetCard
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dp(4f))) {
                row.forEach { app ->
                    val icon = rememberAppIcon(app)
                    Column(
                        Modifier.weight(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.launch(app) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dp(2f)),
                    ) {
                        Box(Modifier.size(dp(40f)), contentAlignment = Alignment.Center) {
                            if (icon != null) {
                                Image(icon, app.label, Modifier.size(dp(36f)), filterQuality = FilterQuality.None)
                            } else {
                                GlyphIcon(Glyph.FOLDER, dp(28f))
                            }
                        }
                        RetroText(app.label, size = 8.5f, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Battery at a glance: percentage, bar, charging state and connection. */
@Composable
fun PowerWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f), height: Dp? = null) {
    val p = LocalPalette.current
    val s = vm.snapshot
    WidgetCard(
        "power",
        Glyph.POWER,
        modifier,
        width = width,
        height = height,
        onClick = { vm.openWindow(WinKind.SYSMON) },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            RetroText(if (s.battery < 0) "--" else "${s.battery}", size = 30f, display = true, weight = FontWeight.Bold)
            RetroText("%", size = 13f, color = p.mid)
            if (s.charging) RetroText("+", size = 16f, weight = FontWeight.Bold)
            Box(Modifier.weight(1f))
            RetroText(s.net, size = 10f, color = p.mid, maxLines = 1)
        }
        RetroProgress(s.batFrac, cells = 20, thickness = dp(10f))
        RetroText(
            if (s.battery < 0) "no battery reported" else if (s.charging) "on charge · ${s.batFrac.times(100).toInt()}%"
            else "on battery · ${s.batFrac.times(100).toInt()}%",
            size = 10f, color = p.mid, maxLines = 1,
        )
    }
}
