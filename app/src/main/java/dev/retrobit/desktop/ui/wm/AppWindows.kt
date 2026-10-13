package dev.retrobit.desktop.ui.wm

import androidx.compose.runtime.mutableStateMapOf

/**
 * Bookkeeping for "app windows": which desktop windows wrap a real, launched app.
 *
 * Android does not allow one app to render another app inside its own view, so an app window owns
 * the chrome while the app itself runs in its own task (full screen, or floating on freeform
 * tablets). This registry remembers which windows have already sent their app off, so the window
 * can show the right button ("bring it back" instead of "open app") and forget the state when the
 * window is closed.
 */
class AppWindows {

    private val launched = mutableStateMapOf<Int, Boolean>()

    fun markLaunched(windowId: Int) {
        launched[windowId] = true
    }

    fun isRunning(windowId: Int): Boolean = launched[windowId] == true

    fun forget(windowId: Int) {
        launched.remove(windowId)
    }

    /** Forgets windows that no longer exist. */
    fun retain(ids: Set<Int>) {
        launched.keys.filterNot { it in ids }.forEach { launched.remove(it) }
    }

    fun clear() = launched.clear()
}
