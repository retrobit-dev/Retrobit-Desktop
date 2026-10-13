package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Offset

/**
 * Geometry of the desktop icon grid.
 *
 * [mirrorRight] lays columns out from the right edge (classic macOS behaviour); the top-left is
 * the origin of the cell numbering either way, so the stored (col,row) pairs stay meaningful when
 * the shell or the screen orientation changes.
 */
data class GridSpec(
    val cols: Int,
    val rows: Int,
    val cellW: Float,
    val cellH: Float,
    val originX: Float,
    val originY: Float,
    val mirrorRight: Boolean,
) {
    val capacity: Int get() = cols * rows
}

/** Pure placement maths for freely positioned desktop icons (unit tested in GridMathTest). */
object GridMath {

    const val MIN_COLS = 2

    fun spec(
        width: Float,
        height: Float,
        cellW: Float,
        cellH: Float,
        padX: Float = 0f,
        padY: Float = 0f,
        mirrorRight: Boolean = false,
    ): GridSpec {
        val usableW = (width - padX * 2f).coerceAtLeast(cellW)
        val usableH = (height - padY * 2f).coerceAtLeast(cellH)
        val cols = (usableW / cellW).toInt().coerceAtLeast(MIN_COLS)
        val rows = (usableH / cellH).toInt().coerceAtLeast(1)
        return GridSpec(cols, rows, cellW, cellH, padX, padY, mirrorRight)
    }

    /** Top-left pixel offset of a cell, taking the mirroring into account. */
    fun offsetOf(spec: GridSpec, col: Int, row: Int): Offset {
        val c = if (spec.mirrorRight) (spec.cols - 1 - col) else col
        return Offset(spec.originX + c * spec.cellW, spec.originY + row * spec.cellH)
    }

    /** Cell under a point, clamped into the grid. */
    fun cellAt(spec: GridSpec, x: Float, y: Float): Pair<Int, Int> {
        val rawCol = ((x - spec.originX) / spec.cellW).toInt()
        val rawRow = ((y - spec.originY) / spec.cellH).toInt()
        val col = if (spec.mirrorRight) (spec.cols - 1 - rawCol) else rawCol
        return col.coerceIn(0, spec.cols - 1) to rawRow.coerceIn(0, spec.rows - 1)
    }

    /** True when the point is inside the grid area at all (used to detect "dropped outside"). */
    fun contains(spec: GridSpec, x: Float, y: Float): Boolean {
        val right = spec.originX + spec.cols * spec.cellW
        val bottom = spec.originY + spec.rows * spec.cellH
        return x >= spec.originX && x <= right && y >= spec.originY && y <= bottom
    }

    /**
     * Default arrangement: fill top → bottom, then the next column (Windows/macOS "auto arrange").
     * Columns run right-to-left when [GridSpec.mirrorRight] is set.
     */
    fun autoCells(spec: GridSpec, count: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(count)
        var index = 0
        while (out.size < count && index < spec.capacity) {
            out += index % spec.cols to index / spec.cols
            index++
        }
        // if there are more icons than cells, keep appending on the last column
        while (out.size < count) out += (spec.cols - 1) to (spec.rows - 1)
        return out
    }

    /**
     * Closest free cell to [target] (Manhattan rings). Returns [target] itself when the grid is full
     * or the target is free.
     */
    fun nearestFree(spec: GridSpec, occupied: Set<Pair<Int, Int>>, target: Pair<Int, Int>): Pair<Int, Int> {
        if (target !in occupied) return target
        for (radius in 1..spec.capacity) {
            for (dx in -radius..radius) {
                val dyAbs = radius - kotlin.math.abs(dx)
                for (dy in if (dyAbs == 0) listOf(0) else listOf(dyAbs, -dyAbs)) {
                    val cell = (target.first + dx) to (target.second + dy)
                    if (cell.first in 0 until spec.cols && cell.second in 0 until spec.rows && cell !in occupied) return cell
                }
            }
        }
        return target
    }

    /** Index of a cell in reading order, used as a tie-breaker when saving positions. */
    fun indexOf(spec: GridSpec, cell: Pair<Int, Int>): Int = cell.second * spec.cols + cell.first
}
