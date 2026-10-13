package dev.retrobit.desktop.ui.wm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.retrobit.desktop.data.AppRepository
import dev.retrobit.desktop.data.Settings
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.WinKind
import kotlin.math.max
import kotlin.math.min

class WinState(
    val id: Int,
    val kind: WinKind,
    val app: AppEntry?,
    title: String,
) {
    var title by mutableStateOf(title)
    var x by mutableStateOf(0f)
    var y by mutableStateOf(0f)
    var width by mutableStateOf(600f)
    var height by mutableStateOf(420f)
    var maximized by mutableStateOf(false)
    var minimized by mutableStateOf(false)
    var z by mutableStateOf(0)
    val isApp: Boolean get() = kind == WinKind.APPS
}

/** Geometry of the usable desktop area plus cascade metrics, all in px. */
data class ShellMetrics(
    val topInset: Float,
    val bottomInset: Float,
    val margin: Float,
    val cascade: Float,
    val minW: Float,
    val minH: Float,
)

class WindowManager(private val repo: AppRepository, private val settings: Settings) {

    val windows = mutableStateListOf<WinState>()
    var focusedId by mutableStateOf<Int?>(null)
        private set
    private var nextId = 1
    private var topZ = 1
    private var base = Rect.Zero
    private var metrics = ShellMetrics(0f, 0f, 24f, 48f, 420f, 320f)
    private var style = settings.style
    private var tiling = settings.tiling
    private var cascadeIndex = 0

    val visible: List<WinState> get() = windows.filter { !it.minimized }.sortedBy { it.z }
    val focused: WinState? get() = windows.firstOrNull { it.id == focusedId && !it.minimized }

    fun relayout(area: Rect, m: ShellMetrics, style: DesktopStyle, tiling: Boolean) {
        val areaChanged = area != base
        base = area
        metrics = m
        cascadeIndex = 0
        for (w in windows) {
            if (w.maximized || (tiling && !w.minimized)) {
                w.x = area.left; w.y = area.top; w.width = area.width; w.height = area.height
                continue
            }
            if (areaChanged) {
                // keep windows inside the new desktop bounds
                w.width = min(w.width, max(m.minW, area.width - m.margin * 2))
                w.height = min(w.height, max(m.minH, area.height - m.margin * 2))
                w.x = w.x.coerceIn(area.left, max(area.left, area.right - w.width))
                w.y = w.y.coerceIn(area.top, max(area.top, area.bottom - w.height))
            }
        }
        if (style == DesktopStyle.MACOS) {
            // menu bar extra at the top, dock reserves space at the bottom
            for (w in windows) if (!w.maximized) {
                w.height = min(w.height, max(m.minH, area.height - m.margin * 2))
            }
        }
        if (!areaChanged && !tiling) cascadeIndex = windows.size
        this.style = style
    }

    fun open(kind: WinKind, app: AppEntry? = null, title: String? = null) {
        if (kind.singleton) {
            val existing = windows.firstOrNull { it.kind == kind && it.app?.key == app?.key }
            if (existing != null) {
                existing.minimized = false
                focus(existing.id)
                return
            }
        }
        val area = base
        val seed = cascadeIndex++
        val w = when (kind) {
            WinKind.APPS -> max(metrics.minW, area.width - metrics.margin * 2)
            WinKind.TERMINAL -> max(metrics.minW, area.width * 0.62f)
            else -> max(metrics.minW, area.width * 0.55f)
        }
        val h = when (kind) {
            WinKind.APPS -> max(metrics.minH, area.height - metrics.margin * 2)
            WinKind.TERMINAL -> max(metrics.minH, area.height * 0.6f)
            else -> max(metrics.minH, area.height * 0.5f)
        }
        val state = WinState(nextId++, kind, app, title ?: defaultTitle(kind, app))
        state.width = w
        state.height = h
        val spread = metrics.cascade
        state.x = (area.left + metrics.margin + spread * (seed % 6)).coerceAtMost(max(area.left, area.right - w))
        state.y = (area.top + metrics.margin + spread * (seed % 6)).coerceAtMost(max(area.top, area.bottom - h))
        state.z = ++topZ
        windows += state
        focusedId = state.id
    }

