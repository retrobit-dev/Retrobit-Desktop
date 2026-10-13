package dev.retrobit.desktop.ui.wm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the dock reordering maths (drag an icon to any slot). */
class DockMathTest {

    private val vertical = listOf(
        Offset(39f, 60f), Offset(39f, 150f), Offset(39f, 240f),
    )
    private val horizontal = listOf(
        Offset(120f, 980f), Offset(210f, 980f), Offset(300f, 980f),
    )

    @Test
    fun `the nearest slot wins on a vertical dock`() {
        assertEquals(0, DockMath.slotIndex(vertical, Offset(39f, 70f), horizontal = false))
        assertEquals(2, DockMath.slotIndex(vertical, Offset(39f, 300f), horizontal = false))
    }

    @Test
    fun `the nearest slot wins on a horizontal dock`() {
        assertEquals(0, DockMath.slotIndex(horizontal, Offset(130f, 990f), horizontal = true))
        assertEquals(1, DockMath.slotIndex(horizontal, Offset(215f, 970f), horizontal = true))
    }

    @Test
    fun `an empty dock has no slot`() {
        assertEquals(-1, DockMath.slotIndex(emptyList(), Offset(10f, 10f), horizontal = true))
    }

    @Test
    fun `moving a pinned app down reorders the list`() {
        val items = listOf("a", "b", "c", "d")
        // dropped over slot 1 the icon stays where it is, over slot 2 it trades places with "c"
        assertEquals(listOf("a", "b", "c", "d"), DockMath.move(items, "b", 1))
        assertEquals(listOf("a", "c", "b", "d"), DockMath.move(items, "b", 2))
    }

    @Test
    fun `moving a pinned app up reorders the list`() {
        val items = listOf("a", "b", "c", "d")
        assertEquals(listOf("d", "a", "b", "c"), DockMath.move(items, "d", 0))
    }

    @Test
    fun `an app that is not pinned yet is inserted at the requested slot`() {
        val items = listOf("a", "b")
        assertEquals(listOf("a", "x", "b"), DockMath.move(items, "x", 1))
    }

    @Test
    fun `a target past the end of the dock appends the app`() {
        val items = listOf("a", "b")
        assertEquals(listOf("a", "b", "x"), DockMath.move(items, "x", 99))
    }

    @Test
    fun `a negative target puts the app first`() {
        assertEquals(listOf("x", "a"), DockMath.move(listOf("a"), "x", -5))
    }

    @Test
    fun `the dock strip accepts a drop just outside its edge`() {
        val dock = Rect(0f, 30f, 78f, 800f)
        assertTrue(DockMath.inside(Offset(70f, 400f), dock))
        assertTrue(DockMath.inside(Offset(85f, 400f), dock))
        assertFalse(DockMath.inside(Offset(300f, 400f), dock))
    }
}
