package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.screens.main.BatchDeleteConfirmDialog
import com.karakept.app.ui.screens.main.BatchListPickerDialog
import com.karakept.app.ui.screens.main.BatchTagEditorDialog
import com.karakept.app.ui.screens.main.BookmarkListContent
import com.karakept.app.ui.screens.main.DrawerContent
import com.karakept.app.ui.screens.main.HighlightsListContent
import com.karakept.app.ui.screens.main.MainScreenBookmarkActionsMenu
import com.karakept.app.ui.screens.main.MainScreenDrawer
import com.karakept.app.ui.screens.main.MainScreenExpandedLayout
import com.karakept.app.ui.screens.main.MainScreenFilterOverlay
import com.karakept.app.ui.screens.main.MainScreenScrollAction
import com.karakept.app.ui.screens.main.MainScreenTopBar
import com.karakept.app.ui.screens.main.RenameListDialog
import com.karakept.app.ui.screens.main.MainScreenAddBookmarkDialog
import kotlinx.coroutines.launch
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.SnackbarEvent
import com.karakept.app.ui.screens.settings.PerListSettingsScreen
import getPlatform
import org.koin.compose.koinInject

object MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<MainScreenModel>()
        val settingsScreenModel = koinScreenModel<SettingsScreenModel>()
        val serverRepository = koinInject<com.karakept.app.data.repository.ServerRepository>()
        val lists by screenModel.lists.collectAsState()
        val bookmarks by screenModel.bookmarks.collectAsState()
        val servers by serverRepository.servers.collectAsState(initial = emptyList())
        val layoutType by settingsScreenModel.layoutType.collectAsState()
        val isSyncing by screenModel.isSyncing.collectAsState()
        val syncProgress by screenModel.syncProgress.collectAsState()
        val isLoadingMore by screenModel.isLoadingMore.collectAsState()
        val hasMoreItems by screenModel.hasMoreItems.collectAsState()
        val currentFilter by screenModel.currentFilter.collectAsState()
        val tagFilterSourceBookmarkId by screenModel.tagFilterSourceBookmarkId.collectAsState()
        val offlineMode by settingsScreenModel.offlineMode.collectAsState()
        val isAutoOffline by settingsScreenModel.isAutoOffline.collectAsState()
        val showReadingTimeBadge by settingsScreenModel.showReadingTimeBadge.collectAsState()
        val trackReadingProgress by settingsScreenModel.trackReadingProgress.collectAsState()
        val showTags by settingsScreenModel.showTags.collectAsState()
        val showDateInList by settingsScreenModel.showDateInList.collectAsState()
        val dateDisplayMode by settingsScreenModel.dateDisplayMode.collectAsState()
        val swipeLeftAction by screenModel.swipeLeftAction.collectAsState()
        val swipeRightAction by screenModel.swipeRightAction.collectAsState()
        val customSwipeActionConfigs by screenModel.customSwipeActionConfigs.collectAsState()
        val swipeLeftConfigId by screenModel.swipeLeftConfigId.collectAsState()
        val swipeRightConfigId by screenModel.swipeRightConfigId.collectAsState()
        val dimReadBookmarks by screenModel.dimReadBookmarks.collectAsState()
        val activeLayout by screenModel.activeLayout.collectAsState()
        // When a display profile is active, its settings override global settings
        val effectiveLayoutType = activeLayout?.layoutType
            ?.let { LayoutType.fromString(it) } ?: layoutType
        val effectiveDimReadBookmarks = activeLayout?.dimReadBookmarks ?: dimReadBookmarks
        val effectiveShowReadingTimeBadge = activeLayout?.showReadingTime ?: showReadingTimeBadge
        val effectiveShowTags = activeLayout?.showTags ?: showTags
        val effectiveShowDate = activeLayout?.showDate ?: showDateInList
        val effectiveDateDisplayMode = activeLayout?.dateDisplayMode
            ?.let { DateDisplayMode.fromString(it) } ?: dateDisplayMode
        val effectiveThumbnailSide = activeLayout?.thumbnailSide
            ?.let { com.karakept.app.data.model.ThumbnailSide.fromString(it) }
            ?: com.karakept.app.data.model.ThumbnailSide.LEFT
        val effectiveShowFavicon = activeLayout?.showFavicon ?: true
        val effectiveThumbnailSize = activeLayout?.thumbnailSize ?: 80
        val effectiveMetadataPosition = activeLayout?.metadataPosition
            ?.let { com.karakept.app.data.model.MetadataPosition.fromString(it) }
            ?: com.karakept.app.data.model.MetadataPosition.BELOW
        val effectiveTagsScrollable = activeLayout?.tagsScrollable ?: false
        val effectiveQuickActionPosition = activeLayout?.quickActionPosition
            ?.let { com.karakept.app.data.model.QuickActionPosition.valueOf(it) }
            ?: com.karakept.app.data.model.QuickActionPosition.RIGHT
        val expandedLists by screenModel.expandedLists.collectAsState()
        val listCounts by screenModel.listCounts.collectAsState()
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

        val listState = rememberSaveable(
            key = "main_screen_list_state",
            saver = LazyListState.Saver
        ) {
            LazyListState()
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

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isSyncing,
            onRefresh = { if (!offlineMode) screenModel.syncBookmarks() }
        )

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Observe sync completion and show "new bookmarks" snackbar
        LaunchedEffect(Unit) {
            screenModel.syncProgress.collect { progress ->
                if (progress is com.karakept.app.data.model.SyncProgress.SyncComplete &&
                    progress.newBookmarksCount > 0) {
                    val count = progress.newBookmarksCount
                    val message = "$count new bookmark${if (count > 1) "s" else ""}"
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = "View",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            screenModel.scrollToTop()
                        }
                    }
                }
            }
        }

        // Listen for scroll-to-top trigger
        LaunchedEffect(Unit) {
            screenModel.scrollToTopTrigger.collect {
                listState.animateScrollToItem(0)
            }
        }

        // Scroll-triggered action (extracted to MainScreenScrollAction)
        MainScreenScrollAction(
            listState = listState,
            bookmarks = bookmarks,
            bookmarkListVersion = bookmarkListVersion,
            currentListScrollAction = currentListScrollAction,
            currentListScrollActionConfig = currentListScrollActionConfig,
            screenModel = screenModel
        )

        // Listen for create bookmark result (only handle failure snackbar; success is shown upfront)
        LaunchedEffect(Unit) {
            screenModel.createBookmarkResult.collect { result ->
                if (result.isFailure) {
                    scope.launch {
                        snackbarManager.showSnackbar(
                            "Failed to add bookmark: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }

        val allBookmarks by screenModel.allBookmarks.collectAsState()

        // Extract all unique tags from ALL bookmarks for filter dialog
        val allAvailableTags = remember(allBookmarks) {
            allBookmarks.flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }.distinct().sortedBy { it.lowercase() }
        }

        // Get top 10 most used tags with counts, always including any currently active filter tags
        val topTagsWithCounts = remember(allBookmarks, currentFilter) {
            val countMap = allBookmarks
                .flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
            val topTagNames = countMap.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { it.key }
            val topTagsFormatted = topTagNames.map { tag -> "$tag (${countMap[tag]})" }
            val missingActiveTags = currentFilter.tags.filter { it !in topTagNames }
                .map { tag -> countMap[tag]?.let { "$tag ($it)" } ?: tag }
            topTagsFormatted + missingActiveTags
        }

        // Drawer callbacks shared between compact and expanded modes
        val drawerFilterApply: (FilterConfig) -> Unit = { filter ->
            screenModel.applyFilter(filter)
            scope.launch { drawerState.close() }
        }
        val drawerClearFilter: () -> Unit = {
            screenModel.clearFilter()
            scope.launch { drawerState.close() }
        }
        val drawerToggleListExpanded: (String) -> Unit = { listId ->
            screenModel.toggleListExpanded(listId)
        }
        val drawerMarkAllAsRead: (String) -> Unit = { listId ->
            screenModel.markAllBookmarksInListAsRead(listId)
            scope.launch { drawerState.close() }
        }
        val drawerRenameList: (String, String) -> Unit = { listId, listName ->
            val targetList = lists.find { it.id == listId }
            renameListTarget = Triple(listId, listName, targetList?.icon)
            scope.launch { drawerState.close() }
        }
        val drawerNavigateToListSettings: (String, String) -> Unit = { listId, listName ->
            navigator.push(PerListSettingsScreen(listId, listName))
            scope.launch { drawerState.close() }
        }
        val drawerSetAsDefault: (String) -> Unit = { listId ->
            screenModel.setDefaultList(listId)
            scope.launch { drawerState.close() }
        }
        val drawerSetAsDefaultType: (DefaultListType) -> Unit = { type ->
            screenModel.setDefaultListType(type)
            scope.launch { drawerState.close() }
        }
        val drawerNavigateToSettings: () -> Unit = {
            navigator.push(SettingsScreen())
            scope.launch { drawerState.close() }
        }
        val drawerNavigateToHighlights: () -> Unit = {
            navigator.push(HighlightsScreen())
            scope.launch { drawerState.close() }
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
                    try {
                        uriHandler.openUri(bookmark.url)
                        scope.launch { snackbarManager.showSnackbar("Opening in browser") }
                    } catch (e: Exception) {
                        scope.launch { snackbarManager.showSnackbar("Could not open link") }
                    }
                }
                SwipeAction.ADD_TAG -> {
                    val tagName = config?.tagName
                    if (tagName != null) {
                        val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (currentTags.contains(tagName)) {
                            screenModel.removeBookmarkTag(bookmark, tagName)
                            scope.launch { snackbarManager.showSnackbar("Removed tag '$tagName'") }
                        } else {
                            screenModel.addBookmarkTag(bookmark, tagName)
                            scope.launch { snackbarManager.showSnackbar("Added tag '$tagName'") }
                        }
                    }
                }
                SwipeAction.ADD_TO_LIST -> {
                    val listId = config?.listId
                    val listName = config?.listName ?: "list"
                    if (listId != null) {
                        val bookmarkListIds = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (bookmarkListIds.contains(listId)) {
                            screenModel.removeBookmarkFromList(bookmark, listId)
                            scope.launch { snackbarManager.showSnackbar("Removed from '$listName'") }
                        } else {
                            screenModel.moveBookmarkToList(bookmark, listId)
                            scope.launch { snackbarManager.showSnackbar("Added to '$listName'") }
                        }
                    }
                }
                SwipeAction.NONE -> {}
            }
        }

        // Rename list dialog (shared between modes)
        renameListTarget?.let { (listId, initialName, initialIcon) ->
            RenameListDialog(
                initialName = initialName,
                initialIcon = initialIcon ?: "",
                onDismiss = { renameListTarget = null },
                onConfirm = { newName, newIcon ->
                    screenModel.renameList(listId, newName, newIcon.ifBlank { null })
                    renameListTarget = null
                }
            )
        }

        // Scaffold content shared between compact and expanded modes
        @Composable
        fun MainScaffoldContent(isExpandedLayout: Boolean) {
            Scaffold(
                modifier = Modifier.fillMaxSize().onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.keyboardKey == Key.Escape && isSearchActive) {
                        isSearchActive = false
                        screenModel.clearSearch()
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
                        isAutoOffline = isAutoOffline,
                        onMenuClick = {
                            if (isExpandedLayout) {
                                isDrawerVisible = !isDrawerVisible
                            } else {
                                scope.launch { drawerState.open() }
                            }
                        },
                        onFilterClick = { showFilterDialog = true },
                        onRefreshClick = { screenModel.syncBookmarks() },
                        onOfflineBadgeClick = {
                            navigator.push(com.karakept.app.ui.screens.settings.SyncDataSettingsScreen(highlightOfflineMode = true))
                        },
                        isDesktop = isDesktop,
                        isExpandedLayout = isExpandedLayout,
                        isDrawerVisible = isDrawerVisible,
                        hasActiveFilter = hasActiveFilter,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchClick = { isSearchActive = true },
                        onSearchQueryChange = { screenModel.updateSearchQuery(it) },
                        onSearchClose = {
                            isSearchActive = false
                            screenModel.clearSearch()
                        },
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
                        onBatchSetTags = { showBatchTagEditor = true },
                        onBatchMoveToList = { showBatchListPicker = true },
                        onBatchDelete = { showBatchDeleteConfirm = true }
                    )
                },
                floatingActionButton = {
                    if (!isDesktop && !offlineMode && !isAutoOffline && !isSelectionMode) {
                        FloatingActionButton(
                            onClick = { showAddBookmarkDialog = true }
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
                        layoutType = effectiveLayoutType,
                        swipeLeftAction = swipeLeftAction,
                        swipeRightAction = swipeRightAction,
                        swipeLeftConfig = customSwipeActionConfigs.find { it.id == swipeLeftConfigId },
                        swipeRightConfig = customSwipeActionConfigs.find { it.id == swipeRightConfigId },
                        dimReadBookmarks = effectiveDimReadBookmarks,
                        showReadingTimeBadge = effectiveShowReadingTimeBadge,
                        showReadingProgress = trackReadingProgress,
                        showTags = effectiveShowTags,
                        showDate = effectiveShowDate,
                        dateDisplayMode = effectiveDateDisplayMode,
                        thumbnailSide = effectiveThumbnailSide,
                        showFavicon = effectiveShowFavicon,
                        thumbnailSize = effectiveThumbnailSize,
                        metadataPosition = effectiveMetadataPosition,
                        tagsScrollable = effectiveTagsScrollable,
                        quickActionPosition = effectiveQuickActionPosition,
                        offlineMode = offlineMode || isAutoOffline,
                        pendingBookmarkRemoteIds = pendingBookmarkRemoteIds,
                        isSelectionMode = isSelectionMode,
                        selectedBookmarkIds = selectedBookmarkIds,
                        activeBookmarkId = if (isExpandedLayout) selectedBookmarkId else null,
                        onBookmarkSelectionToggle = { bookmark ->
                            screenModel.toggleBookmarkSelection(bookmark)
                        },
                        listState = listState,
                        isDesktop = isDesktop,
                        pullRefreshState = pullRefreshState,
                        onBookmarkClick = { bookmark ->
                            val idx = bookmarks.indexOfFirst { it.remoteId == bookmark.remoteId }
                            if (idx >= 0) screenModel.trackLastClickedIndex(idx)
                            if (isExpandedLayout) {
                                selectedBookmarkId = bookmark.localId
                                scrollToHighlightId = null
                                activeHighlightId = null
                            } else {
                                navigator.push(BookmarkViewerScreen(bookmark.localId))
                            }
                        },
                        onBookmarkLongClick = { bookmark ->
                            if (isSelectionMode) {
                                screenModel.toggleBookmarkSelection(bookmark)
                            } else if (!isDesktop) {
                                selectedBookmarkForActions = bookmark
                            }
                        },
                        serverUrl = servers.firstOrNull()?.url,
                        onSwipeAction = handleSwipeAction,
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
                                is BookmarkAction.MoveToList, is BookmarkAction.UpdateTags -> {
                                    selectedBookmarkForActions = bookmark
                                }
                            }
                        } else null
                    )
                }
            }
        }

        // Adaptive layout: choose between compact (modal drawer) and expanded (3-column) mode
        var isExpandedLayout by remember { mutableStateOf(false) }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            isExpandedLayout = maxWidth >= 840.dp

            if (isExpandedLayout) {
                MainScreenExpandedLayout(
                    maxWidth = maxWidth,
                    lists = lists,
                    listCounts = listCounts,
                    expandedLists = expandedLists,
                    currentFilter = currentFilter,
                    topTagsWithCounts = topTagsWithCounts,
                    allAvailableTags = allAvailableTags,
                    offlineMode = offlineMode,
                    isAutoOffline = isAutoOffline,
                    isDrawerVisible = isDrawerVisible,
                    onDrawerVisibilityChanged = { isDrawerVisible = it },
                    isReaderFullscreen = isReaderFullscreen,
                    onReaderFullscreenChanged = { isReaderFullscreen = it },
                    selectedBookmarkId = selectedBookmarkId,
                    onSelectedBookmarkIdChanged = { selectedBookmarkId = it },
                    showHighlights = showHighlights,
                    onShowHighlightsChanged = { showHighlights = it },
                    scrollToHighlightId = scrollToHighlightId,
                    onScrollToHighlightIdChanged = { scrollToHighlightId = it },
                    activeHighlightId = activeHighlightId,
                    onActiveHighlightIdChanged = { activeHighlightId = it },
                    showFilterDialog = showFilterDialog,
                    onShowFilterDialogChanged = { showFilterDialog = it },
                    showAddBookmarkDialog = showAddBookmarkDialog,
                    onShowAddBookmarkDialogChanged = { showAddBookmarkDialog = it },
                    renameListTarget = renameListTarget,
                    onRenameListTargetChanged = { renameListTarget = it },
                    screenModel = screenModel,
                    navigateTo = { screen -> navigator.push(screen) },
                    isDesktop = isDesktop,
                    scaffoldContent = { isExpanded -> MainScaffoldContent(isExpanded) }
                )
            } else {
                // Compact: modal drawer + full-screen navigation
                MainScreenDrawer(
                    drawerState = drawerState,
                    lists = lists,
                    listCounts = listCounts,
                    expandedLists = expandedLists,
                    currentFilter = currentFilter,
                    onFilterApply = drawerFilterApply,
                    onClearFilter = drawerClearFilter,
                    onToggleListExpanded = drawerToggleListExpanded,
                    onMarkAllAsRead = drawerMarkAllAsRead,
                    onRenameList = drawerRenameList,
                    onNavigateToListSettings = drawerNavigateToListSettings,
                    onSetAsDefault = drawerSetAsDefault,
                    onSetAsDefaultType = drawerSetAsDefaultType,
                    onNavigateToSettings = drawerNavigateToSettings,
                    onNavigateToHighlights = drawerNavigateToHighlights
                ) {
                    MainScaffoldContent(isExpandedLayout = false)
                }
            }
        }

        // Back Handlers
        com.karakept.app.ui.components.BackHandler(enabled = isReaderFullscreen) {
            isReaderFullscreen = false
        }
        com.karakept.app.ui.components.BackHandler(enabled = isSelectionMode) {
            screenModel.clearSelection()
        }
        com.karakept.app.ui.components.BackHandler(enabled = isSearchActive) {
            isSearchActive = false
            screenModel.clearSearch()
        }
        com.karakept.app.ui.components.BackHandler(enabled = showFilterDialog) {
            showFilterDialog = false
        }
        com.karakept.app.ui.components.BackHandler(
            enabled = tagFilterSourceBookmarkId != null && !showFilterDialog
        ) {
            val sourceId = screenModel.consumeTagFilterSource()
            screenModel.clearFilter()
            if (sourceId != null) {
                navigator.push(BookmarkViewerScreen(sourceId))
            }
        }

        // Filter overlay (compact layout only)
        if (!isExpandedLayout) {
            MainScreenFilterOverlay(
                showFilterDialog = showFilterDialog,
                onDismissFilter = { showFilterDialog = false },
                currentFilter = currentFilter,
                topTagsWithCounts = topTagsWithCounts,
                allAvailableTags = allAvailableTags,
                lists = lists,
                onFilterChange = { filter -> screenModel.applyFilter(filter) },
                onFilterReset = { screenModel.applyFilter(FilterConfig()) }
            )
        }

        // Bookmark Actions Menu
        MainScreenBookmarkActionsMenu(
            bookmark = selectedBookmarkForActions,
            lists = lists,
            allAvailableTags = allAvailableTags,
            screenModel = screenModel,
            snackbarManager = snackbarManager,
            scope = scope,
            uriHandler = uriHandler,
            onDismiss = { selectedBookmarkForActions = null }
        )

        // Add Bookmark Dialog
        if (showAddBookmarkDialog) {
            MainScreenAddBookmarkDialog(
                onConfirm = { url ->
                    showAddBookmarkDialog = false
                    screenModel.createBookmark(url)
                    scope.launch { snackbarManager.showSnackbar("Adding bookmark...") }
                },
                onDismiss = { showAddBookmarkDialog = false }
            )
        }

        // Batch dialogs
        if (showBatchDeleteConfirm) {
            BatchDeleteConfirmDialog(
                selectedCount = selectedBookmarkIds.size,
                onConfirm = {
                    screenModel.batchDelete()
                    showBatchDeleteConfirm = false
                },
                onDismiss = { showBatchDeleteConfirm = false }
            )
        }

        if (showBatchListPicker) {
            BatchListPickerDialog(
                lists = lists,
                onListSelected = { listId ->
                    screenModel.batchMoveToList(listId)
                    showBatchListPicker = false
                },
                onDismiss = { showBatchListPicker = false }
            )
        }

        if (showBatchTagEditor) {
            BatchTagEditorDialog(
                selectedBookmarkIds = selectedBookmarkIds,
                bookmarks = bookmarks,
                allAvailableTags = allAvailableTags,
                onTagsUpdated = { newTags ->
                    screenModel.batchSetTags(newTags)
                    showBatchTagEditor = false
                },
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
                is SnackbarEvent.Message -> {
                    snackbarHostState.showSnackbar(
                        message = event.text,
                        duration = event.duration
                    )
                }
                is SnackbarEvent.MessageWithUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text,
                        actionLabel = "Undo",
                        duration = event.duration
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        scope.launch { event.onUndo() }
                    }
                }
                is SnackbarEvent.MessageWithAction -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text,
                        actionLabel = event.actionLabel,
                        duration = event.duration
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        scope.launch { event.onAction() }
                    }
                }
            }
        }
    }

    return snackbarHostState
}
