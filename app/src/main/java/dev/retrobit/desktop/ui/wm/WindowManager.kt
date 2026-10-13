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

    /** Virtual desktop this window lives on (0-based). */
    var desktop by mutableStateOf(0)
    var z by mutableStateOf(0)
    val isApp: Boolean get() = kind == WinKind.APPS

    val rect: Rect get() = Rect(x, y, x + width, y + height)
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

    /** Active virtual desktop (workspace), 0-based. */
    var desktop by mutableStateOf(0)
        private set

    /** Outline shown while a window is dragged against a screen edge. */
    var snapPreview by mutableStateOf<Rect?>(null)
        private set

    private var nextId = 1
    private var topZ = 1
    private var base = Rect.Zero
    private var metrics = ShellMetrics(0f, 0f, 24f, 48f, 420f, 320f)
    private var style = settings.style
    private var tiling = settings.tiling
    private var cascadeIndex = 0
    private var pendingSnap: SnapZone? = null

    val desktopCount: Int get() = settings.workspaceCount.coerceIn(1, 4)

    /** Windows on the active desktop, including minimized ones (task lists use this). */
    val onScreen: List<WinState> get() = windows.filter { it.desktop == desktop }

    /** Windows that should be drawn, back to front. */
    val visible: List<WinState> get() = onScreen.filter { !it.minimized }.sortedBy { it.z }

    val focused: WinState? get() = windows.firstOrNull { it.id == focusedId && it.desktop == desktop && !it.minimized }

    fun relayout(area: Rect, m: ShellMetrics, style: DesktopStyle, tiling: Boolean) {
        val areaChanged = area != base
        base = area
        metrics = m
        cascadeIndex = 0
        for (w in windows) {
            if (w.maximized || (tiling && !w.minimized && w.desktop == desktop)) {
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
        if (!areaChanged && !tiling) cascadeIndex = onScreen.size
        this.style = style
        if (desktop > desktopCount - 1) desktop = (desktopCount - 1).coerceAtLeast(0)
    }

    // ── virtual desktops ────────────────────────────────────────────────────────
    fun switchDesktop(index: Int) {
        val target = index.coerceIn(0, desktopCount - 1)
        if (target == desktop) return
        desktop = target
        snapPreview = null
        pendingSnap = null
        focusedId = visible.maxByOrNull { it.z }?.id
        if (tiling) retile(true)
    }

    fun moveWindowToDesktop(id: Int, index: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        val target = index.coerceIn(0, desktopCount - 1)
        w.desktop = target
        if (target != desktop) {
            if (focusedId == id) focusedId = visible.maxByOrNull { it.z }?.id
        } else {
            focus(id)
        }
    }

    fun moveFocusedToDesktop(index: Int) {
        focus()?.let { moveWindowToDesktop(it.id, index) }
    }

    fun hasWindowsOn(index: Int): Boolean = windows.any { it.desktop == index }

    // ── opening / closing ───────────────────────────────────────────────────────
    fun open(kind: WinKind, app: AppEntry? = null, title: String? = null) {
        if (kind.singleton) {
            // a singleton window is found on any desktop: switch to it instead of opening a twin
            val existing = windows.firstOrNull { it.kind == kind && it.app?.key == app?.key }
            if (existing != null) {
                if (existing.desktop != desktop) switchDesktop(existing.desktop)
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
        state.desktop = desktop
        state.width = w
        state.height = h
        val spread = metrics.cascade
        state.x = (area.left + metrics.margin + spread * (seed % 6)).coerceAtMost(max(area.left, area.right - w))
        state.y = (area.top + metrics.margin + spread * (seed % 6)).coerceAtMost(max(area.top, area.bottom - h))
        state.z = ++topZ
        windows += state
        focusedId = state.id
        if (tiling) retile(true)
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
            if (tiling) retile(true)
        }
    }

    fun minimize(id: Int) {
        windows.firstOrNull { it.id == id }?.let {
            it.minimized = true
            if (focusedId == id) focusedId = visible.maxByOrNull { w -> w.z }?.id
            if (tiling) retile(true)
        }
    }

    fun focus(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.desktop != desktop) switchDesktop(w.desktop)
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
        if (tiling) retile(true)
    }

    // ── moving / resizing / snapping ────────────────────────────────────────────
    fun move(id: Int, delta: Offset) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.maximized) return
        val area = base
        w.x = (w.x + delta.x).coerceIn(area.left - w.width * 0.4f, area.right - w.width * 0.6f)
        w.y = (w.y + delta.y).coerceIn(area.top, max(area.top, area.bottom - 40f))
        updateSnapHint(w)
    }

    private fun updateSnapHint(w: WinState) {
        if (!settings.snapWindows || tiling || w.maximized) {
            pendingSnap = null
            snapPreview = null
            return
        }
        val zone = LayoutMath.zoneFor(base, w.rect)
        pendingSnap = zone
        snapPreview = zone?.let { LayoutMath.rectFor(base, it) }
    }

    /** Called when the title-bar drag finishes: applies a pending edge snap, if any. */
    fun endDrag(id: Int, applySnap: Boolean = true) {
        val zone = pendingSnap
        pendingSnap = null
        snapPreview = null
        if (zone == null || !applySnap) return
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.maximized && zone != SnapZone.MAXIMIZE) w.maximized = false
        when (zone) {
            SnapZone.MAXIMIZE -> {
                w.maximized = true
                w.x = base.left; w.y = base.top; w.width = base.width; w.height = base.height
            }
            SnapZone.LEFT, SnapZone.RIGHT -> {
                val r = LayoutMath.rectFor(base, zone)
                w.maximized = false
                w.x = r.left; w.y = r.top; w.width = r.width; w.height = r.height
            }
        }
    }

    fun cancelDrag() {
        pendingSnap = null
        snapPreview = null
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

    fun minimizeAll() = onScreen.forEach { it.minimized = true }
    fun closeAll() {
        windows.removeAll { it.desktop == desktop }
        focusedId = null
    }

    /** Applies the master–stack tiling layout (Linux-style window manager). */
    fun retile(tiling: Boolean) {
        this.tiling = tiling
        settings.tiling = tiling
        cascadeIndex = 0
        val area = base
        val list = visible
        if (area.width <= 0f) return
        if (!tiling || list.isEmpty()) {
            list.forEach { w ->
                w.maximized = false
                w.width = min(w.width, max(metrics.minW, area.width - metrics.margin * 2))
                w.height = min(w.height, max(metrics.minH, area.height - metrics.margin * 2))
                w.x = w.x.coerceIn(area.left, max(area.left, area.right - w.width))
                w.y = w.y.coerceIn(area.top, max(area.top, area.bottom - w.height))
            }
            return
        }
        val rects = LayoutMath.tilingRects(area, list.size, metrics.margin)
        list.forEachIndexed { i, w ->
            val r = rects.getOrNull(i) ?: return@forEachIndexed
            w.maximized = false
            w.x = r.left; w.y = r.top; w.width = r.width; w.height = r.height
        }
    }

    fun size(): Size = Size(base.width, base.height)
}
