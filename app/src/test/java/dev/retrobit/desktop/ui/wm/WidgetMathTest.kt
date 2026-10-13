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

    // ── adding / removing widgets on the home screen ─────────────────────────────

    @Test
    fun `adding a widget appends it and never duplicates`() {
        assertEquals(listOf("a", "b"), WidgetMath.add(listOf("a"), "b"))
        assertEquals(listOf("a"), WidgetMath.add(listOf("a"), "a"))
    }

    @Test
    fun `the home screen holds at most six widgets`() {
        val full = listOf("1", "2", "3", "4", "5", "6")
        assertEquals(full, WidgetMath.add(full, "7"))
    }

    @Test
    fun `removing a widget leaves the rest in place`() {
        assertEquals(listOf("a", "c"), WidgetMath.remove(listOf("a", "b", "c"), "b"))
        assertEquals(emptyList<String>(), WidgetMath.remove(listOf("a"), "a"))
    }

    @Test
    fun `moving a widget drops it one slot down`() {
        // a -> one slot down: a, b, c  =>  b, a, c
        assertEquals(listOf("b", "a", "c"), WidgetMath.move(listOf("a", "b", "c"), "a", 1))
    }

    @Test
    fun `moving a widget pulls it one slot up`() {
        // c -> one slot up: b, a, c  =>  b, c, a
        assertEquals(listOf("b", "c", "a"), WidgetMath.move(listOf("b", "a", "c"), "c", -1))
    }

    @Test
    fun `moving past the ends is clamped`() {
        assertEquals(listOf("a", "b"), WidgetMath.move(listOf("a", "b"), "a", -1))
        assertEquals(listOf("a", "b"), WidgetMath.move(listOf("a", "b"), "b", 5))
    }

    @Test
    fun `toggling turns a widget on and off`() {
        assertEquals(listOf("a", "b"), WidgetMath.toggle(listOf("a"), "b"))
        assertEquals(listOf("a"), WidgetMath.toggle(listOf("a", "b"), "b"))
    }
}
