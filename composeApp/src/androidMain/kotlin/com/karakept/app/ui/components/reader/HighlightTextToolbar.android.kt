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
 * Wraps an [ActionMode.Callback] to inject a "Highlight" menu item and
 * delegate all other items to the [original] callback.
 */
private fun createHighlightCallback(
    original: ActionMode.Callback,
    onHighlight: (ActionMode, ActionMode.Callback) -> Unit
): ActionMode.Callback {
    return if (original is ActionMode.Callback2) {
        object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original.onCreateActionMode(mode, menu)
                menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 100, "Highlight")
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
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original.onCreateActionMode(mode, menu)
                menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 100, "Highlight")
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

                try {
                    val field = callback.javaClass.getDeclaredField("mWrapped")
                    field.isAccessible = true
                    val original = field.get(callback) as ActionMode.Callback

                    val wrapped = createHighlightCallback(original) { mode, composeCb ->
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
