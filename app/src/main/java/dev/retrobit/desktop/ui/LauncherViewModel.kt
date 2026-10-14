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
import dev.retrobit.desktop.shell.ChromeService
import dev.retrobit.desktop.shell.CyberdeckChrome
import dev.retrobit.desktop.ui.apps.TerminalShell
import dev.retrobit.desktop.ui.components.MenuItem
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MenuRequest(val title: String, val subtitle: String?, val anchors: Offset, val items: List<MenuItem>)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    val repo = AppRepository(app)
    val calendar = dev.retrobit.desktop.data.CalendarStore(app)
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

    /**
     * Floating windows of **our own** apps: terminal, settings, notes, the app drawer...
     *
     * Opening one of them puts a framed box on the desktop - movable, resizable from eight handles,
     * minimizable, maximizable and closable - instead of taking the whole screen straight away.
     * Third-party apps are not part of this: they keep opening full screen with the cyberdeck chrome
     * drawn over them.
     */
    val wm = dev.retrobit.desktop.ui.wm.WindowManager(settings)

    /**
     * The app of ours that is maximized right now (`null` while every window floats). A maximized
     * window owns the screen and is drawn by [dev.retrobit.desktop.ui.desktop.ShellHost], the same
     * full-screen frame the chrome draws over somebody else's app.
     */
    val shellOpen: WinKind? get() = wm.maximizedWindow?.kind

    /** Which page of desktop icons and widgets is showing (see [switchDesktop]). */
    var desktop by mutableStateOf(0)
        private set

    /** Pages of desktop icons and widgets ("workspaces"). */
    val desktopCount: Int get() = settings.workspaceCount.coerceIn(1, 4)

    /**
     * The app we opened last: while it owns the screen this is what the rails describe, and what
     * the per-app rules are checked against.
     */
    var frontApp: AppEntry? by mutableStateOf(null)
        private set

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
                    // a native theme keeps the picture in colour; the e-paper palettes print it
                    if (palette.isNative) wallpaperRepo.renderColor(cropped)
                    else wallpaperRepo.render(cropped, palette.paper, palette.ink, tone, cells = 260)
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

    // ── cyberdeck chrome ─────────────────────────────────────────────────────────
    // The frame drawn *over* an app that the system opened full screen: see shell/ChromeService.
    // The rails read their content from CyberdeckChrome, which is filled in by syncChrome().

    /** The package whose rails the user hid with the eye key (a "not now", not a setting). */
    private var chromeMutedFor: String? = null

    /** A short message the rails print for two seconds (pin, unpin, mode switch...). */
    private var chromeFlash: String = ""
    private var chromeFlashAt = 0L

    /** Shows [message] on the rail that just did something, for two seconds. */
    private fun flashChrome(message: String) {
        chromeFlash = message
        chromeFlashAt = System.currentTimeMillis()
        shell.print(message)
        syncChrome()
    }

    /**
     * Bumped whenever the chrome state may have changed (permission granted, service started or
     * stopped). The Settings section reads it, so its status line is fresh the moment the user comes
     * back from the system permission screen.
     */
    var chromeRev: Int by mutableStateOf(0)
        private set

    /** True when the overlay ("display over other apps") permission is granted. */
    fun chromeAllowed(): Boolean =
        runCatching { android.provider.Settings.canDrawOverlays(appContext) }.getOrDefault(false)

    /** One line for the Settings screen: permission + service state + what the rails will draw. */
    fun chromeStatusLine(): String {
        val permitted = if (chromeAllowed()) "display over other apps: granted" else "display over other apps: NOT granted"
        val service = when {
            !ChromeService.running -> "chrome: stopped"
            ChromeService.visible -> "chrome: drawing the frame now"
            else -> "chrome: ready"
        }
        return "$permitted | $service | every app opens full screen and the frame is drawn over it"
    }

    /** Turns the chrome on or off; turning it on asks for the permission when it is missing. */
    fun setCyberdeck(on: Boolean) {
        settings.cyberdeckChrome = on
        if (on) {
            if (chromeAllowed()) {
                ChromeService.start(appContext)
                shell.print("cyberdeck chrome on - the frame follows apps that open full screen")
            } else {
                shell.print("cyberdeck chrome needs the \"display over other apps\" permission", TerminalShell.Kind.ERR)
                requestChromePermission()
            }
        } else {
            ChromeService.stop(appContext)
            shell.print("cyberdeck chrome off")
        }
        chromeRev++
        syncChrome()
    }

    /** Opens the system screen where the overlay permission is granted. */
    fun requestChromePermission() = runCatching {
        appContext.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + appContext.packageName),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Shows the chrome for a few seconds while the desktop is on screen, so it can be seen. */
    fun previewChrome(seconds: Int = 8) {
        if (!chromeAllowed()) {
            requestChromePermission()
            shell.print("grant \"display over other apps\", then press preview again", TerminalShell.Kind.ERR)
            return
        }
        ChromeService.preview(appContext, seconds)
        shell.print("cyberdeck chrome preview - ${seconds}s")
    }

    /** The rail's "desktop" key: back to the launcher (which also takes the chrome down). */
    fun goDesktop() = runCatching {
        appContext.startActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Everything the rails draw, pushed into the shared chrome state. Cheap: a few list copies. */
    fun syncChrome() {
        val palette = dev.retrobit.desktop.ui.theme.Palettes.of(settings.palette)
        val front = frontApp
        val dock = dockPkgs()
            .take(6)
            .map { pkg ->
                dev.retrobit.desktop.shell.CyberdeckChrome.Bar(
                    appByPackage(pkg)?.label ?: pkg,
                    "app:$pkg",
                    pkg = pkg,
                    pinned = settings.pinnedDock.contains(pkg),
                )
            }
        CyberdeckChrome.state = dev.retrobit.desktop.shell.CyberdeckChrome.State(
            ink = dev.retrobit.desktop.shell.CyberdeckChrome.Ink(
                paper = palette.paper.toArgb(),
                ink = palette.ink.toArgb(),
                mid = palette.mid.toArgb(),
                accent = palette.accent.toArgb(),
            ),
            title = front?.label ?: "desktop",
            inFrame = front != null,
            desktop = desktop + 1,
            desks = desktopCount,
            dock = dock,
            scale = settings.uiScale.coerceIn(0.6f, 2f),
            topRail = settings.chromeTopRail,
            bottomDock = settings.chromeDock,
            autoHide = settings.chromeAutoHide,
            autoHideSecs = settings.chromeAutoHideSecs.coerceIn(3, 60),
            net = snapshot.net,
            railMode = settings.railMode,
            size = CyberdeckChrome.sizeFactor(settings.chromeSize),
            brightness = settings.brightness,
            flash = chromeFlash.takeIf { System.currentTimeMillis() - chromeFlashAt < 2_500L }.orEmpty(),
        )
    }

    /** Wired once: keeps the chrome state fresh and routes the rail buttons back into the launcher. */
    private fun wireChrome() {
        CyberdeckChrome.onRefresh = { syncChrome() }
        CyberdeckChrome.onFlash = { flashChrome(it) }
        CyberdeckChrome.onHideNow = { hideChromeNow() }
        CyberdeckChrome.onToggleRails = { toggleChromeRails() }
        CyberdeckChrome.onRailMode = { switchRailMode() }
        CyberdeckChrome.onRailHold = { action -> onRailHold(action) }
        CyberdeckChrome.onPanel = { openPanel() }
        dev.retrobit.desktop.shell.PanelChrome.onRefresh = { syncPanel() }
        dev.retrobit.desktop.shell.PanelChrome.onClose = { panelOpen = false }
        // the panel runs in this same process: it calls straight into the view model, so its keys
        // work while another app owns the screen (a broadcast receiver could not: this activity is
        // stopped exactly then, which is when the panel is used)
        dev.retrobit.desktop.shell.PanelChrome.onAction = { action -> onPanelAction(action) }
        CyberdeckChrome.onHome = { goDesktop() }
        CyberdeckChrome.onHideChrome = { setCyberdeck(false) }
        CyberdeckChrome.onOpenPackage = { pkg ->
            val app = appByPackage(pkg)
            if (app != null) {
                launch(app)
            } else {
                runCatching {
                    appContext.packageManager.getLaunchIntentForPackage(pkg)?.let {
                        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(it)
                    }
                }
            }
        }
        syncChrome()
    }

    /**
     * The eye key: hide the rails for the app that is in front right now. Walking into another app
     * brings them back, so this is a "not now" and never a setting.
     */
    private fun hideChromeNow() {
        chromeMutedFor = frontApp?.pkg
        runCatching { ChromeService.hide(appContext) }
        flashChrome("rails hidden for ${frontApp?.label ?: "this app"}")
    }

    /** A tap on an empty rail spot: bring the rails up, or take them down for this app. */
    private fun toggleChromeRails() {
        if (ChromeService.visible) hideChromeNow() else {
            chromeMutedFor = null
            runCatching { ChromeService.show(appContext) }
        }
    }

    /**
     * The dock list, in the order it is drawn: the pinned apps, or - in "most used" mode - whatever
     * this tablet actually opens, always with the apps that asked for no chrome filtered out.
     * Shared by the chrome overlay ([syncChrome]) and by our own full-screen shells (ShellHost dock).
     */
    fun dockPkgs(): List<String> {
        val pinned = settings.pinnedDock
        val order = if (settings.railMode == "used") {
            (pinned.take(2) + appsByUsage().map { it.pkg } + pinned.drop(2)).distinct()
        } else pinned
        return order.filterNot { settings.chromeNoApps.contains(it) }
    }

    /** The drawer order: the apps launched most often first, the rest alphabetical (see AppUsage). */
    fun appsByUsage(): List<AppEntry> = dev.retrobit.desktop.data.AppUsage.order(
        items = apps,
        counts = usageCounts(),
        pkg = { it.pkg },
        label = { it.label },
    )

    /** Launch counts per package, parsed once per read (the preference is a single string). */
    fun usageCounts(): Map<String, Int> = dev.retrobit.desktop.data.AppUsage.parse(settings.appUsage)

    /** How often [pkg] has been opened from this launcher. */
    fun usageOf(pkg: String): Int = usageCounts()[pkg] ?: 0

    /** The same dock list as app entries (up to [max]) - what our own full-screen dock draws. */
    fun dockApps(max: Int = 12): List<AppEntry> = dockPkgs().take(max).mapNotNull { appByPackage(it) }

    /** The dock divider: the pinned apps, or the ones this tablet uses most. */
    private fun switchRailMode() {
        settings.railMode = if (settings.railMode == "used") "pinned" else "used"
        flashChrome("dock: " + if (settings.railMode == "used") "most used first" else "pinned apps")
        syncChrome()
        runCatching { dev.retrobit.desktop.shell.PanelService.sync(appContext) }
    }

    /** A rail key was held: an app key pins or unpins that app in the dock. */
    private fun onRailHold(action: String) {
        when {
            action.startsWith("app:") -> toggleDockPin(action.removePrefix("app:"))
        }
    }

    /** Pins or unpins an app in the dock (the same list the desktop dock draws). */
    fun toggleDockPin(pkg: String) {
        val list = settings.pinnedDock
        settings.pinnedDock = if (list.contains(pkg)) list - pkg else list + pkg
        flashChrome(
            (if (settings.pinnedDock.contains(pkg)) "pinned to the dock: " else "unpinned: ") +
                (appByPackage(pkg)?.label ?: pkg),
        )
        syncChrome()
    }

    /**
     * "Shell here": the terminal opens with the app's package already typed into the notes of the
     * [AppShell] helper - a small, honest substitute for running a real shell in another app.
     */
    private fun openShellFor(app: AppEntry) {
        shell.print("app shell: ${app.label} (${app.pkg})")
        shell.print("  uid  target=${app.user.hashCode()}  opens full screen", TerminalShell.Kind.DIM)
        shell.print("  use `info ${app.label}` for the full package record", TerminalShell.Kind.DIM)
        openWindow(WinKind.TERMINAL)
        goDesktop()
    }

    /** The per-app rules: apps the chrome must keep away from. */
    fun toggleChromeRule(pkg: String) {
        val list = settings.chromeNoApps
        settings.chromeNoApps = if (list.contains(pkg)) list - pkg else list + pkg
        val label = appByPackage(pkg)?.label ?: pkg
        flashChrome((if (settings.chromeNoApps.contains(pkg)) "no chrome for " else "chrome back for ") + label)
        // a rule for the app in front applies right away
        if (settings.chromeNoApps.contains(pkg) && frontApp?.pkg == pkg) {
            runCatching { ChromeService.hide(appContext) }
        }
        syncChrome()
    }

    // ── quick panel ─────────────────────────────────────────────────────────────
    /** Opens the quick panel over whatever owns the screen. */
    fun openPanel() {
        if (!chromeAllowed()) {
            requestChromePermission()
            shell.print("grant \"display over other apps\" first, then the panel can open", TerminalShell.Kind.ERR)
            return
        }
        syncPanel()
        dev.retrobit.desktop.shell.PanelService.show(appContext)
    }

    fun closePanel() {
        dev.retrobit.desktop.shell.PanelService.hide(appContext)
    }

    /** True while the panel window is up (the launcher draws the desktop dimmed underneath it). */
    var panelOpen: Boolean by mutableStateOf(false)
        private set

    /** Fills the panel state from the settings; the panel repaints when this changes. */
    /** Same shape as the rail clock: the panel ticks while it is open. */
    private val panelClock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)

    fun syncPanel() {
        val palette = dev.retrobit.desktop.ui.theme.Palettes.of(settings.palette)
        panelOpen = dev.retrobit.desktop.shell.PanelChrome.visible
        // the app in front first, then what this tablet opens most: tap to open, hold for its shell
        val refs = (frontApp?.let { listOf(it) }.orEmpty() + appsByUsage())
            .distinctBy { it.pkg }
            .take(5)
            .map { dev.retrobit.desktop.shell.PanelChrome.AppRef(it.label, it.pkg) }
        dev.retrobit.desktop.shell.PanelChrome.state = dev.retrobit.desktop.shell.PanelChrome.State(
            paper = palette.paper.toArgb(),
            ink = palette.ink.toArgb(),
            mid = palette.mid.toArgb(),
            accent = palette.accent.toArgb(),
            scale = settings.uiScale.coerceIn(0.6f, 2f),
            brightness = settings.brightness.takeIf { it > 0f },
            rotateLocked = settings.rotateLocked,
            night = settings.nightMode,
            chromeOn = settings.cyberdeckChrome,
            chromeAllowed = chromeAllowed(),
            railMode = settings.railMode,
            sizeName = settings.chromeSize,
            topRail = settings.chromeTopRail,
            dockOn = settings.chromeDock,
            autoHide = settings.chromeAutoHide,
            autoHideSecs = settings.chromeAutoHideSecs.coerceIn(3, 60),
            apps = refs,
            noChrome = settings.chromeNoApps,
            clock = panelClock.format(java.util.Date(now)),
        )
    }

    /** Rail & dock size from anywhere (panel, settings, terminal). */
    fun setChromeSize(name: String) {
        val wanted = name.lowercase()
            .takeIf { it == "compact" || it == "normal" || it == "large" || it == "huge" }
            ?: "normal"
        if (settings.chromeSize == wanted) return
        settings.chromeSize = wanted
        flashChrome("rail & dock: $wanted")
        syncChrome()
        runCatching { dev.retrobit.desktop.shell.PanelService.sync(appContext) }
    }

    /** Everything the quick panel can ask for, in one place. */
    fun onPanelAction(action: String) {
        when {
            action.startsWith("size:") -> setChromeSize(action.removePrefix("size:"))
            action.startsWith("bright:") -> {
                val tail = action.removePrefix("bright:")
                when {
                    tail == "auto" -> setBrightness(null)
                    tail == "+" -> setBrightness(((settings.brightness.takeIf { it > 0f } ?: currentBrightness) + 0.1f).coerceAtMost(1f))
                    tail == "-" -> setBrightness(((settings.brightness.takeIf { it > 0f } ?: currentBrightness) - 0.1f).coerceAtLeast(0.1f))
                    tail == "drag" -> Unit
                    else -> tail.toFloatOrNull()?.let { setBrightness(it.coerceIn(0.05f, 1f)) }
                }
            }
            action == "rotate:toggle" -> {
                settings.rotateLocked = !settings.rotateLocked
                shell.print("rotation: " + if (settings.rotateLocked) "locked" else "auto")
            }
            action == "night:toggle" -> setNight(!settings.nightMode)
            action == "chrome:toggle" -> setCyberdeck(!settings.cyberdeckChrome)
            action == "rails:toggle" -> hideChromeNow()
            action == "autohide:toggle" -> {
                settings.chromeAutoHide = !settings.chromeAutoHide
                syncChrome()
            }
            action.startsWith("autohide:") ->
                action.removePrefix("autohide:").toIntOrNull()?.let {
                    settings.chromeAutoHideSecs = it.coerceIn(3, 60)
                    settings.chromeAutoHide = true
                    syncChrome()
                }
            action.startsWith("mode:") -> {
                settings.railMode = action.removePrefix("mode:")
                syncChrome()
            }
            action == "rail:top" -> {
                settings.chromeTopRail = !settings.chromeTopRail
                syncChrome()
            }
            action == "rail:dock" -> {
                settings.chromeDock = !settings.chromeDock
                syncChrome()
            }
            action.startsWith("win:") -> appByPackage(action.removePrefix("win:"))?.let { launch(it) }
            action.startsWith("rule:") -> toggleChromeRule(action.removePrefix("rule:"))
            action == "close" -> closePanel()
        }
        syncPanel()
        runCatching { dev.retrobit.desktop.shell.PanelService.sync(appContext) }
    }

    /** Brightness the activity is actually using (set by it on every resume / panel change). */
    private var currentBrightness: Float = 0.5f

    /** Called from the activity so the panel's B- / B+ start from the real value. */
    fun noteBrightness(value: Float) {
        currentBrightness = value.coerceIn(0.05f, 1f)
    }

    private fun setBrightness(value: Float?) {
        settings.brightness = value ?: -1f
        brightnessRequest++
        shell.print("brightness: " + (value?.let { "${(it * 100).toInt()}%" } ?: "device"))
    }

    /** Bumped when the panel changes the brightness: the activity applies it to its window. */
    var brightnessRequest: Int by mutableStateOf(0)
        private set

    /** Night mode: the dark Ink palette, and back to the day palette when it is turned off. */
    fun setNight(on: Boolean) {
        if (on == settings.nightMode) return
        if (on) {
            if (settings.palette != PaletteId.INK) settings.dayPalette = settings.palette.name
            setPalette(PaletteId.INK)
        } else {
            val back = runCatching { PaletteId.valueOf(settings.dayPalette) }
                .getOrDefault(PaletteId.PAPER)
            setPalette(if (back == PaletteId.INK) PaletteId.PAPER else back)
        }
        settings.nightMode = on
        shell.print("night mode: " + if (on) "on" else "off")
    }

    /** The launcher is in front again: the chrome is not needed while our own frames are visible. */
    fun onLauncherVisible() {
        chromeRev++
        panelOpen = false
        syncPanel()
        runCatching { ChromeService.hide(appContext) }
        // the service can be killed by the system (or by the user), and the user may have just come
        // back from granting the permission: make sure the chrome is running when it is wanted
        if (settings.cyberdeckChrome && chromeAllowed() && !ChromeService.running) {
            runCatching { ChromeService.start(appContext) }
        }
        syncChrome()
    }

    /** Another app owns the screen: draw the frame over it when the user asked for the chrome. */
    fun onLauncherHidden() {
        val pkg = frontApp?.pkg
        val excluded = pkg != null && settings.chromeNoApps.contains(pkg)
        val muted = pkg != null && chromeMutedFor == pkg
        if (settings.cyberdeckChrome && chromeAllowed() && !excluded && !muted) {
            runCatching { ChromeService.show(appContext) }
        } else if (settings.cyberdeckChrome && chromeAllowed() && excluded) {
            // "no chrome for this app" must not be a dead end: the rails fade in for a few seconds
            // with the reason and the way back
            chromeFlash = "no chrome for ${frontApp?.label ?: pkg} - the eye key turns it back on"
            chromeFlashAt = System.currentTimeMillis()
            syncChrome()
            runCatching { ChromeService.preview(appContext, 4) }
        }
        syncChrome()
    }

    /** Wired by the activity: summon or hide the internal (on-screen) keyboard. */
    var toggleKeyboard: (() -> Unit)? = null

    /** True while the launcher is the app in front; the clock / battery timer runs only then. */
    var foreground by mutableStateOf(false)
        private set

    /** Called from the activity's lifecycle so the timer can idle in the background. */
    fun setAppForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) {
            // coming back to the front: refresh at once instead of waiting for the next tick
            now = System.currentTimeMillis()
            viewModelScope.launch { snapshot = SystemInfo.snapshot(getApplication()) }
        }
    }

    private var seeded = false

    init {
        wireChrome()
        repo.registerChanges { viewModelScope.launch { repo.reload() } }
        viewModelScope.launch { repo.reload(); seedDefaults() }
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                // The clock only has to tick while the launcher is actually on screen. In the
                // background the old loop kept waking up once a second and asked the system for the
                // battery, memory, disk and network state every time — a binder round-trip per
                // second, forever, for a widget nobody could see.
                if (foreground) {
                    now = System.currentTimeMillis()
                    // the heavy figures change slowly: once every five seconds is plenty
                    if (ticks % 5 == 0) snapshot = SystemInfo.snapshot(getApplication())
                    ticks++
                }
                // Sleep to the next second boundary instead of a flat second: the chrono widget
                // then flips its seconds exactly with the wall clock, and the loop cannot drift
                // (a flat delay slowly accumulates the time Compose needs to draw each tick).
                delay(1_000L - System.currentTimeMillis() % 1_000L)
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
     * Launches an installed app. Third-party apps open the way they always did - full screen, with
     * the cyberdeck chrome drawing the frame around them (our own apps live in floating windows
     * instead, see [openWindow]).
     */
    fun launch(app: AppEntry) {
        selectedApp = null
        // the launch counts drive the drawer order: most used first, then A-Z
        settings.noteLaunch(app.pkg)
        frontApp = app
        val ok = repo.launch(app)
        if (!ok) shell.print("error: unable to launch ${app.label}", TerminalShell.Kind.ERR)
        startMenu = false
        launchpad = false
        spotlight = false
        syncChrome()
    }

    /** The application context, used by the windowed-launch helpers. */
    val appContext: android.content.Context get() = getApplication()

    /** Same thing, under the name the menus and the terminal use. */
    fun launchFullScreen(app: AppEntry) = launch(app)

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

    /**
     * Opens one of our own apps (terminal, settings, the drawer, ...) in its **floating window**: a
     * framed, movable, resizable box on the desktop - not a full screen takeover. Opening it again
     * brings the window that is already there back to the front.
     */
    fun openWindow(kind: WinKind) {
        wm.open(kind)
        selectedApp = null
        menu = null
        startMenu = false
        launchpad = false
        spotlight = false
        syncChrome()
    }

    /**
     * The "desktop" key: leaves the full-screen frame and goes back to the desktop. A maximized
     * window shrinks back to its floating box (nothing is lost); if nothing is maximized, the
     * focused window is minimized.
     */
    fun closeShell() {
        val max = wm.maximizedWindow
        if (max != null) wm.restore(max.id) else wm.minimizeFocused()
        syncChrome()
    }

    // ── window actions (buttons, keyboard shortcuts, terminal) ─────────────────
    fun minimizeFocused() = wm.minimizeFocused()
    fun restoreFocused() = wm.focused?.let { wm.restore(it.id) }
    fun maximizeFocused() = wm.focused?.let { wm.toggleMaximize(it.id) }
    fun closeFocusedWindow() = wm.closeFocused()
    fun minimizeAllWindows() = wm.minimizeAll()

    /** Brings a window back to the front (the task chips in the panels / dock). */
    fun showWindow(win: dev.retrobit.desktop.ui.wm.WinState) {
        if (win.maximized) wm.restore(win.id) else wm.focus(win.id)
        syncChrome()
    }

    /** The windows a task list shows: everything open on this page of the desktop. */
    val openWindows: List<dev.retrobit.desktop.ui.wm.WinState> get() = wm.onScreen

    /** Drops every window back to its default size and forgets the remembered geometry. */
    fun resetWindowSizes() {
        wm.resetGeometry()
        shell.print("window sizes reset - windows open at the default size again")
    }

    /**
     * The task list, as menu items: the windows open on this page of the desktop, most recent
     * first, then the two housekeeping entries. Tapping a window raises it and, if it was
     * minimized, brings it back.
     */
    fun windowMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        val open = wm.onScreen.sortedByDescending { it.z }
        if (open.isEmpty()) {
            items += MenuItem(label = "no windows open", glyph = null, enabled = false) {}
        }
        for (w in open) {
            items += MenuItem(
                label = w.title + when {
                    w.minimized -> "  (minimized)"
                    w.maximized -> "  (maximized)"
                    else -> ""
                },
                glyph = dev.retrobit.desktop.ui.wm.glyphFor(w.kind),
            ) { showWindow(w) }
        }
        if (open.isNotEmpty()) {
            items += MenuItem(label = "minimize all windows", glyph = null) { wm.minimizeAll() }
            items += MenuItem(label = "close all windows", glyph = null) { wm.closeAll() }
        }
        items += MenuItem(label = "reset window sizes", glyph = null) { resetWindowSizes() }
        return items
    }

    /** The panel / menu bar "windows" entry: the same task list as a popup under [anchor]. */
    fun windowsMenu(anchor: Offset) {
        openMenu(MenuRequest("Windows", "floating windows of our own apps", anchor, windowMenuItems()))
    }

    /** Opens the app drawer (used by the app context menu). */
    fun openAppDrawerWindow(app: AppEntry) {
        selectedApp = app
        openWindow(WinKind.APPS)
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

    init {
        // every window the user moves or resizes is remembered per app, so it opens the same way
        wm.onGeometryChanged = { w -> if (w.width > 0f && w.height > 0f) settings.setWindowRect(w.kind, w.placement) }
    }

    // ── appearance ───────────────────────────────────────────────────────────────
    fun setStyle(style: DesktopStyle) { settings.style = style; shell.print("desktop style -> ${style.label}") }
    /** Picks a palette without touching the desktop style (night mode and the terminal use this). */
    fun setPalette(p: PaletteId) { settings.palette = p }

    /**
     * Picks a palette from the UI. The **native** palettes bring their shell along, so one tap on
     * "Windows 95" gives the silver-and-teal desktop in the Windows shell, "Aqua" the macOS one and
     * so on; the e-paper palettes leave the style exactly where the user put it. The desktop style
     * can always be switched right afterwards — the two settings stay independent.
     */
    fun applyPalette(p: PaletteId) {
        settings.palette = p
        p.style?.let { style ->
            if (settings.style != style) {
                settings.style = style
                shell.print("desktop style -> ${style.label} · ${style.tagline}")
            }
        }
        // a native desktop shows its app icons in colour, the e-paper palettes print them 1-bit
        val wantedIcons = if (p.native) IconMode.COLOR else IconMode.MONO
        if (settings.iconMode != wantedIcons) {
            settings.iconMode = wantedIcons
            shell.print("app icons -> ${wantedIcons.label.lowercase()} (part of the theme; change it any time)")
        }
        shell.print(
            "palette -> ${p.label}" +
                if (p.native) " (native desktop palette; the desk is painted in its own colour)" else "",
        )
    }
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

    /** Switches page: each page has its own icons, widgets and floating windows. */
    fun switchDesktop(index: Int) {
        desktop = index.coerceIn(0, desktopCount - 1)
        wm.switchDesktop(desktop)
        shell.print("desktop ${desktop + 1} of $desktopCount")
    }

    fun setWorkspaceCount(count: Int) {
        settings.workspaceCount = count.coerceIn(1, 4)
        if (desktop > desktopCount - 1) desktop = desktopCount - 1
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
                    MenuItem("Open", GlyphOpen(), onSelect = { launch(app) }),
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
                ),
            )
        )
    }

    fun workspaceMenu(anchor: Offset) {
        val items = (0 until desktopCount).map { index ->
            MenuItem(
                label = "Desktop ${index + 1}" + if (index == desktop) "   (current)" else "",
                glyph = if (index == desktop) dev.retrobit.desktop.ui.components.Glyph.GRID else null,
                onSelect = { switchDesktop(index) },
            )
        }
        openMenu(MenuRequest("Desktops", "workspace ${desktop + 1} of $desktopCount - icons & widgets", anchor, items))
    }

    // ── boot ─────────────────────────────────────────────────────────────────────
    fun boot() {
        if (bootedThisProcess) { bootDone = true; return }
        bootedThisProcess = true
        if (!settings.bootSequence) { bootDone = true; return }
        viewModelScope.launch {
            booting = true
            bootLines.clear()
            val cores = Runtime.getRuntime().availableProcessors()
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            val metrics = getApplication<Application>().resources.displayMetrics
            fun bootLine(text: String) = text.padEnd(42, '.') + " OK"
            val lines = listOf(
                "Retrobit BIOS v1.0  (c) retrobit-dev",
                bootLine("detecting e-paper panel"),
                bootLine("cpu: $abi / $cores cores"),
                bootLine("framebuffer ${metrics.widthPixels}x${metrics.heightPixels} / 16 grays"),
                bootLine("mounting /dev/paper0"),
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
