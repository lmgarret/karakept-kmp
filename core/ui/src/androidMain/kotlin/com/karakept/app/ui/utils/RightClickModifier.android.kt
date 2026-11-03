package com.karakept.app.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

actual fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier = this

actual fun Modifier.onSecondaryClickWithPosition(onClick: (Offset) -> Unit): Modifier = this

actual fun Modifier.onDesktopModifiedClick(
    key1: Any?,
    key2: Any?,
    onCtrlClick: (() -> Unit)?,
    onShiftClick: (() -> Unit)?
): Modifier = this
