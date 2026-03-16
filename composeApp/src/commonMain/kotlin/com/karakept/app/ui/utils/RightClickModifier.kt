package com.karakept.app.ui.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import getPlatform

/**
 * Triggers [onClick] when the user right-clicks (secondary button press) on desktop.
 * On non-desktop platforms this is a no-op.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier {
    if (!getPlatform().isDesktop) return this
    return this.pointerInput(onClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press &&
                    event.button == PointerButton.Secondary
                ) {
                    event.changes.forEach { it.consume() }
                    onClick()
                }
            }
        }
    }
}

/**
 * Triggers [onClick] with the click position when the user right-clicks on desktop.
 * On non-desktop platforms this is a no-op.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onSecondaryClickWithPosition(onClick: (Offset) -> Unit): Modifier {
    if (!getPlatform().isDesktop) return this
    return this.pointerInput(onClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press &&
                    event.button == PointerButton.Secondary
                ) {
                    val position = event.changes.firstOrNull()?.position ?: Offset.Zero
                    event.changes.forEach { it.consume() }
                    onClick(position)
                }
            }
        }
    }
}
