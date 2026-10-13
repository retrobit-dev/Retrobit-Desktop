package dev.retrobit.desktop.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.retrobit.desktop.data.AppRepository
import dev.retrobit.desktop.data.Settings
import dev.retrobit.desktop.data.SystemInfo
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.PaletteId
import dev.retrobit.desktop.model.WallPattern
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.apps.TerminalShell
import dev.retrobit.desktop.ui.components.MenuItem
import dev.retrobit.desktop.ui.wm.WindowManager
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MenuRequest(val title: String, val subtitle: String?, val anchors: Offset, val items: List<MenuItem>)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    val repo = AppRepository(app)
    val calendar = dev.retrobit.desktop.data.CalendarStore(app)
    val wm = WindowManager(repo, settings)
    /** Which desktop windows wrap a launched third-party app. */
    val appWindows = dev.retrobit.desktop.ui.wm.AppWindows()
    val shell = TerminalShell(this)

    var snapshot by mutableStateOf(SystemInfo.snapshot(app))
        private set
    var now by mutableStateOf(System.currentTimeMillis())
        private set

    var menu by mutableStateOf<MenuRequest?>(null)
    var spotlight by mutableStateOf(false)
    var startMenu by mutableStateOf(false)
    var launchpad by mutableStateOf(false)
    /**
     * The icon the user tapped once: it is drawn inverted (like a selected desktop icon) and opens on
     * the second tap, on Enter, or from its context menu.
     */
    var selectedApp by mutableStateOf<AppEntry?>(null)

    /** True while the "add app or widget to the desktop" panel is open. */
    var appPicker by mutableStateOf(false)

    /** Safe-area padding of the shell in px, kept up to date by the desktop (camera hole + corners). */
    var desktopInsets by mutableStateOf(dev.retrobit.desktop.ui.theme.DesktopInsets.Zero)

    /** The rectangle the last app was placed at, shown in the app window for transparency. */
    var placedRect by mutableStateOf<dev.retrobit.desktop.ui.wm.Placement?>(null)

    /** Bumped when a frame has been moved / resized; the UI debounces and re-places after it. */
    var frameSettleTick by mutableStateOf(0)

    /**
     * The rectangle last sent to the system per window: an unchanged frame is never re-sent, so a
     * re-layout or a tiling pass cannot drag an app back to the front for nothing.
     */
    private val placedByWindow = HashMap<Int, dev.retrobit.desktop.ui.wm.Placement>()

    fun selectIcon(app: AppEntry) {
        selectedApp = app
    }

    fun clearSelection() {
        selectedApp = null
    }

    /** Opens whatever is currently selected (Enter, or a second tap on the icon). */
    fun openSelected() {
        selectedApp?.let { launch(it) }
    }

    /**
     * Puts [app] on the home screen: it is pinned to the desktop list and its stored grid cell is
     * cleared, so the icon lands in the first free cell instead of on top of another icon.
     */
    fun addAppToDesktop(app: AppEntry) {
        if (!settings.pinnedDesktop.contains(app.pkg)) {
            settings.pinnedDesktop = settings.pinnedDesktop + app.pkg
            shell.print("${app.label} added to the desktop")
        }
        settings.clearIconCell(app.pkg)
        selectedApp = app
    }

    /** Bumped on demand so every icon is rendered again (see IconCache.clear / "re-ink icons"). */
    var iconEpoch by mutableStateOf(0)

    /** Drops every cached icon bitmap and makes the whole desktop draw its icons again. */
    fun reinkIcons() {
        dev.retrobit.desktop.data.IconCache.clear()
        iconEpoch++
    }

    /** True while the home screen is in widget-editing mode (dashed frames, sizing, add/remove). */
    var widgetEdit by mutableStateOf(false)
    var booting by mutableStateOf(false)
    var bootDone by mutableStateOf(false)
    val bootLines = mutableStateListOf<String>()

    /** Wired by the activity so the settings app can ask to become the default launcher. */
    var requestDefaultLauncher: (() -> Unit)? = null
    var requestRoleState: (() -> Boolean)? = null

    /** Processed desktop background when the user chooses the device wallpaper or their own image. */
    var wallpaperImage by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        private set
    var wallpaperBusy by mutableStateOf(false)
        private set
    var wallpaperNote by mutableStateOf("")
        private set

    private val wallpaperRepo = dev.retrobit.desktop.data.WallpaperRepository(app)

    fun setWallpaperSource(source: dev.retrobit.desktop.data.WallpaperSource) {
        settings.wallpaperSource = source.name
        refreshWallpaper()
    }

    fun setWallpaperTone(tone: dev.retrobit.desktop.data.WallpaperTone) {
        settings.wallpaperTone = tone.name
        refreshWallpaper()
    }

    fun setWallpaperUri(uri: android.net.Uri) {
        wallpaperRepo.takePersistable(uri)
        settings.wallpaperUri = uri.toString()
        settings.wallpaperSource = dev.retrobit.desktop.data.WallpaperSource.IMAGE.name
        refreshWallpaper()
    }

    /** Loads + e-paper-ifies the background. Safe to call often; it is cheap when unchanged. */
    fun refreshWallpaper(aspect: Float = 1.6f) {
        val source = settings.wallpaperSourceEnum
        if (source == dev.retrobit.desktop.data.WallpaperSource.PATTERN) {
            wallpaperImage = null
            wallpaperNote = ""
            return
        }
        viewModelScope.launch {
            wallpaperBusy = true
            val tone = settings.wallpaperToneEnum
            val palette = dev.retrobit.desktop.ui.theme.Palettes.of(settings.palette)
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val raw = when (source) {
                    dev.retrobit.desktop.data.WallpaperSource.DEVICE -> wallpaperRepo.deviceWallpaper()
                    dev.retrobit.desktop.data.WallpaperSource.IMAGE ->
                        settings.wallpaperUri.takeIf { it.isNotBlank() }?.let {
                            runCatching { android.net.Uri.parse(it) }.getOrNull()?.let { uri -> wallpaperRepo.loadImage(uri) }
                        }
                    else -> null
                }
                if (raw == null) null
                else {
                    val cropped = wallpaperRepo.cropToAspect(raw, aspect)
                    wallpaperRepo.render(cropped, palette.paper, palette.ink, tone, cells = 260)
                }
            }
            wallpaperImage = result
            wallpaperNote = when {
                result != null -> ""
                source == dev.retrobit.desktop.data.WallpaperSource.DEVICE ->
                    "the system would not share the wallpaper — pick an image instead"
                else -> "no image selected yet"
            }
            wallpaperBusy = false
        }
    }

    /** Wired by the activity: summon or hide the internal (on-screen) keyboard. */
    var toggleKeyboard: (() -> Unit)? = null

    private var seeded = false

    init {
        // every move, resize, maximize or re-layout of a frame ends up here: that is the single
        // place where the app inside the frame has to follow (see onAppFrameSettled)
        wm.onGeometrySettled = { win -> onAppFrameSettled(win) }
        repo.registerChanges { viewModelScope.launch { repo.reload() } }
        viewModelScope.launch { repo.reload(); seedDefaults() }
        viewModelScope.launch {
            while (true) {
                snapshot = SystemInfo.snapshot(getApplication())
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    private fun seedDefaults() {
        if (seeded) return
        seeded = true
        if (!settings.defaultsSeeded) {
            settings.defaultsSeeded = true
            val apps = repo.apps.value
            val browser = repo.defaultBrowser()
            val picks = ArrayList<String>()
            browser?.let { p -> apps.firstOrNull { it.pkg.contains("browser", true) || it.pkg.startsWith("com.android.chrome") || it.pkg.startsWith("com.huawei.browser") || it.pkg == p }?.let { picks += it.pkg } }
            listOf("clock", "calendar", "gallery", "camera", "files", "settings").forEach { hint ->
                if (picks.size < 6) apps.firstOrNull { a -> a.pkg.contains(hint, true) && picks.none { it == a.pkg } }?.let { picks += it.pkg }
            }
            settings.pinnedDock = picks
        }
    }

    val apps: List<AppEntry> get() = repo.apps.value

    fun appByPackage(pkg: String): AppEntry? = repo.apps.value.firstOrNull { it.pkg == pkg }
    fun appsByPackages(list: List<String>): List<AppEntry> =
        list.mapNotNull { pkg -> repo.apps.value.firstOrNull { it.pkg == pkg } }

    // ── launching ────────────────────────────────────────────────────────────────
    /**
     * Opens an app **wrapped in its own desktop window** — the same way Settings, Terminal, Notes and
     * the calendar open: a frame with a title bar that can be moved, resized, minimized, maximized
     * and restored. The app is sent to the frame's content rectangle, so on a tablet that can float
     * windows it really sits inside the box. With "float apps inside their frame" turned off the app
     * itself opens full screen while the frame stays its entry in the taskbar and the workspaces.
     */
    fun launch(app: AppEntry) {
        selectedApp = null
        openAppWindow(app)
        startMenu = false
        launchpad = false
        spotlight = false
    }

    /** The application context, used by the windowed-launch helpers. */
    val appContext: android.content.Context get() = getApplication()

    /**
     * Opens the wrapper window for [app] and sends the app to that rectangle when possible. An app
     * that already has a window is brought back to the front instead of opening a second twin.
     */
    fun openAppWindow(app: AppEntry) {
        // the launch counts drive the drawer order: most used first, then A-Z
        settings.noteLaunch(app.pkg)
        val existing = wm.windows.firstOrNull {
            it.kind == WinKind.APPWINDOW && it.app?.pkg == app.pkg && it.desktop == wm.desktop
        }
        if (existing != null) {
            wm.focus(existing.id)
            appWindows.markLaunched(existing.id)
            sendAppToWindow(app, existing)
            return
        }
        val win = wm.openAppWindow(app) ?: return
        appWindows.markLaunched(win.id)
        sendAppToWindow(app, win)
    }

    /** The drawer order: the apps launched most often first, the rest alphabetical (see AppUsage). */
    fun appsByUsage(): List<AppEntry> = dev.retrobit.desktop.data.AppUsage.order(
        items = apps,
        counts = usageCounts(),
        pkg = { it.pkg },
        label = { it.label },
    )

    /** How often [pkg] has been opened from this launcher. */
    fun usageOf(pkg: String): Int = usageCounts()[pkg] ?: 0

    /** Launch counts per package, parsed once per read (the preference is a single string). */
    fun usageCounts(): Map<String, Int> = dev.retrobit.desktop.data.AppUsage.parse(settings.appUsage)

    /**
     * "Place here": asks the system to put the app of [win] inside the frame's content area.
     *
     * [force] clears and recreates the app's task, which is the only public way to move an app that
     * is already running into a new rectangle (Android offers no API to move another app's task).
     */
    fun placeAppWindow(win: dev.retrobit.desktop.ui.wm.WinState, force: Boolean = false) {
        val app = win.app ?: return
        appWindows.markLaunched(win.id)
        placedRect = null
        // an explicit press of "place here" always asks for the floating rectangle
        sendAppToWindow(app, win, force = force, quiet = false, float = true)
    }

    /** Brings the app of an app window back to the front, inside its frame when possible. */
    fun focusAppWindow(win: dev.retrobit.desktop.ui.wm.WinState) {
        val app = win.app ?: return
        appWindows.markLaunched(win.id)
        sendAppToWindow(app, win, quiet = true)
    }

    /** Screen size in px, used to clamp the rectangle we send to the system. */
    private fun screenSize(): Pair<Int, Int> {
        val dm = appContext.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    /**
     * The screen rectangle the app should occupy for [win]: the frame's content area — below the
     * title bar, inside the border — plus the safe-area padding of the launcher. In screen pixels,
     * which is what `ActivityOptions.setLaunchBounds` expects.
     */
    fun placementFor(win: dev.retrobit.desktop.ui.wm.WinState): dev.retrobit.desktop.ui.wm.Placement? {
        val density = appContext.resources.displayMetrics.density
        val (sw, sh) = screenSize()
        return dev.retrobit.desktop.ui.wm.AppWindowMath.contentRect(
            frameX = win.x,
            frameY = win.y,
            frameW = win.width,
            frameH = win.height,
            titlePx = dev.retrobit.desktop.ui.wm.AppWindowMath.titleHeightPx(
                TITLE_BAR_DP, settings.uiScale, density,
            ),
            borderPx = dev.retrobit.desktop.ui.wm.AppWindowMath.borderPx(FRAME_BORDER_DP, density),
            insetLeft = desktopInsets.left,
            insetTop = desktopInsets.top,
            screenW = sw,
            screenH = sh,
        )
    }

    /** Launches the app into the rectangle of its desktop window. */
    private fun sendAppToWindow(
        app: AppEntry,
        win: dev.retrobit.desktop.ui.wm.WinState,
        force: Boolean = false,
        quiet: Boolean = false,
        float: Boolean = settings.floatingApps,
        onlyIfChanged: Boolean = false,
    ) {
        if (!float) {
            // the frame is there either way; with floating off the app itself opens full screen
            repo.launch(app)
            placedRect = null
            if (!quiet) shell.print("${app.label}: opened full screen (floating windows are off)")
            return
        }
        val placement = placementFor(win)
        if (onlyIfChanged && placement != null && placedByWindow[win.id] == placement) return
        val options = dev.retrobit.desktop.data.WindowedLaunch.activityOptionsFor(placement)
        placedRect = placement
        if (placement == null) {
            // the frame itself is too small (or too much of it is chrome): no window to ask for
            repo.launch(app)
            if (!quiet) shell.print("${app.label}: window frame is too small, opened full screen")
            return
        }
        val ok = repo.launch(app, placement.toRect(), options, force)
        if (!ok) {
            shell.print("error: unable to launch ${app.label}", TerminalShell.Kind.ERR)
            return
        }
        placedByWindow[win.id] = placement
        if (quiet) return
        shell.print(
            (if (force) "${app.label}: restarting into " else "${app.label}: placed at ") +
                "$placement px (frame ${win.x.toInt()},${win.y.toInt()})",
        )
    }

    /**
     * Called when a window frame has been dragged or resized: the app inside it follows the frame
     * (when "keep the app in its frame" is on and the device can float windows).
     */
    fun onAppFrameSettled(win: dev.retrobit.desktop.ui.wm.WinState) {
        if (win.kind != dev.retrobit.desktop.model.WinKind.APPWINDOW) return
        if (!settings.appWindowFollow) return
        if (!appWindows.isRunning(win.id)) return
        frameSettleTick++
    }

    /** Runs the debounced re-placement after a frame drag / resize (see [onAppFrameSettled]). */
    fun refitAppWindows() {
        // nothing to place when the tablet is not asked to float apps inside their frames
        if (!settings.floatingApps) return
        wm.windows
            .filter {
                it.kind == dev.retrobit.desktop.model.WinKind.APPWINDOW &&
                    !it.minimized && appWindows.isRunning(it.id)
            }
            .forEach { win ->
                val app = win.app ?: return@forEach
                // only frames that really changed are sent again (see placedByWindow)
                sendAppToWindow(app, win, quiet = true, onlyIfChanged = true)
            }
    }

    /**
     * Keeps the app-window bookkeeping in sync with the open windows: called by the desktop whenever
     * a window is closed, so a recycled window id can never inherit an old rectangle.
     */
    fun retainAppWindows(ids: Set<Int>) {
        appWindows.retain(ids)
        placedByWindow.keys.retainAll(ids)
    }

    /** Plain launch, no window involved. */
    fun launchFullScreen(app: AppEntry) {
        val ok = repo.launch(app)
        if (!ok) shell.print("error: unable to launch ${app.label}", TerminalShell.Kind.ERR)
    }

    fun launchResolved(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val match = repo.apps.value.firstOrNull { it.label.lowercase() == q }
            ?: repo.apps.value.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: repo.apps.value.firstOrNull { it.pkg.contains(q) }
            ?: return false
        launch(match)
        return true
    }

    fun openWindow(kind: WinKind) {
        wm.open(kind)
        startMenu = false
        launchpad = false
        spotlight = false
    }

    /** Opens the Applications window showing [app]'s entry (used by the app context menu). */
    fun openAppDrawerWindow(app: AppEntry) {
        wm.openApp(app)
        startMenu = false
        launchpad = false
        spotlight = false
    }

    // ── pinning ──────────────────────────────────────────────────────────────────
    fun toggleDockPin(app: AppEntry) {
        val list = settings.pinnedDock.toMutableList()
        if (!list.remove(app.pkg)) list += app.pkg
        settings.pinnedDock = list
    }

    fun toggleDesktopPin(app: AppEntry) {
        val list = settings.pinnedDesktop.toMutableList()
        if (!list.remove(app.pkg)) list += app.pkg
        settings.pinnedDesktop = list
    }

    /** Puts an app in the dock (or sidebar / taskbar) at [index], taking it off the desktop. */
    fun moveToDock(app: AppEntry, index: Int) {
        settings.pinnedDesktop = settings.pinnedDesktop.filterNot { it == app.pkg }
        settings.moveDockPin(app.pkg, index)
    }

    /** Puts an app on the desktop grid, taking it off the dock. */
    fun moveToDesktop(app: AppEntry) {
        settings.pinnedDock = settings.pinnedDock.filterNot { it == app.pkg }
        if (!settings.pinnedDesktop.contains(app.pkg)) settings.pinnedDesktop = settings.pinnedDesktop + app.pkg
    }

    fun unpin(app: AppEntry) {
        settings.pinnedDock = settings.pinnedDock.filterNot { it == app.pkg }
        settings.pinnedDesktop = settings.pinnedDesktop.filterNot { it == app.pkg }
    }

    // ── appearance ───────────────────────────────────────────────────────────────
    fun setStyle(style: DesktopStyle) { settings.style = style; shell.print("desktop style -> ${style.label}") }
    fun setPalette(p: PaletteId) { settings.palette = p }
    fun setWallpaper(w: WallPattern) { settings.wallpaper = w }
    fun setIconMode(m: IconMode) { settings.iconMode = m }
    fun invertPalette() {
        // handled in the shell (needs the palette object); kept here for the terminal command
        invertRequested++
    }
    var invertRequested by mutableStateOf(0)
        private set

    /** Invert the current palette (paper <-> ink), e-paper style dark mode. */
    fun invertTheme() { invertRequested++ }

    fun setTiling(on: Boolean) { wm.retile(on) }

    fun switchDesktop(index: Int) { wm.switchDesktop(index) }
    fun moveFocusedToDesktop(index: Int) { wm.moveFocusedToDesktop(index) }
    fun setWorkspaceCount(count: Int) {
        settings.workspaceCount = count.coerceIn(1, 4)
        if (wm.desktop > settings.workspaceCount - 1) wm.switchDesktop(settings.workspaceCount - 1)
    }

    // ── menus ────────────────────────────────────────────────────────────────────
    fun openMenu(request: MenuRequest) { menu = request }
    fun dismissMenu() { menu = null }

    fun appMenu(app: AppEntry, anchor: Offset) {
        val inDock = settings.pinnedDock.contains(app.pkg)
        val onDesktop = settings.pinnedDesktop.contains(app.pkg)
        selectedApp = app
        openMenu(
            MenuRequest(
                title = app.label,
                subtitle = app.pkg + if (app.isWorkProfile) "  [work profile]" else "",
                anchors = anchor,
                items = listOf(
                    MenuItem("Open in its window", GlyphOpen(), onSelect = { launch(app) }),
                    MenuItem("Open full screen", GlyphOpen(), onSelect = { launchFullScreen(app) }),
                    MenuItem("Show in app drawer", null, onSelect = { openAppDrawerWindow(app) }),
                    MenuItem(if (inDock) "Remove from dock" else "Add to dock", GlyphPin(), onSelect = { toggleDockPin(app) }),
                    MenuItem("Add apps or widgets to the desktop…", GlyphPin(), onSelect = { appPicker = true }),
                    MenuItem(if (onDesktop) "Remove from desktop" else "Add to desktop", GlyphPin(), onSelect = {
                        if (onDesktop) toggleDesktopPin(app) else addAppToDesktop(app)
                    }),
                    MenuItem("App info", null, onSelect = { repo.openAppInfo(app) }),
                    MenuItem("Uninstall", null, onSelect = { repo.uninstall(app) }),
                ),
            )
        )
    }

    // Small helper glyph aliases keep menu code readable.
    private fun GlyphOpen() = dev.retrobit.desktop.ui.components.Glyph.FOLDER
    private fun GlyphPin() = dev.retrobit.desktop.ui.components.Glyph.LOGO

    fun desktopMenu(anchor: Offset) {
        openMenu(
            MenuRequest(
                title = "Desktop",
                subtitle = settings.style.osName,
                anchors = anchor,
                items = listOf(
                    MenuItem("Add apps or widgets to the desktop…", dev.retrobit.desktop.ui.components.Glyph.GRID, onSelect = {
                        appPicker = true
                    }),
                    MenuItem("Applications", dev.retrobit.desktop.ui.components.Glyph.GRID, onSelect = { openWindow(WinKind.APPS) }),
                    MenuItem("Terminal", dev.retrobit.desktop.ui.components.Glyph.TERMINAL, onSelect = { openWindow(WinKind.TERMINAL) }),
                    MenuItem("System monitor", dev.retrobit.desktop.ui.components.Glyph.SYSMON, onSelect = { openWindow(WinKind.SYSMON) }),
                    MenuItem("Notes", dev.retrobit.desktop.ui.components.Glyph.NOTES, onSelect = { openWindow(WinKind.NOTES) }),
                    MenuItem("Calendar", dev.retrobit.desktop.ui.components.Glyph.CALENDAR, onSelect = { openWindow(WinKind.CALENDAR) }),
                    MenuItem("Settings", dev.retrobit.desktop.ui.components.Glyph.SETTINGS, onSelect = { openWindow(WinKind.SETTINGS) }),
                    MenuItem(
                        if (settings.tiling) "Disable tiling (Linux style)" else "Enable tiling (Linux style)",
                        dev.retrobit.desktop.ui.components.Glyph.GRID,
                        onSelect = { setTiling(!settings.tiling) },
                    ),
                    MenuItem(
                        if (settings.snapWindows) "Disable edge snapping" else "Enable edge snapping",
                        dev.retrobit.desktop.ui.components.Glyph.COMPUTER,
                        onSelect = { settings.snapWindows = !settings.snapWindows },
                    ),
                    MenuItem("Desktops / workspaces…", dev.retrobit.desktop.ui.components.Glyph.GRID, onSelect = {
                        workspaceMenu(Offset(150f, 220f))
                    }),
                    MenuItem(
                        if (widgetEdit) "Done editing widgets" else "Edit widgets (hold a widget)",
                        dev.retrobit.desktop.ui.components.Glyph.GRID,
                        onSelect = { widgetEdit = !widgetEdit },
                    ),
                    // widget management: add, remove, or size them by hand right on the desktop
                    *dev.retrobit.desktop.model.WidgetId.entries.map { id ->
                        val on = settings.isWidgetEnabled(id)
                        MenuItem(
                            if (on) "Remove widget: ${id.label}" else "Add widget: ${id.label}",
                            null,
                            onSelect = { if (on) settings.removeWidget(id) else settings.addWidget(id) },
                        )
                    }.toTypedArray(),
                    MenuItem("Reset widget sizes", null, onSelect = {
                        settings.resetWidgets()
                        shell.print("widget sizes reset to ${settings.widgetWidth}dp wide")
                    }),
                    MenuItem("Auto arrange icons", dev.retrobit.desktop.ui.components.Glyph.GRID, onSelect = {
                        settings.clearIconCells()
                        shell.print("desktop icons auto arranged")
                    }),
                    MenuItem(
                        if (settings.showGrid) "Hide placement grid" else "Show placement grid",
                        null,
                        onSelect = { settings.showGrid = !settings.showGrid },
                    ),
                    MenuItem("Wallpaper & icons…", dev.retrobit.desktop.ui.components.Glyph.SETTINGS, onSelect = {
                        openWindow(WinKind.SETTINGS)
                    }),
                    MenuItem("Close all windows on this desktop", null, onSelect = { wm.closeAll() }),
                ),
            )
        )
    }

    fun workspaceMenu(anchor: Offset) {
        val items = (0 until wm.desktopCount).map { index ->
            MenuItem(
                label = "Desktop ${index + 1}" + if (index == wm.desktop) "   (current)" else "",
                glyph = if (index == wm.desktop) dev.retrobit.desktop.ui.components.Glyph.GRID else null,
                onSelect = { switchDesktop(index) },
            )
        } + (0 until wm.desktopCount).map { index ->
            MenuItem(
                label = "   send window to desktop ${index + 1}",
                enabled = wm.focused != null && index != wm.desktop,
                onSelect = { moveFocusedToDesktop(index) },
            )
        }
        openMenu(MenuRequest("Desktops", "${wm.onScreen.size} windows on desktop ${wm.desktop + 1}", anchor, items))
    }

    // ── boot ─────────────────────────────────────────────────────────────────────
    fun boot() {
        if (bootedThisProcess) { bootDone = true; return }
        bootedThisProcess = true
        if (!settings.bootSequence) { bootDone = true; return }
        viewModelScope.launch {
            booting = true
            bootLines.clear()
            val lines = listOf(
                "Retrobit BIOS v1.0  (c) retrobit-dev",
                "detecting e-paper panel .................. OK",
                "cpu: arm64 / 8 cores .................... OK",
                "framebuffer 2560x1600 / 16 grays ........ OK",
                "mounting /dev/paper0 .................... OK",
                "loading window manager: " + settings.style.tagline.split(" · ").first(),
                "starting retrobit-desktop.service ....... OK",
                "",
                settings.style.osName + " ready.  welcome, ${settings.userName}.",
            )
            lines.forEach { line ->
                bootLines += line
                delay(if (line.isEmpty()) 60 else 110)
            }
            delay(320)
            booting = false
            bootDone = true
            shell.print("retrobit-desktop 1.0 — ${settings.style.label} style active. type `help`.")
        }
    }

    private var lastRefresh = 0L

    /** Re-enumerate installed apps (called when the launcher comes back to the foreground). */
    fun refreshApps(force: Boolean = false) {
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastRefresh < 5_000) return
        lastRefresh = nowMs
        viewModelScope.launch {
            repo.reload()
            if (settings.pinnedDock.isEmpty()) {
                settings.defaultsSeeded = false
                seedDefaults()
            }
        }
    }

    fun showBootScreenAgain() {
        bootedThisProcess = false
        bootDone = false
        boot()
    }

    companion object {
        private var bootedThisProcess = false
    }
}

/** Height of the window title bar in dp, matching WindowFrame's titleH (24 dp bar + 6 dp grip). */
private const val TITLE_BAR_DP = 30f

/** Frame border in dp (2.4 dp while focused, 1.4 dp otherwise). */
private const val FRAME_BORDER_DP = 2.4f
