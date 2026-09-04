package com.karakept.app.utils

import androidx.compose.ui.platform.Clipboard

/**
 * Puts [text] on the system clipboard as plain text.
 *
 * `Clipboard` (which replaced the deprecated `ClipboardManager`) only exposes the platform's own
 * `ClipEntry`, whose constructor differs per target — `ClipData` on Android, `Transferable` on
 * desktop — so building one cannot live in common code.
 */
expect suspend fun Clipboard.setPlainText(text: String)
