package dev.retrobit.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.retrobit.desktop.R
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.IconTheme
import dev.retrobit.desktop.model.PaletteId

/** Relative luminance of a colour (WCAG 2.1 definition), used to keep print legible on the desk. */
fun luminance(c: Color): Float {
    fun channel(v: Float): Float =
        if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
}

/**
 * WCAG contrast ratio between two colours: 1.0 for identical colours, 21.0 for black on white.
 * 4.5 is the ratio text needs, 3.0 the one large print and marks need.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

@Immutable
data class Palette(
    val id: PaletteId,
    val paper: Color,
    val ink: Color,
    val accentColor: Color? = null,
    val inverted: Boolean = false,
    /**
     * Colour of the desktop itself — the background the wallpaper, the icons and the widgets sit on,
     * while every panel, window and menu stays [paper].
     *
     * `null` means the desk is painted in [paper], which is what all the e-paper palettes do. The
     * native ones hand it the colour that desktop really uses behind its icons: Windows 95 teal,
     * Ubuntu aubergine, a slate blue for Aqua.
     */
    val deskColor: Color? = null,
) {
    val mid: Color get() = lerp(paper, ink, 0.5f)
    val soft: Color get() = lerp(paper, ink, 0.13f)
    val pattern: Color get() = lerp(paper, ink, 0.30f)
    /** The wallpaper pattern is printed on the desk, so its dots follow the desk, not the paper. */
    val desk: Color get() = deskColor ?: paper
    val deskPattern: Color get() = lerp(desk, ink, 0.34f)
    val accent: Color get() = accentColor ?: ink
    /** What prints on the desk itself (wallpaper caption, registration marks): ink or paper. */
    val onDesk: Color get() = if (contrastRatio(desk, ink) >= 2.6f) ink else paper
    val isDark: Boolean get() = (paper.red + paper.green + paper.blue) < 1.5f
    fun invert(): Palette = copy(
        paper = ink,
        ink = paper,
        accentColor = null,
        inverted = !inverted,
        // night mode keeps a native desk recognisable, it just takes the glare out of it
        deskColor = deskColor?.let { lerp(it, Color.Black, 0.45f) },
    )
}

object Palettes {
    /**
     * Every palette carries an accent colour. App icon frames, the letter plate on icons and the
     * selection marks are printed in it, so switching the palette visibly re-inks the whole desktop
     * — on the monochrome palettes the accent is the palette's own ink tone.
     */
    val all: Map<PaletteId, Palette> = mapOf(
        // ── e-paper ──────────────────────────────────────────────────────────────
        PaletteId.PAPER to Palette(PaletteId.PAPER, Color(0xFFE8E5DA), Color(0xFF1B1B1B), Color(0xFF3F3F3F)),
        PaletteId.SEPIA to Palette(PaletteId.SEPIA, Color(0xFFEADFC6), Color(0xFF2E2418), Color(0xFF8C6B3F)),
        PaletteId.BWR to Palette(PaletteId.BWR, Color(0xFFEDEBE4), Color(0xFF161616), Color(0xFFB1281F)),
        PaletteId.INK to Palette(PaletteId.INK, Color(0xFF171717), Color(0xFFDAD6CA), Color(0xFF8C8878)),
        PaletteId.PHOSPHOR to Palette(PaletteId.PHOSPHOR, Color(0xFF0B110B), Color(0xFF9CF08A), Color(0xFF4FB93F)),
        PaletteId.AMBER to Palette(PaletteId.AMBER, Color(0xFF130D05), Color(0xFFFFB542), Color(0xFFE07A16)),

        // ── native desktops ──────────────────────────────────────────────────────
        // Windows 95: silver chrome, black text, navy accent, and the teal desk everybody remembers
        PaletteId.WIN95 to Palette(
            PaletteId.WIN95, Color(0xFFC0C0C0), Color(0xFF000000), Color(0xFF000080),
            deskColor = Color(0xFF008080),
        ),
        // Windows 11 (dark): near-black surfaces, soft white text, the Fluent blue accent
        PaletteId.WIN11 to Palette(
            PaletteId.WIN11, Color(0xFF2B2B2B), Color(0xFFF2F2F2), Color(0xFF60CDFF),
            deskColor = Color(0xFF15181C),
        ),
        // macOS Aqua: snow chrome, near-black text, system blue, slate blue desk
        PaletteId.AQUA to Palette(
            PaletteId.AQUA, Color(0xFFF4F4F6), Color(0xFF1D1D1F), Color(0xFF0A84FF),
            deskColor = Color(0xFF5578A6),
        ),
        // macOS dark: graphite chrome, off-white text, the same system blue
        PaletteId.AQUA_DARK to Palette(
            PaletteId.AQUA_DARK, Color(0xFF1E1E20), Color(0xFFF2F2F7), Color(0xFF0A84FF),
            deskColor = Color(0xFF101216),
        ),
        // Ubuntu: warm paper, aubergine ink, the orange accent, aubergine desk
        PaletteId.UBUNTU to Palette(
            PaletteId.UBUNTU, Color(0xFFF2EFEB), Color(0xFF2C001E), Color(0xFFE95420),
            deskColor = Color(0xFF77216F),
        ),
        // GNOME Adwaita (dark): the grey of GTK 4 / libadwaita with the lighter Adwaita blue
        PaletteId.ADWAITA to Palette(
            PaletteId.ADWAITA, Color(0xFF242424), Color(0xFFFFFFFF), Color(0xFF78AEED),
            deskColor = Color(0xFF1A1A1A),
        ),
    )

    /** The e-paper palettes: paper and ink only, they fit every shell. */
    val ePaper: List<PaletteId> = PaletteId.values().filter { !it.native }

    /** The native desktop palettes, each one paired with the shell it belongs to. */
    val native: List<PaletteId> = PaletteId.values().filter { it.native }

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
