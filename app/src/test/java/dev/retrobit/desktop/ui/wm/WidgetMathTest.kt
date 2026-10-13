package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the manual widget sizing maths. */
class WidgetMathTest {

    @Test
    fun `a width drag adds the movement`() {
        assertEquals(288f, WidgetMath.width(280f, 9f), 0.01f)
    }

    @Test
    fun `sizes snap to the grid`() {
        assertEquals(288f, WidgetMath.snap(290f), 0.01f)
        assertEquals(280f, WidgetMath.snap(281f), 0.01f)
    }

    @Test
    fun `a widget never gets narrower than the minimum`() {
        assertEquals(WidgetMath.MIN_WIDTH, WidgetMath.width(200f, -500f), 0.01f)
    }

    @Test
    fun `a widget never gets shorter than the minimum`() {
        assertEquals(WidgetMath.MIN_HEIGHT, WidgetMath.height(80f, -500f), 0.01f)
    }

    @Test
    fun `a widget stops at the desktop edge`() {
        assertEquals(900f, WidgetMath.width(700f, 1000f, WidgetMath.MIN_WIDTH, 900f), 0.01f)
    }

    @Test
    fun `the corner grip changes both axes`() {
        val s = WidgetMath.size(Size(280f, 180f), 16f, 40f)
        assertEquals(296f, s.width, 0.01f)
        assertEquals(224f, s.height, 0.01f)
    }

    @Test
    fun `the right edge is the width handle`() {
        assertEquals(WidgetMath.WidgetHandle.RIGHT, WidgetMath.handleAt(280f - 3f, 60f, 280f, 200f, 14f))
    }

    @Test
    fun `the bottom right corner is the corner handle`() {
        assertEquals(WidgetMath.WidgetHandle.CORNER, WidgetMath.handleAt(280f - 3f, 200f - 3f, 280f, 200f, 14f))
    }

    @Test
    fun `the middle of a widget has no handle`() {
        assertNull(WidgetMath.handleAt(120f, 60f, 280f, 200f, 14f))
    }
}
