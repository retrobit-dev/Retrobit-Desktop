package dev.retrobit.desktop.ui.theme

import dev.retrobit.desktop.model.PaletteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widgets are the part of the desktop people look at the most, so their look is pinned down here:
 * a native theme has to dress a widget in its own chrome (window face, border, title gradient,
 * accent meter), while the e-paper palettes have to keep printing it.
 */
class WidgetSkinTest {

    @Test
    fun `native palettes dress widgets in their own desktop chrome`() {
        Palettes.native.forEach { id ->
            val p = Palettes.of(id)
            val w = WidgetSkin.of(p)
            val skin = p.skin!!
            assertTrue("${id.label}: widgets should be native", w.native)
            assertEquals("${id.label}: the panel is the window face", p.paper, w.panel)
            assertEquals("${id.label}: the frame is the theme border", skin.border, w.border)
            assertEquals("${id.label}: the corners follow the theme", skin.cornerDp, w.cornerDp, 0.001f)
            assertEquals("${id.label}: header top", skin.titleTop, w.headerTop)
            assertEquals("${id.label}: header bottom", skin.titleBottom, w.headerBottom)
            assertEquals("${id.label}: the title on the bar", skin.titleText, w.headerText)
            assertEquals("${id.label}: the rule under the bar", skin.border, w.separator)
            // the meter is the theme accent on its soft face, not a printed ink bar
            assertEquals("${id.label}: meter fill", p.accent, w.meterFill)
            assertEquals("${id.label}: meter track", p.soft, w.meterTrack)
            assertEquals("${id.label}: a picked chip", p.plate, w.plate)
            assertEquals("${id.label}: ... with its text", p.plateText, w.plateText)
            assertEquals("${id.label}: the resize grip", skin.border, w.grip)
            assertTrue("${id.label}: modern widgets are rounded", if (skin.cornerDp > 0f) w.cornerDp > 0f else w.cornerDp == 0f)
            assertTrue("${id.label}: a native frame is a hairline", w.borderDp <= 2f)
        }
    }

    @Test
    fun `the e-paper palettes keep printing widgets`() {
        Palettes.ePaper.forEach { id ->
            val p = Palettes.of(id)
            val w = WidgetSkin.of(p)
            assertTrue("${id.label}: e-paper widgets are printed", !w.native)
            assertEquals("${id.label}: printed cards are square", 0f, w.cornerDp, 0.001f)
            assertEquals("${id.label}: the frame is the ink", p.ink, w.border)
            assertEquals("${id.label}: a drawn frame is thicker", 1.6f, w.borderDp, 0.001f)
            assertEquals("${id.label}: the title prints in ink", p.ink, w.headerText)
            assertEquals("${id.label}: the meter is ink on paper", p.ink, w.meterFill)
            assertEquals("${id.label}: ... on a paper track", p.paper, w.meterTrack)
            assertEquals("${id.label}: a half cell is the mid tone", p.mid, w.meterPartial)
            assertEquals("${id.label}: the plate is the ink", p.ink, w.plate)
            assertEquals("${id.label}: ... with paper text", p.paper, w.plateText)
            assertTrue("${id.label}: a printed header is flat", w.headerFlat)
        }
    }

    @Test
    fun `the widget title stays readable on its bar`() {
        Palettes.native.forEach { id ->
            val w = WidgetSkin.of(Palettes.of(id))
            listOf("top" to w.headerTop, "bottom" to w.headerBottom).forEach { (where, c) ->
                val ratio = contrastRatio(c, w.headerText)
                // the title is printed bold, and Windows 95's real navy->blue bar sits at 4.0
                assertTrue("${id.label}: the widget title on the $where is only $ratio:1", ratio >= 3.5f)
            }
        }
    }

    @Test
    fun `the meter reads against its track`() {
        PaletteId.values().forEach { id ->
            val w = WidgetSkin.of(Palettes.of(id))
            val ratio = contrastRatio(w.meterFill, w.meterTrack)
            // a meter is a graphic, not text: 2.4:1 keeps it visible on the palest track there is
            // (Aqua's blue on its snow face), while the printed ink-on-paper palettes sit far above
            assertTrue("${id.label}: the meter is only $ratio:1", ratio >= 2.4f)
        }
    }

    @Test
    fun `flat themes get a flat bar and the rest a gradient`() {
        Palettes.native.forEach { id ->
            val p = Palettes.of(id)
            val w = WidgetSkin.of(p)
            assertEquals("${id.label}: headerFlat should follow the title bar", p.skin!!.isFlatTitle, w.headerFlat)
        }
        // the two dozen colours are not all the same, of course: Win95 and Aqua paint a gradient
        assertNotEquals(WidgetSkin.of(Palettes.of(PaletteId.WIN95)).headerTop, WidgetSkin.of(Palettes.of(PaletteId.WIN95)).headerBottom)
        assertNotEquals(WidgetSkin.of(Palettes.of(PaletteId.AQUA)).headerTop, WidgetSkin.of(Palettes.of(PaletteId.AQUA)).headerBottom)
        assertTrue(WidgetSkin.of(Palettes.of(PaletteId.WIN11)).headerFlat)
        assertTrue(WidgetSkin.of(Palettes.of(PaletteId.ADWAITA)).headerFlat)
    }

    @Test
    fun `a widget bar keeps a sane height`() {
        PaletteId.values().forEach { id ->
            val w = WidgetSkin.of(Palettes.of(id))
            assertTrue("${id.label}: the header is ${w.headerHeight}dp", w.headerHeight in 18f..26f)
            assertTrue("${id.label}: the frame is ${w.borderDp}dp", w.borderDp in 0.5f..3f)
            assertTrue("${id.label}: the corners are ${w.cornerDp}dp", w.cornerDp in 0f..20f)
        }
        // a native bar is a touch taller than a printed strip
        assertTrue(WidgetSkin.of(Palettes.of(PaletteId.AQUA)).headerHeight > WidgetSkin.of(Palettes.of(PaletteId.PAPER)).headerHeight)
    }
}
