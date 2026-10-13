package dev.retrobit.desktop.model

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

enum class DesktopStyle(val label: String, val osName: String, val tagline: String) {
    LINUX("Linux", "retrOS GNU/Linux", "top panel · side dock · tiling · terminal"),
    MACOS("macOS", "Retro System 9", "menu bar · dock · launchpad · spotlight"),
    WINDOWS("Windows", "Retrodows 95", "start menu · taskbar · system tray"),
}

enum class PaletteId(val label: String) {
    PAPER("Paper"),
    SEPIA("Sepia"),
    BWR("Tri-color"),
    INK("Ink"),
    PHOSPHOR("Phosphor"),
    AMBER("Amber"),
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

enum class WinKind(val singleton: Boolean) {
    APPS(false),
    TERMINAL(false),
    SETTINGS(true),
    SYSMON(true),
    NOTES(true),
    ABOUT(true),
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
