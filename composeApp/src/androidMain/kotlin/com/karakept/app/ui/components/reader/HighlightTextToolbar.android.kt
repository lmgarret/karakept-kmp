package com.karakept.app.ui.components.reader

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.text.AnnotatedString

private const val TAG = "HighlightToolbar"
private const val MENU_LABEL_HIGHLIGHT = "Highlight"

private object HighlightMenuKey

// ─── Reading the selection out of Compose ────────────────────────────────────

/**
 * Returns true if [cls] is the real SelectionManager class, not a synthetic
 * lambda or inner class generated from it (e.g. `$$ExternalSyntheticLambda9`).
 */
private fun isSelectionManagerClass(cls: Class<*>): Boolean {
    val name = cls.name
    return name.contains("SelectionManager") &&
        !name.contains("\$\$") &&
        !name.contains("\$Lambda")
}

/**
 * Recursively searches [root]'s field tree for Compose's internal
 * `SelectionManager` instance. Skips JDK / Android framework types and avoids
 * cycles via an identity-hash visited set.
 *
 * `SelectionManager` ships inside the app, not the platform, so unlike the
 * framework internals this reflection is not subject to the non-SDK interface
 * restrictions.
 */
internal fun findSelectionManager(
    root: Any,
    maxDepth: Int = 6,
    visited: MutableSet<Int> = mutableSetOf()
): Any? {
    val id = System.identityHashCode(root)
    if (!visited.add(id)) return null
    if (maxDepth <= 0) return null
    if (isSelectionManagerClass(root.javaClass)) return root

    return try {
        for (field in root.javaClass.declaredFields) {
            field.isAccessible = true
            val value = field.get(root) ?: continue
            val typeName = value.javaClass.name
            if (typeName.startsWith("java.lang.") ||
                typeName.startsWith("kotlin.") ||
                typeName.startsWith("android.") ||
                typeName.startsWith("java.util.")
            ) continue
            if (isSelectionManagerClass(value.javaClass)) return value
            val found = findSelectionManager(value, maxDepth - 1, visited)
            if (found != null) return found
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Calls `SelectionManager.getSelectedText$<module>()` on [selectionManager].
 *
 * Compose exposes no public accessor for the current selection's text, so the
 * internal one is reached by reflection.
 */
internal fun readSelectedText(selectionManager: Any): String? {
    return try {
        val method = selectionManager.javaClass.methods
            .find { it.name.startsWith("getSelectedText") }
            ?: run {
                Log.w(TAG, "No getSelectedText on ${selectionManager.javaClass.name}")
                return null
            }
        (method.invoke(selectionManager) as? AnnotatedString)?.text?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read selected text: ${e.javaClass.name}: ${e.message}")
        null
    }
}

/**
 * Holds the `SelectionManager` behind the context menu currently being built.
 *
 * Compose hands the menu builder no reference to the selection, but every item
 * `SelectionContainer` contributes — Copy, Select all — closes over the
 * `SelectionManager` to perform its action. Reading one back out of those
 * closures is the only way to learn what the user selected.
 */
internal class SelectionManagerHolder {
    var current: Any? = null
        private set

    /** Records the `SelectionManager` reachable from [item], if any. */
    fun observe(item: TextContextMenuItem) {
        if (current != null) return
        current = findSelectionManager(item.onClick)
    }

    fun selectedText(): String? {
        val manager = current ?: run {
            Log.e(TAG, "No SelectionManager captured from the context menu components")
            return null
        }
        return readSelectedText(manager)
    }
}

// ─── Composable entry points ─────────────────────────────────────────────────

/**
 * Android does not need a custom [TextToolbar]. Compose's context menu drives
 * the selection toolbar through `LocalTextContextMenuToolbarProvider`, and
 * `LocalTextToolbar` is never consulted.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? = null

/**
 * Adds a "Highlight" entry to the text selection menu.
 *
 * The entry is contributed through Compose's public text context menu API, so
 * the platform renders it alongside Copy and Select all in the native selection
 * toolbar. Menu data is collected by traversing *ancestors* of the
 * `SelectionContainer`, which is why the modifiers sit on a wrapper around
 * [content] rather than inside it.
 *
 * Nothing here touches `com.android.internal`: an earlier implementation
 * injected the item by reflecting into the platform's ActionMode callback
 * wrapper, which the non-SDK interface restrictions block outright on recent
 * builds — `getDeclaredFields()` comes back empty — so the entry silently
 * disappeared (#295).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun HighlightContextMenuProvider(
    onHighlightRequested: (selectedText: String) -> Unit,
    content: @Composable () -> Unit
) {
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)
    val holder = remember { SelectionManagerHolder() }

    Box(
        modifier = Modifier
            .filterTextContextMenuComponents { component ->
                if (component is TextContextMenuItem) holder.observe(component)
                true
            }
            .appendTextContextMenuComponents {
                item(key = HighlightMenuKey, label = MENU_LABEL_HIGHLIGHT) {
                    holder.selectedText()?.let { latestOnHighlight.value(it) }
                    close()
                }
            }
    ) {
        content()
    }
}
