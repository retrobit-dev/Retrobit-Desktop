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
    val all: Map<PaletteId, Palette> = mapOf(
        PaletteId.PAPER to Palette(PaletteId.PAPER, Color(0xFFE8E5DA), Color(0xFF1B1B1B)),
        PaletteId.SEPIA to Palette(PaletteId.SEPIA, Color(0xFFEADFC6), Color(0xFF2E2418)),
        PaletteId.BWR to Palette(PaletteId.BWR, Color(0xFFEDEBE4), Color(0xFF161616), Color(0xFFB1281F)),
        PaletteId.INK to Palette(PaletteId.INK, Color(0xFF171717), Color(0xFFDAD6CA)),
        PaletteId.PHOSPHOR to Palette(PaletteId.PHOSPHOR, Color(0xFF0B110B), Color(0xFF9CF08A)),
        PaletteId.AMBER to Palette(PaletteId.AMBER, Color(0xFF130D05), Color(0xFFFFB542)),
    )

    fun of(id: PaletteId): Palette = all.getValue(id)
}

val LocalPalette = staticCompositionLocalOf { Palettes.of(PaletteId.PAPER) }
val LocalIconMode = staticCompositionLocalOf { IconMode.MONO }
val LocalDesktopStyle = staticCompositionLocalOf { DesktopStyle.LINUX }

val Plex = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

val Pixel = FontFamily(Font(R.font.vt323, FontWeight.Normal))
