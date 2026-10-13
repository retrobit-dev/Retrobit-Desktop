package dev.retrobit.desktop.ui.apps

import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import dev.retrobit.desktop.data.CalendarMath
import dev.retrobit.desktop.data.SystemInfo
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.EntryKind
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.PaletteId
import dev.retrobit.desktop.model.WallPattern
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The `rb-sh` command interpreter behind the terminal app. */
class TerminalShell(private val vm: LauncherViewModel) {

    enum class Kind { OUT, ERR, IN, HEAD, DIM }

    data class Line(val text: String, val kind: Kind)

    val lines = mutableStateListOf<Line>()
    private val history = mutableStateListOf<String>()
    var historyCursor = -1
        private set

    fun print(text: String, kind: Kind = Kind.OUT) {
        text.split('\n').forEach { lines += Line(it, kind) }
        if (lines.size > 800) while (lines.size > 600) lines.removeAt(0)
    }

    fun clear() = lines.clear()

    fun historyPrev(current: String): String {
        if (history.isEmpty()) return current
        if (historyCursor == -1) historyCursor = history.size
        historyCursor = (historyCursor - 1).coerceAtLeast(0)
        return history[historyCursor]
    }

    fun historyNext(): String {
        if (history.isEmpty() || historyCursor == -1) return ""
        historyCursor++
        if (historyCursor >= history.size) { historyCursor = history.size; return "" }
        return history[historyCursor]
    }

    fun submit(raw: String) {
        val input = raw.trim()
        print(prompt() + input, Kind.IN)
        if (input.isNotEmpty()) {
            history += input
            historyCursor = -1
            run(input)
        }
    }

    fun prompt(): String = "${vm.settings.userName}@retrobit:~$ "

