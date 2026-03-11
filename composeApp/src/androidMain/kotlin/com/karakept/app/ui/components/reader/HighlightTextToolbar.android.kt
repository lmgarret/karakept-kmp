package com.karakept.app.ui.components.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Android implementation that adds a "Highlight" action to the text selection toolbar.
 *
 * Uses the platform floating ActionMode to add a custom menu item alongside
 * the standard Copy/Select All actions.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current
    return remember(view) {
        AndroidHighlightTextToolbar(view, onHighlightRequested)
    }
}

private class AndroidHighlightTextToolbar(
    private val view: View,
    private val onHighlightRequested: (String) -> Unit
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var _status = TextToolbarStatus.Hidden

    override val status: TextToolbarStatus
        get() = _status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (onCopyRequested != null) {
                    menu.add(0, MENU_COPY, 0, "Copy")
                }
                if (onSelectAllRequested != null) {
                    menu.add(0, MENU_SELECT_ALL, 1, "Select All")
                }
                // Add Highlight action
                menu.add(0, MENU_HIGHLIGHT, 2, "Highlight")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    MENU_COPY -> {
                        onCopyRequested?.invoke()
                        mode.finish()
                        true
                    }
                    MENU_SELECT_ALL -> {
                        onSelectAllRequested?.invoke()
                        true
                    }
                    MENU_HIGHLIGHT -> {
                        // Get the selected text from the clipboard manager
                        // Unfortunately, SelectionContainer doesn't directly expose selection text.
                        // We copy first, then read from clipboard.
                        onCopyRequested?.invoke()
                        // Read from clipboard
                        val clipboard = view.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val selectedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!selectedText.isNullOrBlank()) {
                            onHighlightRequested(selectedText)
                        }
                        mode.finish()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                _status = TextToolbarStatus.Hidden
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                outRect.set(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt()
                )
            }
        }

        actionMode?.finish()
        actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        _status = TextToolbarStatus.Shown
    }

    override fun hide() {
        actionMode?.finish()
        actionMode = null
        _status = TextToolbarStatus.Hidden
    }

    companion object {
        private const val MENU_COPY = 1
        private const val MENU_SELECT_ALL = 2
        private const val MENU_HIGHLIGHT = 3
    }
}
