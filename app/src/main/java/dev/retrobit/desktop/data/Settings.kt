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
    var showWidgets: Boolean by boolPref("showWidgets", true)
    var bootSequence: Boolean by boolPref("bootSequence", true)
    var hideSystemBars: Boolean by boolPref("hideSystemBars", true)
    var tiling: Boolean by boolPref("tiling", false)
    /** M-Pencil / stylus: double tap behaves as a right click (context menu). */
    var penRightClick: Boolean by boolPref("penRightClick", true)
    /** Draw an e-paper pointer arrow while a pen or mouse is in use. */
    var showCursor: Boolean by boolPref("showCursor", true)
    /** Drag a window against a screen edge to snap it (half screen / maximize). */
    var snapWindows: Boolean by boolPref("snapWindows", true)
    /** Virtual desktops (workspaces), 1..4. */
    var workspaceCount: Int by intPref("workspaceCount", 3)
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

    private fun listPref(key: String): PrefState<List<String>> {
        val raw = sp.getString(key, "") ?: ""
        val initial = raw.split('\n').filter { it.isNotBlank() }
        return PrefState(initial) { sp.edit().putString(key, it.joinToString("\n")).apply() }
    }
}
