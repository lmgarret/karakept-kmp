package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.ui.screens.QuickFilterCounts
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.screens.main.BatchDeleteConfirmDialog
import com.karakept.app.ui.screens.main.BatchListPickerDialog
import com.karakept.app.ui.screens.main.BatchTagEditorDialog
import com.karakept.app.ui.screens.main.MainScreenBookmarkActionsMenu
import com.karakept.app.ui.screens.main.MainScreenDisplayConfig
import com.karakept.app.ui.screens.main.MainScreenDrawer
import com.karakept.app.ui.screens.main.MainScreenExpandedLayout
import com.karakept.app.ui.screens.main.MainScreenFilterOverlay
import com.karakept.app.ui.screens.main.MainScreenScaffoldContent
import com.karakept.app.ui.screens.main.MainScreenScrollAction
import com.karakept.app.ui.screens.main.RenameListDialog
import com.karakept.app.ui.screens.main.MainScreenAddBookmarkDialog
import kotlinx.coroutines.launch
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.SnackbarEvent
import com.karakept.app.domain.action.undoableAction
import com.karakept.app.ui.screens.main.HighlightsListContent
import com.karakept.app.ui.screens.HighlightsScreenModel
import com.karakept.app.ui.screens.settings.PerListSettingsScreen
import getPlatform
import org.koin.compose.koinInject

@Serializable
object MainScreen : NavKey {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<MainScreenModel>()
        val settingsScreenModel = koinViewModel<SettingsScreenModel>()
        val serverRepository = koinInject<com.karakept.app.data.repository.ServerRepository>()
        val lists by screenModel.lists.collectAsState()
        val bookmarks by screenModel.bookmarks.collectAsState()
        val servers by serverRepository.servers.collectAsState(initial = emptyList())
        val layoutType by settingsScreenModel.layoutType.collectAsState()
        val isSyncing by screenModel.isSyncing.collectAsState()
        val syncProgress by screenModel.syncProgress.collectAsState()
        val isLoadingMore by screenModel.isLoadingMore.collectAsState()
        val hasMoreItems by screenModel.hasMoreItems.collectAsState()
        val newBookmarksAbove by screenModel.newBookmarksAbove.collectAsState()
        val currentFilter by screenModel.currentFilter.collectAsState()
        val tagFilterSourceBookmarkId by screenModel.tagFilterSourceBookmarkId.collectAsState()
        val offlineMode by settingsScreenModel.offlineMode.collectAsState()
        val trackReadingProgress by settingsScreenModel.trackReadingProgress.collectAsState()
        val swipeLeftAction by screenModel.swipeLeftAction.collectAsState()
        val swipeRightAction by screenModel.swipeRightAction.collectAsState()
        val customSwipeActionConfigs by screenModel.customSwipeActionConfigs.collectAsState()
        val swipeLeftConfigId by screenModel.swipeLeftConfigId.collectAsState()
        val swipeRightConfigId by screenModel.swipeRightConfigId.collectAsState()
        val activeLayout by screenModel.activeLayout.collectAsState()

