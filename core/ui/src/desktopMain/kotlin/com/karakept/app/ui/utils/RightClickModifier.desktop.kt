package com.karakept.app.ui.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier {
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

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onSecondaryClickWithPosition(onClick: (Offset) -> Unit): Modifier {
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

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onDesktopModifiedClick(
    key1: Any?,
    key2: Any?,
    onCtrlClick: (() -> Unit)?,
    onShiftClick: (() -> Unit)?
): Modifier {
    if (onCtrlClick == null && onShiftClick == null) return this
    return this.pointerInput(key1, key2) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press &&
                    event.button == PointerButton.Primary
                ) {
                    val modifiers = event.keyboardModifiers
                    val handled = when {
                        modifiers.isShiftPressed && onShiftClick != null -> {
                            onShiftClick.invoke()
                            true
                        }
                        (modifiers.isCtrlPressed || modifiers.isMetaPressed) && onCtrlClick != null -> {
                            onCtrlClick.invoke()
                            true
                        }
                        else -> false
                    }
                    if (handled) {
                        event.changes.forEach { it.consume() }
                        // Also consume the release event
                        var releaseEvent = awaitPointerEvent(PointerEventPass.Initial)
                        releaseEvent.changes.forEach { it.consume() }
                        while (releaseEvent.type != PointerEventType.Release) {
                            releaseEvent = awaitPointerEvent(PointerEventPass.Initial)
                            releaseEvent.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        }
    }
}
