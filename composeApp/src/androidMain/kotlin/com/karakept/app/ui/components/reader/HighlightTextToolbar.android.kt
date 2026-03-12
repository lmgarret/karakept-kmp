package com.karakept.app.ui.components.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Android implementation that provides a native floating ActionMode toolbar
 * with a "Highlight" menu item, mirroring the WebView's `startActionMode`
 * approach in `HtmlRenderer.android.kt`.
 *
 * Compose's [SelectionContainer] calls [TextToolbar.showMenu] when the user
 * selects text. This implementation starts a native [ActionMode.TYPE_FLOATING]
 * toolbar which is the standard Android text-selection UI.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current

    return remember(view, clipboardManager, onHighlightRequested) {
        object : TextToolbar {
            private var actionMode: ActionMode? = null
            override val status: TextToolbarStatus
                get() = if (actionMode != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                actionMode?.finish()
                actionMode = view.startActionMode(
                    object : ActionMode.Callback2() {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            // Highlight first (order 0), then Copy (order 1), then Select All (order 2)
                            menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 0, "Highlight")
                                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                            if (onCopyRequested != null) {
                                menu.add(Menu.NONE, MENU_ID_COPY, 1, "Copy")
                                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                            }
                            if (onSelectAllRequested != null) {
                                menu.add(Menu.NONE, MENU_ID_SELECT_ALL, 2, "Select All")
                                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                            }
                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                            when (item.itemId) {
                                MENU_ID_HIGHLIGHT -> {
                                    // Copy to clipboard first, then read it back
                                    onCopyRequested?.invoke()
                                    val text = clipboardManager.getText()?.text
                                    mode.finish()
                                    if (!text.isNullOrBlank()) {
                                        onHighlightRequested(text)
                                    }
                                    return true
                                }
                                MENU_ID_COPY -> {
                                    onCopyRequested?.invoke()
                                    mode.finish()
                                    return true
                                }
                                MENU_ID_SELECT_ALL -> {
                                    onSelectAllRequested?.invoke()
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
                            view: android.view.View,
                            outRect: android.graphics.Rect
                        ) {
                            // Position the floating toolbar near the selection
                            outRect.set(
                                rect.left.toInt(),
                                rect.top.toInt(),
                                rect.right.toInt(),
                                rect.bottom.toInt()
                            )
                        }
                    },
                    ActionMode.TYPE_FLOATING
                )
            }

            override fun hide() {
                actionMode?.finish()
                actionMode = null
            }
        }
    }
}

private const val MENU_ID_HIGHLIGHT = 0x7f0f0001
private const val MENU_ID_COPY = 0x7f0f0002
private const val MENU_ID_SELECT_ALL = 0x7f0f0003
