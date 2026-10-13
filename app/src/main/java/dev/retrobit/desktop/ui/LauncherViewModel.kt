package dev.retrobit.desktop.ui

import android.app.Application
import android.content.Intent
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
    val wm = WindowManager(repo, settings)
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
    fun launch(app: AppEntry) {
        val ok = repo.launch(app)
        if (!ok) shell.print("error: unable to launch ${app.label}", TerminalShell.Kind.ERR)
        startMenu = false
        launchpad = false
        spotlight = false
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

    fun openAppWindow(app: AppEntry) {
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
                    MenuItem("Open", GlyphOpen(), onSelect = { launch(app) }),
                    MenuItem("Open in window", GlyphOpen(), onSelect = { openAppWindow(app) }),
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
                    MenuItem("Settings", dev.retrobit.desktop.ui.components.Glyph.SETTINGS, onSelect = { openWindow(WinKind.SETTINGS) }),
                    MenuItem(
                        if (settings.tiling) "Disable tiling (Linux style)" else "Enable tiling (Linux style)",
                        dev.retrobit.desktop.ui.components.Glyph.GRID,
                        onSelect = { setTiling(!settings.tiling) },
                    ),
                    MenuItem("Close all windows", null, onSelect = { wm.closeAll() }),
                ),
            )
        )
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

    fun showBootScreenAgain() {
        bootedThisProcess = false
        bootDone = false
        boot()
    }

    fun openIntent(intent: Intent?) = Unit

    companion object {
        private var bootedThisProcess = false
    }
}
