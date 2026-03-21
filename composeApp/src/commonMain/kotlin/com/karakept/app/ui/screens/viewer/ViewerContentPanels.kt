package com.karakept.app.ui.screens.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.ui.screens.BookmarkLoadingState
import com.karakept.app.ui.screens.BookmarkViewerScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * All the overlay panels and dialogs shown on top of the viewer content:
 * back handler, viewer mode dialog, appearance panel, delete confirmation,
 * list picker, tag editor, highlight details, and bookmark details.
 */
@Composable
fun ViewerContentPanels(
    loadingState: BookmarkLoadingState,
    viewerMode: com.karakept.app.data.model.ViewerMode,
    htmlTextColor: Color?,
    htmlBackgroundColor: Color?,
    htmlFontSize: Int,
    htmlFontFamily: ReaderFontFamily,
    lists: List<com.karakept.api.model.KarakeepList>,
    showModeDialog: Boolean,
    onShowModeDialogChanged: (Boolean) -> Unit,
    showAppearancePanel: Boolean,
    onShowAppearancePanelChanged: (Boolean) -> Unit,
    showDetailsPanel: Boolean,
    onShowDetailsPanelChanged: (Boolean) -> Unit,
    showDeleteConfirmation: Boolean,
    onShowDeleteConfirmationChanged: (Boolean) -> Unit,
    showListPicker: Boolean,
    onShowListPickerChanged: (Boolean) -> Unit,
    showTagEditor: Boolean,
    onShowTagEditorChanged: (Boolean) -> Unit,
    selectedHighlightId: String?,
    onSelectedHighlightIdChanged: (String?) -> Unit,
    selectedHighlightText: String?,
    onSelectedHighlightTextChanged: (String?) -> Unit,
    selectedHighlight: Highlight?,
    screenModel: BookmarkViewerScreenModel,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    // Back Handler for panels
    com.karakept.app.ui.components.BackHandler(
        enabled = showAppearancePanel || showModeDialog || selectedHighlightId != null || showDetailsPanel
    ) {
        if (showDetailsPanel) onShowDetailsPanelChanged(false)
        else if (showAppearancePanel) onShowAppearancePanelChanged(false)
        else if (showModeDialog) onShowModeDialogChanged(false)
        else if (selectedHighlightId != null) onSelectedHighlightIdChanged(null)
    }

    // Viewer mode dialog
    ViewerModeDialog(
        visible = showModeDialog,
        viewerMode = viewerMode,
        onModeSelected = { mode ->
            screenModel.setViewerMode(mode)
            onShowModeDialogChanged(false)
        },
        onDismiss = { onShowModeDialogChanged(false) }
    )

    // Reader appearance panel
    ReaderAppearancePanel(
        visible = showAppearancePanel,
        textColor = htmlTextColor,
        backgroundColor = htmlBackgroundColor,
        fontSize = htmlFontSize,
        fontFamily = htmlFontFamily,
        onTextColorChange = { screenModel.setHtmlTextColor(it) },
        onBackgroundColorChange = { screenModel.setHtmlBackgroundColor(it) },
        onFontSizeChange = { screenModel.setHtmlFontSize(it) },
        onFontFamilyChange = { screenModel.setHtmlFontFamily(it) },
        onReset = { scope.launch { screenModel.resetReaderAppearance() } },
        onDismiss = { onShowAppearancePanelChanged(false) }
    )

    // Delete Confirmation Dialog
    if (showDeleteConfirmation && loadingState is BookmarkLoadingState.FullyLoaded) {
        val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
        DeleteConfirmationDialog(
            visible = true,
            onConfirm = {
                screenModel.deleteBookmark(fullyLoadedState.bookmark) { onBack() }
                onShowDeleteConfirmationChanged(false)
            },
            onDismiss = { onShowDeleteConfirmationChanged(false) }
        )
    }

    // List Picker Dialog
    if (showListPicker && loadingState is BookmarkLoadingState.FullyLoaded && lists.isNotEmpty()) {
        val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
        com.karakept.app.ui.components.ListPickerDialog(
            lists = lists,
            currentListIds = fullyLoadedState.bookmark.listIds.split(",").filter { it.isNotBlank() },
            onListSelected = { listId ->
                screenModel.moveBookmarkToList(fullyLoadedState.bookmark, listId)
                onShowListPickerChanged(false)
            },
            onDismiss = { onShowListPickerChanged(false) }
        )
    }

    // Tag Editor Dialog
    if (showTagEditor && loadingState is BookmarkLoadingState.FullyLoaded) {
        val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
        com.karakept.app.ui.components.TagEditorDialog(
            currentTags = fullyLoadedState.bookmark.tags.split(",").filter { it.isNotBlank() },
            onTagsUpdated = { newTags ->
                screenModel.updateBookmarkTags(fullyLoadedState.bookmark, newTags)
                onShowTagEditorChanged(false)
            },
            onDismiss = { onShowTagEditorChanged(false) }
        )
    }

    // Highlight Details Panel
    HighlightDetailsPanel(
        visible = selectedHighlightId != null && selectedHighlight != null,
        highlight = selectedHighlight,
        fontFamily = htmlFontFamily,
        fontSize = htmlFontSize,
        onUpdateHighlight = { id, note, color ->
            if (loadingState is BookmarkLoadingState.FullyLoaded) {
                val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                screenModel.updateHighlight(fullyLoadedState.bookmark, id, note, color)
            }
        },
        onDeleteHighlight = { id ->
            if (loadingState is BookmarkLoadingState.FullyLoaded) {
                val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                screenModel.deleteHighlight(fullyLoadedState.bookmark, id)
            }
            onSelectedHighlightIdChanged(null)
        },
        onDismiss = {
            onSelectedHighlightIdChanged(null)
            onSelectedHighlightTextChanged(null)
        }
    )

    // Bookmark Details Panel (slides from the right)
    val detailsBookmark = (loadingState as? BookmarkLoadingState.FullyLoaded)?.bookmark
    BookmarkDetailsPanel(
        visible = showDetailsPanel,
        bookmark = detailsBookmark,
        onDismiss = { onShowDetailsPanelChanged(false) }
    )
}
