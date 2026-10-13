package dev.retrobit.desktop.ui.wm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop icon grid: placement, mirroring and the "find the nearest free cell" fallback. */
class GridMathTest {

    private val spec = GridMath.spec(width = 1000f, height = 600f, cellW = 100f, cellH = 120f)

    @Test
    fun `grid dimensions come from the desktop size and the cell size`() {
        assertEquals(10, spec.cols)
        assertEquals(5, spec.rows)
        assertEquals(50, spec.capacity)
    }

    @Test
    fun `small desktops still get a usable grid`() {
        val tiny = GridMath.spec(width = 40f, height = 30f, cellW = 100f, cellH = 120f)
        assertEquals(GridMath.MIN_COLS, tiny.cols)
        assertEquals(1, tiny.rows)
    }

    @Test
    fun `cell offsets follow the cell size`() {
        assertEquals(0f, GridMath.offsetOf(spec, 0, 0).x, 0.01f)
        assertEquals(300f, GridMath.offsetOf(spec, 3, 0).x, 0.01f)
        assertEquals(240f, GridMath.offsetOf(spec, 0, 2).y, 0.01f)
    }

    @Test
    fun `mirrored grids count columns from the right edge`() {
        val mirrored = spec.copy(mirrorRight = true)
        // column 0 is the right-most column
        assertEquals(900f, GridMath.offsetOf(mirrored, 0, 0).x, 0.01f)
        assertEquals(0f, GridMath.offsetOf(mirrored, 9, 0).x, 0.01f)
    }

    @Test
    fun `a point maps to the cell that contains it`() {
        assertEquals(0 to 0, GridMath.cellAt(spec, 10f, 10f))
        assertEquals(2 to 1, GridMath.cellAt(spec, 250f, 150f))
        assertEquals(9 to 4, GridMath.cellAt(spec, 999f, 599f))
    }

    @Test
    fun `points outside the grid are clamped to the edge cells`() {
        assertEquals(0 to 0, GridMath.cellAt(spec, -50f, -50f))
        assertEquals(9 to 4, GridMath.cellAt(spec, 5000f, 5000f))
    }

    @Test
    fun `mirrored cell lookup matches mirrored offsets`() {
        val mirrored = spec.copy(mirrorRight = true)
        val cell = GridMath.cellAt(mirrored, 950f, 10f)
        assertEquals(0 to 0, cell)
        assertEquals(900f, GridMath.offsetOf(mirrored, cell.first, cell.second).x, 0.01f)
    }

    @Test
    fun `contains only accepts points inside the grid`() {
        assertTrue(GridMath.contains(spec, 500f, 300f))
        assertFalse(GridMath.contains(spec, 1001f, 300f))
        assertFalse(GridMath.contains(spec, 500f, -1f))
    }

    @Test
    fun `auto layout fills column by column`() {
        val cells = GridMath.autoCells(spec, 7)
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 1 to 0, 1 to 1), cells)
    }

    @Test
    fun `auto layout keeps working when there are more icons than cells`() {
        val cells = GridMath.autoCells(spec, 60)
        assertEquals(60, cells.size)
        assertEquals(listOf(9 to 4), cells.takeLast(1))
    }

    @Test
    fun `a free target cell is used as is`() {
        val occupied = setOf(0 to 0, 1 to 0)
        assertEquals(3 to 2, GridMath.nearestFree(spec, occupied, 3 to 2))
    }

    @Test
    fun `an occupied cell falls back to the closest free neighbour`() {
        val occupied = setOf(0 to 0, 1 to 0, 0 to 1)
        val free = GridMath.nearestFree(spec, occupied, 0 to 0)
        assertFalse(free in occupied)
        // (1,1) is two steps away, (2,0) also two — both acceptable, but never the target itself
        assertTrue(free == (1 to 1) || free == (2 to 0))
    }

    @Test
    fun `fallback prefers the shortest distance`() {
        // everything in ring 1 around (2,2) is taken, so it must pick something in ring 2
        val occupied = buildSet {
            add(2 to 2)
            for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) add(2 + dx to 2 + dy)
        }
        val free = GridMath.nearestFree(spec, occupied, 2 to 2)
        assertFalse(free in occupied)
        val distance = kotlin.math.abs(free.first - 2) + kotlin.math.abs(free.second - 2)
        assertEquals(2, distance)
    }

    @Test
    fun `a full grid returns the requested cell instead of looping forever`() {
        val full = HashSet<Pair<Int, Int>>()
        for (c in 0 until spec.cols) for (r in 0 until spec.rows) full += c to r
        assertEquals(4 to 3, GridMath.nearestFree(spec, full, 4 to 3))
    }

    @Test
    fun `reading order index is row major`() {
        assertEquals(0, GridMath.indexOf(spec, 0 to 0))
        assertEquals(10, GridMath.indexOf(spec, 0 to 1))
        assertEquals(14, GridMath.indexOf(spec, 4 to 1))
    }
}
