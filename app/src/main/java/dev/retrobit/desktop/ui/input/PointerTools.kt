package dev.retrobit.desktop.ui.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
 *  · [penRightClick] when enabled, **holding** the pen opens the context menu (right click) while a
 *                    tap stays a left click — exactly like a mouse on a desktop. The barrel button
 *                    keeps working too.
 *  · [holdMs]        how long the pen has to rest before the press counts as a right click
 *  · [touchHoldEnabled] holding a finger on an empty surface also opens the menu (launcher habit)
 *  · [showCursor]    draw the e-paper pointer arrow for pen / mouse input
 *  · [textInput]     a text field has the focus, so the stylus goes back to being a stylus
 *  · [penIsPen]      honour [textInput] at all (setting "stylus becomes a pen in text fields")
 *
 * Nothing is delayed any more: taps fire immediately and only a hold produces the context menu.
 */
class PointerRouter(
    val doubleTapWindowMs: Long = 320L,
) {
    /** Hold the pen (or a finger on an empty surface) to open the context menu. */
    var penRightClick by mutableStateOf(true)
    var holdMs: Long = 380L
    var touchHoldEnabled by mutableStateOf(true)
    var showCursor by mutableStateOf(true)
    var source by mutableStateOf(InputSource.TOUCH)
    var cursor by mutableStateOf<Offset?>(null)
    var cursorPressed by mutableStateOf(false)

    /** The text fields that hold the focus right now (see [textFocusChanged]). */
    private val focusedFields = mutableStateListOf<Any>()

    /** Honor [textInput]: false keeps the old "the pen is always a mouse" behaviour. */
    var penIsPen by mutableStateOf(true)

    /**
     * True while a text field has the focus. The M-Pencil then goes back to being a **pen**, not a
     * mouse: holding it no longer opens a context menu, dragging it no longer moves icons, and the
     * e-paper arrow gives way to the field's own I-beam — so the pen can select text and hand
     * writing over to the tablet's own stylus input.
     */
    val textInput: Boolean get() = penIsPen && focusedFields.isNotEmpty()

    /** A text field gained or lost the focus ([field] is its identity, one per field). */
    fun textFocusChanged(field: Any, focused: Boolean) {
        if (focused) {
            if (!focusedFields.contains(field)) focusedFields.add(field)
        } else {
            focusedFields.remove(field)
        }
    }

    /** Forget every focused field: called when the shell or the app in front changes. */
    fun resetTextFocus() {
        if (focusedFields.isNotEmpty()) focusedFields.clear()
    }

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
 *  touch held down     → secondary action (context menu), like long-press on a phone
 *  mouse left button   → primary action
 *  mouse right button  → secondary action (context menu)
 *  pen barrel button   → secondary action (context menu)
 *  pen tap             → primary action
 *  pen **held down**   → secondary action (context menu), the pen's right click
 *
 * While a text field has the focus the pen is a pen again ([PointerRouter.textInput]): it behaves
 * like a finger here — no right click on hold, no drag — and the tablet's own stylus input (text
 * selection, handwriting) reaches the field untouched.
 *
 * With [select] and [doubleClick] set it behaves like a desktop: the first tap only selects (or
 * arms) the item, a second tap inside [PointerRouter.doubleTapWindowMs] opens it, and a hold still
 * opens the context menu. Moving the pointer past the touch slop never fires a tap at all, so a
 * scroll inside a list cannot launch an application by accident.
 *
 * Holding the pen is what opens the context menu — the same gesture as right-clicking on a desktop,
 * and the way into editing widgets and icons. [key] restarts the detector when the composable is
 * recycled for another item, which cancels a still pending gesture.
 */
@Composable
fun Modifier.desktopClick(
    primary: (Offset) -> Unit,
    secondary: ((Offset) -> Unit)? = null,
    /** Fired by the first tap of a pair: the item is only selected, [primary] comes on the second. */
    select: ((Offset) -> Unit)? = null,
    /** Desktop-style opening: [primary] runs on the second tap, [select] on the first. */
    doubleClick: Boolean = false,
    longPressMs: Long? = null,
    /** Surfaces that already implement a long press (icon tiles) pass false to avoid a double menu. */
    holdTouch: Boolean = true,
    key: Any? = Unit,
    router: PointerRouter = LocalPointerRouter.current,
): Modifier {
    val primaryState = rememberUpdatedState(primary)
    val secondaryState = rememberUpdatedState(secondary)
    val longPressState = rememberUpdatedState(longPressMs)
    val holdTouchState = rememberUpdatedState(holdTouch)
    val selectState = rememberUpdatedState(select)
    val doubleClickState = rememberUpdatedState(doubleClick)
    val routerState = rememberUpdatedState(router)
    return this.pointerInput(key) {
        coroutineScope {
            // survive between gestures: the second tap of a double click has to find the first one
            var lastTapAt = 0L
            var lastTapPos = Offset.Zero
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val secondaryDown = currentEvent.buttons.isSecondaryPressed
                    // the pen is a mouse, unless a text field has the focus: then it is a pen again
                    val penAsMouse = down.type.isPen() && !routerState.value.textInput
                    val touch = !down.type.isPenOrMouse() || (down.type.isPen() && !penAsMouse)
                    val secondary = secondaryState.value
                    val slop = viewConfiguration.touchSlop
                    val holdFor = longPressState.value ?: routerState.value.holdMs

                    var consumed = false
                    var position = down.position
                    var moved = false
                    var dragged = false
                    var fired = false
                    var release: PointerInputChange? = null
                    var latest: PointerInputChange? = down

                    // hold → right click: a timer runs while the pointer rests on the surface
                    val holdJob = if (secondary != null && !secondaryDown && ((penAsMouse && routerState.value.penRightClick) ||
                            (touch && holdTouchState.value && routerState.value.touchHoldEnabled))) {
                        launch {
                            delay(holdFor)
                            if (!moved && !consumed) {
                                fired = true
                                secondary.invoke(position)
                                // claim the press: a drag detector that is still waiting for its slop
                                // must not start dragging after the menu opened
                                latest?.consume()
                            }
                        }
                    } else null

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            consumed = true
                            break
                        }
                        latest = change
                        position = change.position
                        if (!moved && (change.position - down.position).getDistance() > slop) {
                            moved = true
                            dragged = true
                            holdJob?.cancel()
                        }
                        if (!change.pressed) {
                            release = change
                            break
                        }
                    }
                    holdJob?.cancel()
                    if (consumed) continue
                    // Claim the gesture: without this the surface underneath would treat the same press
                    // as its own and open a second menu.
                    release?.consume()
                    val selectAction = selectState.value
                    when {
                        dragged            -> Unit          // a scroll or a drag, never a click
                        secondaryDown && secondary != null -> secondary.invoke(position)
                        fired              -> Unit          // already handled by the hold
                        doubleClickState.value && selectAction != null -> {
                            val now = System.currentTimeMillis()
                            val close = now - lastTapAt <= routerState.value.doubleTapWindowMs &&
                                (lastTapPos - position).getDistance() <= slop * 2f
                            if (close) {
                                lastTapAt = 0L
                                primaryState.value(position)
                            } else {
                                lastTapAt = now
                                lastTapPos = position
                                selectAction.invoke(position)
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
    router: PointerRouter? = null,
): Modifier = this.pointerInput(router) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!down.type.isPenOrMouse()) return@awaitEachGesture
        // while a text field has the focus the pen is a pen: it must not drag the desktop around
        if (down.type.isPen() && router?.textInput == true) return@awaitEachGesture
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
