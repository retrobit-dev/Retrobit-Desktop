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
import dev.retrobit.desktop.ui.theme.rememberChromeBrush
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WidgetCard(
    title: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    width: Dp = dp(220f),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val hatch = rememberChromeBrush(ChromePattern.STRIPES_H, p.pattern, dp(1f))
    Column(
        modifier
            .width(width)
            .background(p.paper)
            .border(dp(1.6f), p.ink)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
                ) else Modifier
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(dp(20f)).background(hatch).padding(horizontal = dp(6f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            GlyphIcon(glyph, dp(12f))
            RetroText(title, size = 10.5f, weight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink))
        Column(Modifier.padding(dp(9f)), verticalArrangement = Arrangement.spacedBy(dp(6f))) { content() }
    }
}

@Composable
fun ClockWidget(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val s = vm.settings
    val fmt = if (s.use24h) "HH:mm" else "hh:mm a"
    val time = SimpleDateFormat(fmt, Locale.getDefault()).format(Date(vm.now))
    val date = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(vm.now))
    WidgetCard("chrono", Glyph.SYSMON, modifier) {
        RetroText(time, size = 34f, display = true, weight = FontWeight.Bold)
        RetroText(date, size = 11f)
        RetroText("uptime ${SystemInfo.fmtUptime(vm.snapshot.uptimeMs)}", size = 10f, color = LocalPalette.current.mid)
    }
}

@Composable
fun SystemWidget(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val s = vm.snapshot
    WidgetCard("sysinfo", Glyph.SYSMON, modifier, onClick = { vm.openWindow(WinKind.SYSMON) }) {
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
fun NotesWidget(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    WidgetCard("notes", Glyph.NOTES, modifier, onClick = { vm.openWindow(WinKind.NOTES) }) {
        RetroText(
            vm.settings.notes.lines().take(5).joinToString("\n").ifBlank { "empty — tap to write" },
            size = 10.5f,
            color = p.mid,
            maxLines = 5,
        )
    }
}
