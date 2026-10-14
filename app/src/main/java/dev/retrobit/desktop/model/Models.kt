package dev.retrobit.desktop.model

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

enum class DesktopStyle(val label: String, val osName: String, val tagline: String) {
    LINUX("Linux", "retrOS GNU/Linux", "top panel · side dock · terminal"),
    MACOS("macOS", "Retro System 9", "menu bar · dock · launchpad · spotlight"),
    WINDOWS("Windows", "Retrodows 95", "start menu · taskbar · system tray"),
}

/**
 * The colour scheme of the whole desktop.
 *
 * The first six are the **e-paper** palettes: one paper tone, one ink tone, one accent. The last
 * six are the **native desktop** ones: they use exactly the same e-paper engine, but with the
 * colours a familiar desktop uses, including the colour that desktop paints behind its icons
 * ([dev.retrobit.desktop.ui.theme.Palette.desk]).
 *
 * [style] is the shell a native palette belongs to, so picking one can bring its layout along
 * (Windows 95 goes with the Windows shell, Aqua with macOS, and so on); the e-paper palettes work
 * with every shell and leave the user's choice alone.
 */
enum class PaletteId(val label: String, val native: Boolean = false, val style: DesktopStyle? = null) {
    PAPER("Paper"),
    SEPIA("Sepia"),
    BWR("Tri-color"),
    INK("Ink"),
    PHOSPHOR("Phosphor"),
    AMBER("Amber"),

    // native desktops: a light and a dark take on each of the three shells
    WIN95("Windows 95", native = true, style = DesktopStyle.WINDOWS),
    WIN11("Windows 11", native = true, style = DesktopStyle.WINDOWS),
    AQUA("Aqua", native = true, style = DesktopStyle.MACOS),
    AQUA_DARK("Aqua dark", native = true, style = DesktopStyle.MACOS),
    UBUNTU("Ubuntu", native = true, style = DesktopStyle.LINUX),
    ADWAITA("Adwaita", native = true, style = DesktopStyle.LINUX),
}

enum class WallPattern(val label: String) {
    DOTS("Halftone"),
    GRID("Grid"),
    DITHER("Stipple"),
    HATCH("Hatch"),
    PLAIN("Plain"),
}

enum class IconMode(val label: String) {
    COLOR("Color"),
    MONO("Mono"),
    DITHER("1-bit"),
}

/**
 * The launcher's own apps. Each one opens **full screen** ([dev.retrobit.desktop.ui.desktop.ShellHost]);
 * third-party apps are launched plainly and fill the screen too - the cyberdeck chrome draws the
 * frame around whatever owns the screen.
 */
enum class WinKind(val singleton: Boolean) {
    APPS(false),
    TERMINAL(false),
    SETTINGS(true),
    SYSMON(true),
    NOTES(true),
    ABOUT(true),
    CALENDAR(true),
}

/**
 * Desktop widgets. Each one can be resized by hand; [defaultWidth] is the calendar width so every
 * widget starts out aligned.
 */
enum class WidgetId(val label: String, val defaultWidth: Int, val defaultHeight: Int) {
    CLOCK("chrono", 250, 0),
    CALENDAR("calendar", 250, 0),
    SYSTEM("sysinfo", 250, 0),
    NOTES("notes", 250, 0),
    AGENDA("agenda", 250, 0),
    TASKS("tasks", 250, 0),
    LAUNCHER("launcher", 250, 0),
    POWER("power", 250, 0),
    ;

    companion object {
        fun of(raw: String?): WidgetId? = entries.firstOrNull { it.name.equals(raw, true) || it.label.equals(raw, true) }
    }
}

/**
 * Shape of the desktop app icons. [AUTO] follows the active desktop style:
 * Windows → classic square bevel, macOS → rounded, Linux → flat.
 */
enum class IconTheme(val label: String, val hint: String) {
    AUTO("Follow desktop", "pick the shape from the active style"),
    CLASSIC("Classic 95", "square, bevelled, hard outline"),
    ROUNDED("Rounded", "rounded corners, outline"),
    FLAT("Flat", "soft corners, no outline"),
    PLAIN("Plain", "no frame at all"),
    ;

    companion object {
        fun forStyle(style: DesktopStyle): IconTheme = when (style) {
            DesktopStyle.WINDOWS -> CLASSIC
            DesktopStyle.MACOS -> ROUNDED
            DesktopStyle.LINUX -> FLAT
        }

        fun resolve(theme: IconTheme, style: DesktopStyle): IconTheme =
            if (theme == AUTO) forStyle(style) else theme
    }
}

data class AppEntry(
    val key: String,
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val user: UserHandle,
    val info: LauncherActivityInfo,
    val isWorkProfile: Boolean,
)
