package com.karakept.app.ui.components.reader

import android.app.Activity
import android.content.ContextWrapper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

private const val MENU_ID_HIGHLIGHT = 0x7f0f0001

/**
 * Android implementation that adds a "Highlight" action to the text-selection
 * floating toolbar, mirroring the approach used by the WebView-based reader.
 *
 * Two mechanisms work together:
 *
 * 1. **TextToolbar wrapper** — wraps the platform default [TextToolbar]
 *    (`AndroidTextToolbar`) so that it can capture the `onCopyRequested`
 *    callback supplied by Compose's `SelectionManager`. Everything else
 *    (menu creation, positioning, ActionMode lifecycle) is delegated
 *    unchanged.
 *
 * 2. **[Window.Callback] interceptor** — listens for
 *    [Window.Callback.onActionModeStarted] and injects a "Highlight"
 *    [MenuItem] into the floating toolbar menu. When tapped, the saved
 *    `onCopyRequested` is invoked to place the selected text on the
 *    clipboard, then the text is read back for highlight creation.
 *
 * This mirrors `WebView.startActionMode` wrapping used on main for the
 * WebView-based reader, adapted for Compose's `SelectionContainer`.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)

    // Read the platform-default TextToolbar (AndroidTextToolbar) before we
    // override it.  We delegate all actual ActionMode work to it.
    val defaultToolbar = LocalTextToolbar.current

    // Mutable holder for the copy callback from the most recent showMenu().
    val copyCallbackRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    // ---- Window.Callback interceptor ----
    DisposableEffect(view) {
        val activity = view.context.findActivity()
            ?: return@DisposableEffect onDispose {}

        val window = activity.window
        val original: Window.Callback = window.callback
            ?: return@DisposableEffect onDispose {}

        val wrapper = object : Window.Callback by original {

            override fun onActionModeStarted(mode: ActionMode) {
                original.onActionModeStarted(mode)

                if (mode.type != ActionMode.TYPE_FLOATING) return
                if (mode.menu.findItem(MENU_ID_HIGHLIGHT) != null) return

                val highlightItem = mode.menu.add(
                    Menu.NONE,
                    MENU_ID_HIGHLIGHT,
                    0,          // order — before Copy
                    "Highlight"
                )
                highlightItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                highlightItem.setOnMenuItemClickListener {
                    // Copy selected text to clipboard via Compose's callback.
                    copyCallbackRef.value?.invoke()

                    val text = clipboardManager.getText()?.text
                    if (!text.isNullOrBlank()) {
                        latestOnHighlight.value(text)
                    }

                    try { mode.finish() } catch (_: Exception) { }
                    true
                }

                // Refresh the floating toolbar so the new item is visible.
                mode.invalidate()
            }

            override fun onActionModeFinished(mode: ActionMode) {
                original.onActionModeFinished(mode)
            }
        }

        window.callback = wrapper

        onDispose {
            if (window.callback === wrapper) {
                window.callback = original
            }
        }
    }

    // ---- TextToolbar wrapper ----
    // Wraps the default toolbar to capture onCopyRequested while delegating
    // all menu / ActionMode logic unchanged.
    return remember(defaultToolbar) {
        object : TextToolbar {
            override val status: TextToolbarStatus get() = defaultToolbar.status

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                copyCallbackRef.value = onCopyRequested
                defaultToolbar.showMenu(
                    rect,
                    onCopyRequested,
                    onPasteRequested,
                    onCutRequested,
                    onSelectAllRequested
                )
            }

            override fun hide() {
                defaultToolbar.hide()
            }
        }
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
