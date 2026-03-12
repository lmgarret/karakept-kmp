package com.karakept.app.ui.components.reader

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar

/**
 * Android implementation that intercepts the system ActionMode to add a "Highlight"
 * action to the text selection toolbar.
 *
 * Compose's [SelectionContainer] on Android manages its own ActionMode internally
 * and does NOT use [LocalTextToolbar]. Therefore, we intercept at the Activity's
 * [Window.Callback] level, wrapping any ActionMode.Callback to inject our custom
 * menu item.
 *
 * Returns `null` so that [NativeHtmlRenderer] does not provide a custom TextToolbar
 * (which would be ignored by SelectionContainer on Android anyway).
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current

    DisposableEffect(view) {
        val activity = view.context.findActivity()
        if (activity == null) {
            return@DisposableEffect onDispose {}
        }

        val originalCallback = activity.window.callback

        val wrappedCallback = object : Window.Callback by originalCallback {
            override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
                val wrapped = callback?.let {
                    HighlightActionModeWrapper(it, view, onHighlightRequested)
                }
                return originalCallback.onWindowStartingActionMode(wrapped)
            }

            override fun onWindowStartingActionMode(
                callback: ActionMode.Callback?,
                type: Int
            ): ActionMode? {
                val wrapped = callback?.let {
                    HighlightActionModeWrapper(it, view, onHighlightRequested)
                }
                return originalCallback.onWindowStartingActionMode(wrapped, type)
            }
        }

        activity.window.callback = wrappedCallback

        onDispose {
            // Only restore if our wrapper is still the active callback
            if (activity.window.callback === wrappedCallback) {
                activity.window.callback = originalCallback
            }
        }
    }

    // Return null — don't provide a custom TextToolbar.
    // The ActionMode interception above handles adding "Highlight" to the system toolbar.
    return null
}

/**
 * Wraps an [ActionMode.Callback] to inject a "Highlight" menu item.
 *
 * Extends [ActionMode.Callback2] so that the floating ActionMode positioning
 * (via [onGetContentRect]) is preserved from the original callback.
 */
private class HighlightActionModeWrapper(
    private val delegate: ActionMode.Callback,
    private val view: View,
    private val onHighlightRequested: (String) -> Unit
) : ActionMode.Callback2() {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val result = delegate.onCreateActionMode(mode, menu)
        if (result) {
            // Add Highlight before other items (order = 0 puts it first)
            menu.add(0, MENU_HIGHLIGHT_ID, 0, "Highlight")
        }
        return result
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return delegate.onPrepareActionMode(mode, menu)
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == MENU_HIGHLIGHT_ID) {
            // Trigger Copy to put selected text on the clipboard.
            // Compose's SelectionContainer uses itemId = 0 for Copy.
            val copyItem = mode.menu.findItem(COMPOSE_COPY_ITEM_ID)
            if (copyItem != null) {
                delegate.onActionItemClicked(mode, copyItem)
            }

            // Read the selected text from clipboard
            val clipboard = view.context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrBlank()) {
                onHighlightRequested(text)
            }
            mode.finish()
            return true
        }
        return delegate.onActionItemClicked(mode, item)
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        delegate.onDestroyActionMode(mode)
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
        if (delegate is ActionMode.Callback2) {
            delegate.onGetContentRect(mode, view, outRect)
        } else {
            super.onGetContentRect(mode, view, outRect)
        }
    }

    companion object {
        /** Unique ID for our Highlight menu item. */
        private const val MENU_HIGHLIGHT_ID = 0x7f0f_0001

        /**
         * Compose's SelectionContainer uses itemId = 0 for the Copy action.
         * See AndroidTextToolbar.android.kt in the Compose source.
         */
        private const val COMPOSE_COPY_ITEM_ID = 0
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
