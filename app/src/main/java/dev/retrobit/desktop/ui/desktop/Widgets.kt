package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.data.SystemInfo
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroProgress
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.ChromePattern
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.rememberPanelTexture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WidgetCard(
    title: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    width: Dp = dp(250f),
    height: Dp? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    // native themes print a flat header bar instead of the e-paper hatch
    val hatch = rememberPanelTexture()
    Column(
        modifier
            .width(width)
            .then(if (height != null && height > dp(0f)) Modifier.heightIn(min = height) else Modifier)
            .background(p.paper)
            .border(dp(1.6f), p.edge)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
                ) else Modifier
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(dp(20f))
                .then(
                    if (hatch != null) Modifier.background(hatch)
                    else Modifier.background(p.soft)
                )
                .padding(horizontal = dp(6f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            GlyphIcon(glyph, dp(12f))
            RetroText(title, size = 11f, weight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.edge))
        Column(Modifier.padding(dp(9f)), verticalArrangement = Arrangement.spacedBy(dp(6f))) { content() }
    }
}

@Composable
fun ClockWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f), height: Dp? = null) {
    val s = vm.settings
    val loc = Locale.getDefault()
    val moment = Date(vm.now)
    // The formatters are remembered: this widget redraws every second, and building six
    // SimpleDateFormat objects per tick is pure waste on a tablet that runs on battery.
    val hour = remember(loc, s.use24h) { SimpleDateFormat(if (s.use24h) "HH" else "hh", loc) }
    val minute = remember(loc) { SimpleDateFormat("mm", loc) }
    val second = remember(loc) { SimpleDateFormat("ss", loc) }
    val meridiem = remember(loc) { SimpleDateFormat("a", loc) }
    val day = remember(loc) { SimpleDateFormat("EEE, dd MMM yyyy", loc) }
    val dayName = remember(loc) { SimpleDateFormat("EEEE", loc) }
    val h = hour.format(moment)
    val m = minute.format(moment)
    val sec = second.format(moment)
    val ampm = meridiem.format(moment)
    val date = day.format(moment)
    val weekday = dayName.format(moment)

    WidgetCard(
        "chrono",
        Glyph.SYSMON,
        modifier,
        width = width,
        height = height,
        onClick = { vm.openWindow(dev.retrobit.desktop.model.WinKind.CALENDAR) },
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(dp(2f))) {
            // the seconds sit on the same baseline, in the same size as the minutes
            RetroText(if (s.showSeconds) "$h:$m:$sec" else "$h:$m", size = 33f, display = true, weight = FontWeight.Bold)
            if (!s.use24h) RetroText(ampm, size = 11f, color = LocalPalette.current.mid)
        }
        RetroText(date, size = 11f)
        RetroText("$weekday · uptime ${SystemInfo.fmtUptime(vm.snapshot.uptimeMs)}", size = 10f, color = LocalPalette.current.mid, maxLines = 1)
    }
}

@Composable
fun SystemWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f), height: Dp? = null) {
    val p = LocalPalette.current
    val s = vm.snapshot
    WidgetCard("sysinfo", Glyph.SYSMON, modifier, width = width, height = height, onClick = { vm.openWindow(WinKind.SYSMON) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            RetroText("BAT", size = 10f, color = p.mid)
            RetroText(if (s.battery < 0) "--" else "${s.battery}%", size = 12f, weight = FontWeight.Bold)
            if (s.charging) RetroText("+", size = 12f)
            Box(Modifier.weight(1f))
            RetroText(s.net, size = 10f, color = p.mid)
        }
        RetroProgress(s.batFrac, cells = 16, thickness = dp(8f))
        RetroText("RAM  ${SystemInfo.fmtBytes(s.memUsed)} / ${SystemInfo.fmtBytes(s.memTotal)}", size = 10f, color = p.mid)
        RetroProgress(s.memFrac, cells = 16, thickness = dp(8f))
        RetroText("DISK ${SystemInfo.fmtBytes(s.diskUsed)} / ${SystemInfo.fmtBytes(s.diskTotal)}", size = 10f, color = p.mid)
        RetroProgress(s.diskFrac, cells = 16, thickness = dp(8f))
    }
}

@Composable
fun NotesWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f), height: Dp? = null) {
    val p = LocalPalette.current
    WidgetCard("notes", Glyph.NOTES, modifier, width = width, height = height, onClick = { vm.openWindow(WinKind.NOTES) }) {
        val text = vm.settings.notes.lines().take(5).joinToString("\n")
        if (text.isNotBlank()) {
            RetroText(text, size = 11f, color = p.mid, maxLines = 5)
        }
    }
}
