package com.karakept.app.ui.screens.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.ReaderFontFamily
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.karakept.app.data.local.entity.AssetEntity
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
    selectedSource: ContentSource = ContentSource.EXTRACTED,
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
    assets: List<AssetEntity> = emptyList(),
    showSearch: Boolean = false,
    onShowSearchChanged: (Boolean) -> Unit = {},
    screenModel: BookmarkViewerScreenModel,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onLinkCopied: () -> Unit = {},
    onOpenLink: () -> Unit = {}
) {
    // Back Handler for panels
    com.karakept.app.ui.components.BackHandler(
        enabled = showAppearancePanel || showModeDialog || selectedHighlightId != null || showDetailsPanel || showSearch
    ) {
        if (showSearch) onShowSearchChanged(false)
        else if (showDetailsPanel) onShowDetailsPanelChanged(false)
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
    val scrollToTopEnabled by screenModel.scrollToTopEnabled.collectAsState()
    val readerTypography by screenModel.readerTypography.collectAsState()
    ReaderAppearancePanel(
        visible = showAppearancePanel,
        textColor = htmlTextColor,
        backgroundColor = htmlBackgroundColor,
        fontSize = htmlFontSize,
        fontFamily = htmlFontFamily,
        typography = readerTypography,
        onTextColorChange = { screenModel.setHtmlTextColor(it) },
        onBackgroundColorChange = { screenModel.setHtmlBackgroundColor(it) },
        onFontSizeChange = { screenModel.setHtmlFontSize(it) },
        onFontFamilyChange = { screenModel.setHtmlFontFamily(it) },
        onLineHeightScaleChange = { screenModel.setReaderLineHeightScale(it) },
        onHorizontalMarginChange = { screenModel.setReaderHorizontalMarginDp(it) },
        onMaxWidthChange = { screenModel.setReaderMaxWidthDp(it) },
        onReset = { scope.launch { screenModel.resetReaderAppearance() } },
        onDismiss = { onShowAppearancePanelChanged(false) },
        scrollToTopEnabled = scrollToTopEnabled,
        onScrollToTopToggle = { screenModel.setScrollToTopEnabled(it) }
    )

    // Delete Confirmation Dialog
    if (showDeleteConfirmation && loadingState is BookmarkLoadingState.FullyLoaded) {
        val fullyLoadedState = loadingState
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
        val fullyLoadedState = loadingState
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
        val fullyLoadedState = loadingState
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
                val fullyLoadedState = loadingState
                screenModel.updateHighlight(fullyLoadedState.bookmark, id, note, color)
            }
        },
        onDeleteHighlight = { id ->
            if (loadingState is BookmarkLoadingState.FullyLoaded) {
                val fullyLoadedState = loadingState
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
    val serverCrawlInFlight by screenModel.serverCrawlInFlight.collectAsState()
    val offlineMode by screenModel.offlineMode.collectAsState()
    val assetDownloads by screenModel.assetDownloads.collectAsState()
    val aiActionInFlight by screenModel.aiActionInFlight.collectAsState()
    val aiCapabilitiesByServer by screenModel.aiCapabilities.collectAsState()
    BookmarkDetailsPanel(
        visible = showDetailsPanel,
        bookmark = detailsBookmark,
        assets = assets,
        selectedSource = selectedSource,
        serverCrawlInFlight = serverCrawlInFlight,
        aiActionInFlight = aiActionInFlight,
        aiCapabilities = aiCapabilitiesByServer[detailsBookmark?.serverId] ?: AiCapabilities(),
        serverActionsEnabled = !offlineMode,
        assetDownloads = assetDownloads,
        onSourceSelected = { source ->
            screenModel.setContentSource(source, detailsBookmark)
        },
        onDownloadAsset = { asset ->
            if (detailsBookmark != null) screenModel.downloadOrRefreshAsset(asset, detailsBookmark)
        },
        onRefreshAsset = { asset ->
            if (detailsBookmark != null) screenModel.downloadOrRefreshAsset(asset, detailsBookmark)
        },
        onDownloadAndUseAsset = { asset ->
            if (detailsBookmark != null) {
                screenModel.downloadOrRefreshAsset(asset, detailsBookmark, useWhenDone = true)
            }
        },
        onOpenAssetExternally = { asset -> screenModel.openAssetExternally(asset) },
        onDeleteAssetLocal = { asset -> screenModel.deleteAssetLocal(asset) },
        onDeleteAssetOnServer = { asset ->
            if (detailsBookmark != null) screenModel.deleteAssetOnServer(asset, detailsBookmark)
        },
        onRequestServerCrawl = { action ->
            if (detailsBookmark != null) screenModel.requestServerCrawl(detailsBookmark, action)
        },
        onRunAiAction = { action ->
            if (detailsBookmark != null) screenModel.runAiAction(detailsBookmark, action)
        },
        onLinkCopied = onLinkCopied,
        onOpenLink = onOpenLink,
        onDismiss = { onShowDetailsPanelChanged(false) }
    )
}
