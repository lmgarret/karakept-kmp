package com.karakept.app.ui.components.reader

import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString

private const val TAG = "HighlightToolbar"
private const val MENU_ID_HIGHLIGHT = 1001

// ─── Reflection helpers to extract selected text from Compose internals ──────

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
 */
private fun findSelectionManager(
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
 * Extracts the selected text from Compose's `SelectionManager` by navigating
 * the [onCopyRequested] lambda's closure fields via reflection.
 *
 * `SelectionManager` calls `TextToolbar.showMenu(onCopyRequested = { copy() })`,
 * so the lambda captures the manager. We find it and call `getSelectedText()`.
 */
private fun extractSelectedTextViaReflection(onCopyRequested: (() -> Unit)?): String? {
    if (onCopyRequested == null) return null
    return try {
        val manager = findSelectionManager(onCopyRequested) ?: run {
            Log.w(TAG, "SelectionManager not found in onCopyRequested closure tree")
            return null
        }
        val method = manager.javaClass.methods
            .find { it.name.startsWith("getSelectedText") }
            ?: run {
                Log.w(TAG, "getSelectedText method not found on ${manager.javaClass.name}")
                return null
            }
        (method.invoke(manager) as? AnnotatedString)?.text?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to extract selected text: $e")
        null
    }
}

// ─── Custom TextToolbar with Highlight action ────────────────────────────────

/**
 * Custom [TextToolbar] that manages the floating [ActionMode] directly,
 * adding a "Highlight" item alongside the standard Copy and Select All.
 *
 * Key fix: suppresses [showMenu] calls when [onCopyRequested] is null.
 * When text is being deselected, Compose's SelectionContainer briefly calls
 * `showMenu(onCopyRequested = null)` before `hide()`, which would create a
 * ghost ActionMode showing only "Select All | Highlight". By checking for
 * null we prevent that flash entirely.
 */
private class HighlightTextToolbarImpl(
    private val view: View,
    private val onHighlight: State<(String) -> Unit>
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var currentRect: Rect = Rect.Zero
    private var currentOnCopy: (() -> Unit)? = null
    private var currentOnSelectAll: (() -> Unit)? = null

    override val status: TextToolbarStatus
        get() = if (actionMode != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        // Don't show the menu if there's no text to copy/highlight.
        // This prevents the ghost ActionMode that briefly flashes when
        // the selection is being cleared (SelectionContainer calls
        // showMenu with onCopyRequested=null right before hide()).
        if (onCopyRequested == null) {
            hide()
            return
        }

        currentRect = rect
        currentOnCopy = onCopyRequested
        currentOnSelectAll = onSelectAllRequested

        if (actionMode != null) {
            actionMode?.invalidate()
            return
        }

        actionMode = view.startActionMode(
            HighlightActionModeCallback(),
            ActionMode.TYPE_FLOATING
        )
    }

    override fun hide() {
        actionMode?.finish()
        actionMode = null
    }

    private inner class HighlightActionModeCallback : ActionMode.Callback2() {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (currentOnCopy != null) {
                menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy)
            }
            menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 1, "Highlight")
            if (currentOnSelectAll != null) {
                menu.add(Menu.NONE, android.R.id.selectAll, 2, android.R.string.selectAll)
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                android.R.id.copy -> {
                    currentOnCopy?.invoke()
                    mode.finish()
                    return true
                }
                MENU_ID_HIGHLIGHT -> {
                    val selectedText = extractSelectedTextViaReflection(currentOnCopy)
                    if (selectedText != null) {
                        onHighlight.value(selectedText)
                    } else {
                        Log.e(TAG, "Failed to extract selected text")
                    }
                    mode.finish()
                    return true
                }
                android.R.id.selectAll -> {
                    currentOnSelectAll?.invoke()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
        }

        override fun onGetContentRect(
            mode: ActionMode,
            view: View,
            outRect: android.graphics.Rect
        ) {
            val rect = currentRect
            outRect.set(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
        }
    }
}

// ─── Composable entry point ──────────────────────────────────────────────────

/**
 * Android implementation that returns a custom [TextToolbar] with a
 * "Highlight" action in the floating ActionMode.
 *
 * Unlike the previous Window.Callback interception approach, this directly
 * controls the ActionMode lifecycle, which prevents the ghost menu flash
 * that appeared when deselecting text.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)

    return remember(view) {
        HighlightTextToolbarImpl(view, latestOnHighlight)
    }
}

/**
 * Android: no-op wrapper. Android uses the custom TextToolbar (above) instead.
 */
@Composable
actual fun HighlightContextMenuProvider(
    onHighlightRequested: (selectedText: String) -> Unit,
    content: @Composable () -> Unit
) {
    content()
}
