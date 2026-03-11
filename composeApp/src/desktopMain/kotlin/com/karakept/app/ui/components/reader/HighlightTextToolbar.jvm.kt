package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.TextToolbar

/**
 * Desktop implementation — returns null to use the default text toolbar.
 * Highlight creation via text selection is not yet supported on Desktop.
 */
@Composable
actual fun rememberHighlightTextToolbar(
    onHighlightRequested: (selectedText: String) -> Unit
): TextToolbar? = null
