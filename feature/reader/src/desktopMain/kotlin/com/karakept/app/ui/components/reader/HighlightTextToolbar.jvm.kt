package com.karakept.app.ui.components.reader

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * Extracts the selected text from Compose's internal `SelectionManager` by
 * navigating the [onCopyRequested] lambda's closure fields via reflection.
 */
private fun extractSelectedTextViaReflection(onCopyRequested: (() -> Unit)?): String? {
    if (onCopyRequested == null) return null
    return try {
        val manager = findSelectionManager(onCopyRequested) ?: return null
        val method = manager.javaClass.methods
            .find { it.name.startsWith("getSelectedText") } ?: return null
        (method.invoke(manager) as? AnnotatedString)?.text?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

private fun findSelectionManager(
    root: Any,
    maxDepth: Int = 6,
    visited: MutableSet<Int> = mutableSetOf()
): Any? {
    val id = System.identityHashCode(root)
    if (!visited.add(id)) return null
    if (maxDepth <= 0) return null
    val name = root.javaClass.name
    if (name.contains("SelectionManager") && !name.contains("\$\$") && !name.contains("\$Lambda")) {
        return root
    }
    return try {
        for (field in root.javaClass.declaredFields) {
            field.isAccessible = true
            val value = field.get(root) ?: continue
            val typeName = value.javaClass.name
            if (typeName.startsWith("java.lang.") || typeName.startsWith("kotlin.")) continue
            val valName = value.javaClass.name
            if (valName.contains("SelectionManager") && !valName.contains("\$\$") && !valName.contains("\$Lambda")) {
                return value
            }
            val found = findSelectionManager(value, maxDepth - 1, visited)
            if (found != null) return found
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Desktop implementation that shows a floating popup with Highlight, Copy, and
 * Select All actions when text is selected in a `SelectionContainer`.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
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
                Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = {
                        showPopup = false
                        val selectedText = extractSelectedTextViaReflection(copyCallback)
                        if (selectedText != null) {
                            onHighlightRequested(selectedText)
                        }
                    }) {
                        Text("Highlight", color = MaterialTheme.colorScheme.primary)
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
                popupOffset = IntOffset(rect.left.toInt(), (rect.top - 48).coerceAtLeast(0f).toInt())
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
 * Desktop: provides a custom [TextContextMenu] via [LocalTextContextMenu] that
 * adds "Highlight" to the right-click context menu inside a [SelectionContainer].
 *
 * Uses the new Compose 1.10 [TextContextMenu] API which provides direct access
 * to the selected text via [TextContextMenu.TextManager.selectedText].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun HighlightContextMenuProvider(
    onHighlightRequested: (selectedText: String) -> Unit,
    content: @Composable () -> Unit
) {
    val latestCallback = rememberUpdatedState(onHighlightRequested)
    val parentTextMenu = LocalTextContextMenu.current

    val customTextMenu = remember(parentTextMenu) {
        object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit
            ) {
                // Add "Highlight" item that reads selected text from textManager
                ContextMenuDataProvider({
                    val selectedText = textManager.selectedText.text
                    if (selectedText.isNotEmpty()) {
                        listOf(
                            ContextMenuItem("Highlight") {
                                latestCallback.value(selectedText)
                            }
                        )
                    } else {
                        emptyList()
                    }
                }) {
                    // Delegate to the original TextContextMenu for default items (Copy, Select All)
                    parentTextMenu.Area(textManager, state, content = content)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalTextContextMenu provides customTextMenu
    ) {
        content()
    }
}
