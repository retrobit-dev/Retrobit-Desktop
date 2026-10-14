package dev.retrobit.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import dev.retrobit.desktop.model.IconTheme
import dev.retrobit.desktop.ui.theme.LocalIconTheme
import dev.retrobit.desktop.ui.theme.LocalPalette

/**
 * 16×16 pixel-art icons.  '#' = ink, 'o' = paper, '+' = mid-tone, anything else = transparent.
 */
enum class Glyph(vararg raw: String) {
    FOLDER(
        "",
        " #####",
        "#ooooo#",
        "#oooooo#######",
        "#oooooooooooooo#",
        "################",
        "#oooooooooooooo#",
        "#oooooooooooooo#",
        "#oooooooooooooo#",
        "#oooooooooooooo#",
        "#oooooooooooooo#",
        "#oooooooooooooo#",
        "################",
    ),
    TERMINAL(
        "################",
        "#oooooooooooooo#",
        "################",
        "################",
        "##o#############",
        "###o############",
        "####o###########",
        "###o############",
        "##o####ooooo####",
        "################",
        "################",
        "################",
    ),
    NOTES(
        "  #########",
        "  #ooooooo##",
        "  #ooooooo#o#",
        "  #ooooooo####",
        "  #oooooooooo#",
        "  #o######ooo#",
        "  #oooooooooo#",
        "  #o########o#",
        "  #oooooooooo#",
        "  #o#######oo#",
        "  #oooooooooo#",
        "  #o#####oooo#",
        "  #oooooooooo#",
        "  ############",
    ),
    SETTINGS(
        "   ###",
        "   #o#",
        "################",
        "   #o#",
        "   ###",
        "          ###",
        "          #o#",
        "################",
        "          #o#",
        "          ###",
        "      ###",
        "      #o#",
        "################",
        "      #o#",
        "      ###",
    ),
    SYSMON(
        "################",
        "#oooooooooooooo#",
        "#ooooooooooo#oo#",
        "#oooooooooo#o#o#",
        "#ooo#oooooo#o#o#",
        "#oo#o#oooo#ooo##",
        "#o#ooo#ooo#oooo#",
        "##ooooo#o#ooooo#",
        "#oooooo#o#ooooo#",
        "#ooooooo#oooooo#",
        "#oooooooooooooo#",
        "################",
        "     ######",
        "   ##########",
    ),
    CALENDAR(
        "  ############  ",
        "  ##o######o##  ",
        "  ############  ",
        "  #oooooooooo#  ",
        "  #o#o#o#o#oo#  ",
        "  #oooooooooo#  ",
        "  #o###o###oo#  ",
        "  #oooooooooo#  ",
        "  #o#o#o#o#oo#  ",
        "  #oooooooooo#  ",
        "  #o###o##oo #  ",
        "  ############  ",
    ),
    KEYBOARD(
        "  ############  ",
        "  #oooooooooo#  ",
        "  #o#o#o#o#oo#  ",
        "  #oooooooooo#  ",
        "  #o#o#o#o#oo#  ",
        "  #oooooooooo#  ",
        "  #oo####oooo#  ",
        "  ############  ",
    ),
    PEN(
        "            ##  ",
        "           #o#  ",
        "          #oo#  ",
        "         #oo#   ",
        "        #oo#    ",
        "       #oo#     ",
        "      #oo#      ",
        "     #oo#       ",
        "    #oo#        ",
        "   #oo#         ",
        "  #oo#          ",
        "  ###           ",
    ),
    MOUSE(
        "   ######   ",
        "  #oooooo#  ",
        "  #oo##oo#  ",
        "  #oo##oo#  ",
        "  #oooooo#  ",
        "  #oooooo#  ",
        "  #oooooo#  ",
        "  #oooooo#  ",
        "  #oooooo#  ",
        "   #oooo#   ",
        "    ####    ",
    ),
    CURSOR(
        "##          ",
        "###         ",
        "####        ",
        "#o##        ",
        "#oo##       ",
        "#ooo##      ",
        "#oooo##     ",
        "#ooooo##    ",
        "#oooooo##   ",
        "#ooooooo##  ",
        "#####oooo#  ",
        "   #ooooo#  ",
        "   #ooooo#  ",
        "    #ooo#   ",
        "    #ooo#   ",
        "     ##     ",
    ),
    ABOUT(
        "  ############",
        " #oooooooooooo#",
        "#oooooo##oooooo#",
        "#oooooo##oooooo#",
        "#oooooooooooooo#",
        "#ooooo###oooooo#",
        "#oooooo##oooooo#",
        "#oooooo##oooooo#",
        "#oooooo##oooooo#",
        "#oooooo##oooooo#",
        "#ooooo####ooooo#",
        " #oooooooooooo#",
        "  ############",
    ),
    GRID(
        "####  ####  ####",
        "#oo#  #oo#  #oo#",
        "#oo#  #oo#  #oo#",
        "####  ####  ####",
        "",
        "",
        "####  ####  ####",
        "#oo#  #oo#  #oo#",
        "#oo#  #oo#  #oo#",
        "####  ####  ####",
        "",
        "",
        "####  ####  ####",
        "#oo#  #oo#  #oo#",
        "#oo#  #oo#  #oo#",
        "####  ####  ####",
    ),
    HDD(
        "################",
        "#oooooooooooooo#",
        "#o++++++++++++o#",
        "#oooooooooooooo#",
        "################",
        "#oooooooooooooo#",
        "#o##oooooooo#oo#",
        "#oooooooooooooo#",
        "################",
    ),
    HOME(
        "       ##",
        "      #oo#",
        "     #oooo#",
        "    #oooooo#",
        "   #oooooooo#",
        "  #oooooooooo#",
        " ##############",
        "  #oooooooooo#",
        "  #ooo###oooo#",
        "  #ooo#o#oooo#",
        "  #ooo#o#o##o#",
        "  #ooo#o#o##o#",
        "  #ooo#o#oooo#",
        "  ############",
    ),
    COMPUTER(
        " ##############",
        " #oooooooooooo#",
        " #o##########o#",
        " #o#++++++++#o#",
        " #o#++++++++#o#",
        " #o#++++++++#o#",
        " #o#++++++++#o#",
        " #o##########o#",
        " #oooooooooo#o#",
        " ##############",
        "      #oo#",
        "    ########",
        "  ############",
        "  #oooooooooo#",
        "  ############",
    ),
    LOGO(
        "################",
        "################",
        "###oooooooo#####",
        "###ooo###ooo####",
        "###ooo####oo####",
        "###ooo###ooo####",
        "###oooooooo#####",
        "###ooo##ooo#####",
        "###ooo###ooo####",
        "###ooo####ooo###",
        "###ooo#####ooo##",
        "################",
        "################",
    ),
    POWER(
        "       ##",
        "   ##  ##  ##",
        "  ##   ##   ##",
        " ##    ##    ##",
        " #     ##     #",
        "##            ##",
        "##            ##",
        "##            ##",
        " #            #",
        " ##          ##",
        "  ##        ##",
        "   ##########",
        "     ######",
    ),
    SEARCH(
        "   #####",
        "  #ooooo#",
        " #ooooooo#",
        " #ooooooo#",
        " #ooooooo#",
        " #ooooooo#",
        "  #ooooo#",
        "   ######",
        "        ###",
        "         ###",
        "          ###",
        "           ##",
    ),
    HELP(
        "                ",
        "                ",
        "                ",
        "     ######     ",
        "    ##    ##    ",
        "    ##    ##    ",
        "          ##    ",
        "      #####     ",
        "     ##         ",
        "     ##         ",
        "                ",
        "     ##         ",
        "     ##         ",
        "                ",
        "                ",
        "                ",
    ),
    UP(
        "                ",
        "                ",
        "                ",
        "       ##       ",
        "      #oo#      ",
        "     #oooo#     ",
        "    #oooooo#    ",
        "   #oooooooo#   ",
        "  #oooooooooo#  ",
        " #oooooooooooo# ",
        "################",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
    ),
    DOWN(
        "                ",
        "                ",
        "                ",
        "################",
        " #oooooooooooo# ",
        "  #oooooooooo#  ",
        "   #oooooooo#   ",
        "    #oooooo#    ",
        "     #oooo#     ",
        "      #oo#      ",
        "       ##       ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
    ),
    CLOSE(
        "                ",
        "                ",
        " ##          ## ",
        "  ##        ##  ",
        "   ##      ##   ",
        "    ##    ##    ",
        "     ##  ##     ",
        "      ####      ",
        "      ####      ",
        "     ##  ##     ",
        "    ##    ##    ",
        "   ##      ##   ",
        "  ##        ##  ",
        " ##          ## ",
        "                ",
        "                ",
    ),
    EYE(
        "                ",
        "                ",
        "                ",
        "     ######     ",
        "   ##      ##   ",
        "  #          #  ",
        " #   ######   # ",
        "#   ##oooo##   #",
        "#   #oooooo#   #",
        "#   #oooooo#   #",
        "#   ##oooo##   #",
        " #   ######   # ",
        "  #          #  ",
        "   ##      ##   ",
        "     ######     ",
        "                ",
    ),
    SPARK(
        "                ",
        "                ",
        "       ##       ",
        "       ##       ",
        "      ####      ",
        "     ######     ",
        "    ########    ",
        "  ############# ",
        "  ############# ",
        "    ########    ",
        "     ######     ",
        "      ####      ",
        "       ##       ",
        "       ##       ",
        "       ##       ",
        "                ",
    ),
    STAR(
        "      ####      ",
        "      ####      ",
        "      ####      ",
        "      ####      ",
        "    ########    ",
        "    ########    ",
        "################",
        "################",
        "################",
        "################",
        "    ########    ",
        "    ########    ",
        "      ####      ",
        "      ####      ",
        "      ####      ",
        "      ####      ",
    ),
    ROTATE(
        "                ",
        "        #####   ",
        "       #######  ",
        "  ##   ##    ## ",
        " ##    #      # ",
        "##            # ",
        "#             # ",
        "#             # ",
        "##           ## ",
        " ##         ##  ",
        "  ##       ##   ",
        "   ##     ##    ",
        "    #######     ",
        "                ",
        "                ",
        "                ",
    ),
    BARS(
        "                ",
        "                ",
        "                ",
        "             ## ",
        "             ## ",
        "         ##  ## ",
        "         ##  ## ",
        "     ##  ##  ## ",
        "     ##  ##  ## ",
        " ##  ##  ##  ## ",
        " ##  ##  ##  ## ",
        " ##  ##  ##  ## ",
        " ##  ##  ##  ## ",
        " ##  ##  ##  ## ",
        " ##  ##  ##  ## ",
        "                ",
    ),
    ;

