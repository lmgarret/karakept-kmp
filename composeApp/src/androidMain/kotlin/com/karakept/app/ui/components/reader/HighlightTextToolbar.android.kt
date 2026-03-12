package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/**
 * Android implementation that shows a floating Compose Popup with Highlight,
 * Copy, and Select All actions when text is selected in a [SelectionContainer].
 *
 * Uses a [PopupPositionProvider] that converts the window-space selection rect
 * (received from [TextToolbar.showMenu]) into absolute popup coordinates so the
 * toolbar appears directly above the selected text, regardless of where the
 * composable sits in the layout tree.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val clipboardManager = LocalClipboardManager.current

    var showPopup by remember { mutableStateOf(false) }
    var selectionRect by remember { mutableStateOf(Rect.Zero) }
    var copyCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectAllCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showPopup) {
        val positionProvider = remember(selectionRect) {
            AboveSelectionPositionProvider(selectionRect)
        }
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = { showPopup = false }
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = {
                        // Capture callback and hide before invoking to avoid state conflicts
                        val cb = copyCallback
                        showPopup = false
                        cb?.invoke()
                        val text = clipboardManager.getText()?.text
                        if (!text.isNullOrBlank()) {
                            onHighlightRequested(text)
                        }
                    }) {
                        Text(
                            "Highlight",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (copyCallback != null) {
                        TextButton(onClick = {
                            copyCallback?.invoke()
                            showPopup = false
                        }) { Text("Copy") }
                    }
                    if (selectAllCallback != null) {
                        TextButton(onClick = {
                            selectAllCallback?.invoke()
                        }) { Text("Select All") }
                    }
                }
            }
        }
    }

    return remember {
        object : TextToolbar {
            private var _status = TextToolbarStatus.Hidden
            override val status get() = _status

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                copyCallback = onCopyRequested
                selectAllCallback = onSelectAllRequested
                selectionRect = rect
                showPopup = true
                _status = TextToolbarStatus.Shown
            }

            override fun hide() {
                showPopup = false
                _status = TextToolbarStatus.Hidden
            }
        }
    }
}

/**
 * Positions a popup above [selectionRect] using window-space coordinates.
 *
 * [selectionRect] comes from [TextToolbar.showMenu], which provides it in the
 * composable root's coordinate space (equivalent to window-space on Android).
 * [PopupPositionProvider.calculatePosition] returns absolute window coordinates,
 * so we can use the rect values directly without anchor-relative math.
 *
 * Falls back to below the selection when there is insufficient space above.
 */
private class AboveSelectionPositionProvider(
    private val selectionRect: Rect
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = selectionRect.left.roundToInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val yAbove = selectionRect.top.roundToInt() - popupContentSize.height - 8
        val yBelow = selectionRect.bottom.roundToInt() + 8
        val y = if (yAbove >= 0) yAbove
                else yBelow.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