        // Bundle effective display settings (active layout overrides global settings)
        val showReadingTimeBadge by settingsScreenModel.showReadingTimeBadge.collectAsState()
        val showTags by settingsScreenModel.showTags.collectAsState()
        val showDateInList by settingsScreenModel.showDateInList.collectAsState()
        val dateDisplayMode by settingsScreenModel.dateDisplayMode.collectAsState()
        val dimReadBookmarks by screenModel.dimReadBookmarks.collectAsState()
        val showScrollCursor by settingsScreenModel.showScrollCursor.collectAsState()
        val displayConfig = remember(activeLayout, layoutType, dimReadBookmarks, showReadingTimeBadge, showTags, showDateInList, dateDisplayMode) {
            MainScreenDisplayConfig(
                layoutType = activeLayout?.layoutType?.let { LayoutType.fromString(it) } ?: layoutType,
                dimReadBookmarks = activeLayout?.dimReadBookmarks ?: dimReadBookmarks,
                showReadingTimeBadge = activeLayout?.showReadingTime ?: showReadingTimeBadge,
                showTags = activeLayout?.showTags ?: showTags,
                showDate = activeLayout?.showDate ?: showDateInList,
                dateDisplayMode = activeLayout?.dateDisplayMode?.let { DateDisplayMode.fromString(it) } ?: dateDisplayMode,
                thumbnailSide = activeLayout?.thumbnailSide
                    ?.let { com.karakept.app.data.model.ThumbnailSide.fromString(it) }
                    ?: com.karakept.app.data.model.ThumbnailSide.LEFT,
                showFavicon = activeLayout?.showFavicon ?: true,
                thumbnailSize = activeLayout?.thumbnailSize ?: 80,
                metadataPosition = activeLayout?.metadataPosition
                    ?.let { com.karakept.app.data.model.MetadataPosition.fromString(it) }
                    ?: com.karakept.app.data.model.MetadataPosition.BELOW,
                tagsScrollable = activeLayout?.tagsScrollable ?: false,
                quickActionPosition = activeLayout?.quickActionPosition
                    ?.let { com.karakept.app.data.model.QuickActionPosition.valueOf(it) }
                    ?: com.karakept.app.data.model.QuickActionPosition.RIGHT,
                showDescription = activeLayout?.showDescription ?: true,
                descriptionPosition = activeLayout?.descriptionPosition
                    ?.let { DescriptionPosition.fromString(it) }
                    ?: DescriptionPosition.BELOW_TITLE,
                showUrl = activeLayout?.showUrl ?: false,
                urlDisplayMode = activeLayout?.urlDisplayMode
                    ?.let { UrlDisplayMode.fromString(it) }
                    ?: UrlDisplayMode.DOMAIN_ONLY,
                urlPosition = activeLayout?.urlPosition
                    ?.let { UrlPosition.fromString(it) }
                    ?: UrlPosition.BELOW_TITLE,
                urlIconMode = activeLayout?.urlIconMode
                    ?.let { UrlIconMode.fromString(it) }
                    ?: UrlIconMode.GLOBE_ONLY,
                faviconByLinkSize = activeLayout?.faviconByLinkSize ?: 16,
            )
        }

        val expandedLists by screenModel.expandedLists.collectAsState()
        val listCounts by screenModel.listCounts.collectAsState()
        val listSyncStatuses by screenModel.listSyncStatuses.collectAsState()
        val quickFilterCounts by screenModel.quickFilterCounts.collectAsState()
        val highlightsCount by screenModel.highlightsCount.collectAsState()
        val currentListId by screenModel.currentListContext.collectAsState()
        val currentListScrollAction by screenModel.currentListScrollAction.collectAsState()
        val currentListScrollActionConfig by screenModel.currentListScrollActionConfig.collectAsState()
        val bookmarkListVersion by screenModel.bookmarkListVersion.collectAsState()
        val searchQuery by screenModel.searchQuery.collectAsState()
        val isSelectionMode by screenModel.isSelectionMode.collectAsState()
        val selectedBookmarkIds by screenModel.selectedBookmarkIds.collectAsState()

        val hasActiveFilter = currentFilter.tags.isNotEmpty() ||
            currentFilter.lists.isNotEmpty() ||
            currentFilter.status != com.karakept.app.data.model.FilterStatus.ALL

        var showFilterDialog by remember { mutableStateOf(false) }
        var showAddBookmarkDialog by remember { mutableStateOf(false) }
        var renameListTarget by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
        val pendingBookmarkRemoteIds by screenModel.pendingBookmarkRemoteIds.collectAsState()
        var isSearchActive by remember { mutableStateOf(false) }
        var selectedBookmarkForActions by remember { mutableStateOf<com.karakept.app.data.local.entity.BookmarkEntity?>(null) }
        var showBatchDeleteConfirm by remember { mutableStateOf(false) }
        var showBatchListPicker by remember { mutableStateOf(false) }
        var showBatchTagEditor by remember { mutableStateOf(false) }
        val snackbarManager = koinInject<ActionSnackbarManager>()
        val snackbarHostState = rememberSnackbarHostState(snackbarManager)

