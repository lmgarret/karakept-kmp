package com.karakept.app.ui.components.reader

import androidx.compose.runtime.staticCompositionLocalOf

data class SearchMatch(val startOffset: Int, val endOffset: Int)

/**
 * Pair of (all matches, active match index) provided to descendant renderers.
 * Null means search is inactive.
 */
val LocalSearchState = staticCompositionLocalOf<Pair<List<SearchMatch>, Int>?> { null }

/**
 * Callback provided to block renderers. When a renderer detects that the active
 * search match falls within its offset range, it invokes this with the block's
 * root-space Y coordinate so the viewer can scroll to it.
 */
val LocalSearchMatchScrollCallback = staticCompositionLocalOf<((Float) -> Unit)?> { null }
