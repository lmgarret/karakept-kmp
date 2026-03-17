package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Applies a horizontal-resize cursor icon on hover (desktop) or a no-op (Android).
 */
@Composable
expect fun Modifier.horizontalResizeCursor(): Modifier
