package dev.retrobit.desktop.model

import dev.retrobit.desktop.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The icon shape is part of the theme: every native desktop palette has to bring the shape that
 * desktop really uses, "follow palette" has to pick it, and an explicit choice must survive.
 */
class IconThemeTest {

    @Test
    fun `every native palette brings the shape of its desktop`() {
        val expected = mapOf(
            PaletteId.WIN95 to IconTheme.CLASSIC,
            PaletteId.WIN11 to IconTheme.SQUIRCLE,
            PaletteId.AQUA to IconTheme.AQUA,
            PaletteId.AQUA_DARK to IconTheme.AQUA,
            PaletteId.UBUNTU to IconTheme.YARU,
            PaletteId.ADWAITA to IconTheme.CIRCLE,
        )
        expected.forEach { (id, shape) ->
            assertEquals("$id should bring $shape", shape, IconTheme.forPalette(id))
            assertEquals("$id: the palette should expose the same shape", shape, Palettes.of(id).iconTheme)
        }
        // the mapping has to cover every native palette, so a new one cannot be forgotten
        assertEquals(expected.size, PaletteId.values().count { it.native })
    }

    @Test
    fun `the e-paper palettes have no shape of their own`() {
        PaletteId.values().filter { !it.native }.forEach { id ->
            assertNull("$id must not pin a shape", IconTheme.forPalette(id))
            assertNull("$id: the palette should say so too", Palettes.of(id).iconTheme)
        }
        assertTrue(PaletteId.values().count { !it.native } >= 6)
    }

    @Test
    fun `follow palette prefers the theme over the style`() {
        // Aqua palette + a Windows desktop style: the palette wins, because that is what the user
        // just tapped; the style only matters when the palette has no shape of its own
        assertEquals(IconTheme.AQUA, IconTheme.resolve(IconTheme.AUTO, DesktopStyle.WINDOWS, PaletteId.AQUA))
        assertEquals(IconTheme.YARU, IconTheme.resolve(IconTheme.AUTO, DesktopStyle.MACOS, PaletteId.UBUNTU))
        // e-paper palettes fall back to the desktop style
        Palettes.ePaper.forEach { id ->
            assertEquals(
                "${id.label}: the style should decide",
                IconTheme.forStyle(DesktopStyle.WINDOWS), IconTheme.resolve(IconTheme.AUTO, DesktopStyle.WINDOWS, id),
            )
        }
        // ... and with no palette known at all the style decides too
        assertEquals(IconTheme.CLASSIC, IconTheme.resolve(IconTheme.AUTO, DesktopStyle.WINDOWS))
    }

    @Test
    fun `an explicit shape wins over the palette`() {
        IconTheme.entries.filter { it != IconTheme.AUTO }.forEach { shape ->
            assertEquals("$shape should be honoured", shape, IconTheme.resolve(shape, DesktopStyle.LINUX, PaletteId.AQUA))
        }
    }

    @Test
    fun `every desktop style has a fallback shape`() {
        assertEquals(IconTheme.CLASSIC, IconTheme.forStyle(DesktopStyle.WINDOWS))
        assertEquals(IconTheme.ROUNDED, IconTheme.forStyle(DesktopStyle.MACOS))
        assertEquals(IconTheme.FLAT, IconTheme.forStyle(DesktopStyle.LINUX))
    }

    @Test
    fun `the shape list stays presentable`() {
        assertEquals("follow palette has to be the first chip", IconTheme.AUTO, IconTheme.entries.first())
        assertEquals("plain has to be the last chip", IconTheme.PLAIN, IconTheme.entries.last())
        IconTheme.entries.forEach { theme ->
            assertTrue("${theme.name} has no label", theme.label.isNotBlank())
            assertTrue("${theme.name} has no hint", theme.hint.isNotBlank())
        }
        // the labels are what the Settings grid prints: they have to stay short and unique
        val labels = IconTheme.entries.map { it.label }
        assertEquals("labels must be unique", labels.size, labels.toSet().size)
        assertTrue("a label is too long for a chip", labels.all { it.length <= 14 })
        // describe() names the shape actually used, for the terminal status line
        assertEquals("Aqua (auto)", IconTheme.describe(IconTheme.AUTO, DesktopStyle.LINUX, PaletteId.AQUA))
        assertEquals("Flat (flat)", IconTheme.describe(IconTheme.FLAT, DesktopStyle.LINUX, PaletteId.AQUA))
        assertNotNull(IconTheme.describe(IconTheme.AUTO, DesktopStyle.LINUX, null))
    }
}
