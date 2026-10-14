package dev.retrobit.desktop.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Desktop-class input: mouse right click and pen barrel button open the context menu.
 * (The MatePad's M-Pencil reports a secondary button when the barrel button is squeezed.)
 */
fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                onClick(event.changes.first().position)
                event.changes.forEach { it.consume() }
            }
        }
    }
}
