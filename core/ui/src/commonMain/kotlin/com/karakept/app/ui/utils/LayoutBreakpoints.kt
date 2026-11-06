package com.karakept.app.ui.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width at or above which a screen switches from the single-pane phone layout to the multi-pane
 * one. This is the Material 3 "expanded" breakpoint.
 *
 * A 7-inch e-ink reader reports well under this, so it stays on the phone layout — which is the
 * right call: three columns on a 7-inch panel would leave no usable measure in any of them.
 *
 * https://m3.material.io/foundations/layout/applying-layout/window-size-classes
 */
val ExpandedWidthBreakpoint: Dp = 840.dp

fun isExpandedWidth(maxWidth: Dp): Boolean = maxWidth >= ExpandedWidthBreakpoint
