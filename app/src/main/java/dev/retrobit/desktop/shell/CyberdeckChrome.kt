package dev.retrobit.desktop.shell

/**
 * Shared state of the **cyberdeck chrome**: the frame the launcher draws *over* an app that the
 * system refused to float.
 *
 * Why this exists: Android only lets a normal app place another app inside its own view through a
 * freeform (floating) system window, and plenty of tablets simply do not have that windowing mode —
 * on those devices every app opens full screen and the desktop frame behind it is never seen. The
 * launcher can still draw *on top* of the app (with the "display over other apps" permission), so
 * the frame, the title rail and the dock are drawn as overlay strips: the app then reads as a
 * window of the desktop even though the system gave it the whole screen.
 *
 * The launcher view model fills this object in and wires the actions; [ChromeService] owns the
 * windows and calls back once a second ([onRefresh]) so the rails always read fresh data.
 */
object CyberdeckChrome {

    /** The four palette colours the rails are printed with, as ARGB ints. */
    data class Ink(val paper: Int, val ink: Int, val mid: Int, val accent: Int)

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
        /** True when the app in front came from one of our window frames. */
        val inFrame: Boolean = false,
        /** True when that app has a frame we can send it back into (drives the "fit" button). */
        val canRefit: Boolean = false,
        val desktop: Int = 1,
        val desks: Int = 1,
        /** Open app windows, so the dock can jump between them. */
        val windows: List<Bar> = emptyList(),
        /** Pinned dock apps. */
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
        /** A short confirmation ("pinned to the dock: Files") drawn on the rail for a moment. */
        val flash: String = "",
    )

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

    /** Send the app in front back into its window frame (relaunch it at the frame rectangle). */
    @Volatile
    var onRefit: (() -> Unit)? = null

    /** Focus the desktop window with this id (raises the app inside it). */
    @Volatile
    var onOpenWindow: ((Int) -> Unit)? = null

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

    /** A rail key was held: "win:<id>" opens that app's shell, "app:<pkg>" pins or unpins it. */
    @Volatile
    var onRailHold: ((String) -> Unit)? = null

    /** The panel key was tapped: open the quick panel over the running app. */
    @Volatile
    var onPanel: (() -> Unit)? = null
}
