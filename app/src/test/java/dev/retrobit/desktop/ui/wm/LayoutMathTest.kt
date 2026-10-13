package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure window-manager maths: master–stack tiling and screen-edge snapping.
 * Run with `./gradlew :app:testDebugUnitTest` (the CI workflow does this on every push).
 */
class LayoutMathTest {

    private val area = Rect(0f, 40f, 1200f, 800f)

    // ── the desktop area is respected ───────────────────────────────────────────
    private fun assertInside(r: Rect, label: String) {
        assertTrue("$label left ${r.left} < ${area.left}", r.left >= area.left - 0.01f)
        assertTrue("$label top ${r.top} < ${area.top}", r.top >= area.top - 0.01f)
        assertTrue("$label right ${r.right} > ${area.right}", r.right <= area.right + 0.01f)
        assertTrue("$label bottom ${r.bottom} > ${area.bottom}", r.bottom <= area.bottom + 0.01f)
    }

    // ── tiling ──────────────────────────────────────────────────────────────────
    @Test
    fun `tiling with one window fills the whole desktop`() {
        val rects = LayoutMath.tilingRects(area, 1, margin = 8f)
        assertEquals(1, rects.size)
        assertEquals(area, rects[0])
    }

    @Test
    fun `tiling with zero windows returns nothing`() {
        assertTrue(LayoutMath.tilingRects(area, 0, margin = 8f).isEmpty())
    }

    @Test
    fun `two windows split the desktop in half with a gap`() {
        val rects = LayoutMath.tilingRects(area, 2, margin = 10f)
        assertEquals(2, rects.size)
        assertInside(rects[0], "left")
        assertInside(rects[1], "right")
        // equal widths
        assertEquals(rects[0].width, rects[1].width, 0.01f)
        // the gap between them is exactly 2 * margin
        assertEquals(2f * 10f, rects[1].left - rects[0].right, 0.01f)
        // both are full height
        assertEquals(area.height, rects[0].height, 0.01f)
        assertEquals(area.height, rects[1].height, 0.01f)
    }

    @Test
    fun `master stack keeps the master on the left and stacks the rest`() {
        val count = 4
        val margin = 12f
        val rects = LayoutMath.tilingRects(area, count, margin)
        assertEquals(count, rects.size)
        rects.forEachIndexed { i, r -> assertInside(r, "window $i") }

        val master = rects.first()
        val stack = rects.drop(1)
        // master is taller than any stacked window and sits on the left
        assertTrue(master.height > stack[0].height)
        assertEquals(area.top, master.top, 0.01f)
        assertTrue(stack.all { it.left > master.right })
        // stacked windows never overlap vertically
        for (i in 0 until stack.size - 1) {
            assertTrue("stack $i overlaps ${i + 1}", stack[i].bottom <= stack[i + 1].top + 0.01f)
        }
        // the last window reaches the bottom edge
        assertEquals(area.bottom, stack.last().bottom, 0.01f)
    }

    @Test
    fun `tiling is stable for a large number of windows`() {
        val rects = LayoutMath.tilingRects(area, 9, margin = 6f)
        assertEquals(9, rects.size)
        rects.forEachIndexed { i, r -> assertInside(r, "window $i") }
        assertTrue(rects.all { it.width > 0f && it.height > 0f })
    }

    @Test
    fun `tiling degrades gracefully on a zero sized area`() {
        val empty = Rect(0f, 0f, 0f, 0f)
        assertEquals(4, LayoutMath.tilingRects(empty, 4, margin = 8f).size)
    }

    // ── snapping ────────────────────────────────────────────────────────────────
    @Test
    fun `dragging to the left edge snaps left`() {
        val window = Rect(area.left + 4f, 300f, area.left + 604f, 700f)
        assertEquals(SnapZone.LEFT, LayoutMath.zoneFor(area, window))
        val snapped = LayoutMath.rectFor(area, SnapZone.LEFT)
        assertEquals(area.left, snapped.left, 0.01f)
        assertEquals(area.width / 2f, snapped.width, 0.01f)
        assertInside(snapped, "snapped left")
    }

    @Test
    fun `dragging to the right edge snaps right`() {
        val window = Rect(area.right - 604f, 300f, area.right - 4f, 700f)
        assertEquals(SnapZone.RIGHT, LayoutMath.zoneFor(area, window))
        val snapped = LayoutMath.rectFor(area, SnapZone.RIGHT)
        assertEquals(area.right, snapped.right, 0.01f)
        assertEquals(area.width / 2f, snapped.width, 0.01f)
    }

