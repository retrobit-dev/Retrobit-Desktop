package dev.retrobit.desktop.shell

import androidx.compose.ui.graphics.toArgb
import dev.retrobit.desktop.ui.theme.Palette
import dev.retrobit.desktop.ui.theme.WidgetSkin

/**
 * Shared state of the **cyberdeck chrome**: the frame the launcher draws *over* the app that owns
 * the screen.
 *
 * Why this exists: Android only lets a normal app place another app inside its own view through a
 * freeform (floating) system window, and this desktop deliberately never asks for one. Every app
 * opens full screen - that is the model now, there are no desktop boxes either - and the launcher
 * draws *on top* of the app instead (with the "display over other apps" permission): the black
 * frame, the title rail and the dock are overlay strips, so whatever app is in front reads as a
 * window of this desktop. Our own apps get the same frame from the launcher itself
 * ([dev.retrobit.desktop.ui.desktop.ShellHost]), so the desktop looks the same either way.
 *
 * The launcher view model fills this object in and wires the actions; [ChromeService] owns the
 * overlay windows and calls back once a second ([onRefresh]) so the rails always read fresh data.
 */
object CyberdeckChrome {

    /** The four palette colours the rails are printed with, as ARGB ints. */
    data class Ink(val paper: Int, val ink: Int, val mid: Int, val accent: Int)

    /**
     * The window chrome the overlays draw, mapped straight from the active palette.
     *
     * `null` means "print it": the e-paper palettes keep their square paper rails with ink rules
     * and the hard checker shadow, exactly as before. A native palette gets its own desktop chrome
     * instead - the face, the title-strip gradient, the hairline border, the corner radius and the
     * accent its windows use (see [WidgetSkin], the contract the widgets already follow, so a theme
     * change moves the rails, the dock and the widgets together).
     */
    data class Skin(
        /** Face of the rails, the dock and the quick panel. */
        val face: Int,
        val border: Int,
        val borderDp: Float,
        val cornerDp: Float,
        /** Title strip of the top rail: the theme's own title bar, gradient or flat. */
        val titleTop: Int,
        val titleBottom: Int,
        val titleText: Int,
        /** A key or tile that is switched on / holding the app in front. */
        val plate: Int,
        val plateText: Int,
        /** Hairline rules inside a surface. */
        val soft: Int,
        val accent: Int,
    ) {
        val flatTitle: Boolean get() = titleTop == titleBottom

        /** Radius of a small part: never as round as the surface it sits on. */
        fun tile(share: Float = 0.6f, cap: Float = 10f): Float =
            if (cornerDp <= 0f) 0f else (cornerDp * share).coerceAtMost(cap)

        companion object {
            /** The chrome of [p], or `null` while the palette prints an e-paper shell. */
            fun of(p: Palette): Skin? {
                if (!p.isNative) return null
                val w = WidgetSkin.of(p)
                return Skin(
                    face = w.panel.toArgb(),
                    border = w.border.toArgb(),
                    borderDp = w.borderDp,
                    cornerDp = w.cornerDp,
                    titleTop = w.headerTop.toArgb(),
                    titleBottom = w.headerBottom.toArgb(),
                    titleText = w.headerText.toArgb(),
                    plate = w.plate.toArgb(),
                    plateText = w.plateText.toArgb(),
                    soft = w.separator.toArgb(),
                    accent = w.meterFill.toArgb(),
                )
            }
        }
    }

    /** One button of a rail / one entry of the dock. */
    data class Bar(
        val label: String,
        val action: String,
        val active: Boolean = false,
        /** Package of the app, so the dock can print its real icon instead of a name only. */
        val pkg: String? = null,
        /** True when the app is pinned to the dock: a hold toggles that. */
        val pinned: Boolean = false,
    )

    data class State(
        val ink: Ink = Ink(0xFFE8E5DA.toInt(), 0xFF1B1B1B.toInt(), 0xFF3F3F3F.toInt(), 0xFF3F3F3F.toInt()),
        /** Name shown in the title rail: the app in front, or "desktop". */
        val title: String = "desktop",
        /** True when an app owns the screen (so the frame reads as a window around it). */
        val inFrame: Boolean = false,
        val desktop: Int = 1,
        val desks: Int = 1,
        /** The dock list: the pinned apps, or the most used ones (see railMode). */
        val dock: List<Bar> = emptyList(),
        /** Desktop scale of the launcher, so the rails match the rest of the UI. */
        val scale: Float = 1f,
        val topRail: Boolean = true,
        val bottomDock: Boolean = true,
        /** Hide the rails after a few seconds of no use, leaving only the frame and a handle. */
        val autoHide: Boolean = true,
        val autoHideSecs: Int = 6,
        val net: String = "",
        /** "pinned" (the dock shows the pinned apps) or "used" (the most used apps first). */
        val railMode: String = "pinned",
        /** How big the rails read: 1f is the plain size, and the rail/dock heights follow it. */
        val size: Float = 1f,
        /**
         * Screen brightness the overlay windows carry: 0.05..1, or -1 while the tablet decides.
         * The rails are the only windows we own while another app is in front, so this is what
         * makes the quick panel's brightness groove visible from inside an app.
         */
        val brightness: Float = -1f,
        /** A short confirmation ("pinned to the dock: Files") drawn on the rail for a moment. */
        val flash: String = "",
        /** Window chrome of the active palette: `null` on the e-paper palettes (printed rails). */
        val skin: Skin? = null,
    )

    /**
     * Rail & dock size as a multiplier. It is deliberately separate from the launcher's own UI
     * scale: the chrome sits on top of *other* apps, and those icons have nothing to do with how
     * big the desktop grid should be.
     */
    fun sizeFactor(name: String): Float = when (name) {
        "compact" -> 0.85f
        "large" -> 1.2f
        "huge" -> 1.4f
        else -> 1f
    }

    @Volatile
    var state: State = State()

    // ── actions, wired by the launcher view model ───────────────────────────────
    /** Bring the desktop (this launcher) back to the front. */
    @Volatile
    var onHome: (() -> Unit)? = null

    /** A short confirmation to print on the rail (used by the keyboard key). */
    @Volatile
    var onFlash: ((String) -> Unit)? = null

    /** The user pressed hide on a rail and wants the chrome off for good. */
    @Volatile
    var onHideChrome: (() -> Unit)? = null

    /** Open an installed app by package name. */
    @Volatile
    var onOpenPackage: ((String) -> Unit)? = null

    /** Called once a second by the service so the state can be refreshed from the launcher. */
    @Volatile
    var onRefresh: (() -> Unit)? = null

    /** "hide the rails for now" (the eye key): they come back with the next app, not for good. */
    @Volatile
    var onHideNow: (() -> Unit)? = null

    /** A tap on an empty spot of a rail, or on a handle: show or hide the rails right away. */
    @Volatile
    var onToggleRails: (() -> Unit)? = null

    /** The dock divider was tapped: switch between the pinned apps and the most used ones. */
    @Volatile
    var onRailMode: (() -> Unit)? = null

    /** A rail key was held: "app:<pkg>" pins or unpins it in the dock. */
    @Volatile
    var onRailHold: ((String) -> Unit)? = null

    /** The panel key was tapped: open the quick panel over the running app. */
    @Volatile
    var onPanel: (() -> Unit)? = null
}
