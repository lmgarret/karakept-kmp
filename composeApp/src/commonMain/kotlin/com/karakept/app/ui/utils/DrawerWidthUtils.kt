package com.karakept.app.ui.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central home for navigation-drawer sizing rules.
 *
 * The compact (modal) drawer and the expanded (resizable sidebar) layout are
 * deliberately separate concerns with different bounds, but keeping both rule
 * sets here means drawer dimensions live in one place.
 */

/**
 * MD3 caps a modal navigation drawer at 360dp, but on narrow screens it must
 * leave at least a 56dp strip of scrim so the user can see/tap the content
 * behind it. Without this the drawer fills the whole screen on small devices
 * (e.g. ~360dp-wide phones), hiding the scrim entirely.
 */
fun modalDrawerWidth(availableWidth: Dp): Dp {
    val maxWidth = 360.dp
    val scrimStrip = 56.dp
    val constrained = availableWidth - scrimStrip
    return if (constrained < maxWidth) constrained else maxWidth
}

/** Lower bound for the resizable sidebar in the expanded (large-screen) layout. */
val ExpandedDrawerMinWidth: Dp = 200.dp

/** Upper bound for the resizable sidebar in the expanded (large-screen) layout. */
val ExpandedDrawerMaxWidth: Dp = 400.dp

/** Default width of the resizable sidebar before the user drags it. */
val ExpandedDrawerDefaultWidth: Dp = 280.dp

/**
 * Clamps a proposed expanded-layout sidebar width (in dp) to the allowed range.
 * Works on the raw [Float] dp value used by the drag state.
 */
fun coerceExpandedDrawerWidth(widthDp: Float): Float =
    widthDp.coerceIn(ExpandedDrawerMinWidth.value, ExpandedDrawerMaxWidth.value)