    @Test
    fun `dragging to the top centre maximizes`() {
        val window = Rect(area.center.x - 300f, area.top + 2f, area.center.x + 300f, 400f)
        assertEquals(SnapZone.MAXIMIZE, LayoutMath.zoneFor(area, window))
        assertEquals(area, LayoutMath.rectFor(area, SnapZone.MAXIMIZE))
    }

    @Test
    fun `a window floating in the middle does not snap`() {
        val window = Rect(area.center.x - 250f, area.center.y - 150f, area.center.x + 250f, area.center.y + 150f)
        assertNull(LayoutMath.zoneFor(area, window))
    }

    @Test
    fun `the top left corner snaps left instead of maximizing`() {
        val window = Rect(area.left + 4f, area.top + 1f, area.left + 404f, 400f)
        assertEquals(SnapZone.LEFT, LayoutMath.zoneFor(area, window))
    }

    @Test
    fun `the top right corner snaps right instead of maximizing`() {
        val window = Rect(area.right - 404f, area.top + 1f, area.right - 4f, 400f)
        assertEquals(SnapZone.RIGHT, LayoutMath.zoneFor(area, window))
    }

    @Test
    fun `a small window at the top but off to one side does not maximize`() {
        val window = Rect(area.left + 120f, area.top + 1f, area.left + 480f, 240f)
        assertNull(LayoutMath.zoneFor(area, window))
    }

    @Test
    fun `snapping is disabled on a degenerate desktop`() {
        val tiny = Rect(0f, 0f, 10f, 10f)
        assertNull(LayoutMath.zoneFor(tiny, Rect(0f, 0f, 10f, 10f)))
    }

    // ── manual resizing: every edge and corner is a handle ───────────────────────

    private val win = Rect(400f, 300f, 800f, 600f)

    @Test
    fun `dragging the right edge only changes the width`() {
        val r = LayoutMath.resize(win, ResizeEdge.RIGHT, 60f, 0f, 200f, 120f, area)
        assertEquals(win.left, r.left, 0.01f)
        assertEquals(win.top, r.top, 0.01f)
        assertEquals(win.bottom, r.bottom, 0.01f)
        assertEquals(860f, r.right, 0.01f)
    }

    @Test
    fun `dragging the left edge moves the left side and keeps the right side`() {
        val r = LayoutMath.resize(win, ResizeEdge.LEFT, -50f, 0f, 200f, 120f, area)
        assertEquals(350f, r.left, 0.01f)
        assertEquals(win.right, r.right, 0.01f)
    }

    @Test
    fun `the bottom right corner resizes both axes at once`() {
        val r = LayoutMath.resize(win, ResizeEdge.BOTTOM_RIGHT, 30f, 40f, 200f, 120f, area)
        assertEquals(830f, r.right, 0.01f)
        assertEquals(640f, r.bottom, 0.01f)
        assertEquals(win.left, r.left, 0.01f)
        assertEquals(win.top, r.top, 0.01f)
    }

    @Test
    fun `the top left corner resizes up and to the left`() {
        val r = LayoutMath.resize(win, ResizeEdge.TOP_LEFT, -20f, -30f, 200f, 120f, area)
        assertEquals(380f, r.left, 0.01f)
        assertEquals(area.top, r.top, 0.01f)
    }

    @Test
    fun `a window never shrinks below the minimum size`() {
        val tiny = Rect(100f, 100f, 340f, 260f)
        val r = LayoutMath.resize(tiny, ResizeEdge.RIGHT, -500f, 0f, 200f, 120f, area)
        assertEquals(220f, r.width, 0.01f)
        val r2 = LayoutMath.resize(tiny, ResizeEdge.BOTTOM, 0f, -500f, 200f, 120f, area)
        assertEquals(120f, r2.height, 0.01f)
    }

    @Test
    fun `resizing stops at the desktop edge`() {
        val r = LayoutMath.resize(win, ResizeEdge.RIGHT, 5000f, 5000f, 200f, 120f, area)
        assertEquals(area.right, r.right, 0.01f)
    }

    @Test
    fun `a hit on the left border reports the left handle`() {
        val edge = LayoutMath.edgeAt(Offset(win.left + 3f, win.center.y), win, 14f)
        assertEquals(ResizeEdge.LEFT, edge)
    }

    @Test
    fun `a hit on the bottom right corner reports the corner handle`() {
        val edge = LayoutMath.edgeAt(Offset(win.right - 4f, win.bottom - 4f), win, 14f)
        assertEquals(ResizeEdge.BOTTOM_RIGHT, edge)
    }

    @Test
    fun `a hit in the middle of the window reports no handle`() {
        assertNull(LayoutMath.edgeAt(win.center, win, 14f))
    }
}
