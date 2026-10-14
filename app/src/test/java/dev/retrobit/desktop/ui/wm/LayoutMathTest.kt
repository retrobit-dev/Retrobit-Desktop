package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge snapping and manual resizing of a floating desktop window: which zone a window is dragged
 * into, where that zone puts it, and what the eight resize handles do to a rectangle.
 */
class LayoutMathTest {

    // the Linux shell on the 2560x1600 tablet: 78px dock on the left, 30px panel on top
    private val area = Rect(0f, 0f, 2404f, 1540f)
    private val minW = 360f
    private val minH = 260f

    private fun rect(l: Float, t: Float, r: Float, b: Float) = Rect(l, t, r, b)

    @Test
    fun `a window dragged into the left edge fills the left half`() {
        val zone = LayoutMath.zoneFor(area, rect(20f, 400f, 800f, 1000f))
        assertEquals(SnapZone.LEFT, zone)
        assertEquals(rect(0f, 0f, 1202f, 1540f), LayoutMath.rectFor(area, zone!!))
    }

    @Test
    fun `a window dragged into the right edge fills the right half`() {
        val zone = LayoutMath.zoneFor(area, rect(2000f, 300f, 2400f, 900f))
        assertEquals(SnapZone.RIGHT, zone)
        assertEquals(rect(1202f, 0f, 2404f, 1540f), LayoutMath.rectFor(area, zone!!))
    }

    @Test
    fun `a window dragged to the top centre maximizes`() {
        val zone = LayoutMath.zoneFor(area, rect(1000f, 6f, 1600f, 600f))
        assertEquals(SnapZone.MAXIMIZE, zone)
        assertEquals(area, LayoutMath.rectFor(area, zone!!))
    }

    @Test
    fun `a window in the middle of the screen snaps nowhere`() {
        assertNull(LayoutMath.zoneFor(area, rect(900f, 500f, 1500f, 1100f)))
    }

    @Test
    fun `the zone and its rectangle go together`() {
        SnapZone.entries.forEach { zone ->
            val r = LayoutMath.rectFor(area, zone, gap = 4f)
            assertTrue("empty rectangle for $zone", r.width > 0f && r.height > 0f)
            assertTrue("$zone left the desktop", r.left >= area.left && r.right <= area.right)
            assertTrue("$zone left the desktop", r.top >= area.top && r.bottom <= area.bottom)
        }
    }

    @Test
    fun `every edge and corner is a resize handle`() {
        val r = rect(300f, 300f, 900f, 800f)
        val handle = 16f
        assertEquals(ResizeEdge.TOP_LEFT, LayoutMath.edgeAt(Offset(302f, 302f), r, handle))
        assertEquals(ResizeEdge.TOP_RIGHT, LayoutMath.edgeAt(Offset(898f, 302f), r, handle))
        assertEquals(ResizeEdge.BOTTOM_LEFT, LayoutMath.edgeAt(Offset(302f, 798f), r, handle))
        assertEquals(ResizeEdge.BOTTOM_RIGHT, LayoutMath.edgeAt(Offset(898f, 798f), r, handle))
        assertEquals(ResizeEdge.LEFT, LayoutMath.edgeAt(Offset(302f, 550f), r, handle))
        assertEquals(ResizeEdge.RIGHT, LayoutMath.edgeAt(Offset(898f, 550f), r, handle))
        assertEquals(ResizeEdge.TOP, LayoutMath.edgeAt(Offset(600f, 302f), r, handle))
        assertEquals(ResizeEdge.BOTTOM, LayoutMath.edgeAt(Offset(600f, 798f), r, handle))
        assertNull(LayoutMath.edgeAt(Offset(600f, 550f), r, handle))
    }

    @Test
    fun `dragging the right edge grows the window to the right only`() {
        val r = rect(300f, 300f, 900f, 800f)
        val out = LayoutMath.resize(r, ResizeEdge.RIGHT, dx = 120f, dy = 80f, minW = minW, minH = minH, bounds = area)
        assertEquals(rect(300f, 300f, 1020f, 800f), out)
    }

    @Test
    fun `dragging the left edge moves the left side and keeps the right one`() {
        val r = rect(300f, 300f, 900f, 800f)
        val out = LayoutMath.resize(r, ResizeEdge.LEFT, dx = -80f, dy = 40f, minW = minW, minH = minH, bounds = area)
        assertEquals(rect(220f, 300f, 900f, 800f), out)
    }

    @Test
    fun `the bottom right corner resizes both sides at once`() {
        val r = rect(300f, 300f, 900f, 800f)
        val out = LayoutMath.resize(r, ResizeEdge.BOTTOM_RIGHT, dx = 60f, dy = 90f, minW = minW, minH = minH, bounds = area)
        assertEquals(rect(300f, 300f, 960f, 890f), out)
    }

    @Test
    fun `a window cannot be resized smaller than the minimum size`() {
        val r = rect(300f, 300f, 900f, 800f)
        val out = LayoutMath.resize(r, ResizeEdge.RIGHT, dx = -2000f, dy = 0f, minW = minW, minH = minH, bounds = area)
        assertEquals(minW, out.width, 0.01f)
        assertEquals(r.left, out.left, 0.01f)
    }

    @Test
    fun `a window cannot be resized past the edge of the desktop`() {
        val r = rect(1800f, 300f, 2400f, 800f)
        val out = LayoutMath.resize(r, ResizeEdge.RIGHT, dx = 800f, dy = 0f, minW = minW, minH = minH, bounds = area)
        assertEquals(area.right, out.right, 0.01f)
    }

    @Test
    fun `a window cannot be dragged above or left of the desktop`() {
        val r = rect(50f, 50f, 650f, 550f)
        val up = LayoutMath.resize(r, ResizeEdge.TOP, dx = 0f, dy = -400f, minW = minW, minH = minH, bounds = area)
        assertEquals(area.top, up.top, 0.01f)
        val left = LayoutMath.resize(r, ResizeEdge.LEFT, dx = -400f, dy = 0f, minW = minW, minH = minH, bounds = area)
        assertEquals(area.left, left.left, 0.01f)
        assertEquals(r.right, left.right, 0.01f)
    }

    @Test
    fun `every app gets its own default window size, none of them full screen`() {
        listOf("APPS", "TERMINAL", "SETTINGS", "SYSMON", "NOTES", "ABOUT", "CALENDAR").forEach { kind ->
            val (w, h) = LayoutMath.defaultFraction(kind)
            assertTrue("$kind would open full screen", w < 0.95f && h < 0.95f)
            assertTrue("$kind would open tiny", w > 0.3f && h > 0.3f)
        }
        assertTrue(LayoutMath.defaultFraction("APPS").second > LayoutMath.defaultFraction("NOTES").second)
    }
}