    /** Normalised 16×16 grid, vertically centred. */
    val rows: List<String> = run {
        val r = raw.map { it.take(16).padEnd(16, ' ') }.take(16)
        val top = (16 - r.size) / 2
        List(top) { " ".repeat(16) } + r + List(16 - r.size - top) { " ".repeat(16) }
    }
}

/**
 * A 16x16 pixel-art glyph in the palette's ink. [tint] overrides the ink and [clear] the paper, so a
 * glyph can be printed on a plate (a selected row, an active button) in the plate's own colours.
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    clear: Color? = null,
) {
    val p = LocalPalette.current
    Canvas(modifier.size(iconSize)) {
        val cell = this.size.width / 16f
        paintGlyph(glyph, 0f, 0f, cell, tint ?: p.ink, clear ?: p.paper, p.mid)
    }
}

/**
 * One glyph, cell by cell: `#` = ink, `o` = the surface showing through, `+` = a mid tone. The
 * glyphs are 16 cells wide, so [cell] is a sixteenth of the icon and the origin places it.
 */
private fun DrawScope.paintGlyph(
    glyph: Glyph,
    originX: Float,
    originY: Float,
    cell: Float,
    ink: Color,
    hole: Color,
    mid: Color,
) {
    val ext = Size(cell + 0.6f, cell + 0.6f)
    glyph.rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            val c = when (ch) {
                '#' -> ink
                'o' -> hole
                '+' -> mid
                else -> null
            }
            if (c != null) drawRect(c, Offset(originX + x * cell, originY + y * cell), ext)
        }
    }
}