    private fun run(input: String) {
        val parts = input.split(Regex("\\s+"))
        val cmd = parts[0].lowercase()
        val args = parts.drop(1)
        when (cmd) {
            "help", "?" -> help()
            "ls", "apps" -> listApps(args.firstOrNull())
            "open", "run", "launch" -> launchApp(args.joinToString(" "))
            "info", "appinfo" -> appInfo(args.joinToString(" "))
            "neofetch", "sysinfo", "uname" -> neofetch()
            "df", "disk" -> disk()
            "free", "mem" -> mem()
            "date", "time" -> print(SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss z", Locale.US).format(Date()))
            "whoami" -> print(vm.settings.userName)
            "pwd" -> print("/home/${vm.settings.userName}")
            "hostname" -> print("retrobit-matepad")
            "echo" -> print(args.joinToString(" "))
            "style", "wm" -> style(args.firstOrNull())
            "palette", "theme" -> palette(args.firstOrNull())
            "wall", "wallpaper" -> wallpaper(args.firstOrNull())
            "icons", "iconmode" -> icons(args.firstOrNull())
            "scale" -> scale(args.firstOrNull())
            "tiling" -> tiling(args.firstOrNull())
            "workspace", "ws", "desktop" -> workspace(args.firstOrNull())
            "cal", "calendar" -> calendar(args)
            "wallpaper", "wall" -> wallpaper(args.firstOrNull())
            "seconds" -> seconds(args.firstOrNull())
            "arrange" -> arrange()
            "safearea" -> safeArea(args.firstOrNull())
            "dock" -> dock(args)
            "floating" -> floating(args.firstOrNull())
            "widgets", "widget" -> widgets(args)
            "wsmove" -> wsMove(args.firstOrNull())
            "invert" -> vm.invertTheme()
            "app" -> appCmd(args)
            "doubleclick", "dblclick" -> {
                val arg = args.firstOrNull()?.lowercase()
                vm.settings.doubleClickOpen = when (arg) {
                    null -> !vm.settings.doubleClickOpen
                    "on", "1", "true" -> true
                    else -> false
                }
                print("double click opens apps -> ${if (vm.settings.doubleClickOpen) "on (tap selects)" else "off (tap opens)"}")
            }
            "notes" -> vm.openWindow(WinKind.NOTES)
            "calendar", "cal-ui" -> vm.openWindow(WinKind.CALENDAR)
            "settings", "config" -> vm.openWindow(WinKind.SETTINGS)
            "sysmon", "top" -> vm.openWindow(WinKind.SYSMON)
            "about" -> vm.openWindow(WinKind.ABOUT)
            "files" -> vm.openWindow(WinKind.APPS)
            "ps" -> ps()
            "wmctrl" -> wmctrl(args.firstOrNull())
            "boot" -> vm.showBootScreenAgain()
            "clear" -> clear()
            "exit", "quit" -> exit()
            else -> {
                if (vm.launchResolved(input)) {
                    print("launching \"$input\" ...")
                } else {
                    print("rb-sh: command not found: $cmd  (try `help`)", Kind.ERR)
                }
            }
        }
    }

    private fun help() {
        print("RETROBIT DESKTOP SHELL — rb-sh 1.0", Kind.HEAD)
        print(
            """
            apps / ls [filter]     list installed applications
            open <name|pkg>        launch an application
            info <name>            details about a package
            neofetch               system + about summary
            df | free | ps         disk, memory, running windows
            style linux|macos|windows
            palette paper|sepia|bwr|ink|phosphor|amber
            wall dots|grid|dither|hatch|plain
            icons color|mono|dither
            scale 0.8|0.9|1.0|1.15|1.3
            tiling on|off          linux-style tiling window layout
            cal                    calendar entries (cal add "title" 2026-11-02 14:30 event|task|birthday)
            cal list | cal tasks   agenda view / open tasks
            wallpaper pattern|device|image   desktop background source
            seconds on|off         seconds in the clock
            arrange                auto arrange desktop icons
            safearea on|off [dp]   keep the desktop clear of the camera hole and rounded corners
            dock list | dock move <app> <n>   pinned dock apps, reorder them by hand
            floating on|off        apps open in floating desktop windows
            app add|remove <name>  put an app on the desktop / take it off
            doubleclick on|off     apps open with a double click (tap selects) or a single tap
            widgets                widgets (widget add|remove <name> | widget width <dp> | widget size <name> <w> <h> |
                                   widget reset | widget edit on|off | widget hold on|off | widget layout)
            workspace 1..4         switch virtual desktop (also: ws)
            wsmove 1..4            send the focused window to a desktop
            wmctrl list|close|tile | invert | boot
            notes | settings | sysmon | about | clear | exit
            """.trimIndent(),
            Kind.DIM,
        )
        print("tip: tap & hold any icon for its context menu.", Kind.DIM)
        print("pen: 1 tap = click, 2 taps = right click. keyboard: the keyboard button in the panel, or ctrl+alt+k.", Kind.DIM)
    }

    private fun listApps(filter: String?) {
        val f = filter?.lowercase()
        val list = vm.apps.filter { f == null || it.label.lowercase().contains(f) || it.pkg.contains(f) }
        if (list.isEmpty()) { print("no matching applications", Kind.DIM); return }
        print("${list.size} applications installed", Kind.HEAD)
        list.take(120).forEach { print("  ${it.label.padEnd(28).take(28)} ${it.pkg}") }
        if (list.size > 120) print("  ... ${list.size - 120} more", Kind.DIM)
    }

    private fun launchApp(query: String) {
        if (query.isBlank()) { print("usage: open <app name>", Kind.ERR); return }
        val q = query.lowercase()
        val app = vm.apps.firstOrNull { it.label.lowercase() == q }
            ?: vm.apps.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: vm.apps.firstOrNull { it.pkg == query }
            ?: vm.apps.firstOrNull { it.label.lowercase().contains(q) }
        if (app == null) { print("rb-sh: no app matches \"$query\"", Kind.ERR); return }
        print("launching ${app.label} (${app.pkg})")
        vm.launch(app)
    }

    private fun appInfo(query: String) {
        val app = vm.apps.firstOrNull { it.label.equals(query, true) || it.pkg == query } ?: run {
            print("rb-sh: unknown app \"$query\"", Kind.ERR); return
        }
        print("name      : ${app.label}")
        print("package   : ${app.pkg}")
        print("activity  : ${app.component.className}")
        print("profile   : ${if (app.isWorkProfile) "work" else "personal"}")
    }

    private fun ps() {
        val wins = vm.wm.windows
        if (wins.isEmpty()) { print("no windows open", Kind.DIM); return }
        print("  PID  WS  WIN            TITLE", Kind.HEAD)
        wins.forEach { w ->
            print("  ${w.id.toString().padStart(3)}  d${w.desktop + 1}  ${w.kind.name.padEnd(13)} ${w.title}${if (w.minimized) "  (minimized)" else ""}")
        }
    }

    private fun wmctrl(arg: String?) {
        when (arg) {
            null, "list" -> ps()
            "close" -> { vm.wm.closeAll(); print("all windows closed") }
            "tile" -> { vm.setTiling(true); print("tiling enabled") }
            "untile" -> { vm.setTiling(false); print("tiling disabled") }
            "min" -> { vm.wm.minimizeAll(); print("all windows minimized") }
            else -> print("usage: wmctrl list|close|tile|untile|min", Kind.ERR)
        }
    }

    private fun neofetch() {
        val s = vm.snapshot
        val art = listOf(
            "   .-------------------.  ",
            "   |  .-------------.  |  ",
            "   |  | retR0BIT  _ |  |  ",
            "   |  |           >_|  |  ",
            "   |  '-------------'  |  ",
            "   '-------------------'  ",
            "        '-----------'     ",
        )
        val info = listOf(
            "${vm.settings.userName}@retrobit-matepad",
            "-------------------------",
            "OS       : ${vm.settings.style.osName} (Android ${Build.VERSION.RELEASE})",
            "Kernel   : ${Build.DISPLAY}",
            "Device   : ${Build.MANUFACTURER} ${Build.MODEL}",
            "WM       : retrobit-wm (${vm.settings.style.label} style)",
            "Theme    : ${vm.settings.palette.label} / ${vm.settings.wallpaper.label} / ${vm.settings.iconMode.label}",
            "Windows  : ${vm.wm.onScreen.size} on desktop ${vm.wm.desktop + 1} / ${vm.wm.windows.size} total",
            "Uptime   : ${SystemInfo.fmtUptime(s.uptimeMs)}",
            "Memory   : ${SystemInfo.fmtBytes(s.memUsed)} / ${SystemInfo.fmtBytes(s.memTotal)}",
            "Disk     : ${SystemInfo.fmtBytes(s.diskUsed)} / ${SystemInfo.fmtBytes(s.diskTotal)}",
            "Battery  : ${if (s.battery < 0) "n/a" else "${s.battery}%" + if (s.charging) " (charging)" else ""}",
            "Network  : ${s.net}",
        )
        val rows = maxOf(art.size, info.size)
        for (i in 0 until rows) {
            val a = art.getOrElse(i) { " ".repeat(26) }
            val b = info.getOrElse(i) { "" }
            print("$a $b", if (i == 0) Kind.HEAD else Kind.OUT)
        }
    }

    private fun disk() {
        val s = vm.snapshot
        print("Filesystem      Size   Used   Avail  Use%", Kind.HEAD)
        print("/data         ${SystemInfo.fmtBytes(s.diskTotal).padStart(6)} ${SystemInfo.fmtBytes(s.diskUsed).padStart(6)} ${SystemInfo.fmtBytes(s.diskTotal - s.diskUsed).padStart(6)}  ${(s.diskFrac * 100).toInt()}%")
        print("  ${SystemInfo.bar(s.diskFrac, 24)}", Kind.DIM)
    }

    private fun mem() {
        val s = vm.snapshot
        print("              total        used        free", Kind.HEAD)
        print("Mem:    ${SystemInfo.fmtBytes(s.memTotal).padStart(10)}  ${SystemInfo.fmtBytes(s.memUsed).padStart(10)}  ${SystemInfo.fmtBytes(s.memTotal - s.memUsed).padStart(10)}")
        print("  ${SystemInfo.bar(s.memFrac, 24)}", Kind.DIM)
    }

    private fun style(arg: String?) {
        val map = mapOf(
            "linux" to DesktopStyle.LINUX, "gnome" to DesktopStyle.LINUX, "kde" to DesktopStyle.LINUX,
            "macos" to DesktopStyle.MACOS, "mac" to DesktopStyle.MACOS, "osx" to DesktopStyle.MACOS,
            "windows" to DesktopStyle.WINDOWS, "win" to DesktopStyle.WINDOWS, "win95" to DesktopStyle.WINDOWS,
        )
        val s = map[arg?.lowercase()]
        if (s == null) { print("usage: style linux|macos|windows", Kind.ERR); return }
        vm.setStyle(s)
        print("desktop style -> ${s.label}")
    }

    private fun palette(arg: String?) {
        val s = arg?.let { a -> PaletteId.values().firstOrNull { it.name.equals(a, true) || it.label.equals(a, true) } }
        if (s == null) { print("palettes: " + PaletteId.values().joinToString(" | ") { it.name.lowercase() }, Kind.ERR); return }
        vm.setPalette(s)
        print("palette -> ${s.label}")
    }

    private fun calendar(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "add", "+" -> {
                if (args.size < 3) { print("usage: cal add \"title\" 2026-11-02 [hh:mm] [event|task|agenda|birthday]", Kind.ERR); return }
                val title = args[1]
                val date = CalendarMath.parseDate(args[2])
                if (date == null) { print("rb-sh: cannot read the date \"${args[2]}\" (use yyyy-mm-dd)", Kind.ERR); return }
                val time = CalendarMath.parseTime(args.getOrNull(3))
                val kind = args.getOrNull(4)?.let { raw ->
                    EntryKind.values().firstOrNull { it.name.equals(raw, true) }
                } ?: EntryKind.EVENT
                val entry = vm.calendar.add(title, date, kind, time)
                if (entry == null) print("rb-sh: empty title", Kind.ERR)
                else print("added ${kind.label} \"${entry.title}\" on ${CalendarMath.prettyDate(entry.date)} ${CalendarMath.prettyTime(entry.time)}")
            }
            "tasks", "todo" -> {
                val open = vm.calendar.openTasks()
                if (open.isEmpty()) { print("no open tasks", Kind.DIM); return }
                print("${open.size} open tasks", Kind.HEAD)
                open.forEach { print("  [ ] ${it.title}  ${CalendarMath.prettyDate(it.date)} ${CalendarMath.prettyTime(it.time)}") }
            }
            "clear" -> { vm.calendar.clearAll(); print("calendar cleared") }
            else -> {
                val upcoming = vm.calendar.upcoming(java.time.LocalDate.now(), 14)
                if (upcoming.isEmpty()) { print("nothing planned in the next 14 days", Kind.DIM); return }
                print("next 14 days", Kind.HEAD)
                upcoming.forEach { (day, entry) ->
                    val tag = entry.kind.label.take(3)
                    print("  ${CalendarMath.prettyDate(day)}  [$tag] ${entry.title}  ${CalendarMath.prettyTime(entry.time)}")
                }
            }
        }
    }

