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
 * Shape of the desktop app icons.
 *
 * [AUTO] follows the **palette** first — every native desktop theme brings the icon shape that
 * desktop really uses — and falls back to the desktop style (Windows → classic square bevel,
 * macOS → rounded, Linux → flat) for the e-paper palettes, which have no desktop of their own.
 * The rest are the shapes themselves: pick one to override the theme for good.
 */
enum class IconTheme(val label: String, val hint: String) {
    AUTO("Follow palette", "the shape of the active palette (or of the desktop style)"),
    CLASSIC("Classic 95", "square, bevelled, hard outline"),
    ROUNDED("Rounded", "rounded corners, outline"),
    FLAT("Flat", "soft corners, no outline"),
    SQUIRCLE("Fluent", "rounded square, soft edge, no outline"),
    AQUA("Aqua", "glossy rounded tile, thin edge"),
    YARU("Yaru", "rounded square with an accent trim"),
    CIRCLE("GNOME", "circle with a thin ring"),
    PLAIN("Plain", "no frame at all"),
    ;

    companion object {
        fun forStyle(style: DesktopStyle): IconTheme = when (style) {
            DesktopStyle.WINDOWS -> CLASSIC
            DesktopStyle.MACOS -> ROUNDED
            DesktopStyle.LINUX -> FLAT
        }

        /**
         * The icon shape that belongs to a palette, or `null` for the e-paper palettes (they are
         * printed 1-bit and take their shape from the desktop style instead).
         */
        fun forPalette(palette: PaletteId): IconTheme? = when (palette) {
            PaletteId.WIN95 -> CLASSIC
            PaletteId.WIN11 -> SQUIRCLE
            PaletteId.AQUA, PaletteId.AQUA_DARK -> AQUA
            PaletteId.UBUNTU -> YARU
            PaletteId.ADWAITA -> CIRCLE
            PaletteId.PAPER, PaletteId.SEPIA, PaletteId.BWR,
            PaletteId.INK, PaletteId.PHOSPHOR, PaletteId.AMBER,
            -> null
        }

        /**
         * The shape actually used for drawing: an explicit choice wins, otherwise the palette
         * decides, otherwise the desktop style.
         */
        fun resolve(theme: IconTheme, style: DesktopStyle, palette: PaletteId? = null): IconTheme =
            if (theme != AUTO) theme else palette?.let { forPalette(it) } ?: forStyle(style)

        /** How this shape is drawn, and where it comes from. Used by the terminal and Settings. */
        fun describe(theme: IconTheme, style: DesktopStyle, palette: PaletteId?): String =
            "${resolve(theme, style, palette).label} (${theme.label.lowercase()})"

        /**
         * Corner radius of the tile a shape draws for a side of [side] px: half the side for a
         * circle, the rounding that desktop uses for a rounded square, and a hard square for the
         * printed/squared shapes.
         *
         * `data/IconCache` frames the app icons with it and `GlyphTile` draws the **internal icons**
         * of the shell with it, so an app icon and a shell icon of one theme are the same shape.
         */
        fun tileRadius(theme: IconTheme, side: Float): Float = when (theme) {
            CLASSIC, PLAIN -> 0f
            ROUNDED -> side * 0.20f
            FLAT -> side * 0.06f
            SQUIRCLE -> side * 0.24f
            AQUA -> side * 0.26f
            YARU -> side * 0.16f
            CIRCLE -> side * 0.5f
            AUTO -> 0f
        }

        /** How far the tile is inset from the icon box: the printed square sits closer to the edge. */
        fun tileInset(theme: IconTheme, side: Float): Float = when (theme) {
            CLASSIC -> side * 0.06f
            ROUNDED -> side * 0.05f
            CIRCLE -> side * 0.04f
            PLAIN -> 0f
            else -> side * 0.03f
        }
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
