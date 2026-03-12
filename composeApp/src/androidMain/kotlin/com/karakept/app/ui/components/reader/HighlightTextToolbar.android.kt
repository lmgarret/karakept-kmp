package com.karakept.app.ui.components.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlin.math.roundToInt

private const val MENU_ID_HIGHLIGHT = 1
private const val MENU_ID_COPY = 2
private const val MENU_ID_SELECT_ALL = 3

/**
 * Android implementation that uses a native floating [ActionMode] to present
 * Highlight / Copy / Select All actions when text is selected.
 *
 * Why ActionMode instead of a Compose Popup:
 *  - ActionMode is rendered by the Android framework, completely outside the Compose
 *    layout tree. Showing it does **not** mutate any Compose state and therefore
 *    does **not** trigger recomposition.
 *  - Recomposition was the root cause of both bugs: the popup sometimes failed to
 *    appear due to z-ordering / coordinate issues, AND it caused the LazyColumn to
 *    remeasure its items and produce a visible scroll jump on the first text selection.
 *  - ActionMode positioning is handled by Android via [ActionMode.Callback2.onGetContentRect],
 *    which receives coordinates in the view's local space — no manual coordinate
 *    conversion needed.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    // rememberUpdatedState ensures the lambda inside `remember` always calls the
    // latest version of onHighlightRequested without needing to recreate the toolbar.
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)

    val toolbar = remember(view) {
        object : TextToolbar {
            private var actionMode: ActionMode? = null
            private var _status = TextToolbarStatus.Hidden
            override val status: TextToolbarStatus get() = _status

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                // Dismiss any previous action mode before creating a new one.
                actionMode?.finish()

                val callback = object : ActionMode.Callback2() {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 0, "Highlight")
                        if (onCopyRequested != null) {
                            menu.add(Menu.NONE, MENU_ID_COPY, 1, "Copy")
                        }
                        if (onSelectAllRequested != null) {
                            menu.add(Menu.NONE, MENU_ID_SELECT_ALL, 2, "Select All")
                        }
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        when (item.itemId) {
                            MENU_ID_HIGHLIGHT -> {
                                // Copy the selection to the clipboard first (synchronous),
                                // then read back the text for highlight creation.
                                onCopyRequested?.invoke()
                                val selectedText = clipboardManager.getText()?.text
                                if (!selectedText.isNullOrBlank()) {
                                    latestOnHighlight.value(selectedText)
                                }
                                mode.finish()
                            }
                            MENU_ID_COPY -> {
                                onCopyRequested?.invoke()
                                mode.finish()
                            }
                            MENU_ID_SELECT_ALL -> {
                                onSelectAllRequested?.invoke()
                                // Don't finish — user may want to copy/highlight after select all.
                            }
                            else -> return false
                        }
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode) {
                        if (actionMode == mode) {
                            actionMode = null
                            _status = TextToolbarStatus.Hidden
                        }
                    }

                    /**
                     * Provides Android with the bounding rect of the selected content so the
                     * floating toolbar is positioned above (or below) the selection.
                     *
                     * [rect] from [showMenu] is in Compose-root (ComposeView-local) coordinates.
                     * [onGetContentRect] expects coordinates in the same view-local space as the
                     * [View] passed to [View.startActionMode]. Since [LocalView.current] IS the
                     * ComposeView, the coordinate spaces are identical — no offset conversion needed.
                     */
                    override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                        val left = rect.left.roundToInt().coerceAtLeast(0)
                        val top = rect.top.roundToInt().coerceAtLeast(0)
                        // Ensure non-empty rect so Android has a valid anchor.
                        val right = rect.right.roundToInt().coerceAtLeast(left + 1)
                        val bottom = rect.bottom.roundToInt().coerceAtLeast(top + 1)
                        outRect.set(left, top, right, bottom)
                    }
                }

                actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
                _status = if (actionMode != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
            }

            override fun hide() {
                actionMode?.finish()
                actionMode = null
                _status = TextToolbarStatus.Hidden
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { toolbar.hide() }
    }

    return toolbar
}
