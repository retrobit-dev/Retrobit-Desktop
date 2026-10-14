package dev.retrobit.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.retrobit.desktop.R
import dev.retrobit.desktop.model.DesktopStyle
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.IconTheme
import dev.retrobit.desktop.model.PaletteId

/**
 * The colours of a **real** desktop: the parts an e-paper palette has no room for.
 *
 * The e-paper engine only knows paper, ink and an accent, and it prints everything as a dither. A
 * native theme is the opposite: it has a window face, a border, a **title-bar gradient**, coloured
 * buttons, a selection highlight and a desktop wallpaper of its own — all of them the colours the
 * real desktop uses. When a palette carries a skin, the launcher stops printing and draws solid
 * chrome and real gradients instead, while every view keeps reading `paper` / `ink` / `accent`
 * (which the skin fills in) so nothing else has to know about it.
 */
@Immutable
data class NativeSkin(
    /** Window, panel, menu and dialog face — becomes [Palette.paper]. */
    val window: Color,
    /** Body text — becomes [Palette.ink]. */
    val text: Color,
    /** Window frame, panel edges, separators and menu borders. */
    val border: Color,
    /** Active title bar / headerbar: top and bottom of the real gradient (equal = flat). */
    val titleTop: Color,
    val titleBottom: Color,
    val titleText: Color,
    /** Title-bar buttons and push buttons. */
    val buttonFace: Color,
    val buttonText: Color,
    /** Selection / accent colour — becomes [Palette.accent]. */
    val highlight: Color,
    val highlightText: Color,
    /** The desktop itself, as the real theme paints it. */
    val deskTop: Color,
    val deskBottom: Color,
    /** Icon labels printed straight on the desk, and the plate behind a selected one. */
    val deskText: Color = Color.White,
    /** True when the desk gradient runs corner to corner instead of straight down. */
    val deskDiagonal: Boolean = false,
    /** Window corner radius, in dp. 0 = the square windows of Windows 95. */
    val cornerDp: Float = 0f,
    /** True for macOS: three round lights on the left instead of buttons on the right. */
    val trafficLights: Boolean = false,
    /** True for Win11 / GNOME: small circular flat buttons, no bevels. */
    val flatButtons: Boolean = false,
) {
    val isFlatTitle: Boolean get() = titleTop == titleBottom
}

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
    /**
     * The colours of the real desktop this palette copies, or `null` for the e-paper palettes.
     *
     * Every drawing surface asks the palette whether it has a skin: with one it draws solid chrome,
     * real gradients, rounded corners and coloured buttons; without one it keeps printing dither,
     * hard shadows and 1-bit title bars. That single switch is what makes "Windows 95" look like
     * Windows 95 instead of a grey e-paper theme.
     */
    val skin: NativeSkin? = null,
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

    /**
     * Background and text of a **selected** chip: a highlighted menu row, a tag, an active key.
     * The e-paper palettes invert (ink plate, paper text); a native theme uses its selection
     * colour, so a picked row lights up in Windows 95 navy or Ubuntu orange like the real thing.
     */
    val plate: Color get() = skin?.highlight ?: ink
    val plateText: Color get() = skin?.highlightText ?: paper

    /** True when this palette copies a real desktop instead of printing on e-paper. */
    val isNative: Boolean get() = skin != null

    /**
     * The icon shape this palette brings: every native desktop theme has its own (Windows 95 the
     * beveled square, Aqua the glossy tile, GNOME the circle), the e-paper palettes have none.
     */
    val iconTheme: IconTheme? get() = IconTheme.forPalette(id)
    /** Frame, panel and separator colour: the skin's own border, or the ink for e-paper. */
    val edge: Color get() = skin?.border ?: ink
    /** Title bar: the real gradient of the skin, or the flat e-paper ink bar. */
    val titleTop: Color get() = skin?.titleTop ?: ink
    val titleBottom: Color get() = skin?.titleBottom ?: ink
    val titleText: Color get() = skin?.titleText ?: paper
    /** Window corner radius in dp: square for e-paper and Windows 95. */
    val cornerDp: Float get() = skin?.cornerDp ?: 0f
    val isDark: Boolean get() = (paper.red + paper.green + paper.blue) < 1.5f
    fun invert(): Palette = copy(
        paper = ink,
        ink = paper,
        accentColor = null,
        inverted = !inverted,
        // night mode keeps a native desk recognisable, it just takes the glare out of it
        deskColor = deskColor?.let { lerp(it, Color.Black, 0.45f) },
        // inverting is the e-paper trick (paper <-> ink), so the native chrome is dropped rather
        // than printed half-real: a dark "Windows 95" with silver title bars would look broken
        skin = null,
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
        // Windows 95: silver chrome, black text, the navy→blue title gradient of the real thing,
        // bevelled grey buttons, navy selection, and that teal desk
        PaletteId.WIN95 to Palette(
            PaletteId.WIN95,
            paper = Color(0xFFC0C0C0), ink = Color(0xFF000000), accentColor = Color(0xFF000080),
            deskColor = Color(0xFF008080),
            skin = NativeSkin(
                window = Color(0xFFC0C0C0), text = Color(0xFF000000), border = Color(0xFF000000),
                titleTop = Color(0xFF000080), titleBottom = Color(0xFF1084D0), titleText = Color(0xFFFFFFFF),
                buttonFace = Color(0xFFC0C0C0), buttonText = Color(0xFF000000),
                highlight = Color(0xFF000080), highlightText = Color(0xFFFFFFFF),
                deskTop = Color(0xFF008080), deskBottom = Color(0xFF008080),
                deskText = Color(0xFFFFFFFF), cornerDp = 0f,
            ),
        ),
        // Windows 11 (dark): the Mica surfaces and the Fluent accent, flat title bar, rounded corners
        PaletteId.WIN11 to Palette(
            PaletteId.WIN11,
            paper = Color(0xFF202020), ink = Color(0xFFFFFFFF), accentColor = Color(0xFF60CDFF),
            deskColor = Color(0xFF1B1F24),
            skin = NativeSkin(
                window = Color(0xFF202020), text = Color(0xFFFFFFFF), border = Color(0xFF3A3A3A),
                titleTop = Color(0xFF2B2B2B), titleBottom = Color(0xFF2B2B2B), titleText = Color(0xFFFFFFFF),
                buttonFace = Color(0xFF2B2B2B), buttonText = Color(0xFFFFFFFF),
                highlight = Color(0xFF60CDFF), highlightText = Color(0xFF00243D),
                deskTop = Color(0xFF1B1F24), deskBottom = Color(0xFF0F1216),
                deskText = Color(0xFFFFFFFF), cornerDp = 8f, flatButtons = true,
            ),
        ),
        // macOS Aqua: snow chrome, the grey title gradient, red/amber/green traffic lights,
        // system blue selection, and the blue desk of the Aqua era
        PaletteId.AQUA to Palette(
            PaletteId.AQUA,
            paper = Color(0xFFF4F4F6), ink = Color(0xFF1D1D1F), accentColor = Color(0xFF0A84FF),
            deskColor = Color(0xFF3F72A8),
            skin = NativeSkin(
                window = Color(0xFFF4F4F6), text = Color(0xFF1D1D1F), border = Color(0xFFB9B9BF),
                titleTop = Color(0xFFF7F7F8), titleBottom = Color(0xFFDCDCDE), titleText = Color(0xFF1D1D1F),
                buttonFace = Color(0xFFF0F0F2), buttonText = Color(0xFF1D1D1F),
                highlight = Color(0xFF0A84FF), highlightText = Color(0xFFFFFFFF),
                deskTop = Color(0xFF3F72A8), deskBottom = Color(0xFF1E4E7E),
                deskText = Color(0xFFFFFFFF), cornerDp = 10f, trafficLights = true,
            ),
        ),
        // macOS dark: graphite chrome, same traffic lights and system blue, near-black desk
        PaletteId.AQUA_DARK to Palette(
            PaletteId.AQUA_DARK,
            paper = Color(0xFF1E1E20), ink = Color(0xFFF2F2F7), accentColor = Color(0xFF0A84FF),
            deskColor = Color(0xFF17191D),
            skin = NativeSkin(
                window = Color(0xFF1E1E20), text = Color(0xFFF2F2F7), border = Color(0xFF3C3C3F),
                titleTop = Color(0xFF3A3A3C), titleBottom = Color(0xFF2C2C2E), titleText = Color(0xFFF2F2F7),
                buttonFace = Color(0xFF2E2E30), buttonText = Color(0xFFF2F2F7),
                highlight = Color(0xFF0A84FF), highlightText = Color(0xFFFFFFFF),
                deskTop = Color(0xFF17191D), deskBottom = Color(0xFF0B0C0F),
                deskText = Color(0xFFFFFFFF), cornerDp = 10f, trafficLights = true,
            ),
        ),
        // Ubuntu / Yaru: light window, dark headerbar, the orange accent, aubergine desk
        PaletteId.UBUNTU to Palette(
            PaletteId.UBUNTU,
            paper = Color(0xFFF6F5F4), ink = Color(0xFF2E3436), accentColor = Color(0xFFE95420),
            deskColor = Color(0xFF77216F),
            skin = NativeSkin(
                window = Color(0xFFF6F5F4), text = Color(0xFF2E3436), border = Color(0xFFBEBAB6),
                titleTop = Color(0xFF3D3D3D), titleBottom = Color(0xFF2C2C2C), titleText = Color(0xFFFFFFFF),
                buttonFace = Color(0xFF4A4A4A), buttonText = Color(0xFFFFFFFF),
                highlight = Color(0xFFE95420), highlightText = Color(0xFFFFFFFF),
                deskTop = Color(0xFF77216F), deskBottom = Color(0xFF4B1749),
                deskText = Color(0xFFFFFFFF), deskDiagonal = true, cornerDp = 12f, flatButtons = true,
            ),
        ),
        // GNOME Adwaita (dark): the grey of GTK 4 / libadwaita, flat headerbar, Adwaita blue
        PaletteId.ADWAITA to Palette(
            PaletteId.ADWAITA,
            paper = Color(0xFF242424), ink = Color(0xFFFFFFFF), accentColor = Color(0xFF78AEED),
            deskColor = Color(0xFF1A1A1A),
            skin = NativeSkin(
                window = Color(0xFF242424), text = Color(0xFFFFFFFF), border = Color(0xFF3D3D3D),
                titleTop = Color(0xFF303030), titleBottom = Color(0xFF242424), titleText = Color(0xFFFFFFFF),
                buttonFace = Color(0xFF303030), buttonText = Color(0xFFFFFFFF),
                highlight = Color(0xFF78AEED), highlightText = Color(0xFF00325C),
                deskTop = Color(0xFF1A1A1A), deskBottom = Color(0xFF141414),
                deskText = Color(0xFFFFFFFF), cornerDp = 12f, flatButtons = true,
            ),
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
    /**
     * Frame colour of the icon tile. `null` on the e-paper palettes (they print the ink outline);
     * on a native theme it is the border of that desktop, so a Windows 95 tile is framed in black
     * and an Aqua one in the pale Aqua grey instead of the text colour.
     */
    val edge: Color? = null,
)

/**
 * Panel texture for faceplates (tray, widget headers, dock strips): the e-paper palettes print a
 * hatch on them, the native ones draw flat chrome the way a real desktop does. `null` = flat.
 */
@Composable
fun rememberPanelTexture(color: Color? = null): Brush? {
    val p = LocalPalette.current
    if (p.isNative) return null
    return rememberChromeBrush(ChromePattern.STRIPES_H, color ?: p.pattern, 1.dp)
}

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
