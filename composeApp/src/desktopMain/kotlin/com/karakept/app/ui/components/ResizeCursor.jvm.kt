package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

@Composable
actual fun Modifier.horizontalResizeCursor(): Modifier {
    // E_RESIZE_CURSOR maps to X11's XC_right_side — a one-directional arrow on Linux.
    // "EW Resize" requests XC_sb_h_double_arrow (the correct bidirectional cursor)
    // via the system cursor table. Falls back to E_RESIZE_CURSOR if unavailable.
    val cursor = try {
        Cursor.getSystemCustomCursor("EW Resize")
    } catch (_: Exception) {
        Cursor(Cursor.E_RESIZE_CURSOR)
    }
    return this.pointerHoverIcon(PointerIcon(cursor))
}
