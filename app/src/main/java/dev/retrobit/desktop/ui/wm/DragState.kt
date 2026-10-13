package dev.retrobit.desktop.ui.wm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.retrobit.desktop.model.AppEntry

data class DragInfo(val app: AppEntry, val origin: Offset, val position: Offset)

/** Shared drag-and-drop state so icons on any surface (grid, dock, sidebar) can talk to each other. */
class DragState {
    var info by mutableStateOf<DragInfo?>(null)

    /** Where the last drag was released (root coordinates) — used to resolve a grid cell. */
    var lastPosition: Offset = Offset.Zero
    var desktopBounds = Rect.Zero
    var dockBounds = Rect.Zero
    var sidebarBounds = Rect.Zero

    fun start(app: AppEntry, origin: Offset) { info = DragInfo(app, origin, origin) }
    fun move(position: Offset) { info = info?.copy(position = position) }
    val active: Boolean get() = info != null
    fun clear() {
        info?.let { lastPosition = it.position }
        info = null
    }

    enum class Drop { NONE, DOCK, SIDEBAR, DESKTOP, TASKBAR }

    fun dropTarget(): Drop = when (val d = info) {
        null -> Drop.NONE
        else -> when {
            dockBounds.contains(d.position) -> Drop.DOCK
            sidebarBounds.contains(d.position) -> Drop.SIDEBAR
            desktopBounds.contains(d.position) -> Drop.DESKTOP
            else -> Drop.TASKBAR
        }
    }
}

val LocalDragState = staticCompositionLocalOf<DragState> { error("DragState not provided") }
