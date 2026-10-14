package dev.retrobit.desktop.ui.wm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.retrobit.desktop.data.Settings
import dev.retrobit.desktop.model.WinKind
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One window on the desktop: a **floating box** that holds one of the launcher's own apps
 * (terminal, settings, notes, ...).
 *
 * Geometry is kept in desktop pixels, relative to the shell's usable area (the screen minus the
 * safe area and minus the shell's own panel / dock). The frame is drawn by
 * [dev.retrobit.desktop.ui.wm.WindowFrame]; the app inside it is just a Compose view, because these
 * are our own apps - nothing here needs the system's window mode.
 */
class WinState(
    val id: Int,
    val kind: WinKind,
    title: String,
) {
    var title by mutableStateOf(title)
    var x by mutableStateOf(0f)
    var y by mutableStateOf(0f)
    var width by mutableStateOf(600f)
    var height by mutableStateOf(420f)
    var maximized by mutableStateOf(false)
    var minimized by mutableStateOf(false)

    /**
     * Geometry the window had before it was maximized. "Restore" is not a fresh guess: it puts the
     * frame back to this rectangle (clamped into the desktop when the screen changed).
     */
    var restoreRect by mutableStateOf<Rect?>(null)

    /** Virtual desktop (page of icons & widgets) this window lives on, 0-based. */
    var desktop by mutableStateOf(0)
    var z by mutableStateOf(0)

    val rect: Rect get() = Rect(x, y, x + width, y + height)

    val placement: Placement
        get() = Placement(x.roundToInt(), y.roundToInt(), (x + width).roundToInt(), (y + height).roundToInt())
}

/** Geometry of the usable desktop area plus cascade metrics, all in px. */
data class ShellMetrics(
    val margin: Float,
    val cascade: Float,
    val minW: Float,
    val minH: Float,
)

/**
 * The window manager: which desktop windows exist, where they are, and which one has the focus.
 *
 * Deliberately small now. It knows nothing about tiling, freeform system windows or third-party
 * apps: a window here is always one of **our own** full-size views in a framed box that the user can
 * drag, resize from eight handles, minimize, maximize + restore and close. Third-party apps keep
 * opening the way they always do - full screen, with the cyberdeck chrome drawn over them.
 */
class WindowManager(private val settings: Settings) {

    val windows = mutableStateListOf<WinState>()

    var focusedId by mutableStateOf<Int?>(null)
        private set

    /** Active virtual desktop (page of icons & widgets), 0-based. */
    var desktop by mutableStateOf(0)
        private set

    /** Outline shown while a window is dragged against a screen edge. */
    var snapPreview by mutableStateOf<Rect?>(null)
        private set

    /** The window currently being resized, and from which handle. */
    var resizingId by mutableStateOf<Int?>(null)
        private set
    private var resizeEdge: ResizeEdge? = null

    /** Called whenever a window ends up with new geometry, so it can be remembered per app. */
    var onGeometryChanged: ((WinState) -> Unit)? = null

    private var nextId = 1
    private var topZ = 1
    private var base = Rect.Zero
    private var metrics = ShellMetrics(24f, 48f, 320f, 240f)
    private var cascadeIndex = 0
    private var pendingSnap: SnapZone? = null

    /** Windows opened before the desktop had a size: placed properly on the first layout pass. */
    private val unplaced = mutableSetOf<Int>()

    val desktopCount: Int get() = settings.workspaceCount.coerceIn(1, 4)

    /** Windows on the active desktop, including minimized and maximized ones (task lists use this). */
    val onScreen: List<WinState> get() = windows.filter { it.desktop == desktop }

    /** Floating windows that should be drawn as boxes, back to front. */
    val visible: List<WinState> get() = onScreen.filter { !it.minimized && !it.maximized }.sortedBy { it.z }

    /** The window that owns the screen right now, if any. */
    val maximizedWindow: WinState? get() = onScreen.firstOrNull { it.maximized && !it.minimized }