        // Initialise LazyListState from the hoisted position stored in the ScreenModel.
        // This survives Voyager push/pop even when rememberSaveable state is lost (e.g.
        // object Screen singletons) or when background sync replaces the bookmarks list
        // while the reader is open.
        val listState = rememberSaveable(key = "main_screen_list_state", saver = LazyListState.Saver) {
            LazyListState(
                firstVisibleItemIndex = screenModel.savedScrollIndex,
                firstVisibleItemScrollOffset = screenModel.savedScrollOffset
            )
        }

        // Persist scroll position into the ScreenModel so it survives navigation.
        LaunchedEffect(listState) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.collect { (index, offset) ->
                screenModel.saveScrollPosition(index, offset)
            }
        }
        val isDesktop = remember { getPlatform().name.contains("Java") }
        val uriHandler = LocalUriHandler.current

        // Three-column adaptive layout state
        var selectedBookmarkId by remember { mutableStateOf<Long?>(null) }
        var isDrawerVisible by rememberSaveable { mutableStateOf(true) }
        var showHighlights by remember { mutableStateOf(false) }
        var scrollToHighlightId by remember { mutableStateOf<String?>(null) }
        var activeHighlightId by remember { mutableStateOf<String?>(null) }
        var isReaderFullscreen by remember { mutableStateOf(false) }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Observe sync completion and show "new bookmarks" snackbar
        LaunchedEffect(Unit) {
            screenModel.syncProgress.collect { progress ->
                if (progress is com.karakept.app.data.model.SyncProgress.SyncComplete &&
                    progress.newBookmarksCount > 0) {
                    val count = progress.newBookmarksCount
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "$count new bookmark${if (count > 1) "s" else ""}",
                            actionLabel = "View",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) { screenModel.scrollToTop() }
                    }
                }
            }
        }

        // Listen for scroll-to-top trigger
        LaunchedEffect(Unit) { screenModel.scrollToTopTrigger.collect { listState.animateScrollToItem(0, 0) } }

        // Keep the viewport pinned to the same bookmark when the list mutates beneath
        // the user (e.g. a bookmark removed by smart-list reconciliation after a quick
        // action), so the list doesn't jump.
        com.karakept.app.ui.screens.main.PreserveListScrollAnchor(
            listState = listState, bookmarks = bookmarks, bookmarkListVersion = bookmarkListVersion
        )

        // Scroll-triggered action
        MainScreenScrollAction(
            listState = listState, bookmarks = bookmarks, bookmarkListVersion = bookmarkListVersion,
            currentListId = currentListId,
            currentListScrollAction = currentListScrollAction, currentListScrollActionConfig = currentListScrollActionConfig,
            screenModel = screenModel
        )

        // Listen for create bookmark result
        LaunchedEffect(Unit) {
            screenModel.createBookmarkResult.collect { result ->
                if (result.isFailure) {
                    scope.launch { snackbarManager.showSnackbar("Failed to add bookmark: ${result.exceptionOrNull()?.message ?: "Unknown error"}") }
                }
            }
        }

        val allBookmarks by screenModel.allBookmarks.collectAsState()
        val allAvailableTags = remember(allBookmarks) {
            allBookmarks.flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }.distinct().sortedBy { it.lowercase() }
        }
        val topTagsWithCounts = remember(allBookmarks, currentFilter) {
            val countMap = allBookmarks.flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }.groupingBy { it }.eachCount()
            val topTagNames = countMap.entries.sortedByDescending { it.value }.take(10).map { it.key }
            val topTagsFormatted = topTagNames.map { tag -> "$tag (${countMap[tag]})" }
            val missingActiveTags = currentFilter.tags.filter { it !in topTagNames }.map { tag -> countMap[tag]?.let { "$tag ($it)" } ?: tag }
            topTagsFormatted + missingActiveTags
        }

        // Swipe action handler shared between modes
        val handleSwipeAction: (com.karakept.app.data.local.entity.BookmarkEntity, SwipeAction, com.karakept.app.data.model.CustomSwipeActionConfig?) -> Unit = { bookmark, action, config ->
            when (action) {
                SwipeAction.ARCHIVE -> screenModel.toggleBookmarkArchive(bookmark)
                SwipeAction.MARK_READ -> screenModel.toggleBookmarkRead(bookmark)
                SwipeAction.FAVOURITE -> screenModel.toggleBookmarkFavorite(bookmark)
                SwipeAction.DELETE -> selectedBookmarkForActions = bookmark
                SwipeAction.SHARE -> {
                    com.karakept.app.utils.ShareUtils.shareText(bookmark.url, bookmark.title)
                    scope.launch { snackbarManager.showSnackbar("Shared") }
                }
                SwipeAction.OPEN_IN_BROWSER -> {
                    try { uriHandler.openUri(bookmark.url); scope.launch { snackbarManager.showSnackbar("Opening in browser") } }
                    catch (e: Exception) { scope.launch { snackbarManager.showSnackbar("Could not open link") } }
                }
                SwipeAction.ADD_TAG -> {
                    val tagName = config?.tagName
                    if (tagName != null) {
                        val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (currentTags.contains(tagName)) {
                            screenModel.removeBookmarkTag(bookmark, tagName)
                            scope.undoableAction(snackbarManager, "Removed tag '$tagName'") {
                                screenModel.addBookmarkTag(bookmark, tagName)
                            }
                        } else {
                            screenModel.addBookmarkTag(bookmark, tagName)
                            scope.undoableAction(snackbarManager, "Added tag '$tagName'") {
                                screenModel.removeBookmarkTag(bookmark, tagName)
                            }
                        }
                    }
                }
                SwipeAction.ADD_TO_LIST -> {
                    val listId = config?.listId; val listName = config?.listName ?: "list"
                    if (listId != null) {
                        val position = screenModel.accumulatedBookmarkPosition(bookmark)
                        val bookmarkListIds = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (bookmarkListIds.contains(listId)) {
                            screenModel.removeBookmarkFromList(bookmark, listId)
                            scope.undoableAction(snackbarManager, "Removed from '$listName'") {
                                screenModel.restoreAndMoveBookmarkToList(bookmark, listId)
                            }
                        } else {
                            screenModel.moveBookmarkToList(bookmark, listId)
                            scope.undoableAction(snackbarManager, "Added to '$listName'") {
                                screenModel.restoreAndRemoveBookmarkFromList(bookmark, listId, position)
                            }
                        }
                    }
                }
                SwipeAction.NONE -> {}
            }
        }

        // Common scaffold content builder used by both layout modes
        // Derive the best total count for the scroll cursor denominator.
        // quickFilterCounts / listCounts are computed from the full (unfiltered) DB so they
        // represent the true total, not just the currently loaded page.
        val totalBookmarkCount = when {
            currentFilter.lists.size == 1 ->
                listCounts[currentFilter.lists.first()] ?: quickFilterCounts.all
            currentFilter.lists.size > 1 ->
                currentFilter.lists.sumOf { listCounts[it] ?: 0 }
            currentFilter.status == com.karakept.app.data.model.FilterStatus.FAVORITES ->
                quickFilterCounts.favorites
            currentFilter.status == com.karakept.app.data.model.FilterStatus.ARCHIVED ->
                quickFilterCounts.archived
            currentFilter.status == com.karakept.app.data.model.FilterStatus.ALL_INCLUDING_ARCHIVED ->
                quickFilterCounts.all + quickFilterCounts.archived
            else -> quickFilterCounts.all
        }

        val scaffoldContent: @Composable (isExpanded: Boolean, activeBookmarkId: Long?, onBookmarkClick: (com.karakept.app.data.local.entity.BookmarkEntity) -> Unit, onMenuClick: () -> Unit) -> Unit =
            { isExpanded, activeBmId, onBookmarkClick, onMenuClick ->
                MainScreenScaffoldContent(
                    isExpandedLayout = isExpanded, bookmarks = bookmarks, isSyncing = isSyncing, syncProgress = syncProgress,
                    isLoadingMore = isLoadingMore, hasMoreItems = hasMoreItems,
                    showScrollCursor = showScrollCursor, sortOption = currentFilter.sort,
                    totalBookmarkCount = totalBookmarkCount,
                    displayConfig = displayConfig,
                    swipeLeftAction = swipeLeftAction, swipeRightAction = swipeRightAction,
                    customSwipeActionConfigs = customSwipeActionConfigs, swipeLeftConfigId = swipeLeftConfigId, swipeRightConfigId = swipeRightConfigId,
                    trackReadingProgress = trackReadingProgress, offlineMode = offlineMode,
                    pendingBookmarkRemoteIds = pendingBookmarkRemoteIds, isSelectionMode = isSelectionMode,
                    selectedBookmarkIds = selectedBookmarkIds, activeBookmarkId = activeBmId,
                    isSearchActive = isSearchActive, searchQuery = searchQuery, listState = listState, isDesktop = isDesktop,
                    snackbarHostState = snackbarHostState,
                    isDrawerVisible = isDrawerVisible, hasActiveFilter = hasActiveFilter, serverUrl = servers.firstOrNull()?.url,
                    screenModel = screenModel, snackbarManager = snackbarManager, scope = scope, uriHandler = uriHandler,
                    onMenuClick = onMenuClick, onFilterClick = { showFilterDialog = true },
                    onSearchClick = { isSearchActive = true },
                    onSearchQueryChange = { screenModel.updateSearchQuery(it) },
                    onSearchClose = { isSearchActive = false; screenModel.clearSearch() },
                    contextMenuLists = lists,
                    contextMenuTags = allAvailableTags,
                    onBookmarkClick = onBookmarkClick,
                    onBookmarkLongClick = { bookmark ->
                        if (isSelectionMode) screenModel.toggleBookmarkSelection(bookmark)
                        else if (!isDesktop) selectedBookmarkForActions = bookmark
                    },
                    onSwipeAction = handleSwipeAction,
                    onShowAddBookmarkDialog = { showAddBookmarkDialog = true },
                    onShowBatchTagEditor = { showBatchTagEditor = true },
                    onShowBatchListPicker = { showBatchListPicker = true },
                    onShowBatchDeleteConfirm = { showBatchDeleteConfirm = true },
                    navigateTo = { screen -> navigator.push(screen) },
                    newBookmarksAbove = newBookmarksAbove,
                    onClearNewBookmarksAbove = { screenModel.clearNewBookmarksAbove() }
                )
            }

        // Rename list dialog (shared between modes)
        renameListTarget?.let { (listId, initialName, initialIcon) ->
            RenameListDialog(
                initialName = initialName, initialIcon = initialIcon ?: "",
                onDismiss = { renameListTarget = null },
                onConfirm = { newName, newIcon -> screenModel.renameList(listId, newName, newIcon.ifBlank { null }); renameListTarget = null }
            )
        }

        // Adaptive layout: compact (modal drawer) vs expanded (3-column)
        var isExpandedLayout by remember { mutableStateOf(false) }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            isExpandedLayout = maxWidth >= 840.dp
            if (isExpandedLayout) {
                MainScreenExpandedLayout(
                    maxWidth = maxWidth, lists = lists, listCounts = listCounts, expandedLists = expandedLists,
                    currentFilter = currentFilter, topTagsWithCounts = topTagsWithCounts, allAvailableTags = allAvailableTags,
                    offlineMode = offlineMode,
                    isDrawerVisible = isDrawerVisible, onDrawerVisibilityChanged = { isDrawerVisible = it },
                    isReaderFullscreen = isReaderFullscreen, onReaderFullscreenChanged = { isReaderFullscreen = it },
                    selectedBookmarkId = selectedBookmarkId, onSelectedBookmarkIdChanged = { selectedBookmarkId = it },
                    showHighlights = showHighlights, onShowHighlightsChanged = { showHighlights = it },
                    scrollToHighlightId = scrollToHighlightId, onScrollToHighlightIdChanged = { scrollToHighlightId = it },
                    activeHighlightId = activeHighlightId, onActiveHighlightIdChanged = { activeHighlightId = it },
                    showFilterDialog = showFilterDialog, onShowFilterDialogChanged = { showFilterDialog = it },
                    showAddBookmarkDialog = showAddBookmarkDialog, onShowAddBookmarkDialogChanged = { showAddBookmarkDialog = it },
                    renameListTarget = renameListTarget, onRenameListTargetChanged = { renameListTarget = it },
                    screenModel = screenModel, navigateTo = { screen -> navigator.push(screen) }, isDesktop = isDesktop,
                    quickFilterCounts = quickFilterCounts, highlightsCount = highlightsCount,
                    listSyncStatuses = listSyncStatuses,
                    scaffoldContent = { isExpanded ->
                        scaffoldContent(isExpanded, selectedBookmarkId, { bookmark ->
                            val idx = bookmarks.indexOfFirst { it.remoteId == bookmark.remoteId }
                            if (idx >= 0) screenModel.trackLastClickedIndex(idx)
                            selectedBookmarkId = bookmark.localId; scrollToHighlightId = null; activeHighlightId = null
                        }, { isDrawerVisible = !isDrawerVisible })
                    }
                )
            } else {
                MainScreenDrawer(
                    drawerState = drawerState, lists = lists, listCounts = listCounts, expandedLists = expandedLists,
                    currentFilter = currentFilter,
                    onFilterApply = { filter -> showHighlights = false; screenModel.applyFilter(filter); scope.launch { drawerState.close() } },
                    onClearFilter = { showHighlights = false; screenModel.clearFilter(); scope.launch { drawerState.close() } },
                    onToggleListExpanded = { screenModel.toggleListExpanded(it) },
                    onMarkAllAsRead = { screenModel.markAllBookmarksInListAsRead(it); scope.launch { drawerState.close() } },
                    onRenameList = { listId, listName -> val t = lists.find { it.id == listId }; renameListTarget = Triple(listId, listName, t?.icon); scope.launch { drawerState.close() } },
                    onNavigateToListSettings = { listId, listName -> navigator.push(PerListSettingsScreen(listId, listName)); scope.launch { drawerState.close() } },
                    onSetAsDefault = { screenModel.setDefaultList(it); scope.launch { drawerState.close() } },
                    onSetAsDefaultType = { screenModel.setDefaultListType(it); scope.launch { drawerState.close() } },
                    onNavigateToSettings = { navigator.push(SettingsScreen()); scope.launch { drawerState.close() } },
                    onNavigateToHighlights = { showHighlights = true; scope.launch { drawerState.close() } },
                    isHighlightsSelected = showHighlights,
                    quickFilterCounts = quickFilterCounts,
                    highlightsCount = highlightsCount,
                    listSyncStatuses = listSyncStatuses
                ) {
                    if (showHighlights) {
                        val highlightsScreenModel = koinInject<HighlightsScreenModel>()
                        val highlightsList by highlightsScreenModel.highlights.collectAsState()
                        val isHighlightsSyncing by highlightsScreenModel.isSyncing.collectAsState()
                        val isHighlightsLoadingMore by highlightsScreenModel.isLoadingMore.collectAsState()
                        val hasMoreHighlights by highlightsScreenModel.hasMoreItems.collectAsState()

                        LaunchedEffect(showHighlights) {
                            highlightsScreenModel.syncHighlights()
                        }

                        HighlightsListContent(
                            highlights = highlightsList,
                            isSyncing = isHighlightsSyncing,
                            isLoadingMore = isHighlightsLoadingMore,
                            hasMoreItems = hasMoreHighlights,
                            activeHighlightId = null,
                            onHighlightClick = { highlight ->
                                scope.launch {
                                    val bookmarkLocalId = highlightsScreenModel.getBookmarkLocalIdForHighlight(highlight)
                                    if (bookmarkLocalId != null) navigator.push(BookmarkViewerScreen(bookmarkLocalId, highlight.id))
                                }
                            },
                            onDeleteHighlight = { highlightsScreenModel.deleteHighlight(it) },
                            onLoadMore = { highlightsScreenModel.loadNextPage() },
                            onBack = { showHighlights = false },
                            onRefresh = { highlightsScreenModel.syncHighlights() },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                    } else {
                        scaffoldContent(false, null, { bookmark ->
                            val idx = bookmarks.indexOfFirst { it.remoteId == bookmark.remoteId }
                            if (idx >= 0) screenModel.trackLastClickedIndex(idx)
                            navigator.push(BookmarkViewerScreen(bookmark.localId))
                        }, { scope.launch { drawerState.open() } })
                    }
                }
            }
        }

        // Back Handlers
        com.karakept.app.ui.components.BackHandler(enabled = isReaderFullscreen) { isReaderFullscreen = false }
        com.karakept.app.ui.components.BackHandler(enabled = showHighlights && !isExpandedLayout) { showHighlights = false }
        com.karakept.app.ui.components.BackHandler(enabled = isSelectionMode) { screenModel.clearSelection() }
        com.karakept.app.ui.components.BackHandler(enabled = isSearchActive) { isSearchActive = false; screenModel.clearSearch() }
        com.karakept.app.ui.components.BackHandler(enabled = showFilterDialog) { showFilterDialog = false }
        com.karakept.app.ui.components.BackHandler(enabled = tagFilterSourceBookmarkId != null && !showFilterDialog) {
            val sourceId = screenModel.consumeTagFilterSource(); screenModel.clearFilter()
            if (sourceId != null) navigator.push(BookmarkViewerScreen(sourceId))
        }

        // Filter overlay (compact layout only)
        if (!isExpandedLayout) {
            MainScreenFilterOverlay(
                showFilterDialog = showFilterDialog, onDismissFilter = { showFilterDialog = false },
                currentFilter = currentFilter, topTagsWithCounts = topTagsWithCounts, allAvailableTags = allAvailableTags,
                lists = lists, onFilterChange = { screenModel.applyFilter(it) }, onFilterReset = { screenModel.applyFilter(FilterConfig()) }
            )
        }

        // Bookmark Actions Menu
        MainScreenBookmarkActionsMenu(
            bookmark = selectedBookmarkForActions, lists = lists, allAvailableTags = allAvailableTags,
            screenModel = screenModel, snackbarManager = snackbarManager, scope = scope, uriHandler = uriHandler,
            onDismiss = { selectedBookmarkForActions = null }
        )

        // Add Bookmark Dialog
        if (showAddBookmarkDialog) {
            MainScreenAddBookmarkDialog(
                onConfirm = { url -> showAddBookmarkDialog = false; screenModel.createBookmark(url); scope.launch { snackbarManager.showSnackbar("Adding bookmark...") } },
                onDismiss = { showAddBookmarkDialog = false }
            )
        }

        // Batch dialogs
        if (showBatchDeleteConfirm) {
            BatchDeleteConfirmDialog(
                selectedCount = selectedBookmarkIds.size,
                onConfirm = { screenModel.batchDelete(); showBatchDeleteConfirm = false },
                onDismiss = { showBatchDeleteConfirm = false }
            )
        }
        if (showBatchListPicker) {
            BatchListPickerDialog(
                lists = lists,
                onListSelected = { listId -> screenModel.batchMoveToList(listId); showBatchListPicker = false },
                onDismiss = { showBatchListPicker = false }
            )
        }
        if (showBatchTagEditor) {
            BatchTagEditorDialog(
                selectedBookmarkIds = selectedBookmarkIds, bookmarks = bookmarks, allAvailableTags = allAvailableTags,
                onTagsUpdated = { newTags -> screenModel.batchSetTags(newTags); showBatchTagEditor = false },
                onDismiss = { showBatchTagEditor = false }
            )
        }
    }
}

@Composable
fun rememberSnackbarHostState(
    manager: ActionSnackbarManager,
    enabled: Boolean = true
): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        manager.snackbarEvents.collect { event ->
            when (event) {
                is SnackbarEvent.Message -> snackbarHostState.showSnackbar(message = event.text, duration = event.duration)
                is SnackbarEvent.MessageWithUndo -> {
                    val result = snackbarHostState.showSnackbar(message = event.text, actionLabel = "Undo", duration = event.duration)
                    if (result == SnackbarResult.ActionPerformed) scope.launch {
                        event.onUndo()
                        manager.showSnackbar("Undone")
                    }
                }
                is SnackbarEvent.MessageWithAction -> {
                    val result = snackbarHostState.showSnackbar(message = event.text, actionLabel = event.actionLabel, duration = event.duration)
                    if (result == SnackbarResult.ActionPerformed) scope.launch { event.onAction() }
                }
            }
        }
    }
    return snackbarHostState
}
