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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * Desktop implementation that shows a floating popup with Copy, Select All,
 * and Highlight actions when text is selected in a [SelectionContainer].
 *
 * The popup appears near the selection and allows the user to highlight
 * selected text in a single click. Copy is triggered first to put the
 * selected text on the clipboard, then it is read back for highlighting.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    var showPopup by remember { mutableStateOf(false) }
    var popupOffset by remember { mutableStateOf(IntOffset.Zero) }
    var copyCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectAllCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showPopup) {
        Popup(
            onDismissRequest = { showPopup = false },
            offset = popupOffset
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(modifier = androidx.compose.ui.Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = {
                        // Copy to clipboard first, then read back for highlight
                        copyCallback?.invoke()
                        val text = clipboardManager.getText()?.text
                        showPopup = false
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
                popupOffset = IntOffset(rect.left.toInt(), rect.bottom.toInt())
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