    fun openApp(app: AppEntry) = open(WinKind.APPS, app, app.label)

    private fun defaultTitle(kind: WinKind, app: AppEntry?): String = when (kind) {
        WinKind.APPS -> app?.label ?: "Apps"
        WinKind.TERMINAL -> "terminal"
        WinKind.SETTINGS -> "settings"
        WinKind.SYSMON -> "system monitor"
        WinKind.NOTES -> "notes.txt"
        WinKind.ABOUT -> "about"
    }

    fun close(id: Int) {
        val i = windows.indexOfFirst { it.id == id }
        if (i >= 0) {
            windows.removeAt(i)
            if (focusedId == id) focusedId = visible.maxByOrNull { it.z }?.id
        }
    }

    fun minimize(id: Int) {
        windows.firstOrNull { it.id == id }?.let {
            it.minimized = true
            if (focusedId == id) focusedId = visible.maxByOrNull { w -> w.z }?.id
        }
    }

    fun focus(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        w.minimized = false
        if (w.z != topZ) w.z = ++topZ
        focusedId = id
    }

    fun toggleMaximize(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        focus(id)
        w.maximized = !w.maximized
        val area = base
        if (w.maximized) {
            w.x = area.left; w.y = area.top; w.width = area.width; w.height = area.height
        } else {
            w.width = min(w.width, max(metrics.minW, area.width - metrics.margin * 2))
            w.height = min(w.height, max(metrics.minH, area.height - metrics.margin * 2))
            w.x = area.left + metrics.margin + metrics.cascade
            w.y = area.top + metrics.margin + metrics.cascade
        }
    }

    fun move(id: Int, delta: Offset) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.maximized) return
        val area = base
        w.x = (w.x + delta.x).coerceIn(area.left - w.width * 0.4f, area.right - w.width * 0.6f)
        w.y = (w.y + delta.y).coerceIn(area.top, max(area.top, area.bottom - 40f))
    }

    fun resize(id: Int, delta: Offset) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.maximized) return
        val area = base
        w.width = (w.width + delta.x).coerceIn(metrics.minW, max(metrics.minW, area.right - w.x))
        w.height = (w.height + delta.y).coerceIn(metrics.minH, max(metrics.minH, area.bottom - w.y))
    }

    fun cycleFocus() {
        val vs = visible
        if (vs.size < 2) return
        val cur = vs.indexOfFirst { it.id == focusedId }
        focus(vs[(cur + 1) % vs.size].id)
    }

    fun minimizeAll() = windows.forEach { it.minimized = true }
    fun closeAll() { windows.clear(); focusedId = null }

    /** Apply the tiling layout (Linux-style window manager). */
    fun retile(tiling: Boolean) {
        this.tiling = tiling
        settings.tiling = tiling
        cascadeIndex = 0
        val area = base
        val list = windows.filter { !it.minimized }
        if (!tiling || list.isEmpty()) {
            list.forEach { w ->
                w.maximized = false
                w.width = min(w.width, area.width - metrics.margin * 2)
                w.height = min(w.height, area.height - metrics.margin * 2)
            }
            return
        }
        val m = metrics.margin
        when (list.size) {
            1 -> {
                val w = list[0]
                w.x = area.left; w.y = area.top; w.width = area.width; w.height = area.height
                w.maximized = false
            }
            2 -> {
                list[0].x = area.left; list[0].y = area.top
                list[0].width = area.width * 0.5f - m; list[0].height = area.height
                list[1].x = area.left + area.width * 0.5f + m * 0.0f; list[1].y = area.top
                list[1].width = area.width * 0.5f - m; list[1].height = area.height
            }
            else -> {
                val master = list[0]
                master.x = area.left; master.y = area.top
                master.width = area.width * 0.55f - m
                master.height = area.height
                val rest = list.drop(1)
                val each = (area.height - m * (rest.size - 1)) / rest.size
                rest.forEachIndexed { i, w ->
                    w.x = master.width + m * 2
                    w.y = area.top + i * (each + m)
                    w.width = area.width - master.width - m * 3
                    w.height = each
                }
            }
        }
        if (style == DesktopStyle.WINDOWS) { /* tiling is offered regardless of style */ }
    }

    fun size(): Size = Size(base.width, base.height)
}