    val focused: WinState? get() = windows.firstOrNull { it.id == focusedId && it.desktop == desktop && !it.minimized }

    /** Windows that are open but out of sight: what a task list shows as "minimized". */
    val minimized: List<WinState> get() = onScreen.filter { it.minimized }

    fun relayout(area: Rect, m: ShellMetrics) {
        val areaChanged = area != base
        val usable = area.width > 1f && area.height > 1f
        val firstLayout = !(base.width > 1f && base.height > 1f) && usable
        base = area
        metrics = m
        cascadeIndex = 0
        if (!areaChanged) return
        if (unplaced.isNotEmpty() && usable) {
            // windows opened before the first frame (from the panel, a shortcut or the shell): now
            // that we know how big the desktop is, they get their remembered or default rectangle
            val ids = unplaced.toList()
            unplaced.clear()
            ids.mapNotNull { id -> windows.firstOrNull { it.id == id } }.forEach { w ->
                placeNew(w, settings.windowRect(w.kind), cascadeIndex++)
            }
        } else if (firstLayout) {
            // nothing was opened early, but the cascade seed still wants a known area
            cascadeIndex = windows.count { it.desktop == desktop }
        }
        for (w in windows) {
            if (w.maximized) {
                w.x = area.left; w.y = area.top; w.width = area.width; w.height = area.height
                continue
            }
            // keep windows inside the new desktop bounds (rotation, safe area, another shell), and
            // never smaller than the minimum - a window opened before the layout existed is grown
            val maxW = max(m.minW, area.width - m.margin * 2)
            val maxH = max(m.minH, area.height - m.margin * 2)
            w.width = w.width.coerceIn(min(m.minW, maxW), maxW)
            w.height = w.height.coerceIn(min(m.minH, maxH), maxH)
            w.x = w.x.coerceIn(area.left, max(area.left, area.right - w.width))
            w.y = w.y.coerceIn(area.top, max(area.top, area.bottom - w.height))
        }
        notifySettled()
    }

    private fun notifySettled() {
        val cb = onGeometryChanged ?: return
        windows.forEach { cb(it) }
    }

    /**
     * "Reset window sizes": forgets the remembered geometry of every app and drops the windows that
     * are open back to their default size, in a cascade, the way a fresh boot would place them.
     */
    fun resetGeometry() {
        settings.clearWindowRects()
        val area = placementOf(base)
        var k = 0
        for (w in windows) {
            val r = MaximizeMath.openRect(
                remembered = null,
                area = area,
                fraction = LayoutMath.defaultFraction(w.kind.name),
                margin = metrics.margin,
                cascade = metrics.cascade * (k++ % 6),
                minW = metrics.minW,
                minH = metrics.minH,
            )
            w.maximized = false
            w.restoreRect = null
            w.x = r.left.toFloat(); w.y = r.top.toFloat()
            w.width = r.width.toFloat(); w.height = r.height.toFloat()
        }
        notifySettled()
    }

