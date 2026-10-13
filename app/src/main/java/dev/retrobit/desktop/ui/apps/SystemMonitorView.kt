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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.data.SysSnapshot
import dev.retrobit.desktop.data.SystemInfo
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.RetroProgress
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun SystemMonitorView(vm: LauncherViewModel) {
    val p = LocalPalette.current
    var snap by remember { mutableStateOf(vm.snapshot) }
    var spark by remember { mutableStateOf(List(40) { 0.3f }) }

    LaunchedEffect(Unit) {
        while (true) {
            snap = SystemInfo.snapshot(vm.getApplication())
            spark = (spark + snap.memFrac).takeLast(40)
            delay(1500)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.paper)
            .verticalScroll(rememberScrollState())
            .padding(dp(12f)),
        verticalArrangement = Arrangement.spacedBy(dp(12f)),
    ) {
        RetroText("SYSTEM MONITOR", size = 16f, display = true, weight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            Metric("CPU", "8 cores", 0.34f)
            Metric("MEM", "${(snap.memFrac * 100).toInt()}%", snap.memFrac)
            Metric("DISK", "${(snap.diskFrac * 100).toInt()}%", snap.diskFrac)
            Metric("BAT", if (snap.battery < 0) "--" else "${snap.battery}%", snap.batFrac)
        }

        Section("memory")
        RetroText("${SystemInfo.fmtBytes(snap.memUsed)} used / ${SystemInfo.fmtBytes(snap.memTotal)} total", size = 12f)
        RetroText(SystemInfo.bar(snap.memFrac, 32), size = 12.5f, color = p.mid)
        Section("storage (/data)")
        RetroText("${SystemInfo.fmtBytes(snap.diskUsed)} used / ${SystemInfo.fmtBytes(snap.diskTotal)} total", size = 12f)
        RetroText(SystemInfo.bar(snap.diskFrac, 32), size = 12.5f, color = p.mid)
        Section("trend (memory samples)")
        Trend(spark)
        Section("uptime / network")
        RetroText("uptime  : ${SystemInfo.fmtUptime(snap.uptimeMs)}", size = 12f)
        RetroText("network : ${snap.net}", size = 12f)
        RetroText("battery : " + if (snap.battery < 0) "unavailable" else "${snap.battery}%" + if (snap.charging) " (charging)" else "", size = 12f)
        Section("open windows (${vm.wm.windows.size})")
        if (vm.wm.windows.isEmpty()) {
            RetroText("none — long-press the desktop for a menu", size = 11.5f, color = p.mid)
        } else {
            vm.wm.windows.forEach { w ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = dp(2f)),
                    horizontalArrangement = Arrangement.spacedBy(dp(8f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RetroText("#${w.id}", size = 11.5f, color = p.mid)
                    RetroText(w.kind.name.lowercase(), size = 11.5f, weight = FontWeight.Bold)
                    RetroText(w.title, size = 11.5f, color = p.mid, maxLines = 1, modifier = Modifier.weight(1f))
                    if (w.minimized) dev.retrobit.desktop.ui.components.RetroIconButton(
                        label = "+",
                        boxSize = dp(22f),
                        onClick = { vm.wm.focus(w.id) },
                    ) else dev.retrobit.desktop.ui.components.RetroIconButton(
                        label = "-",
                        boxSize = dp(22f),
                        onClick = { vm.wm.minimize(w.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val p = LocalPalette.current
    Column {
        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink.copy(alpha = 0.4f)))
        RetroText(title, size = 11f, color = p.mid, modifier = Modifier.padding(top = dp(6f), bottom = dp(3f)))
    }
}

@Composable
private fun Metric(label: String, value: String, frac: Float) {
    val p = LocalPalette.current
    Column(
        Modifier
            .background(p.soft)
            .padding(dp(8f)),
        verticalArrangement = Arrangement.spacedBy(dp(4f)),
    ) {
        RetroText(label, size = 10.5f, color = p.mid)
        RetroText(value, size = 17f, display = true, weight = FontWeight.Bold)
        RetroProgress(frac, cells = 8, thickness = dp(8f))
    }
}

@Composable
private fun Trend(values: List<Float>) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(dp(60f)).background(p.soft)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(dp(4f))) {
            val w = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { i, v ->
                val h = size.height * v.coerceIn(0f, 1f)
                drawRect(
                    p.ink,
                    topLeft = androidx.compose.ui.geometry.Offset(i * w, size.height - h),
                    size = androidx.compose.ui.geometry.Size(w * 0.7f, h),
                )
            }
        }
    }
}
