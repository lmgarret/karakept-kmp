package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.SyncProgress
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.app.ui.screens.clearSelection
import com.karakept.app.ui.screens.selectAll
import com.karakept.app.ui.screens.batchMarkRead
import com.karakept.app.ui.screens.batchMarkUnread
import com.karakept.app.ui.screens.batchArchive
import com.karakept.app.ui.screens.batchUnarchive
import com.karakept.app.ui.screens.batchFavourite
import com.karakept.app.ui.screens.batchUnfavourite
import com.karakept.app.ui.screens.toggleBookmarkSelection
import com.karakept.app.ui.screens.loadNextPage
import com.karakept.app.ui.screens.enterSelectionMode
import com.karakept.app.ui.screens.enterSelectionModeWithRange
import com.karakept.app.ui.screens.selectRange
import com.karakept.app.ui.screens.toggleBookmarkArchive
import com.karakept.app.ui.screens.toggleBookmarkFavorite
import com.karakept.app.ui.screens.toggleBookmarkRead
import com.karakept.app.ui.screens.deleteBookmark
import com.karakept.app.ui.screens.moveBookmarkToList
import com.karakept.app.ui.screens.updateBookmarkTags
import com.karakept.app.ui.screens.accumulatedBookmarkPosition
import com.karakept.app.ui.screens.restoreAndRemoveBookmarkFromList
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.undoableAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bundles all effective display settings derived from active layout overrides.
 * Reduces parameter count in composable signatures.
 */
data class MainScreenDisplayConfig(
    val layoutType: LayoutType,
    val dimReadBookmarks: Boolean,
    val showReadingTimeBadge: Boolean,
    val showTags: Boolean,
    val showDate: Boolean,
    val dateDisplayMode: DateDisplayMode,
    val thumbnailSide: ThumbnailSide,
    val showFavicon: Boolean,
    val thumbnailSize: Int,
    val metadataPosition: MetadataPosition,
    val tagsScrollable: Boolean,
    val quickActionPosition: QuickActionPosition,
    val showDescription: Boolean = true,
    val descriptionPosition: DescriptionPosition = DescriptionPosition.BELOW_TITLE,
    val showUrl: Boolean = false,
    val urlDisplayMode: UrlDisplayMode = UrlDisplayMode.DOMAIN_ONLY,
    val urlPosition: UrlPosition = UrlPosition.BELOW_TITLE,
    val urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    val faviconByLinkSize: Int = 16,
)