    // ── virtual desktops ────────────────────────────────────────────────────────
    fun switchDesktop(index: Int) {
        val target = index.coerceIn(0, desktopCount - 1)
        if (target == desktop) return
        desktop = target
        snapPreview = null
        pendingSnap = null
        focusedId = visible.maxByOrNull { it.z }?.id
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

    fun hasWindowsOn(index: Int): Boolean = windows.any { it.desktop == index }

    // ── opening / closing ───────────────────────────────────────────────────────
    /**
     * Opens (or brings back) the window for [kind]. Windows of our own apps behave like desktop
     * apps: opening the same one twice focuses the box that is already there instead of stacking a
     * twin on top of it.
     */
    fun open(kind: WinKind): WinState {
        val existing = windows.firstOrNull { it.kind == kind }
        if (existing != null) {
            if (existing.desktop != desktop) switchDesktop(existing.desktop)
            existing.minimized = false
            focus(existing.id)
            return existing
        }
        val state = WinState(nextId++, kind, defaultTitle(kind))
        state.desktop = desktop
        state.z = ++topZ
        if (base.width > 1f && base.height > 1f) {
            placeNew(state, settings.windowRect(kind), cascadeIndex++)
        } else {
            // the desktop has no size yet (opened before the first frame): placed on the first layout
            unplaced += state.id
            state.x = 0f
            state.y = 0f
            state.width = metrics.minW
            state.height = metrics.minH
        }
        windows += state
        focusedId = state.id
        return state
    }

    /** Opens [w] at its remembered rectangle, or at a default fraction of the desktop in a cascade. */
    private fun placeNew(w: WinState, remembered: Placement?, seed: Int) {
        val area = placementOf(base)
        val r = MaximizeMath.openRect(
            remembered = remembered,
            area = area,
            fraction = LayoutMath.defaultFraction(w.kind.name),
            margin = metrics.margin,
            cascade = metrics.cascade * (seed % 6),
            minW = metrics.minW,
            minH = metrics.minH,
        )
        w.maximized = false
        w.restoreRect = null
        w.x = r.left.toFloat()
        w.y = r.top.toFloat()
        w.width = r.width.toFloat()
        w.height = r.height.toFloat()
    }

    private fun defaultTitle(kind: WinKind): String = when (kind) {
        WinKind.APPS -> "Applications"
        WinKind.TERMINAL -> "terminal"
        WinKind.SETTINGS -> "settings"
        WinKind.SYSMON -> "system monitor"
        WinKind.NOTES -> "notes.txt"
        WinKind.ABOUT -> "about"
        WinKind.CALENDAR -> "calendar"
    }

    fun close(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        // a maximized window is remembered at the size it will go back to, not filling the screen
        if (w.maximized) {
            w.maximized = false
            restoreFrame(w)
        }
        windows.remove(w)
        if (focusedId == id) focusedId = visible.maxByOrNull { it.z }?.id
        onGeometryChanged?.invoke(w)
    }

    fun closeFocused() = focused?.let { close(it.id) }

    fun closeAll() {
        val doomed = onScreen.toList()
        doomed.forEach {
            if (it.maximized) {
                it.maximized = false
                restoreFrame(it)
            }
        }
        windows.removeAll(doomed)
        focusedId = visible.maxByOrNull { it.z }?.id
        doomed.forEach { onGeometryChanged?.invoke(it) }
    }

    fun minimize(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.maximized) {
            w.maximized = false
            restoreFrame(w)
        }
        w.minimized = true
        if (focusedId == id) focusedId = visible.maxByOrNull { it.z }?.id
        onGeometryChanged?.invoke(w)
    }

    fun minimizeFocused() = focused?.let { minimize(it.id) }

    fun minimizeAll() = onScreen.filter { !it.minimized }.forEach { minimize(it.id) }

