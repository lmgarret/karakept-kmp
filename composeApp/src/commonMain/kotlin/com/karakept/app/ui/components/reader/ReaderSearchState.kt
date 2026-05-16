package com.karakept.app.ui.components.reader

import androidx.compose.runtime.staticCompositionLocalOf

data class SearchMatch(val startOffset: Int, val endOffset: Int)

/**
 * Pair of (all matches, active match index) provided to descendant renderers.
 * Null means search is inactive.
 */
val LocalSearchState = staticCompositionLocalOf<Pair<List<SearchMatch>, Int>?> { null }