/**
 * Scaffold content shared between compact and expanded modes.
 * Contains the top bar, bookmark list, FAB, and snackbar host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenScaffoldContent(
    isExpandedLayout: Boolean,
    bookmarks: List<BookmarkEntity>,
    isSyncing: Boolean,
    syncProgress: SyncProgress,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    bookmarkListVersion: Int = 0,
    showScrollCursor: Boolean = false,
    sortOption: com.karakept.app.data.model.SortOption = com.karakept.app.data.model.SortOption.NEWEST,
    totalBookmarkCount: Int = 0,
    displayConfig: MainScreenDisplayConfig,
    swipeLeftAction: SwipeAction,
    swipeRightAction: SwipeAction,
    customSwipeActionConfigs: List<CustomSwipeActionConfig>,
    swipeLeftConfigId: String?,
    swipeRightConfigId: String?,
    trackReadingProgress: Boolean,
    offlineMode: Boolean,
    pendingBookmarkRemoteIds: Set<Long>,
    isSelectionMode: Boolean,
    selectedBookmarkIds: Set<Long>,
    activeBookmarkId: Long?,
    isSearchActive: Boolean,
    searchQuery: String,
    listState: LazyListState,
    isDesktop: Boolean,
    snackbarHostState: SnackbarHostState,
    isDrawerVisible: Boolean,
    hasActiveFilter: Boolean,
    serverUrl: String?,
    screenModel: MainScreenModel,
    snackbarManager: ActionSnackbarManager,
    scope: CoroutineScope,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onMenuClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    contextMenuLists: List<com.karakept.api.model.KarakeepList> = emptyList(),
    contextMenuTags: List<String> = emptyList(),
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkLongClick: (BookmarkEntity) -> Unit,
    onSwipeAction: (BookmarkEntity, SwipeAction, CustomSwipeActionConfig?) -> Unit,
    onShowAddBookmarkDialog: () -> Unit,
    onShowBatchTagEditor: () -> Unit,
    onShowBatchListPicker: () -> Unit,
    onShowBatchDeleteConfirm: () -> Unit,
    navigateTo: (androidx.navigation3.runtime.NavKey) -> Unit,
    newBookmarksAbove: Int = 0,
    onClearNewBookmarksAbove: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.keyboardKey == Key.Escape && isSearchActive) {
                onSearchClose()
                true
            } else if (keyEvent.type == KeyEventType.KeyDown &&
                (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                keyEvent.keyboardKey == Key.F &&
                !isSearchActive) {
                onSearchClick()
                true
            } else if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                keyEvent.keyboardKey == Key.R &&
                keyEvent.type == KeyEventType.KeyDown) {
                screenModel.syncBookmarks()
                true
            } else {
                false
            }
        },
        topBar = {
            MainScreenTopBar(
                offlineMode = offlineMode,
                onMenuClick = onMenuClick,
                onFilterClick = onFilterClick,
                onRefreshClick = { screenModel.syncBookmarks() },
                onOfflineBadgeClick = {
                    navigateTo(com.karakept.app.ui.screens.settings.SyncDataSettingsScreen(highlightOfflineMode = true))
                },
                isDesktop = isDesktop,
                isExpandedLayout = isExpandedLayout,
                isDrawerVisible = isDrawerVisible,
                hasActiveFilter = hasActiveFilter,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchClick = onSearchClick,
                onSearchQueryChange = onSearchQueryChange,
                onSearchClose = onSearchClose,
                isSelectionMode = isSelectionMode,
                selectedCount = selectedBookmarkIds.size,
                allSelected = selectedBookmarkIds.size == bookmarks.size && bookmarks.isNotEmpty(),
                onClearSelection = { screenModel.clearSelection() },
                onSelectAll = {
                    if (selectedBookmarkIds.size == bookmarks.size) screenModel.clearSelection()
                    else screenModel.selectAll()
                },
                onBatchMarkRead = { screenModel.batchMarkRead() },
                onBatchMarkUnread = { screenModel.batchMarkUnread() },
                onBatchArchive = { screenModel.batchArchive() },
                onBatchUnarchive = { screenModel.batchUnarchive() },
                onBatchFavourite = { screenModel.batchFavourite() },
                onBatchUnfavourite = { screenModel.batchUnfavourite() },
                onBatchSetTags = onShowBatchTagEditor,
                onBatchMoveToList = onShowBatchListPicker,
                onBatchDelete = onShowBatchDeleteConfirm
            )
        },
        floatingActionButton = {
            if (!isDesktop && !offlineMode && !isSelectionMode) {
                FloatingActionButton(
                    onClick = onShowAddBookmarkDialog
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add bookmark"
                    )
                }
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.keyboardKey == Key.R &&
                        (event.isCtrlPressed || event.isMetaPressed) &&
                        !offlineMode) {
                        screenModel.syncBookmarks()
                        true
                    } else {
                        false
                    }
                }
        ) {
            BookmarkListContent(
                bookmarks = bookmarks,
                isSyncing = isSyncing,
                syncProgress = syncProgress,
                isLoadingMore = if (isSearchActive) false else isLoadingMore,
                hasMoreItems = if (isSearchActive) true else hasMoreItems,
                bookmarkListVersion = bookmarkListVersion,
                showScrollCursor = showScrollCursor,
                sortOption = sortOption,
                totalBookmarkCount = totalBookmarkCount,
                layoutType = displayConfig.layoutType,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                swipeLeftConfig = customSwipeActionConfigs.find { it.id == swipeLeftConfigId },
                swipeRightConfig = customSwipeActionConfigs.find { it.id == swipeRightConfigId },
                dimReadBookmarks = displayConfig.dimReadBookmarks,
                showReadingTimeBadge = displayConfig.showReadingTimeBadge,
                showReadingProgress = trackReadingProgress,
                showTags = displayConfig.showTags,
                showDate = displayConfig.showDate,
                dateDisplayMode = displayConfig.dateDisplayMode,
                thumbnailSide = displayConfig.thumbnailSide,
                showFavicon = displayConfig.showFavicon,
                thumbnailSize = displayConfig.thumbnailSize,
                metadataPosition = displayConfig.metadataPosition,
                tagsScrollable = displayConfig.tagsScrollable,
                quickActionPosition = displayConfig.quickActionPosition,
                showDescription = displayConfig.showDescription,
                descriptionPosition = displayConfig.descriptionPosition,
                showUrl = displayConfig.showUrl,
                urlDisplayMode = displayConfig.urlDisplayMode,
                urlPosition = displayConfig.urlPosition,
                urlIconMode = displayConfig.urlIconMode,
                faviconByLinkSize = displayConfig.faviconByLinkSize,
                offlineMode = offlineMode,
                pendingBookmarkRemoteIds = pendingBookmarkRemoteIds,
                isSelectionMode = isSelectionMode,
                selectedBookmarkIds = selectedBookmarkIds,
                activeBookmarkId = activeBookmarkId,
                onBookmarkSelectionToggle = { bookmark ->
                    screenModel.toggleBookmarkSelection(bookmark)
                },
                listState = listState,
                isDesktop = isDesktop,
                contextMenuLists = contextMenuLists,
                contextMenuTags = contextMenuTags,
                onBookmarkClick = onBookmarkClick,
                onBookmarkLongClick = onBookmarkLongClick,
                serverUrl = serverUrl,
                onSwipeAction = onSwipeAction,
                onRefresh = { if (!offlineMode) screenModel.syncBookmarks() },
                onLoadMore = { screenModel.loadNextPage() },
                onCtrlClick = if (isDesktop) { bookmark ->
                    if (!isSelectionMode) {
                        screenModel.enterSelectionMode(bookmark)
                    } else {
                        screenModel.toggleBookmarkSelection(bookmark)
                    }
                } else null,
                onShiftClick = if (isDesktop) { index ->
                    if (!isSelectionMode) {
                        screenModel.enterSelectionModeWithRange(index)
                    } else {
                        screenModel.selectRange(index)
                    }
                } else null,
                onContextMenuAction = if (isDesktop) { bookmark, action ->
                    when (action) {
                        is BookmarkAction.ToggleArchive -> screenModel.toggleBookmarkArchive(bookmark)
                        is BookmarkAction.ToggleFavorite -> screenModel.toggleBookmarkFavorite(bookmark)
                        is BookmarkAction.ToggleRead -> screenModel.toggleBookmarkRead(bookmark)
                        is BookmarkAction.Delete -> screenModel.deleteBookmark(bookmark)
                        is BookmarkAction.Share -> {
                            com.karakept.app.utils.ShareUtils.shareText(bookmark.url, bookmark.title)
                        }
                        is BookmarkAction.OpenInBrowser -> {
                            try {
                                uriHandler.openUri(bookmark.url)
                                scope.launch { snackbarManager.showSnackbar("Opening in browser") }
                            } catch (e: Exception) {
                                scope.launch { snackbarManager.showSnackbar("Could not open link") }
                            }
                        }
                        is BookmarkAction.Select -> screenModel.enterSelectionMode(bookmark)
                        is BookmarkAction.MoveToList -> {
                            val pos = screenModel.accumulatedBookmarkPosition(bookmark)
                            screenModel.moveBookmarkToList(bookmark, action.listId)
                            val listName = contextMenuLists.firstOrNull { it.id == action.listId }?.name ?: "list"
                            scope.undoableAction(snackbarManager, "Moved to '$listName'") {
                                screenModel.restoreAndRemoveBookmarkFromList(bookmark, action.listId, pos)
                            }
                        }
                        is BookmarkAction.UpdateTags -> {
                            val oldTags = bookmark.tags.split(",").filter { it.isNotBlank() }
                            screenModel.updateBookmarkTags(bookmark, action.tags)
                            scope.undoableAction(snackbarManager, "Tags updated") {
                                screenModel.updateBookmarkTags(bookmark, oldTags)
                            }
                        }
                    }
                } else null,
                newBookmarksAbove = newBookmarksAbove,
                onClearNewBookmarksAbove = onClearNewBookmarksAbove
            )
        }
    }
}
