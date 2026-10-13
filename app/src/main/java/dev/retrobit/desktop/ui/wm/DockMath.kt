package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Pure maths behind the reorderable dock / sidebar.
 *
 * The dock records where each slot is on screen; a dropped icon is inserted at the slot nearest to
 * the pointer, which is what makes "move the icon anywhere along the dock" work with a finger,
 * the M-Pencil or a mouse. Android-free so it can be unit tested on the JVM.
 */
object DockMath {

    /** Index of the slot the pointer is nearest to, or -1 when the dock is empty. */
    fun slotIndex(slots: List<Offset>, pointer: Offset, horizontal: Boolean): Int {
        if (slots.isEmpty()) return -1
        var best = 0
        var bestDistance = Float.MAX_VALUE
        slots.forEachIndexed { index, slot ->
            val d = if (horizontal) kotlin.math.abs(slot.x - pointer.x) else kotlin.math.abs(slot.y - pointer.y)
            if (d < bestDistance) {
                bestDistance = d
                best = index
            }
        }
        return best
    }

    /**
     * Moves [pkg] to [index] in the pinned list. Works for both directions (dragging left or right,
     * up or down) and clamps the target so the icon never falls off the dock.
     */
    fun move(items: List<String>, pkg: String, index: Int): List<String> {
        val current = items.indexOf(pkg)
        val list = items.toMutableList()
        if (current >= 0) list.removeAt(current)
        val target = index.coerceIn(0, list.size)
        list.add(target, pkg)
        return list
    }

    /** True while the pointer is inside the dock strip, with a little slack for fat fingers. */
    fun inside(pointer: Offset, dock: Rect, slack: Float = 12f): Boolean =
        dock.width > 0f && dock.height > 0f &&
            pointer.x >= dock.left - slack && pointer.x <= dock.right + slack &&
            pointer.y >= dock.top - slack && pointer.y <= dock.bottom + slack
}

/**
 * Remembers where each dock slot sits on screen. Filled during layout, read while an icon is being
 * dragged, so the shells can show an insertion marker and drop into the nearest slot.
 */
class DockSlots(val horizontal: Boolean = true) {
    private val centers = ArrayList<Offset>()

    fun record(index: Int, center: Offset) {
        while (centers.size <= index) centers.add(Offset.Zero)
        centers[index] = center
    }

    fun trim(count: Int) {
        while (centers.size > count) centers.removeAt(centers.size - 1)
        while (centers.size < count) centers.add(Offset.Zero)
    }

    val size: Int get() = centers.size

    fun indexFor(pointer: Offset): Int = DockMath.slotIndex(centers, pointer, horizontal)

    fun centerOf(index: Int): Offset? = centers.getOrNull(index)?.takeIf { it != Offset.Zero }
}