    private fun seconds(arg: String?) {
        when (arg?.lowercase()) {
            "on", "1", "true" -> { vm.settings.showSeconds = true; print("seconds -> on") }
            "off", "0", "false" -> { vm.settings.showSeconds = false; print("seconds -> off") }
            else -> { vm.settings.showSeconds = !vm.settings.showSeconds; print("seconds -> ${if (vm.settings.showSeconds) "on" else "off"}") }
        }
    }

    private fun floating(arg: String?) {
        when (arg?.lowercase()) {
            "on", "1", "true" -> { vm.settings.floatingApps = true; print("floating apps -> on${freeformNote()}") }
            "off", "0", "false" -> { vm.settings.floatingApps = false; print("floating apps -> off (every app opens full screen)") }
            else -> {
                vm.settings.floatingApps = !vm.settings.floatingApps
                print("floating apps -> ${if (vm.settings.floatingApps) "on" else "off"}${freeformNote()}")
            }
        }
    }

    private fun freeformNote(): String =
        if (dev.retrobit.desktop.data.WindowedLaunch.supportsFreeform(vm.appContext)) " (this device supports floating windows)"
        else " (this device has no floating-window support: apps open full screen)"

    /** `app add <name>` puts an app on the desktop, `app remove <name>` takes it off. */
    private fun appCmd(args: List<String>) {
        val action = args.getOrNull(0)?.lowercase()
        val name = args.drop(1).joinToString(" ")
        val app = vm.apps.firstOrNull {
            it.label.equals(name, true) || it.pkg.equals(name, true) ||
                (name.isNotBlank() && it.label.lowercase().startsWith(name.lowercase()))
        }
        when {
            action == null || app == null -> {
                print(
                    "usage: app add <name|pkg> | app remove <name|pkg>",
                    if (app == null && name.isNotBlank()) Kind.ERR else Kind.OUT,
                )
                print("on the desktop now: ${vm.settings.pinnedDesktop.size} apps")
            }
            action.startsWith("a") -> {
                vm.addAppToDesktop(app)
                print("${app.label} added to the desktop (${vm.settings.pinnedDesktop.size} apps)")
            }
            action.startsWith("r") -> {
                vm.toggleDesktopPin(app)
                print("${app.label} removed from the desktop (${vm.settings.pinnedDesktop.size} apps)")
            }
            else -> print("usage: app add <name|pkg> | app remove <name|pkg>", Kind.ERR)
        }
    }

