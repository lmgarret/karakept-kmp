package com.karakept.app.ui.components.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
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
 * Extracts the currently selected text from Compose's `SelectionManager` by
 * navigating the [actionModeCallback]'s field tree via reflection.
 *
 * The callback is the `FloatingTextActionModeCallback` from Compose's
 * `contextmenu.internal` package. Its closure graph ultimately references
 * `SelectionManager`, on which we call `getSelectedText$<module>()`.
 */
private fun extractSelectedText(actionModeCallback: ActionMode.Callback): String? {
    return try {
        val manager = findSelectionManager(actionModeCallback) ?: run {
            Log.w(TAG, "SelectionManager not found in callback closure tree")
            return null
        }
        val method = manager.javaClass.methods
            .find { it.name.startsWith("getSelectedText") }
            ?: run {
                Log.w(TAG, "getSelectedText method not found on ${manager.javaClass.name}")
                return null
            }
        (method.invoke(manager) as? AnnotatedString)?.text
    } catch (e: Exception) {
        Log.w(TAG, "Failed to extract selected text: $e")
        null
    }
}

// ─── ActionMode injection ────────────────────────────────────────────────────

internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Marker interface to detect callbacks already wrapped by [createHighlightCallback].
 * Prevents double-wrapping when [onWindowStartingActionMode] is called multiple times.
 */
private interface HighlightWrappedCallback

/**
 * Wraps an [ActionMode.Callback] to inject a "Highlight" menu item and
 * delegate all other items to the [original] callback.
 */
private fun createHighlightCallback(
    original: ActionMode.Callback,
    onHighlight: (ActionMode, ActionMode.Callback) -> Unit
): ActionMode.Callback {
    return if (original is ActionMode.Callback2) {
        object : ActionMode.Callback2(), HighlightWrappedCallback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original.onCreateActionMode(mode, menu)
                if (menu.findItem(MENU_ID_HIGHLIGHT) == null) {
                    menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 100, "Highlight")
                }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original.onPrepareActionMode(mode, menu)
                if (menu.findItem(MENU_ID_HIGHLIGHT) == null) {
                    menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 100, "Highlight")
                }
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == MENU_ID_HIGHLIGHT) {
                    onHighlight(mode, original)
                    return true
                }
                return original.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) =
                original.onDestroyActionMode(mode)

            override fun onGetContentRect(
                mode: ActionMode,
                view: android.view.View,
                outRect: android.graphics.Rect
            ) = original.onGetContentRect(mode, view, outRect)
        }
    } else {
        object : ActionMode.Callback, HighlightWrappedCallback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original.onCreateActionMode(mode, menu)
                if (menu.findItem(MENU_ID_HIGHLIGHT) == null) {
                    menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 100, "Highlight")
                }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original.onPrepareActionMode(mode, menu)
                if (menu.findItem(MENU_ID_HIGHLIGHT) == null) {
                    menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 100, "Highlight")
                }
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == MENU_ID_HIGHLIGHT) {
                    onHighlight(mode, original)
                    return true
                }
                return original.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) =
                original.onDestroyActionMode(mode)
        }
    }
}

// ─── Window.Callback wrapper ─────────────────────────────────────────────────

/**
 * Named wrapper class for the Activity's Window.Callback, used to detect
 * whether the callback has already been wrapped (prevents stacking).
 */
private class HighlightWindowCallback(
    private val original: Window.Callback,
    private val latestOnHighlight: androidx.compose.runtime.State<(String) -> Unit>
) : Window.Callback by original {

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int
    ): ActionMode? {
        if (callback == null || type != ActionMode.TYPE_FLOATING) {
            return original.onWindowStartingActionMode(callback, type)
        }

        try {
            val field = callback.javaClass.getDeclaredField("mWrapped")
            field.isAccessible = true
            val inner = field.get(callback) as ActionMode.Callback

            // Guard: skip if already wrapped by us (prevents double Highlight items)
            if (inner is HighlightWrappedCallback) {
                return original.onWindowStartingActionMode(callback, type)
            }

            val wrapped = createHighlightCallback(inner) { mode, composeCb ->
                val selectedText = extractSelectedText(composeCb)
                if (selectedText != null) {
                    latestOnHighlight.value(selectedText)
                } else {
                    Log.e(TAG, "Failed to extract selected text")
                }
                mode.finish()
            }

            field.set(callback, wrapped)
        } catch (e: Exception) {
            Log.e(TAG, "ActionMode callback injection failed: $e")
        }

        return original.onWindowStartingActionMode(callback, type)
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
        return original.onWindowStartingActionMode(callback)
    }
}

// ─── Ghost-menu filtering TextToolbar wrapper ────────────────────────────────

/**
 * Wraps the default [TextToolbar] to suppress [showMenu] calls when
 * [onCopyRequested] is null. This prevents the ghost ActionMode flash
 * that Compose's SelectionContainer triggers during deselection:
 * it briefly calls `showMenu(onCopyRequested=null)` before `hide()`,
 * creating a transient toolbar showing only "Select All | Highlight".
 */
private class FilteringTextToolbar(
    private val delegate: TextToolbar
) : TextToolbar {

    override val status: TextToolbarStatus get() = delegate.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        // No copy callback → no text selected → nothing to highlight.
        // Suppress to prevent the ghost menu flash during deselection.
        if (onCopyRequested == null) {
            delegate.hide()
            return
        }
        delegate.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() {
        delegate.hide()
    }
}

// ─── Composable entry point ──────────────────────────────────────────────────

/**
 * Android implementation that:
 * 1. Intercepts the Activity's `Window.Callback` to inject a "Highlight"
 *    menu item into the native text-selection ActionMode.
 * 2. Wraps the default [TextToolbar] to suppress ghost menu flashes that
 *    occur when Compose's SelectionContainer briefly calls `showMenu`
 *    with `onCopyRequested=null` during text deselection.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val context = LocalContext.current
    val defaultToolbar = LocalTextToolbar.current
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)

    // Install Window.Callback wrapper to inject "Highlight" into ActionMode
    DisposableEffect(context) {
        val activity = context.findActivity()
        if (activity == null) {
            Log.e(TAG, "No activity found, cannot intercept Window.Callback")
            return@DisposableEffect onDispose {}
        }

        val currentCallback = activity.window.callback ?: run {
            Log.e(TAG, "No Window.Callback found")
            return@DisposableEffect onDispose {}
        }

        // Guard: don't stack wrappers if already installed
        if (currentCallback is HighlightWindowCallback) {
            return@DisposableEffect onDispose {}
        }

        val wrapperCallback = HighlightWindowCallback(currentCallback, latestOnHighlight)

        activity.window.callback = wrapperCallback

        onDispose {
            if (activity.window.callback === wrapperCallback) {
                activity.window.callback = currentCallback
            }
        }
    }

    // Wrap the default TextToolbar to filter out ghost menu flashes
    return remember(defaultToolbar) {
        FilteringTextToolbar(defaultToolbar)
    }
}

/**
 * Android: no-op wrapper. Android uses TextToolbar/ActionMode instead.
 */
@Composable
actual fun HighlightContextMenuProvider(
    onHighlightRequested: (selectedText: String) -> Unit,
    content: @Composable () -> Unit
) {
    content()
}
