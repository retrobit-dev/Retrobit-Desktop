package dev.retrobit.desktop.shell

/**
 * Shared state of the **quick panel**: the one surface that adjusts the cyberdeck without leaving the
 * app that is in front (brightness, rotation, night mode, the chrome itself, per-app rules).
 *
 * The launcher view model fills [state] in and wires the [onAction] / [onRefresh] callbacks;
 * [PanelService] owns the overlay window and re-reads the state whenever the launcher says it
 * changed.
 */
object PanelChrome {

    /** One app the rules can be written for: what is open right now, plus the pinned dock. */
    data class AppRef(val label: String, val pkg: String)

    data class State(
        val paper: Int = 0xFFE8E5DA.toInt(),
        val ink: Int = 0xFF1B1B1B.toInt(),
        val mid: Int = 0xFF3F3F3F.toInt(),
        val accent: Int = 0xFF3F3F3F.toInt(),
        val scale: Float = 1f,
        /** 0.1 .. 1.0, or null while the device controls the brightness itself. */
        val brightness: Float? = null,
        val rotateLocked: Boolean = false,
        val night: Boolean = false,
        val chromeOn: Boolean = false,
        val chromeAllowed: Boolean = false,
        /** "pinned" or "used": what the dock shows. */
        val railMode: String = "pinned",
        val topRail: Boolean = true,
        val dockOn: Boolean = true,
        val autoHide: Boolean = true,
        val autoHideSecs: Int = 6,
        /** Windows open right now: tap = bring it to the front and fit it into its frame. */
        val apps: List<AppRef> = emptyList(),
        /** Apps the chrome must stay away from. */
        val noChrome: List<String> = emptyList(),
        /** "compact" / "normal" / "large": how big the rails read. */
        val sizeName: String = "normal",
        /** Live clock, printed in the header so the panel reads like the rest of the shell. */
        val clock: String = "",
    )

    @Volatile
    var state: State = State()

    /** Called by the panel for every tap: see [dev.retrobit.desktop.shell.PanelSwitch.send]. */
    @Volatile
    var onAction: ((String) -> Unit)? = null

    /** Called before the panel draws, so it reads a fresh state. */
    @Volatile
    var onRefresh: (() -> Unit)? = null

    /** The panel asks to be closed (its x key, or a tap on the dimmed desktop). */
    @Volatile
    var onClose: (() -> Unit)? = null

    /** True while the panel window is up (the launcher draws it dimmed and can close it). */
    @Volatile
    var visible: Boolean = false
}
