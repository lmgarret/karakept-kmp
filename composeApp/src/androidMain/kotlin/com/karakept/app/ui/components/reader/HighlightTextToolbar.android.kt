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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.text.AnnotatedString

private const val TAG = "HighlightToolbar"
private const val MENU_ID_HIGHLIGHT = 1001
private const val MENU_LABEL_HIGHLIGHT = "Highlight"

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
 * Adds the "Highlight" item to [menu] unless it is already there, and reports
 * whether it had to be added.
 *
 * Compose rebuilds the whole menu (`Menu.clear()`) every time its context menu
 * data changes, so this runs on every create *and* prepare pass. Order 0 keeps
 * the item ahead of Compose's own items, which are numbered from 1, and
 * [MenuItem.SHOW_AS_ACTION_ALWAYS] matches what Compose sets on each of its
 * items — without it the entry is only reachable through the floating toolbar's
 * overflow.
 */
private fun addHighlightItem(menu: Menu): Boolean {
    if (menu.findItem(MENU_ID_HIGHLIGHT) != null) return false
    menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 0, MENU_LABEL_HIGHLIGHT)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    return true
}

/**
 * Wraps an [ActionMode.Callback] to inject a "Highlight" menu item and
 * delegate all other items to the [original] callback.
 */
internal fun createHighlightCallback(
    original: ActionMode.Callback,
    onHighlight: (ActionMode, ActionMode.Callback) -> Unit
): ActionMode.Callback {
    fun onCreate(mode: ActionMode, menu: Menu): Boolean {
        val handled = original.onCreateActionMode(mode, menu)
        addHighlightItem(menu)
        return handled || menu.size() > 0
    }

    fun onPrepare(mode: ActionMode, menu: Menu): Boolean {
        val updated = original.onPrepareActionMode(mode, menu)
        return addHighlightItem(menu) || updated
    }

    fun onItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != MENU_ID_HIGHLIGHT) {
            return original.onActionItemClicked(mode, item)
        }
        onHighlight(mode, original)
        return true
    }

    return if (original is ActionMode.Callback2) {
        object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = onCreate(mode, menu)

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = onPrepare(mode, menu)

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
                onItemClicked(mode, item)

            override fun onDestroyActionMode(mode: ActionMode) =
                original.onDestroyActionMode(mode)

            override fun onGetContentRect(
                mode: ActionMode,
                view: android.view.View,
                outRect: android.graphics.Rect
            ) = original.onGetContentRect(mode, view, outRect)
        }
    } else {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = onCreate(mode, menu)

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = onPrepare(mode, menu)

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
                onItemClicked(mode, item)

            override fun onDestroyActionMode(mode: ActionMode) =
                original.onDestroyActionMode(mode)
        }
    }
}

/**
 * Swaps the `ActionMode.Callback` that [wrapper] delegates to for one that also
 * offers "Highlight".
 *
 * [wrapper] is `DecorView$ActionModeCallback2Wrapper`, whose `mWrapped` field
 * holds Compose's callback. Both the class and the field are non-SDK, so this
 * can fail on a platform build that blocks them — the reason is logged so the
 * failure is diagnosable from logcat rather than silently dropping the item.
 *
 * Returns true when the swap succeeded.
 */
internal fun injectHighlightCallback(
    wrapper: ActionMode.Callback,
    onHighlight: (ActionMode, ActionMode.Callback) -> Unit
): Boolean {
    return try {
        val field = wrapper.javaClass.getDeclaredField("mWrapped")
        field.isAccessible = true
        val original = field.get(wrapper) as ActionMode.Callback
        field.set(wrapper, createHighlightCallback(original, onHighlight))
        true
    } catch (e: Exception) {
        Log.e(
            TAG,
            "ActionMode callback injection failed on ${wrapper.javaClass.name}: " +
                "${e.javaClass.name}: ${e.message}"
        )
        false
    }
}

// ─── Composable entry point ──────────────────────────────────────────────────

/**
 * Android implementation that intercepts the Activity's `Window.Callback` to
 * inject a "Highlight" menu item into the native text-selection ActionMode.
 *
 * Strategy:
 * 1. Replace `mWrapped` inside `DecorView$ActionModeCallback2Wrapper` via
 *    reflection so the system creates a **single** ActionMode with our
 *    augmented callback (avoids the double-ActionMode / blinking issue).
 * 2. When "Highlight" is tapped, walk the `FloatingTextActionModeCallback`'s
 *    closure graph to find `SelectionManager` and call its internal
 *    `getSelectedText()` — no clipboard involved.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val context = LocalContext.current
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)

    DisposableEffect(context) {
        val activity = context.findActivity()
        if (activity == null) {
            Log.e(TAG, "No activity found, cannot intercept Window.Callback")
            return@DisposableEffect onDispose {}
        }

        val originalCallback = activity.window.callback ?: run {
            Log.e(TAG, "No Window.Callback found")
            return@DisposableEffect onDispose {}
        }

        val wrapperCallback = object : Window.Callback by originalCallback {
            override fun onWindowStartingActionMode(
                callback: ActionMode.Callback?,
                type: Int
            ): ActionMode? {
                if (callback == null || type != ActionMode.TYPE_FLOATING) {
                    return originalCallback.onWindowStartingActionMode(callback, type)
                }

                injectHighlightCallback(callback) { mode, composeCb ->
                    val selectedText = extractSelectedText(composeCb)
                    if (selectedText != null) {
                        latestOnHighlight.value(selectedText)
                    } else {
                        Log.e(TAG, "Failed to extract selected text")
                    }
                    mode.finish()
                }

                return originalCallback.onWindowStartingActionMode(callback, type)
            }

            override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
                return originalCallback.onWindowStartingActionMode(callback)
            }
        }

        activity.window.callback = wrapperCallback

        onDispose {
            if (activity.window.callback === wrapperCallback) {
                activity.window.callback = originalCallback
            }
        }
    }

    return null
}

/**
 * Android: no-op wrapper. Android uses ActionMode interception (above) instead.
 */
@Composable
actual fun HighlightContextMenuProvider(
    onHighlightRequested: (selectedText: String) -> Unit,
    content: @Composable () -> Unit
) {
    content()
}
