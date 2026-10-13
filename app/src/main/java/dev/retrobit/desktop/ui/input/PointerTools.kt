package dev.retrobit.desktop.ui.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class InputSource { TOUCH, MOUSE, PEN }

fun PointerType.isPen(): Boolean = this == PointerType.Stylus || this == PointerType.Eraser
fun PointerType.isPenOrMouse(): Boolean = isPen() || this == PointerType.Mouse

/**
 * Shared state for pen / mouse input:
 *
 *  · [source]        last pointer kind that touched the screen (hints, cursor rules)
 *  · [cursor]        pointer position (root coordinates) while a pen or mouse is active
 *  · [penRightClick] when enabled, a pen *double* tap opens the context menu (right click) while a
 *                    single pen tap is a left click — fired after the double-tap window so the
 *                    second tap can still turn it into a right click
 *  · [showCursor]    draw the e-paper pointer arrow for pen / mouse input
 *
 * Touch is never delayed; only pen taps that have a secondary action wait, exactly like a desktop
 * OS waits to tell a double click from a single click.
 */
class PointerRouter(
    val doubleTapWindowMs: Long = 320L,
) {
    var penRightClick by mutableStateOf(true)
    var showCursor by mutableStateOf(true)
    var source by mutableStateOf(InputSource.TOUCH)
    var cursor by mutableStateOf<Offset?>(null)
    var cursorPressed by mutableStateOf(false)

    fun clearCursor() {
        if (cursor != null) {
            cursor = null
            cursorPressed = false
        }
    }
}

/** Bookkeeping so the tray keyboard button knows which text field to focus. */
class KeyboardRequester {
    var visible by mutableStateOf(false)
    var lastFocus: FocusRequester? = null

    fun rememberFocus(fr: FocusRequester) {
        lastFocus = fr
    }
}

val LocalPointerRouter = staticCompositionLocalOf { PointerRouter() }
val LocalKeyboardRequester = staticCompositionLocalOf { KeyboardRequester() }

/**
 * Desktop click handling for surfaces that also have a context menu:
 *
 *  touch               → primary action immediately
 *  mouse left button   → primary action immediately
 *  mouse right button  → secondary action (context menu)
 *  pen barrel button   → secondary action (context menu)
 *  pen single tap      → primary action (after the double-tap window)
 *  pen double tap      → secondary action (context menu)
 *
 * [key] restarts the detector when the composable is recycled for another item, which cancels any
 * still pending single-tap action.
 */
@Composable
fun Modifier.desktopClick(
    primary: (Offset) -> Unit,
    secondary: ((Offset) -> Unit)? = null,
    key: Any? = Unit,
    router: PointerRouter = LocalPointerRouter.current,
): Modifier {
    val primaryState = rememberUpdatedState(primary)
    val secondaryState = rememberUpdatedState(secondary)
    val routerState = rememberUpdatedState(router)
    return this.pointerInput(key) {
        coroutineScope {
            var pendingJob: Job? = null
            var pendingAction: (() -> Unit)? = null
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val secondaryDown = currentEvent.buttons.isSecondaryPressed
                    val pen = down.type.isPen()
                    // follow the gesture until it lifts or somebody else (drag / scroll) takes over
                    var consumed = false
                    var position = down.position
                    var release: PointerInputChange? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            consumed = true
                            break
                        }
                        position = change.position
                        if (!change.pressed) {
                            release = change
                            break
                        }
                    }
                    if (consumed) continue
                    // Claim the gesture: without this the surface underneath would treat the same tap
                    // as its own and, for a pen double tap, open the desktop menu as well.
                    release?.consume()
                    val secondaryAction = secondaryState.value
                    when {
                        secondaryDown && secondaryAction != null -> secondaryAction.invoke(position)
                        pen && secondaryAction != null && routerState.value.penRightClick -> {
                            if (pendingJob?.isActive == true) {
                                // second pen tap inside the window → right click
                                pendingJob?.cancel()
                                pendingJob = null
                                pendingAction = null
                                secondaryAction.invoke(position)
                            } else {
                                val action = { primaryState.value(position) }
                                pendingAction = action
                                pendingJob = launch {
                                    delay(routerState.value.doubleTapWindowMs)
                                    val a = pendingAction
                                    pendingAction = null
                                    a?.invoke()
                                }
                            }
                        }
                        else -> primaryState.value(position)
                    }
                }
            }
        }
    }
}

/**
 * Immediate drag for pen and mouse. Touch keeps using detectDragGesturesAfterLongPress, but a pen
 * or mouse should be able to move icons and windows straight away, like on a real desktop.
 */
fun Modifier.penOrMouseDrag(
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: (completed: Boolean) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!down.type.isPenOrMouse()) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        var dragging = false
        var cancelled = false
        var reference = down.position
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed) {
                if (dragging) cancelled = true
                break
            }
            if (!change.pressed) break
            if (!dragging) {
                if ((change.position - reference).getDistance() > slop) {
                    dragging = true
                    change.consume()
                    onStart(change.position)
                } else {
                    reference = change.position
                }
            } else {
                change.consume()
                onMove(change.position)
            }
        }
        if (dragging) onEnd(!cancelled)
    }
}

/** Keeps the pointer position (for the on-screen cursor) and the last input source up to date. */
fun Modifier.watchPointer(router: PointerRouter): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.lastOrNull() ?: continue
            val type = change.type
            if (type.isPenOrMouse()) {
                router.source = if (type == PointerType.Mouse) InputSource.MOUSE else InputSource.PEN
                router.cursor = change.position
                router.cursorPressed = change.pressed
            } else {
                if (router.source != InputSource.TOUCH) router.source = InputSource.TOUCH
                router.clearCursor()
            }
        }
    }
}
