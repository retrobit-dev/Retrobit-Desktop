package dev.retrobit.desktop.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.PaletteId
import dev.retrobit.desktop.model.WallPattern
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** A Compose-observable value that is persisted into SharedPreferences on every write. */
class PrefState<T>(initial: T, private val write: (T) -> Unit) : ReadWriteProperty<Any?, T> {
    private val state = mutableStateOf(initial)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        write(value)
    }
}

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("retrobit", Context.MODE_PRIVATE)

    var style: DesktopStyle by enumPref("style", DesktopStyle.LINUX)
    var palette: PaletteId by enumPref("palette", PaletteId.PAPER)
    var wallpaper: WallPattern by enumPref("wallpaper", WallPattern.DOTS)
    var iconMode: IconMode by enumPref("iconMode", IconMode.MONO)
    var uiScale: Float by floatPref("uiScale", 1.0f)
    var use24h: Boolean by boolPref("use24h", true)

    /** Show seconds in the clock widget and in the panel / taskbar clock. */
    var showSeconds: Boolean by boolPref("showSeconds", false)
    var showWidgets: Boolean by boolPref("showWidgets", true)
    var bootSequence: Boolean by boolPref("bootSequence", true)
    var hideSystemBars: Boolean by boolPref("hideSystemBars", true)
    /** Where the desktop background comes from: generated pattern, the device wallpaper, or a picture. */
    var wallpaperSource: String by stringPref("wallpaperSource", dev.retrobit.desktop.data.WallpaperSource.PATTERN.name)
    /** Content URI of the picture chosen by the user. */
    var wallpaperUri: String by stringPref("wallpaperUri", "")
    var wallpaperTone: String by stringPref("wallpaperTone", dev.retrobit.desktop.data.WallpaperTone.HALFTONE.name)
    /** Icon shape: AUTO follows the desktop style. */
    var iconTheme: String by stringPref("iconTheme", dev.retrobit.desktop.model.IconTheme.AUTO.name)
    /** Show the placement grid while dragging desktop icons. */
    var showGrid: Boolean by boolPref("showGrid", true)
    /** Free icon placement on the desktop grid: encoded "pkg=col,row" pairs. */
    var iconCells: String by stringPref("iconCells", "")
    /**
     * M-Pencil / stylus: **hold** the pen on something to open its context menu (right click), the
     * way a desktop mouse works. Tap stays a left click; the barrel button also works.
     */
    var penRightClick: Boolean by boolPref("penRightClick", true)

    /** How long the pen has to rest before the press counts as a right click (ms). */
    var penHoldMs: Int by intPref("penHoldMs", 380)
    /**
     * Desktop-style opening: a tap on a desktop icon, app-drawer icon or launchpad icon only selects
     * it, a double click / double tap opens the app and a hold opens its menu. Turn it off for the
     * old one-tap launching. Docks, sidebars and the launcher widget stay one-click.
     */
    var doubleClickOpen: Boolean by boolPref("doubleClickOpen", true)

    /**
     * How often each app was opened, as `pkg=count|pkg=count`. This is what orders the app drawer:
     * the apps used most come first, the rest follow A-Z. `usage clear` in the terminal forgets it.
     */
    var appUsage: String by stringPref("appUsage", "")

    /** Counts one more launch of [pkg] (see [dev.retrobit.desktop.data.AppUsage]). */
    fun noteLaunch(pkg: String) {
        appUsage = dev.retrobit.desktop.data.AppUsage.bump(appUsage, pkg)
    }


    /**
     * Cyberdeck chrome: draw the desktop frame, the title rail and the dock **over** the app that
     * owns the screen - every app opens full screen and the chrome is what makes it a window.
     * Needs the "display over other apps" permission, which the Settings screen asks for.
     */
    var cyberdeckChrome: Boolean by boolPref("cyberdeckChrome", false)

    /** Draw the title rail (app name, clock, battery, window keys) while the chrome is up. */
    var chromeTopRail: Boolean by boolPref("chromeTopRail", true)

    /** Draw the dock strip at the bottom while the chrome is up. */
    var chromeDock: Boolean by boolPref("chromeDock", true)
    /** Slide the rails away after a few seconds, leaving the frame and a small handle. */
    var chromeAutoHide: Boolean by boolPref("chromeAutoHide", true)

    /** How long the rails stay up before they slide away (seconds). */
    var chromeAutoHideSecs: Int by intPref("chromeAutoHideSecs", 6)

    /** What the dock shows: "pinned" (the apps pinned to the dock) or "used" (most used first). */
    var railMode: String by stringPref("railMode", "pinned")

    /** Rail & dock size: "compact", "normal" (default) or "large" - the chrome's own scale. */
    var chromeSize: String by stringPref("chromeSize", "normal")

    /** Apps the cyberdeck chrome must stay away from (camera, games, video players...). */
    var chromeNoApps: List<String> by listPref("chromeNoApps")

    /** Night mode: the dark Ink palette, remembered against the day palette it replaced. */
    var nightMode: Boolean by boolPref("nightMode", false)
    var dayPalette: String by stringPref("dayPalette", "")

    /** Rotation lock applied while the launcher is in front (quick panel). */
    var rotateLocked: Boolean by boolPref("rotateLocked", false)

    /** Screen brightness set from the quick panel, or -1 while the tablet decides. */
    var brightness: Float by floatPref("brightness", -1f)

    /** Draw an e-paper pointer arrow while a pen or mouse is in use. */
    var showCursor: Boolean by boolPref("showCursor", true)
    /** Virtual desktops (workspaces), 1..4. */
    var workspaceCount: Int by intPref("workspaceCount", 3)
    /** Keep the desktop chrome clear of the punch-hole camera and the rounded display corners. */
    var safeArea: Boolean by boolPref("safeArea", true)
    /** Radius of the display corner the chrome must stay out of, in dp. */
    var cornerInset: Int by intPref("cornerInset", 20)
    /** Draw a name chip under desktop / dock icons so every app is easy to recognise. */
    var iconLabels: Boolean by boolPref("iconLabels", true)
    /** Mark icons with the app's first letter when the dithered artwork is hard to read. */
    var iconMonogram: Boolean by boolPref("iconMonogram", true)
    /** Tint the icon frame with the palette accent colour (red on tri-colour, green on phosphor). */
    var iconAccent: Boolean by boolPref("iconAccent", true)
    var setupDone: Boolean by boolPref("setupDone", false)
    var defaultsSeeded: Boolean by boolPref("defaultsSeeded", false)
    var notes: String by stringPref("notes", "RETROBIT NOTES\n==============\n\n- tap & hold any icon for options\n- type `help` in the terminal\n")
    var userName: String by stringPref("userName", "operator")
    var pinnedDesktop: List<String> by listPref("pinnedDesktop")
    var pinnedDock: List<String> by listPref("pinnedDock")

    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E): PrefState<E> {
        val stored = sp.getString(key, null)
        val initial = enumValues<E>().firstOrNull { it.name == stored } ?: def
        return PrefState(initial) { sp.edit().putString(key, it.name).apply() }
    }

    private fun boolPref(key: String, def: Boolean) =
        PrefState(sp.getBoolean(key, def)) { sp.edit().putBoolean(key, it).apply() }

    private fun intPref(key: String, def: Int) =
        PrefState(sp.getInt(key, def)) { sp.edit().putInt(key, it).apply() }

    private fun floatPref(key: String, def: Float) =
        PrefState(sp.getFloat(key, def)) { sp.edit().putFloat(key, it).apply() }

    private fun stringPref(key: String, def: String) =
        PrefState(sp.getString(key, def) ?: def) { sp.edit().putString(key, it).apply() }

    // ── typed enum accessors -------------------------------------------------------
    val iconThemeEnum: dev.retrobit.desktop.model.IconTheme
        get() = runCatching { dev.retrobit.desktop.model.IconTheme.valueOf(iconTheme) }
            .getOrDefault(dev.retrobit.desktop.model.IconTheme.AUTO)

    val wallpaperSourceEnum: dev.retrobit.desktop.data.WallpaperSource
        get() = runCatching { dev.retrobit.desktop.data.WallpaperSource.valueOf(wallpaperSource) }
            .getOrDefault(dev.retrobit.desktop.data.WallpaperSource.PATTERN)

    val wallpaperToneEnum: dev.retrobit.desktop.data.WallpaperTone
        get() = runCatching { dev.retrobit.desktop.data.WallpaperTone.valueOf(wallpaperTone) }
            .getOrDefault(dev.retrobit.desktop.data.WallpaperTone.HALFTONE)

    // ── desktop icon grid ----------------------------------------------------------
    // ── desktop widget sizes -------------------------------------------------------
    /** Every widget starts at the calendar width so the whole column lines up. */
    var widgetWidth: Int by intPref("widgetWidth", 250)

    /**
     * Hold a widget on the home screen to enter widget editing and size it. Turn it off when a
     * resting finger keeps starting edit mode by accident; the desktop menu and Settings still
     * switch it on, and the widget edges stay draggable either way.
     */
    var widgetHoldEdit: Boolean by boolPref("widgetHoldEdit", true)

    /** Widgets on the home screen, in order ("CLOCK\nCALENDAR\nSYSTEM"). */
    var widgetList: List<String> by listPref("widgetList")

    /** Widgets the home screen starts with; the user can add or remove any of them. */
    val defaultWidgets: List<String> = listOf("CLOCK", "CALENDAR", "SYSTEM")

    /**
     * The enabled widgets. A fresh install (no saved list) starts with [defaultWidgets]; once the
     * user has changed the list, even an empty one is respected — an empty home screen is a choice.
     */
    val widgets: List<dev.retrobit.desktop.model.WidgetId>
        get() {
            val raw = if (sp.contains("widgetList")) widgetList else defaultWidgets
            return raw.mapNotNull { dev.retrobit.desktop.model.WidgetId.of(it) }
        }

    fun isWidgetEnabled(id: dev.retrobit.desktop.model.WidgetId): Boolean = widgets.contains(id)

    private fun setWidgets(ids: List<dev.retrobit.desktop.model.WidgetId>) {
        widgetList = ids.map { it.name }
    }

    fun addWidget(id: dev.retrobit.desktop.model.WidgetId) {
        setWidgets(dev.retrobit.desktop.ui.wm.WidgetMath.add(widgets.map { it.name }, id.name)
            .mapNotNull { dev.retrobit.desktop.model.WidgetId.of(it) })
    }

    fun removeWidget(id: dev.retrobit.desktop.model.WidgetId) {
        setWidgets(dev.retrobit.desktop.ui.wm.WidgetMath.remove(widgets.map { it.name }, id.name)
            .mapNotNull { dev.retrobit.desktop.model.WidgetId.of(it) })
    }

    fun toggleWidget(id: dev.retrobit.desktop.model.WidgetId) {
        setWidgets(dev.retrobit.desktop.ui.wm.WidgetMath.toggle(widgets.map { it.name }, id.name)
            .mapNotNull { dev.retrobit.desktop.model.WidgetId.of(it) })
    }

    fun moveWidget(id: dev.retrobit.desktop.model.WidgetId, delta: Int) {
        setWidgets(dev.retrobit.desktop.ui.wm.WidgetMath.move(widgets.map { it.name }, id.name, delta)
            .mapNotNull { dev.retrobit.desktop.model.WidgetId.of(it) })
    }

    fun resetWidgetList() {
        widgetList = emptyList()
    }

    /** Manual widget sizes, encoded "clock=250,140;calendar=280,420". */
    var widgetSizes: String by stringPref("widgetSizes", "")

    private fun widgetMap(): Map<String, Pair<Int, Int>> = widgetSizes
        .split(';')
        .mapNotNull { part ->
            val (key, value) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val (w, h) = value.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            val width = w.trim().toIntOrNull() ?: return@mapNotNull null
            val height = h.trim().toIntOrNull() ?: return@mapNotNull null
            key.trim() to (width to height)
        }
        .toMap()

    /** Width in dp of a widget: the manual size when set, else the shared widget width. */
    fun widgetWidth(id: dev.retrobit.desktop.model.WidgetId): Int =
        widgetMap()[id.name]?.first ?: widgetWidth

    /** Height in dp, or 0 for "as tall as the content needs". */
    fun widgetHeight(id: dev.retrobit.desktop.model.WidgetId): Int =
        widgetMap()[id.name]?.second ?: id.defaultHeight

    fun setWidgetSize(id: dev.retrobit.desktop.model.WidgetId, width: Int, height: Int) {
        val map = widgetMap().toMutableMap()
        map[id.name] = width.coerceIn(160, 900) to height.coerceIn(0, 1600)
        widgetSizes = map.entries.joinToString(";") { "${it.key}=${it.value.first},${it.value.second}" }
    }

    fun resetWidgetSize(id: dev.retrobit.desktop.model.WidgetId) {
        val map = widgetMap().toMutableMap()
        map.remove(id.name)
        widgetSizes = map.entries.joinToString(";") { "${it.key}=${it.value.first},${it.value.second}" }
    }

    fun resetWidgets() {
        widgetSizes = ""
        widgetWidth = 250
    }

    /** Moves a pinned dock / sidebar app to [index]; drag-to-reorder lives in the shells. */
    fun moveDockPin(pkg: String, index: Int) {
        pinnedDock = dev.retrobit.desktop.ui.wm.DockMath.move(pinnedDock, pkg, index)
    }

    /** Same, for the desktop pin list (used when an icon is dropped from the dock onto the grid). */
    fun moveDesktopPin(pkg: String, index: Int) {
        pinnedDesktop = dev.retrobit.desktop.ui.wm.DockMath.move(pinnedDesktop, pkg, index)
    }

    fun iconCell(pkg: String): Pair<Int, Int>? = parseCells()[pkg]

    fun setIconCell(pkg: String, col: Int, row: Int) {
        val map = parseCells().toMutableMap()
        map[pkg] = col to row
        iconCells = map.entries.joinToString(";") { "${it.key}=${it.value.first},${it.value.second}" }
    }

    fun clearIconCells() {
        iconCells = ""
    }

    /** Forgets the stored cell of one app, so the grid can drop it into the first free cell. */
    fun clearIconCell(pkg: String) {
        val map = parseCells().toMutableMap()
        if (map.remove(pkg) == null) return
        iconCells = map.entries.joinToString(";") { "${it.key}=${it.value.first},${it.value.second}" }
    }

    private fun parseCells(): Map<String, Pair<Int, Int>> {
        val raw = iconCells
        if (raw.isBlank()) return emptyMap()
        return raw.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val pkg = part.substring(0, eq)
            val rc = part.substring(eq + 1).split(',')
            if (rc.size != 2) return@mapNotNull null
            val col = rc[0].toIntOrNull() ?: return@mapNotNull null
            val row = rc[1].toIntOrNull() ?: return@mapNotNull null
            pkg to (col to row)
        }.toMap()
    }

    init {
        // the calendar widget got simpler (month grid + a few agenda lines), so a stored height of
        // exactly the old 420dp default is dropped once; any other manual size is left alone
        if (!sp.getBoolean("migrated_180", false)) {
            val raw = widgetSizes
            if (raw.isNotBlank()) {
                widgetSizes = raw.split(';')
                    .filterNot { it.startsWith("calendar=") && it.endsWith(",420") }
                    .joinToString(";")
            }
            sp.edit().putBoolean("migrated_180", true).apply()
        }
    }

    private fun listPref(key: String): PrefState<List<String>> {
        val raw = sp.getString(key, "") ?: ""
        val initial = raw.split('\n').filter { it.isNotBlank() }
        return PrefState(initial) { sp.edit().putString(key, it.joinToString("\n")).apply() }
    }
}