    private fun widgetNames() = dev.retrobit.desktop.model.WidgetId.entries.joinToString("|") { it.label }

    private fun widgetLine(): String {
        val list = vm.settings.widgets
        return if (list.isEmpty()) "none" else list.joinToString(", ") { it.label }
    }

    private fun widgets(args: List<String>) {
        val s = vm.settings
        when (args.firstOrNull()?.lowercase()) {
            "width" -> {
                val w = args.getOrNull(1)?.toIntOrNull()
                if (w == null) print("usage: widget width <dp>", Kind.ERR)
                else {
                    s.widgetWidth = w.coerceIn(160, 900)
                    print("widget width -> ${s.widgetWidth}dp")
                }
            }
            "size" -> {
                val id = dev.retrobit.desktop.model.WidgetId.of(args.getOrNull(1))
                val w = args.getOrNull(2)?.toIntOrNull()
                val h = args.getOrNull(3)?.toIntOrNull()
                if (id == null || w == null || h == null) {
                    print("usage: widget size <${dev.retrobit.desktop.model.WidgetId.entries.joinToString("|") { it.label }}> <w> <h>", Kind.ERR)
                } else {
                    s.setWidgetSize(id, w, h)
                    print("${id.label} -> ${s.widgetWidth(id)} x ${s.widgetHeight(id)} dp")
                }
            }
            "reset" -> {
                val id = dev.retrobit.desktop.model.WidgetId.of(args.getOrNull(1))
                if (id == null) { s.resetWidgets(); print("all widgets back to ${s.widgetWidth}dp wide") }
                else { s.resetWidgetSize(id); print("${id.label} -> defaults") }
            }
            "add" -> {
                val id = dev.retrobit.desktop.model.WidgetId.of(args.getOrNull(1))
                if (id == null) print("usage: widget add <${widgetNames()}>", Kind.ERR)
                else {
                    s.addWidget(id)
                    print("${id.label} added to the desktop: ${widgetLine()}")
                }
            }
            "remove", "rm" -> {
                val id = dev.retrobit.desktop.model.WidgetId.of(args.getOrNull(1))
                if (id == null) print("usage: widget remove <${widgetNames()}>", Kind.ERR)
                else {
                    s.removeWidget(id)
                    print("${id.label} removed: ${widgetLine()}")
                }
            }
            "edit" -> {
                vm.widgetEdit = args.getOrNull(1)?.let { it.equals("on", true) || it == "1" } ?: !vm.widgetEdit
                print("widget editing -> ${if (vm.widgetEdit) "on (hold or drag a widget to resize)" else "off"}")
            }
            "hold" -> {
                val arg = args.getOrNull(1)?.lowercase()
                vm.settings.widgetHoldEdit = when (arg) {
                    null -> !vm.settings.widgetHoldEdit
                    "on", "1", "true" -> true
                    else -> false
                }
                print(
                    if (vm.settings.widgetHoldEdit) "hold a widget to edit & resize -> on"
                    else "hold a widget to edit & resize -> off (resize from the edges)"
                )
            }
            "reset-layout", "layout" -> {
                s.resetWidgetList()
                print("widget layout reset to defaults: ${widgetLine()}")
            }
            else -> {
                print("desktop widgets (hold one on screen to edit or resize it)", Kind.HEAD)
                dev.retrobit.desktop.model.WidgetId.entries.forEach { id ->
                    val h = s.widgetHeight(id).takeIf { it > 0 } ?: 0
                    val mark = if (s.isWidgetEnabled(id)) "[x]" else "[ ]"
                    print("  $mark ${id.label.padEnd(10)} ${s.widgetWidth(id)} x $h dp")
                }
                print("  on the desktop: ${widgetLine()}")
            }
        }
    }

