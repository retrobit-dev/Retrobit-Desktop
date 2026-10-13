package dev.retrobit.desktop.ui.wm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The maths that puts a floating app inside its desktop window frame. */
class AppWindowMathTest {

    private fun rect(
        x: Float = 100f,
        y: Float = 200f,
        w: Float = 800f,
        h: Float = 600f,
        title: Float = 60f,
        border: Float = 4f,
        insetX: Float = 0f,
        insetY: Float = 0f,
        screenW: Int = 2560,
        screenH: Int = 1600,
    ) = AppWindowMath.contentRect(x, y, w, h, title, border, insetX, insetY, screenW, screenH)

    @Test
    fun `the app starts below the title bar and inside the border`() {
        val r = rect()!!
        assertEquals(104, r.left)
        assertEquals(264, r.top)
        assertEquals(896, r.right)
        assertEquals(796, r.bottom)
        assertEquals(792, r.width)
        assertEquals(532, r.height)
    }

    @Test
    fun `the safe area of the camera hole and rounded corners is added`() {
        val r = rect(insetX = 28f, insetY = 20f)!!
        // +28 / +20 px because the frame is drawn inside the padded shell, not at the physical edge
        assertEquals(132, r.left)
        assertEquals(284, r.top)
    }

    @Test
    fun `a window hanging over the edge is pulled back onto the screen`() {
        val r = rect(x = -300f, y = -200f, screenW = 1200, screenH = 800)!!
        assertEquals(0, r.left)
        assertEquals(0, r.top)
        assertTrue(r.right <= 1200)
        assertTrue(r.bottom <= 800)
    }

    @Test
    fun `a window bigger than the screen keeps a usable size`() {
        val r = rect(w = 4000f, h = 3000f)!!
        assertEquals(2560, r.right)
        assertEquals(1600, r.bottom)
        assertTrue(r.width >= AppWindowMath.MIN_SIDE)
    }

    @Test
    fun `a frame too small for an app is refused`() {
        assertNull(rect(w = 100f, h = 100f))
        assertNull(rect(w = 0f, h = 0f))
    }

    @Test
    fun `a frame that is almost all title bar is refused`() {
        // 200 px tall frame with a 190 px title bar leaves a 6 px strip of app: not a window
        assertNull(rect(h = 200f, title = 190f))
    }

    @Test
    fun `the title bar follows the density and the ui scale`() {
        assertEquals(90f, AppWindowMath.titleHeightPx(30f, 1f, 3f))
        assertEquals(45f, AppWindowMath.titleHeightPx(30f, 0.5f, 3f))
        assertEquals(180f, AppWindowMath.titleHeightPx(30f, 8f, 3f))  // scale is clamped at 2x
        assertEquals(6f, AppWindowMath.borderPx(2f, 3f))
    }

    @Test
    fun `a frame smaller than the display counts as a real window`() {
        val r = rect(w = 800f, h = 500f)
        assertNotNull(r)
        assertTrue(AppWindowMath.isWindowed(r, 2560, 1600))
    }

    @Test
    fun `a request that fills the screen is not a window`() {
        assertFalse(AppWindowMath.isWindowed(Placement(0, 0, 2560, 1600), 2560, 1600))
        assertFalse(AppWindowMath.isWindowed(null, 2560, 1600))
    }
}
