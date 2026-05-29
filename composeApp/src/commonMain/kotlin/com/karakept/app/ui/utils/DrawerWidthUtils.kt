package com.karakept.app.ui.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
