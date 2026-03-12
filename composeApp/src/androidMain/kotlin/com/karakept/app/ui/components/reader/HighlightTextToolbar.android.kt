package com.karakept.app.ui.components.reader

import android.app.Activity
import android.content.ContextWrapper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar

private const val MENU_ID_HIGHLIGHT = 100

/**
 * Android implementation that intercepts the system floating [ActionMode]
 * at the [Window.Callback] level to add a "Highlight" action.
 *
 * Instead of replacing Compose's [TextToolbar] (which the framework can
 * bypass on AppCompat activities for [ActionMode.TYPE_FLOATING]), this
 * approach lets Compose create the ActionMode normally and then inserts
 * the Highlight item into its menu via [Window.Callback.onActionModeStarted].
 *
 * Returns `null` so the caller does **not** override [LocalTextToolbar];
 * the side-effect is installed via [DisposableEffect].
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)

    DisposableEffect(view) {
        val activity = view.context.findActivity()
            ?: return@DisposableEffect onDispose {}

        val window = activity.window
        val original: Window.Callback = window.callback
            ?: return@DisposableEffect onDispose {}

        // The most recent ActionMode.Callback passed through
        // onWindowStartingActionMode — needed to programmatically
        // invoke the Copy action when the user taps Highlight.
        var lastActionModeCallback: ActionMode.Callback? = null

        val wrapper = object : Window.Callback by original {

            override fun onWindowStartingActionMode(
                callback: ActionMode.Callback,
                type: Int
            ): ActionMode? {
                if (type == ActionMode.TYPE_FLOATING) {
                    lastActionModeCallback = callback
                }
                return original.onWindowStartingActionMode(callback, type)
            }

            override fun onActionModeStarted(mode: ActionMode) {
                original.onActionModeStarted(mode)

                // Only modify floating (text-selection) action modes.
                if (mode.type != ActionMode.TYPE_FLOATING) return

                // Guard: only add to menus that already contain "Copy"
                // (i.e. text-selection menus, not arbitrary action modes).
                var hasCopy = false
                for (i in 0 until mode.menu.size()) {
                    if (mode.menu.getItem(i).title?.toString()
                            .equals("Copy", ignoreCase = true)
                    ) {
                        hasCopy = true
                        break
                    }
                }
                if (!hasCopy) return

                // Don't add twice (e.g. on mode.invalidate() round-trips).
                if (mode.menu.findItem(MENU_ID_HIGHLIGHT) != null) return

                val highlightItem = mode.menu.add(
                    Menu.NONE,
                    MENU_ID_HIGHLIGHT,
                    0,          // order — first position
                    "Highlight"
                )
                highlightItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                val savedCallback = lastActionModeCallback
                highlightItem.setOnMenuItemClickListener {
                    // 1. Programmatically trigger Copy so the selected text
                    //    is placed on the clipboard.
                    if (savedCallback != null) {
                        for (i in 0 until mode.menu.size()) {
                            val item = mode.menu.getItem(i)
                            if (item.itemId != MENU_ID_HIGHLIGHT &&
                                item.title?.toString()
                                    .equals("Copy", ignoreCase = true)
                            ) {
                                savedCallback.onActionItemClicked(mode, item)
                                break
                            }
                        }
                    }

                    // 2. Read the text back from the clipboard.
                    val text = clipboardManager.getText()?.text
                    if (!text.isNullOrBlank()) {
                        latestOnHighlight.value(text)
                    }

                    // 3. Dismiss (Copy handler already calls mode.finish(),
                    //    but call it again defensively — it's a no-op if
                    //    the mode is already finished).
                    try {
                        mode.finish()
                    } catch (_: Exception) { /* already finished */ }

                    true
                }

                // Refresh the floating toolbar so the new item is visible.
                mode.invalidate()
            }

            override fun onActionModeFinished(mode: ActionMode) {
                original.onActionModeFinished(mode)
                lastActionModeCallback = null
            }
        }

        window.callback = wrapper

        onDispose {
            if (window.callback === wrapper) {
                window.callback = original
            }
        }
    }

    // Don't override LocalTextToolbar — we intercept at Window level.
    return null
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
