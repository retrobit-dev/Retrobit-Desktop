package dev.retrobit.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.retrobit.desktop.R
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.IconTheme
import dev.retrobit.desktop.model.PaletteId

@Immutable
data class Palette(
    val id: PaletteId,
    val paper: Color,
    val ink: Color,
    val accentColor: Color? = null,
    val inverted: Boolean = false,
) {
    val mid: Color get() = lerp(paper, ink, 0.5f)
    val soft: Color get() = lerp(paper, ink, 0.13f)
    val pattern: Color get() = lerp(paper, ink, 0.30f)
    val accent: Color get() = accentColor ?: ink
    val isDark: Boolean get() = (paper.red + paper.green + paper.blue) < 1.5f
    fun invert(): Palette = copy(paper = ink, ink = paper, accentColor = null, inverted = !inverted)
}

object Palettes {
    /**
     * Every palette carries an accent colour. App icon frames, the letter plate on icons and the
     * selection marks are printed in it, so switching the palette visibly re-inks the whole desktop
     * — on the monochrome palettes the accent is the palette's own ink tone.
     */
    val all: Map<PaletteId, Palette> = mapOf(
        PaletteId.PAPER to Palette(PaletteId.PAPER, Color(0xFFE8E5DA), Color(0xFF1B1B1B), Color(0xFF3F3F3F)),
        PaletteId.SEPIA to Palette(PaletteId.SEPIA, Color(0xFFEADFC6), Color(0xFF2E2418), Color(0xFF8C6B3F)),
        PaletteId.BWR to Palette(PaletteId.BWR, Color(0xFFEDEBE4), Color(0xFF161616), Color(0xFFB1281F)),
        PaletteId.INK to Palette(PaletteId.INK, Color(0xFF171717), Color(0xFFDAD6CA), Color(0xFF8C8878)),
        PaletteId.PHOSPHOR to Palette(PaletteId.PHOSPHOR, Color(0xFF0B110B), Color(0xFF9CF08A), Color(0xFF4FB93F)),
        PaletteId.AMBER to Palette(PaletteId.AMBER, Color(0xFF130D05), Color(0xFFFFB542), Color(0xFFE07A16)),
    )

    fun of(id: PaletteId): Palette = all.getValue(id)
}

/**
 * How app icons are decorated: the palette accent used for icon frames, the first-letter monogram
 * and whether name chips are printed under the icons.
 */
@Immutable
data class IconOptions(
    val accent: Color? = null,
    val monogram: Boolean = false,
    val labels: Boolean = true,
)

val LocalPalette = staticCompositionLocalOf { Palettes.of(PaletteId.PAPER) }
val LocalIconOptions = staticCompositionLocalOf { IconOptions() }
val LocalIconMode = staticCompositionLocalOf { IconMode.MONO }
val LocalDesktopStyle = staticCompositionLocalOf { DesktopStyle.LINUX }
val LocalIconTheme = staticCompositionLocalOf { IconTheme.FLAT }
/** Bumped when the user asks for a full icon re-ink; it is part of every icon's render key. */
val LocalIconEpoch = staticCompositionLocalOf { 0 }

/**
 * PixelDisplay Operator (CC0 1.0, Jayvee Enaguas / HarvettFox96) — the proportional cut, used for
 * headings, clocks and window titles.
 */
val PixelDisplay = FontFamily(
    Font(R.font.pixel_operator, FontWeight.Normal),
    Font(R.font.pixel_operator_bold, FontWeight.Bold),
)

/**
 * PixelDisplay Operator Mono (CC0 1.0) — the monospaced cut, used for all body text, the terminal,
 * tables and progress bars so that ASCII layouts line up.
 */
val PixelMono = FontFamily(
    Font(R.font.pixel_operator_mono, FontWeight.Normal),
    Font(R.font.pixel_operator_mono_hb, FontWeight.Medium),
    Font(R.font.pixel_operator_mono_bold, FontWeight.Bold),
)
