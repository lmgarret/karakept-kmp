package com.karakept.app.ui.theme

import androidx.compose.runtime.Composable

/**
 * Synchronises the native window decoration/background with the current
 * MaterialTheme colour scheme.  On desktop this sets the AWT window
 * background colour; on Android it is a no-op.
 */
@Composable
expect fun SyncWindowTheme()