/**
 * An **internal** icon: the pixel glyphs of the shell itself (launch, find, shell, mon, note, setup,
 * term …), drawn the way the active palette draws its icons.
 *
 * The e-paper palettes keep printing the bare glyph — that *is* their icon style. A native desktop
 * dresses a symbolic icon the way it dresses its app icons: in the shape of the icon theme the
 * palette resolved to (Windows 95 the bevelled square with the accent band, Windows 11 the soft
 * rounded square, Aqua the glassy tile, Ubuntu the rounded square with the orange trim, GNOME the
 * circle), filled with the surface of the theme, edged in its border and carrying the glyph in its
 * ink. Choosing **Plain** as the icon shape takes the tiles away again and leaves the bare glyph,
 * on any palette.
 *
 * [active] is the switched-on state (the app in front, the page you are on): the plate colour and
 * its text, the same vocabulary every other selected part of the desktop uses.
 */
@Composable
fun GlyphTile(
    glyph: Glyph,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tileSize: Dp = iconSize * 1.5f,
    tint: Color? = null,
) {
    val p = LocalPalette.current
    val theme = LocalIconTheme.current
    if (!p.isNative || theme == IconTheme.PLAIN) {
        // the printed shell, or the user asked for plain icons: no tile, just the glyph
        GlyphIcon(glyph, iconSize, modifier, tint = tint)
        return
    }
    Canvas(modifier.size(tileSize)) {
        val side = size.minDimension
        val circle = theme == IconTheme.CIRCLE
        val fill = if (active) p.plate else p.soft
        val ink = tint ?: if (active) p.plateText else p.ink
        val line = if (active) p.plateText.copy(alpha = 0.6f) else p.edge
        // the shape vocabulary data/IconCache frames the app icons with: an app icon and a shell
        // icon of the same theme are the same tile, only the artwork inside differs
        val radius = IconTheme.tileRadius(theme, side)
        val inset = IconTheme.tileInset(theme, side)
        val body = Size(side - inset * 2f, side - inset * 2f)
        val origin = Offset(inset, inset)
        val center = Offset(side / 2f, side / 2f)
        if (circle) {
            drawCircle(fill, body.minDimension / 2f, center)
        } else {
            drawRoundRect(fill, origin, body, CornerRadius(radius))
        }
        val hair = side * 0.035f
        when (theme) {
            // Windows 11 and the flat pair: no outline, the surface alone carries the tile
            IconTheme.SQUIRCLE, IconTheme.FLAT -> Unit
            // 95: a hard ink frame, a bevel highlight top-left and the accent band along the bottom
            IconTheme.CLASSIC -> {
                drawRect(line, origin, body, style = Stroke(hair))
                val hi = size.minDimension * 0.02f
                drawLine(p.paper, Offset(inset + 1f, inset + 1f), Offset(side - inset - 1f, inset + 1f), hi)
                drawLine(p.paper, Offset(inset + 1f, inset + 1f), Offset(inset + 1f, side - inset - 1f), hi)
                val band = side * 0.07f
                drawRect(
                    p.accent,
                    Offset(inset + side * 0.06f, side - inset - band),
                    Size(body.width - side * 0.12f, band * 0.7f),
                )
            }
            IconTheme.YARU -> {
                // Ubuntu: the rounded square with the accent trim inside the edge
                drawRoundRect(line, origin, body, CornerRadius(radius), style = Stroke(hair))
                val trim = side * 0.05f
                drawRoundRect(
                    p.accent,
                    Offset(inset + trim, inset + trim),
                    Size(body.width - trim * 2f, body.height - trim * 2f),
                    CornerRadius(radius * 0.7f),
                    style = Stroke(hair * 0.7f),
                )
            }
            else -> {
                // Aqua, GNOME and the rounded one: a thin edge, plus the glassy top of Aqua
                if (circle) drawCircle(line, body.minDimension / 2f, center, style = Stroke(hair))
                else drawRoundRect(line, origin, body, CornerRadius(radius), Stroke(hair))
                if (theme == IconTheme.AQUA) {
                    val gloss = side * 0.02f
                    drawLine(
                        p.paper.copy(alpha = 0.55f),
                        Offset(inset + radius * 0.6f, inset + 1f),
                        Offset(side - inset - radius * 0.6f, inset + 1f),
                        gloss,
                    )
                }
            }
        }
        // the glyph, punched into the tile: its holes show the tile through
        val g = side * 0.60f
        paintGlyph(glyph, (side - g) / 2f, (side - g) / 2f, g / 16f, ink, fill, p.mid)
    }
}
