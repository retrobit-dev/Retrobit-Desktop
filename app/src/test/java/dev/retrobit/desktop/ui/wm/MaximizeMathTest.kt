package dev.retrobit.desktop.ui.wm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Maximize has to fill the desktop, and restore has to bring the window back to **exactly** the
 * rectangle it had - that is the part users notice immediately when it is wrong. The same maths
 * decides where a window opens when the tablet has never seen that app before.
 */
class MaximizeMathTest {

    // the Linux shell on the 2560x1600 tablet, with the safe area already taken out
    private val area = Placement(78, 30, 2482, 1570)
    private val margin = 10f
    private val cascade = 34f
    private val minW = 360f
    private val minH = 260f

    private fun restore(saved: Placement?) =
        MaximizeMath.restore(saved, area, margin, cascade, minW, minH)

    private fun open(remembered: Placement?, fraction: Pair<Float, Float> = 0.62f to 0.60f) =
        MaximizeMath.openRect(remembered, area, fraction, margin, cascade, minW, minH)

    @Test
    fun `maximize fills the whole desktop area`() {
        val r = MaximizeMath.maximized(area)
        assertEquals(area, r)
        assertEquals(area.width, r.width)
        assertEquals(area.height, r.height)
    }

    @Test
    fun `restore puts the window back exactly where it was`() {
        val saved = Placement(100, 200, 900, 800)
        assertEquals(saved, restore(saved))
    }

    @Test
    fun `restore without a remembered rectangle lands near the corner`() {
        val r = restore(null)
        // 0.62 / 0.60 of the desktop, at area + margin + cascade
        assertEquals(Placement(122, 74, 1612, 998), r)
    }

    @Test
    fun `restore clamps a remembered rectangle that hangs over the right edge`() {
        val r = restore(Placement(2400, 1500, 3200, 2100))
        assertEquals(Placement(1682, 970, 2482, 1570), r)
        assertTrue(r.right <= area.right && r.bottom <= area.bottom)
    }

    @Test
    fun `restore grows a window that is smaller than the minimum size`() {
        val r = restore(Placement(100, 200, 200, 280))
        assertEquals(Placement(100, 200, 460, 460), r)
    }

    @Test
    fun `restore shrinks a window that is larger than the desktop`() {
        val r = restore(Placement(0, 0, 4000, 2000))
        assertEquals(area, r)
    }

    @Test
    fun `restore keeps a window that was moved out of the area inside it`() {
        val r = restore(Placement(5000, 5000, 5800, 5600))
        assertEquals(Placement(1682, 970, 2482, 1570), r)
    }

    @Test
    fun `a degenerate remembered rectangle falls back to the default size`() {
        val r = restore(Placement(300, 300, 300, 300))
        assertEquals(Placement(122, 74, 1612, 998), r)
    }

    @Test
    fun `restore never returns an empty rectangle, even on a collapsed desktop`() {
        val r = MaximizeMath.restore(null, Placement(0, 0, 0, 0), margin, cascade, minW, minH)
        assertTrue(r.width >= 1 && r.height >= 1)
        assertTrue(r.isUsable)
    }

    // ── opening a window ────────────────────────────────────────────────────────

    @Test
    fun `a window with a remembered rectangle opens exactly there`() {
        val remembered = Placement(400, 260, 1400, 1160)
        assertEquals(remembered, open(remembered))
    }

    @Test
    fun `a window opens at the fraction it asked for, not full screen`() {
        val r = open(null, fraction = 0.5f to 0.5f)
        assertEquals(1202, r.width)
        assertEquals(770, r.height)
        assertEquals(Placement(122, 74, 1324, 844), r)
        assertTrue("the window would cover the desktop completely", r.width < area.width)
    }

    @Test
    fun `a remembered rectangle from a wider screen is pulled back inside`() {
        val r = open(Placement(2200, 1400, 3400, 2300))
        assertTrue(r.right <= area.right && r.bottom <= area.bottom)
        assertTrue(r.left >= area.left && r.top >= area.top)
    }

    @Test
    fun `a window never opens smaller than the minimum size`() {
        val r = open(Placement(300, 300, 320, 330))
        assertTrue(r.width >= minW && r.height >= minH)
    }

    @Test
    fun `a window never opens larger than the desktop`() {
        val r = open(Placement(0, 0, 9000, 9000))
        assertEquals(area, r)
    }

    @Test
    fun `two windows opened in a row are cascaded, not stacked`() {
        val first = open(null)
        val second = MaximizeMath.openRect(null, area, 0.62f to 0.60f, margin, cascade * 1, minW, minH)
        assertEquals(first.left + cascade, second.left.toFloat(), 0.01f)
        assertEquals(first.top + cascade, second.top.toFloat(), 0.01f)
    }
}
