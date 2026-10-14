package dev.retrobit.desktop.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.retrobit.desktop.model.PaletteId
import dev.retrobit.desktop.ui.theme.Palettes
import dev.retrobit.desktop.ui.theme.WidgetSkin
import dev.retrobit.desktop.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chrome a full-screen app wears is drawn by the services, not by Compose, so what it looks like
 * is pinned down here: a native palette has to hand the rails, the dock and the quick panel the same
 * window chrome its widgets get, and the e-paper palettes have to keep printing them — a `null` skin
 * is the printed path, and it must stay reachable.
 */
class ChromeSkinTest {

    @Test
    fun `e-paper palettes keep printing their rails`() {
        Palettes.ePaper.forEach { id ->
            assertNull("${id.label} prints ink on paper, so it has no chrome skin", skinOf(id))
        }
        // the state a service starts with has to be the printed one too
        assertNull(CyberdeckChrome.State().skin)
        assertNull(PanelChrome.State().skin)
    }

    @Test
    fun `native palettes wear the window face, border and corners of their theme`() {
        Palettes.native.forEach { id ->
            val p = Palettes.of(id)
            val skin = skinOf(id)
            assertNotNull("${id.label} should hand the rails a native skin", skin)
            val w = WidgetSkin.of(p)
            assertEquals("${id.label}: the rails sit on the window face", w.panel.toArgb(), skin!!.face)
            assertEquals("${id.label}: the frame is the theme border", w.border.toArgb(), skin.border)
            assertEquals("${id.label}: the corners follow the theme", w.cornerDp, skin.cornerDp, 0.001f)
            assertEquals("${id.label}: the hairline follows the theme", w.borderDp, skin.borderDp, 0.001f)
        }
    }

    @Test
    fun `the top rail is dressed as the title bar of that desktop`() {
        Palettes.native.forEach { id ->
            val p = Palettes.of(id)
            val skin = skinOf(id)!!
            val theme = p.skin!!
            assertEquals("${id.label}: the title strip is the theme title bar", theme.titleTop.toArgb(), skin.titleTop)
            assertEquals("${id.label}: and so is its gradient", theme.titleBottom.toArgb(), skin.titleBottom)
            assertEquals("${id.label}: the title text is the theme one", theme.titleText.toArgb(), skin.titleText)
            assertEquals("${id.label}: flat titles stay flat", skin.titleTop == skin.titleBottom, skin.flatTitle)
        }
        // Windows 11 paints a flat title bar, Aqua and the Linux themes keep a gradient
        assertTrue("windows 11 should be flat", skinOf(PaletteId.WIN11)!!.flatTitle)
        assertTrue("aqua should keep its gradient", !skinOf(PaletteId.AQUA)!!.flatTitle)
    }

    @Test
    fun `keys and tiles are never as round as the window they sit on`() {
        // Windows 95 has no rounded corner at all: its keys have to stay hard squares
        assertEquals(0f, skinOf(PaletteId.WIN95)!!.tile(), 0.001f)
        // Aqua (10 dp) and Adwaita (12 dp): smaller than the window, and never past the cap
        assertEquals(6f, skinOf(PaletteId.AQUA)!!.tile(), 0.001f)
        assertEquals(7.2f, skinOf(PaletteId.ADWAITA)!!.tile(), 0.001f)
        // the cap only bites once the share would push it past it
        assertEquals(10f, skinOf(PaletteId.ADWAITA)!!.tile(share = 1.5f, cap = 10f), 0.001f)
        // the surface itself uses the full radius of the theme
        assertEquals(12f, skinOf(PaletteId.ADWAITA)!!.tile(share = 1f, cap = 16f), 0.001f)
    }

    @Test
    fun `a switched-on key stays readable in every theme`() {
        Palettes.native.forEach { id ->
            val skin = skinOf(id)!!
            // the plate and its text are what "this key is on" looks like, so they have to contrast
            val ratio = contrastRatio(Color(skin.plateText), Color(skin.plate))
            assertTrue("${id.label}: on-plate text is too faint ($ratio)", ratio >= 3.5f)
            // the title text is read against the title strip, which on Ubuntu is dark while the
            // window face is light - so the strip is what it has to contrast with
            val onTop = contrastRatio(Color(skin.titleText), Color(skin.titleTop))
            val onBottom = contrastRatio(Color(skin.titleText), Color(skin.titleBottom))
            assertTrue("${id.label}: title text is too faint on the strip ($onTop)", onTop >= 2.5f)
            assertTrue("${id.label}: title text fades on the strip ($onBottom)", onBottom >= 2.5f)
            // and the dock prints the palette ink on the window face, so that pair has to read
            val p = Palettes.of(id)
            val onFace = contrastRatio(p.ink, p.paper)
            assertTrue("${id.label}: dock text is too faint on the face ($onFace)", onFace >= 4.5f)
        }
    }

    @Test
    fun `the dock is the accent desktop, so its rail reads in colour`() {
        Palettes.native.forEach { id ->
            val skin = skinOf(id)!!
            val p = Palettes.of(id)
            assertEquals("${id.label}: the groove and the marks use the accent", p.accent.toArgb(), skin.accent)
            // a printed shell has no hairline: the soft colour only exists for the native chrome
            assertEquals("${id.label}: the rules use the soft tone", WidgetSkin.of(p).separator.toArgb(), skin.soft)
        }
    }

    private fun skinOf(id: PaletteId): CyberdeckChrome.Skin? =
        CyberdeckChrome.Skin.of(Palettes.of(id))
}