    private fun safeArea(arg: String?) {
        when (arg?.lowercase()) {
            "on", "1", "true" -> { vm.settings.safeArea = true; print("safe area -> on (corner inset ${vm.settings.cornerInset}dp)") }
            "off", "0", "false" -> { vm.settings.safeArea = false; print("safe area -> off (desktop draws to the physical edge)") }
            null -> { vm.settings.safeArea = !vm.settings.safeArea; print("safe area -> ${if (vm.settings.safeArea) "on" else "off"}") }
            else -> {
                val radius = arg.toIntOrNull()
                if (radius == null) print("usage: safearea on|off|<radius in dp>", Kind.ERR)
                else {
                    vm.settings.cornerInset = radius.coerceIn(0, 48)
                    vm.settings.safeArea = radius > 0
                    print("corner inset -> ${vm.settings.cornerInset}dp")
                }
            }
        }
    }

    private fun dock(args: List<String>) {
        val pinned = vm.settings.pinnedDock
        when (args.firstOrNull()?.lowercase()) {
            "move", "mv" -> {
                val raw = args.drop(1).dropLast(1).joinToString(" ")
                val index = args.lastOrNull()?.toIntOrNull()
                val app = vm.apps.firstOrNull { it.label.equals(raw, true) || it.pkg.equals(raw, true) }
                if (app == null || index == null) {
                    print("usage: dock move <app name> <slot>", Kind.ERR)
                } else {
                    vm.settings.moveDockPin(app.pkg, index)
                    print("${app.label} -> dock slot ${index + 1}")
                }
            }
            else -> {
                if (pinned.isEmpty()) { print("dock is empty — drag an app onto it or use \"pin\"", Kind.DIM); return }
                print("dock (${pinned.size} apps)", Kind.HEAD)
                pinned.forEachIndexed { i, pkg ->
                    val label = vm.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                    print("  ${i + 1}. $label")
                }
                print("drag icons along the dock to reorder them", Kind.DIM)
            }
        }
    }

