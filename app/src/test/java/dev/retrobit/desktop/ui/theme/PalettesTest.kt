package dev.retrobit.desktop.ui.theme

import dev.retrobit.desktop.model.PaletteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A palette *is* the look of the desktop, so the numbers behind it are worth checking: every id has
 * a palette, text stays legible on every paper tone, nothing is printed in a colour it disappears
 * into, and the native palettes really do bring a desk colour of their own.
 */
class PalettesTest {

    @Test
    fun `every palette id has a palette`() {
        PaletteId.values().forEach { id ->
            val p = Palettes.all[id]
            assertNotNull("no palette for $id", p)
            assertEquals(id, p!!.id)
        }
        assertEquals(PaletteId.values().size, Palettes.all.size)
    }

    @Test
    fun `palette labels are unique`() {
        val labels = PaletteId.values().map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `body text keeps its contrast on every paper tone`() {
        PaletteId.values().forEach { id ->
            val p = Palettes.of(id)
            val ratio = contrastRatio(p.paper, p.ink)
            assertTrue("${p.id}: ink on paper is only $ratio:1", ratio >= 4.5f)
        }
    }

    @Test
    fun `the accent never disappears into the paper`() {
        PaletteId.values().forEach { id ->
            val p = Palettes.of(id)
            val ratio = contrastRatio(p.paper, p.accent)
            assertTrue("${p.id}: accent on paper is only $ratio:1", ratio >= 3f)
        }
    }

    @Test
    fun `what prints on the desk stays readable on it`() {
        PaletteId.values().forEach { id ->
            val p = Palettes.of(id)
            val ratio = contrastRatio(p.desk, p.onDesk)
            assertTrue("${p.id}: ${p.onDesk} on the desk is only $ratio:1", ratio >= 2.6f)
        }
    }

    @Test
    fun `the e-paper palettes paint the desk in their own paper`() {
        Palettes.ePaper.forEach { id ->
            val p = Palettes.of(id)
            assertEquals("${p.id} should not move the desk", p.paper, p.desk)
        }
        assertTrue(Palettes.ePaper.size >= 6)
    }

    @Test
    fun `every native palette brings a desk colour and a shell`() {
        Palettes.native.forEach { id ->
            val p = Palettes.of(id)
            assertNotEquals("${p.id} has no desk colour of its own", p.paper, p.desk)
            assertNotNull("${p.id} belongs to no desktop style", id.style)
        }
        assertTrue(Palettes.native.size >= 6)
    }

    @Test
    fun `the native palettes cover all three shells, light and dark`() {
        assertEquals(3, Palettes.native.mapNotNull { it.style }.toSet().size)
        Palettes.native.groupBy { it.style }.forEach { (style, ids) ->
            assertEquals("$style should have a light and a dark take", 2, ids.size)
            val dark = ids.count { !Palettes.of(it).isDark }
            assertEquals("$style: one light and one dark", 1, dark)
        }
    }

    @Test
    fun `inverting a palette swaps paper and ink`() {
        PaletteId.values().forEach { id ->
            val p = Palettes.of(id)
            val inv = p.invert()
            assertEquals(p.ink, inv.paper)
            assertEquals(p.paper, inv.ink)
            assertTrue(inv.inverted)
            // the accent falls back to the new ink, so an inverted desktop never keeps a stray colour
            assertEquals(p.paper, inv.accent)
            assertEquals(contrastRatio(p.paper, p.ink), contrastRatio(inv.paper, inv.ink), 0.001f)
        }
    }

    @Test
    fun `a dark palette is darker than its own ink and vice versa`() {
        PaletteId.values().forEach { id ->
            val p = Palettes.of(id)
            val paperLum = luminance(p.paper)
            val inkLum = luminance(p.ink)
            if (p.isDark) assertTrue("${p.id} claims to be dark", paperLum < inkLum)
            else assertTrue("${p.id} claims to be light", paperLum > inkLum)
        }
    }
}
