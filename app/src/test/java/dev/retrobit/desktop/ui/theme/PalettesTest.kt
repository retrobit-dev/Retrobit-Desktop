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

    // ── native skins ───────────────────────────────────────────────────────────

    @Test
    fun `native palettes carry a real desktop skin`() {
        Palettes.native.forEach { id ->
            val p = Palettes.of(id)
            val skin = p.skin
            assertNotNull("${p.id} has no skin", skin)
            // the skin drives the palette, so nothing else in the app has to know about it
            assertEquals("${p.id}: paper should be the window face", skin!!.window, p.paper)
            assertEquals("${p.id}: ink should be the text colour", skin.text, p.ink)
            assertEquals("${p.id}: accent should be the selection colour", skin.highlight, p.accent)
            assertEquals("${p.id}: the desk should start at the skin's desk colour", skin.deskTop, p.desk)
            assertEquals("${p.id}: borders should be the skin's border", skin.border, p.edge)
        }
        Palettes.ePaper.forEach { id ->
            assertEquals("${id.label} is not a native theme", null, Palettes.of(id).skin)
        }
    }

    @Test
    fun `the title bar of a native theme is readable`() {
        Palettes.native.forEach { id ->
            val skin = Palettes.of(id).skin!!
            listOf("top" to skin.titleTop, "bottom" to skin.titleBottom).forEach { (where, c) ->
                val ratio = contrastRatio(c, skin.titleText)
                // the bar is printed in bold, so the large-text bar (3:1) is the one that matters;
                // Windows 95's real navy->blue gradient sits at 4.0 and is kept as it was
                assertTrue("${id.label}: title text on the $where of the bar is only $ratio:1", ratio >= 3.5f)
            }
        }
    }

    @Test
    fun `icon labels stay readable on both ends of the desk`() {
        Palettes.native.forEach { id ->
            val skin = Palettes.of(id).skin!!
            listOf("top" to skin.deskTop, "bottom" to skin.deskBottom).forEach { (where, c) ->
                val ratio = contrastRatio(c, skin.deskText)
                assertTrue("${id.label}: the desk text on the $where is only $ratio:1", ratio >= 4.5f)
            }
            val picked = contrastRatio(skin.highlight, skin.highlightText)
            assertTrue("${id.label}: a picked icon is only $picked:1", picked >= 3f)
        }
    }

    @Test
    fun `the window chrome of a native theme is the real one`() {
        // Windows 95 is the odd one out: square windows, a two-colour title gradient, no discs
        val win95 = Palettes.of(PaletteId.WIN95).skin!!
        assertEquals("Windows 95 must keep square windows", 0f, win95.cornerDp, 0.001f)
        assertNotEquals("Windows 95 had a gradient title bar", win95.titleTop, win95.titleBottom)
        assertTrue("Windows 95 uses beveled boxes", !win95.flatButtons && !win95.trafficLights)

        // Aqua is the one with three coloured lights on the left
        listOf(PaletteId.AQUA, PaletteId.AQUA_DARK).forEach { id ->
            val skin = Palettes.of(id).skin!!
            assertTrue("${id.label} should use traffic lights", skin.trafficLights)
            assertTrue("${id.label} should round its windows", skin.cornerDp > 0f)
        }

        // the modern flat themes: square-ish discs, no traffic lights, rounded corners
        listOf(PaletteId.WIN11, PaletteId.UBUNTU, PaletteId.ADWAITA).forEach { id ->
            val skin = Palettes.of(id).skin!!
            assertTrue("${id.label} should use flat buttons", skin.flatButtons)
            assertTrue("${id.label} should round its windows", skin.cornerDp > 0f)
            assertTrue("${id.label} should not use traffic lights", !skin.trafficLights)
        }

        // Windows 95 kept a flat teal desk; every later theme paints a gradient, Ubuntu diagonally
        assertEquals("Windows 95's desk is the flat teal", Palettes.of(PaletteId.WIN95).skin!!.deskTop,
            Palettes.of(PaletteId.WIN95).skin!!.deskBottom)
        listOf(PaletteId.WIN11, PaletteId.AQUA, PaletteId.AQUA_DARK, PaletteId.UBUNTU, PaletteId.ADWAITA)
            .forEach { id ->
                val skin = Palettes.of(id).skin!!
                assertNotEquals("${id.label} has no desk gradient", skin.deskTop, skin.deskBottom)
            }
        assertTrue(Palettes.of(PaletteId.UBUNTU).skin!!.deskDiagonal)
        assertTrue(!Palettes.of(PaletteId.WIN11).skin!!.deskDiagonal)
    }

    @Test
    fun `the highlight is used for plates in native themes only`() {
        Palettes.ePaper.forEach { id ->
            val p = Palettes.of(id)
            assertEquals("${p.id}: e-paper plates are the ink", p.ink, p.plate)
            assertEquals("${p.id}: e-paper plate text is the paper", p.paper, p.plateText)
        }
        Palettes.native.forEach { id ->
            val p = Palettes.of(id)
            assertEquals("${p.id}: a picked row uses the selection colour", p.accent, p.plate)
            assertEquals("${p.id}: ... with its own text colour", p.skin!!.highlightText, p.plateText)
        }
    }

    @Test
    fun `inverting a palette drops the native skin and swaps paper and ink`() {
        PaletteId.values().forEach { id ->
            val p = Palettes.of(id)
            val inv = p.invert()
            assertEquals(p.ink, inv.paper)
            assertEquals(p.paper, inv.ink)
            assertTrue(inv.inverted)
            // the accent falls back to the new ink, so an inverted desktop never keeps a stray colour
            assertEquals(p.paper, inv.accent)
            assertEquals(contrastRatio(p.paper, p.ink), contrastRatio(inv.paper, inv.ink), 0.001f)
            // night mode is the e-paper inversion: a half-native "dark Windows 95" would look broken
            assertEquals("${p.id}: invert should drop the native chrome", null, inv.skin)
            assertTrue(!inv.isNative)
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
