package dev.retrobit.desktop.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App icons and the **internal** icons of the shell (launch, find, shell, mon, note, setup) draw
 * their tile from one place now, so the shape vocabulary is pinned down here: an icon of a theme and
 * an app icon of the same theme have to come out the same shape, and the printed shapes have to stay
 * square.
 */
class IconShapeTest {

    private val side = 100f

    @Test
    fun `the shape of a theme is the same for app icons and shell icons`() {
        // the radii model/Models.kt hands data/IconCache and GlyphTile
        assertEquals("95 stays a hard square", 0f, IconTheme.tileRadius(IconTheme.CLASSIC, side), 0.001f)
        assertEquals("Fluent rounds by a quarter", 24f, IconTheme.tileRadius(IconTheme.SQUIRCLE, side), 0.001f)
        assertEquals("Aqua rounds a little more", 26f, IconTheme.tileRadius(IconTheme.AQUA, side), 0.001f)
        assertEquals("Yaru keeps it tighter", 16f, IconTheme.tileRadius(IconTheme.YARU, side), 0.001f)
        assertEquals("GNOME is a circle", 50f, IconTheme.tileRadius(IconTheme.CIRCLE, side), 0.001f)
        assertEquals("the plain shape has no tile at all", 0f, IconTheme.tileRadius(IconTheme.PLAIN, side), 0.001f)
    }

    @Test
    fun `every shape stays inside its box`() {
        IconTheme.entries.forEach { theme ->
            val radius = IconTheme.tileRadius(theme, side)
            val inset = IconTheme.tileInset(theme, side)
            assertTrue("${theme.label}: radius $radius is past half the side", radius <= side / 2f)
            assertTrue("${theme.label}: radius $radius is negative", radius >= 0f)
            assertTrue("${theme.label}: inset $inset eats the tile", inset * 2f < side * 0.25f)
            assertTrue("${theme.label}: inset $inset is negative", inset >= 0f)
        }
    }

    @Test
    fun `a bigger icon keeps its shape`() {
        // the radius follows the size, it is not a fixed number of pixels
        listOf(16f, 40f, 128f).forEach { s ->
            IconTheme.entries.forEach { theme ->
                val expected = when (theme) {
                    IconTheme.ROUNDED -> s * 0.20f
                    IconTheme.FLAT -> s * 0.06f
                    IconTheme.SQUIRCLE -> s * 0.24f
                    IconTheme.AQUA -> s * 0.26f
                    IconTheme.YARU -> s * 0.16f
                    IconTheme.CIRCLE -> s * 0.5f
                    IconTheme.CLASSIC, IconTheme.PLAIN, IconTheme.AUTO -> 0f
                }
                assertEquals("${theme.label} at $s", expected, IconTheme.tileRadius(theme, s), 0.001f)
            }
        }
    }

    @Test
    fun `the printed square keeps its bevelled inset, the rounded ones keep theirs`() {
        assertEquals("the 95 frame sits further from the edge", 6f, IconTheme.tileInset(IconTheme.CLASSIC, side), 0.001f)
        assertEquals("macOS insets a little", 5f, IconTheme.tileInset(IconTheme.ROUNDED, side), 0.001f)
        assertEquals("GNOME insets a touch", 4f, IconTheme.tileInset(IconTheme.CIRCLE, side), 0.001f)
        assertEquals("the flat pair sits close to the edge", 3f, IconTheme.tileInset(IconTheme.FLAT, side), 0.001f)
        assertEquals("no tile, no inset", 0f, IconTheme.tileInset(IconTheme.PLAIN, side), 0.001f)
    }

    @Test
    fun `every native palette tiles the shell icons, e-paper prints them`() {
        PaletteId.entries.forEach { id ->
            val resolved = IconTheme.resolve(IconTheme.AUTO, DesktopStyle.LINUX, id)
            val native = id.native
            if (native) {
                // a native desktop always resolves to a real shape, never to the bare glyph
                assertTrue("${id.label} should tile its shell icons", resolved != IconTheme.PLAIN)
                assertEquals(
                    "${id.label} should take the shape of its own desktop",
                    IconTheme.forPalette(id), resolved,
                )
                // the tile has to be rounder than a hard square on every modern theme
                val round = IconTheme.tileRadius(resolved, side) > 0f
                assertEquals(
                    "${id.label}: only 95 and the printed palettes are square",
                    id != PaletteId.WIN95, round,
                )
            } else {
                // e-paper: the printed shapes are square (95) or the style's, and stay printed
                val radius = IconTheme.tileRadius(resolved, side)
                assertTrue("${id.label} prints a square or flat tile", radius <= side * 0.24f)
            }
        }
    }
}
