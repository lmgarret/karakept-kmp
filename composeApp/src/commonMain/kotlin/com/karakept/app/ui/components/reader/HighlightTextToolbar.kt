package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.TextToolbar

/**
 * Creates a platform-specific [TextToolbar] that adds a "Highlight" action
 * to the text selection context menu.
 *
 * On Android: adds a custom menu item to the floating action mode.
 * On Desktop: returns null (no custom toolbar; standard copy works via SelectionContainer).
 *
 * @param onHighlightRequested Called when the user taps the "Highlight" action.
 *   Receives the selected text string. The caller is responsible for finding
 *   the text offsets in the document and creating the highlight.
 */
@Composable
expect fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar?
