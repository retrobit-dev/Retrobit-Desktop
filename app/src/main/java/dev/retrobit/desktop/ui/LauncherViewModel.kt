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
import dev.retrobit.desktop.data.SysSnapshot
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
     * Opens an app. With "floating apps" on (the default) the app gets a desktop window: the window
     * is placed on this workspace and the app is sent to exactly those bounds, so on freeform-capable
     * tablets it really floats above the desktop. With the setting off the app opens full screen.
     */
    fun launch(app: AppEntry) {
        if (settings.floatingApps) {
            openAppWindow(app)
        } else {
            launchFullScreen(app)
        }
        startMenu = false
        launchpad = false
        spotlight = false
    }

    /** The application context, used by the windowed-launch helpers. */
    val appContext: android.content.Context get() = getApplication()

    /** Opens the wrapper window for [app] and sends the app to that rectangle when possible. */
    fun openAppWindow(app: AppEntry) {
        val win = wm.openAppWindow(app) ?: return
        appWindows.markLaunched(win.id)
        sendAppToWindow(app, win)
    }

    /** Re-places the app of an existing window at the window's current bounds. */
    fun placeAppWindow(win: dev.retrobit.desktop.ui.wm.WinState) {
        val app = win.app ?: return
        appWindows.markLaunched(win.id)
        sendAppToWindow(app, win)
    }

    /** Brings the app of an app window back to the front (floating where supported). */
    fun focusAppWindow(win: dev.retrobit.desktop.ui.wm.WinState) {
        val app = win.app ?: return
        if (dev.retrobit.desktop.data.WindowedLaunch.supportsFreeform(appContext)) {
            sendAppToWindow(app, win)
        } else {
            launchFullScreen(app)
        }
    }

    /** Launches the app into the rectangle of its desktop window. */
    private fun sendAppToWindow(app: AppEntry, win: dev.retrobit.desktop.ui.wm.WinState) {
        val bounds = android.graphics.Rect(
            win.x.toInt(),
            win.y.toInt(),
            (win.x + win.width).toInt(),
            (win.y + win.height).toInt(),
        )
        val options = dev.retrobit.desktop.data.WindowedLaunch.activityOptionsFor(appContext, bounds)
        val ok = repo.launch(app, bounds, options)
        if (!ok) shell.print("error: unable to launch ${app.label}", TerminalShell.Kind.ERR)
        if (options == null) {
            shell.print("${app.label}: opened full screen (no floating window support on this device)")
        }
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
        openMenu(
            MenuRequest(
                title = app.label,
                subtitle = app.pkg + if (app.isWorkProfile) "  [work profile]" else "",
                anchors = anchor,
                items = listOf(
                    MenuItem("Open (floating window)", GlyphOpen(), onSelect = { launch(app) }),
                    MenuItem("Open full screen", GlyphOpen(), onSelect = { launchFullScreen(app) }),
                    MenuItem("Show in app drawer", null, onSelect = { openAppDrawerWindow(app) }),
                    MenuItem(if (inDock) "Remove from dock" else "Add to dock", GlyphPin(), onSelect = { toggleDockPin(app) }),
                    MenuItem(if (onDesktop) "Remove from desktop" else "Pin to desktop", GlyphPin(), onSelect = { toggleDesktopPin(app) }),
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
