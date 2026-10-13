package dev.retrobit.desktop.ui.wm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Maximize has to fill the desktop, and restore has to bring the window back to **exactly** the
 * rectangle it had — that is the part users notice immediately when it is wrong.
 */
class MaximizeMathTest {

    // the Linux shell on the 2560x1600 tablet: 78px dock on the left, 30px panel on top
    private val area = Placement(78, 30, 2482, 1570)
    private val margin = 10f
    private val cascade = 34f
    private val minW = 360f
    private val minH = 260f

    private fun restore(saved: Placement?) =
        MaximizeMath.restore(saved, area, margin, cascade, minW, minH)

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
}
