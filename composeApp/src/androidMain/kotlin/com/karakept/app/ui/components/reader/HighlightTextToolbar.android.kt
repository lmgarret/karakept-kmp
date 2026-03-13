package com.karakept.app.ui.components.reader

import android.graphics.Rect as AndroidRect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Android implementation that adds a "Highlight" action to the text-selection
 * floating toolbar.
 *
 * This mirrors Compose's internal `AndroidTextToolbar` exactly — same
 * `ActionMode.Callback2`, same `view.startActionMode(cb, TYPE_FLOATING)`,
 * same `MenuItem.SHOW_AS_ACTION_IF_ROOM` — but adds a Highlight item
 * alongside Copy / Select All.
 *
 * When the user taps Highlight, `onCopyRequested` is invoked first (to let
 * Compose's `SelectionManager` copy the selected text to the clipboard),
 * then the `onHighlightRequested` callback fires with that text.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? {
    val view = LocalView.current
    val latestOnHighlight = rememberUpdatedState(onHighlightRequested)

    return remember(view) {
        HighlightTextToolbar(view) { text ->
            latestOnHighlight.value(text)
        }
    }
}

private const val MENU_ITEM_HIGHLIGHT_ID = 0x7f0f0001

private class HighlightTextToolbar(
    private val view: View,
    private val onHighlightRequested: (String) -> Unit,
) : TextToolbar {

    private var actionMode: ActionMode? = null

    // Single mutable callback — updated in-place on each showMenu(), matching
    // Compose's AndroidTextToolbar pattern.
    private val callback = HighlightActionModeCallback(
        onActionModeDestroy = { actionMode = null },
        onHighlightClicked = { onCopy ->
            onCopy?.invoke()
            readClipboardText()?.let { text ->
                if (text.isNotBlank()) onHighlightRequested(text)
            }
        },
    )

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        callback.rect = rect
        callback.onCopyRequested = onCopyRequested
        callback.onPasteRequested = onPasteRequested
        callback.onCutRequested = onCutRequested
        callback.onSelectAllRequested = onSelectAllRequested

        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = if (Build.VERSION.SDK_INT >= 23) {
                view.startActionMode(callback, ActionMode.TYPE_FLOATING)
            } else {
                view.startActionMode(callback)
            }
        } else {
            actionMode?.invalidate()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    private fun readClipboardText(): String? {
        return try {
            val clipboard = view.context.getSystemService(
                android.content.Context.CLIPBOARD_SERVICE
            ) as? android.content.ClipboardManager
            clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * ActionMode.Callback2 that creates the standard text actions (Copy, Paste,
 * Cut, Select All) plus a Highlight action. Mirrors Compose's internal
 * TextActionModeCallback exactly (same IDs, flags, ordering) with Highlight
 * added at position 0.
 *
 * Callbacks are mutable so the owning [HighlightTextToolbar] can update them
 * in-place when `showMenu` is called again (matching Compose's pattern of
 * reusing a single callback instance and calling `actionMode.invalidate()`).
 */
@RequiresApi(23)
private class HighlightActionModeCallback(
    private val onActionModeDestroy: () -> Unit,
    private val onHighlightClicked: (onCopy: (() -> Unit)?) -> Unit,
) : ActionMode.Callback2() {

    var rect: Rect = Rect.Zero
    var onCopyRequested: (() -> Unit)? = null
    var onPasteRequested: (() -> Unit)? = null
    var onCutRequested: (() -> Unit)? = null
    var onSelectAllRequested: (() -> Unit)? = null

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        // Highlight — first item
        menu.add(0, MENU_ITEM_HIGHLIGHT_ID, 0, "Highlight")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        // Standard items — same IDs and ordering as Compose's TextActionModeCallback
        onCopyRequested?.let {
            menu.add(0, android.R.id.copy, 1, android.R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        onPasteRequested?.let {
            menu.add(0, android.R.id.paste, 2, android.R.string.paste)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        onCutRequested?.let {
            menu.add(0, android.R.id.cut, 3, android.R.string.cut)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        onSelectAllRequested?.let {
            menu.add(0, android.R.id.selectAll, 4, android.R.string.selectAll)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        updateMenuItem(menu, android.R.id.copy, 1, android.R.string.copy, onCopyRequested)
        updateMenuItem(menu, android.R.id.paste, 2, android.R.string.paste, onPasteRequested)
        updateMenuItem(menu, android.R.id.cut, 3, android.R.string.cut, onCutRequested)
        updateMenuItem(menu, android.R.id.selectAll, 4, android.R.string.selectAll, onSelectAllRequested)
        // Highlight is always present — no add/remove needed
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_ITEM_HIGHLIGHT_ID -> onHighlightClicked(onCopyRequested)
            android.R.id.copy -> onCopyRequested?.invoke()
            android.R.id.paste -> onPasteRequested?.invoke()
            android.R.id.cut -> onCutRequested?.invoke()
            android.R.id.selectAll -> {
                onSelectAllRequested?.invoke()
                return true // Don't finish — user may want to copy/highlight after select-all
            }
            else -> return false
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        onActionModeDestroy()
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: AndroidRect) {
        outRect.set(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt(),
        )
    }

    private fun updateMenuItem(
        menu: Menu,
        id: Int,
        order: Int,
        titleRes: Int,
        callback: (() -> Unit)?,
    ) {
        when {
            callback != null && menu.findItem(id) == null -> {
                menu.add(0, id, order, titleRes)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            callback == null && menu.findItem(id) != null -> {
                menu.removeItem(id)
            }
        }
    }
}