    fun focus(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.desktop != desktop) switchDesktop(w.desktop)
        w.minimized = false
        if (w.z != topZ) w.z = ++topZ
        focusedId = id
    }

    /** Tab / Ctrl+Alt+Tab: bring the next window on this desktop to the front. */
    fun focusNext() {
        val vs = visible
        if (vs.size < 2) return
        val cur = vs.indexOfFirst { it.id == focusedId }
        focus(vs[(cur + 1) % vs.size].id)
    }

    // ── maximize / restore ──────────────────────────────────────────────────────
    /**
     * Maximize: our own apps then own the whole screen (the same full-screen view with the cyberdeck
     * rail and dock around them). Pressing it again restores the exact rectangle the window had.
     * Only one window can be maximized - a second one restores the first.
     */
    fun toggleMaximize(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.maximized) restore(id) else maximize(id)
    }

    fun maximize(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        focus(id)
        onScreen.firstOrNull { it.maximized && it.id != id }?.let {
            it.maximized = false
            restoreFrame(it)
            onGeometryChanged?.invoke(it)
        }
        if (!w.maximized) {
            w.restoreRect = w.rect
            // the size to come back to is the floating one: never remember "as big as the desktop"
            onGeometryChanged?.invoke(w)
        }
        w.maximized = true
        w.minimized = false
        maximizeFrame(w)
    }

    fun restore(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        focus(id)
        if (w.maximized) {
            w.maximized = false
            restoreFrame(w)
            onGeometryChanged?.invoke(w)
        }
    }

    /** Fills the usable desktop with [w]. */
    private fun maximizeFrame(w: WinState) {
        val r = MaximizeMath.maximized(placementOf(base))
        w.x = r.left.toFloat(); w.y = r.top.toFloat()
        w.width = r.width.toFloat(); w.height = r.height.toFloat()
    }

    /** Puts [w] back to the rectangle it had before it was maximized. */
    private fun restoreFrame(w: WinState) {
        val r = MaximizeMath.restore(
            saved = w.restoreRect?.let { placementOf(it) },
            area = placementOf(base),
            margin = metrics.margin,
            cascade = metrics.cascade,
            minW = metrics.minW,
            minH = metrics.minH,
        )
        w.x = r.left.toFloat(); w.y = r.top.toFloat()
        w.width = r.width.toFloat(); w.height = r.height.toFloat()
        w.restoreRect = null
    }

    private fun placementOf(r: Rect): Placement =
        Placement(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())

    // ── moving / resizing / snapping ────────────────────────────────────────────
    fun move(id: Int, delta: Offset) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.maximized) return
        val area = base
        // a window may hang off the left / right edge a little, the way a desktop window does, but
        // it can never be dragged so far that it would be impossible to grab again
        val minX = area.left - w.width * 0.4f
        val maxX = max(minX, area.right - w.width * 0.6f)
        w.x = (w.x + delta.x).coerceIn(minX, maxX)
        w.y = (w.y + delta.y).coerceIn(area.top, max(area.top, area.bottom - 40f))
        updateSnapHint(w)
    }

    private fun updateSnapHint(w: WinState) {
        if (w.maximized) {
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
        val w = windows.firstOrNull { it.id == id } ?: return
        if (zone != null && applySnap) {
            when (zone) {
                // dragged against the top edge: the same as pressing maximize
                SnapZone.MAXIMIZE -> maximize(id)
                SnapZone.LEFT, SnapZone.RIGHT -> {
                    val r = LayoutMath.rectFor(base, zone)
                    w.maximized = false
                    w.restoreRect = null
                    w.x = r.left; w.y = r.top; w.width = r.width; w.height = r.height
                }
            }
        }
        // the drag moved or resized the window: remember the new geometry
        onGeometryChanged?.invoke(w)
    }

    fun cancelDrag() {
        pendingSnap = null
        snapPreview = null
    }

    // ── manual resizing (every edge and corner) ─────────────────────────────────
    fun beginResize(id: Int, edge: ResizeEdge) {
        windows.firstOrNull { it.id == id } ?: return
        focus(id)
        resizeEdge = edge
        resizingId = id
    }

    fun updateResize(id: Int, dx: Float, dy: Float) {
        val w = windows.firstOrNull { it.id == id } ?: return
        val edge = resizeEdge ?: return
        if (w.maximized) {
            // dragging a handle of a maximized window restores it to its remembered size first
            w.maximized = false
            restoreFrame(w)
        }
        val next = LayoutMath.resize(w.rect, edge, dx, dy, metrics.minW, metrics.minH, base)
        w.x = next.left
        w.y = next.top
        w.width = next.width
        w.height = next.height
    }

    fun endResize() {
        val id = resizingId
        resizingId = null
        resizeEdge = null
        id?.let { wid -> windows.firstOrNull { it.id == wid } }?.let { onGeometryChanged?.invoke(it) }
    }
}
