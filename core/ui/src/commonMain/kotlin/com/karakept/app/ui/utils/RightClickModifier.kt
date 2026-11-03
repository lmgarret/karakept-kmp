package com.karakept.app.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * Triggers [onClick] when the user right-clicks (secondary button press) on desktop.
 * On non-desktop platforms this is a no-op.
 */
expect fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier

/**
 * Triggers [onClick] with the click position when the user right-clicks on desktop.
 * On non-desktop platforms this is a no-op.
 */
expect fun Modifier.onSecondaryClickWithPosition(onClick: (Offset) -> Unit): Modifier

/**
 * Desktop-only: intercepts Ctrl+Click and Shift+Click for multi-selection.
 * On non-desktop platforms this is a no-op.
 */
expect fun Modifier.onDesktopModifiedClick(
    key1: Any?,
    key2: Any?,
    onCtrlClick: (() -> Unit)?,
    onShiftClick: (() -> Unit)?
): Modifier
