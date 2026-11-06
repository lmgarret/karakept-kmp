package com.karakept.app.ui.screens.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.karakept.app.ui.components.highlight.HighlightMaskAccumulator
import com.karakept.app.ui.components.highlight.HighlightPosition
import com.karakept.app.ui.components.reader.SearchMatch

@Stable
internal class ViewerUiState {
    var showModeDialog by mutableStateOf(false)
    var showAppearancePanel by mutableStateOf(false)
    var showDetailsPanel by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var fabExpanded by mutableStateOf(false)
    var showDeleteConfirmation by mutableStateOf(false)
    var showTagEditor by mutableStateOf(false)
    var showListPicker by mutableStateOf(false)
    var selectedHighlightId by mutableStateOf<String?>(null)
    var highlightPosition by mutableStateOf<HighlightPosition?>(null)
    val highlightMask = HighlightMaskAccumulator()
    var selectedHighlightText by mutableStateOf<String?>(null)

    // Search state
    var showSearch by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchMatches by mutableStateOf<List<SearchMatch>>(emptyList())
    var currentMatchIndex by mutableStateOf(0)
    var lastScrolledMatchIndex by mutableStateOf(-1)
    val contentFocusRequester = FocusRequester()
}

@Composable
internal fun rememberViewerUiState(): ViewerUiState = remember { ViewerUiState() }