    private fun arrange() {
        vm.settings.clearIconCells()
        print("desktop icons auto arranged")
    }

    private fun wallpaper(arg: String?) {
        val source = when (arg?.lowercase()) {
            "device", "system", "system-wallpaper" -> dev.retrobit.desktop.data.WallpaperSource.DEVICE
            "image", "photo", "file" -> dev.retrobit.desktop.data.WallpaperSource.IMAGE
            "pattern", "generated" -> dev.retrobit.desktop.data.WallpaperSource.PATTERN
            else -> null
        }
        if (source != null) {
            vm.setWallpaperSource(source)
            print(
                when (source) {
                    dev.retrobit.desktop.data.WallpaperSource.PATTERN -> "wallpaper -> generated pattern"
                    dev.retrobit.desktop.data.WallpaperSource.DEVICE -> "wallpaper -> device wallpaper (rendering…)"
                    dev.retrobit.desktop.data.WallpaperSource.IMAGE ->
                        "wallpaper -> your image; pick one in Settings if none is set"
                }
            )
            return
        }
        val s = arg?.let { a -> WallPattern.values().firstOrNull { it.name.equals(a, true) } }
        if (s == null) { print("patterns: " + WallPattern.values().joinToString(" | ") { it.name.lowercase() }, Kind.ERR); return }
        vm.setWallpaper(s)
        print("wallpaper -> ${s.label}")
    }

    private fun icons(arg: String?) {
        val s = arg?.let { a -> IconMode.values().firstOrNull { it.name.equals(a, true) } }
        if (s == null) { print("modes: color | mono | dither", Kind.ERR); return }
        vm.setIconMode(s)
        print("icon mode -> ${s.label}")
    }

    private fun scale(arg: String?) {
        val v = arg?.toFloatOrNull()
        if (v == null || v !in 0.6f..2.0f) { print("usage: scale 0.8 .. 1.6", Kind.ERR); return }
        vm.settings.uiScale = v
        print("ui scale -> $v")
    }

    private fun tiling(arg: String?) {
        when (arg?.lowercase()) {
            "on", "1", "true" -> { vm.setTiling(true); print("tiling -> on") }
            "off", "0", "false" -> { vm.setTiling(false); print("tiling -> off") }
            else -> print("usage: tiling on|off", Kind.ERR)
        }
    }

    private fun workspace(arg: String?) {
        val index = arg?.toIntOrNull()?.minus(1)
        if (index == null || index !in 0 until vm.wm.desktopCount) {
            print("usage: workspace 1..${vm.wm.desktopCount} (currently on desktop ${vm.wm.desktop + 1})", Kind.ERR)
            return
        }
        vm.switchDesktop(index)
        print("desktop ${index + 1} — ${vm.wm.onScreen.count { !it.minimized }} windows visible")
    }

    private fun wsMove(arg: String?) {
        val index = arg?.toIntOrNull()?.minus(1)
        if (index == null || index !in 0 until vm.wm.desktopCount) {
            print("usage: wsmove 1..${vm.wm.desktopCount}", Kind.ERR)
            return
        }
        if (vm.wm.focused == null) {
            print("no focused window", Kind.ERR)
            return
        }
        vm.moveFocusedToDesktop(index)
        print("window moved to desktop ${index + 1}")
    }

    private fun exit() {
        val term = vm.wm.windows.firstOrNull { it.kind == WinKind.TERMINAL }
        if (term != null) vm.wm.close(term.id)
    }
}
