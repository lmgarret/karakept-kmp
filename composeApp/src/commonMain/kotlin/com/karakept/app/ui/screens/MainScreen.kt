package com.karakept.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.FilterBottomPanel
import com.karakept.app.ui.components.BookmarkActionsMenu
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.screens.main.BookmarkListContent
import com.karakept.app.ui.screens.main.MainScreenDrawer
import com.karakept.app.ui.screens.main.MainScreenTopBar
import kotlinx.coroutines.launch
import getPlatform
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.SnackbarEvent
import org.koin.compose.koinInject

object MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
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
        val savedFilters by screenModel.savedFilters.collectAsState()
        val currentFilter by screenModel.currentFilter.collectAsState()
        val offlineMode by settingsScreenModel.offlineMode.collectAsState()
        val isAutoOffline by settingsScreenModel.isAutoOffline.collectAsState()
        val showReadingTimeBadge by settingsScreenModel.showReadingTimeBadge.collectAsState()
        val showTags by settingsScreenModel.showTags.collectAsState()
        val swipeLeftAction by screenModel.swipeLeftAction.collectAsState()
        val swipeRightAction by screenModel.swipeRightAction.collectAsState()
        val dimReadBookmarks by screenModel.dimReadBookmarks.collectAsState()
        val expandedLists by screenModel.expandedLists.collectAsState()
        val listCounts by screenModel.listCounts.collectAsState()

        var showFilterDialog by remember { mutableStateOf(false) }
        var selectedBookmarkForActions by remember { mutableStateOf<com.karakept.app.data.local.entity.BookmarkEntity?>(null) }
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
                            duration = androidx.compose.material3.SnackbarDuration.Short
                        )

                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
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

        val allBookmarks by screenModel.allBookmarks.collectAsState()

        // Extract all unique tags from ALL bookmarks for filter dialog
        val allAvailableTags = remember(allBookmarks) {
            allBookmarks.flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }.distinct().sortedBy { it.lowercase() }
        }

        // Get top 10 most used tags with counts
        val topTagsWithCounts = remember(allBookmarks) {
            allBookmarks
                .flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(10)
                .map { "${it.key} (${it.value})" }
        }

        MainScreenDrawer(
            drawerState = drawerState,
            lists = lists,
            listCounts = listCounts,
            savedFilters = savedFilters,
            expandedLists = expandedLists,
            currentFilter = currentFilter,
            onFilterApply = { filter ->
                screenModel.applyFilter(filter)
                scope.launch { drawerState.close() }
            },
            onSavedFilterApply = { savedFilter ->
                screenModel.applySavedFilter(savedFilter)
                scope.launch { drawerState.close() }
            },
            onClearFilter = {
                screenModel.clearFilter()
                scope.launch { drawerState.close() }
            },
            onToggleListExpanded = { listId ->
                screenModel.toggleListExpanded(listId)
            },
            onNavigateToFilterManagement = {
                navigator.push(FilterManagementScreen())
                scope.launch { drawerState.close() }
            },
            onNavigateToSettings = {
                navigator.push(SettingsScreen())
                scope.launch { drawerState.close() }
            },
            onNavigateToHighlights = {
                navigator.push(HighlightsScreen())
                scope.launch { drawerState.close() }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize().onKeyEvent { keyEvent ->
                    if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
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
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onFilterClick = { showFilterDialog = true },
                        onRefreshClick = { screenModel.syncBookmarks() },
                        onOfflineBadgeClick = {
                            // Navigate to server settings and highlight the offline mode row
                            navigator.push(com.karakept.app.ui.screens.settings.ServerSettingsScreen(highlightOfflineMode = true))
                        },
                        isDesktop = isDesktop
                    )
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
                        isLoadingMore = isLoadingMore,
                        hasMoreItems = hasMoreItems,
                        layoutType = layoutType,
                        swipeLeftAction = swipeLeftAction,
                        swipeRightAction = swipeRightAction,
                        dimReadBookmarks = dimReadBookmarks,
                        showReadingTimeBadge = showReadingTimeBadge,
                        showTags = showTags,
                        offlineMode = offlineMode || isAutoOffline,
                        listState = listState,
                        pullRefreshState = pullRefreshState,
                        onBookmarkClick = { bookmark ->
                            navigator.push(BookmarkViewerScreen(bookmark.localId))
                        },
                        onBookmarkLongClick = { bookmark ->
                            selectedBookmarkForActions = bookmark
                        },
                        serverUrl = servers.firstOrNull()?.url,
                        onSwipeAction = { bookmark, action ->
                            when (action) {
                                SwipeAction.ARCHIVE -> {
                                    screenModel.toggleBookmarkArchive(bookmark)
                                }
                                SwipeAction.MARK_READ -> {
                                    screenModel.toggleBookmarkRead(bookmark)
                                }
                                SwipeAction.FAVOURITE -> {
                                    screenModel.toggleBookmarkFavorite(bookmark)
                                }
                                SwipeAction.DELETE -> selectedBookmarkForActions = bookmark
                                SwipeAction.SHARE -> {
                                    com.karakept.app.utils.ShareUtils.shareText(bookmark.url, bookmark.title)
                                    scope.launch {
                                        snackbarManager.showSnackbar("Shared")
                                    }
                                }
                                SwipeAction.OPEN_IN_BROWSER -> {
                                    try {
                                        uriHandler.openUri(bookmark.url)
                                        scope.launch {
                                            snackbarManager.showSnackbar("Opening in browser")
                                        }
                                    } catch (e: Exception) {
                                        scope.launch {
                                            snackbarManager.showSnackbar("Could not open link")
                                        }
                                    }
                                }
                                SwipeAction.NONE -> {}
                            }
                        },
                        onRefresh = { if (!offlineMode) screenModel.syncBookmarks() },
                        onLoadMore = { screenModel.loadNextPage() }
                    )
                }
            }
        }

        // Back Handler for filter panel
        com.karakept.app.ui.components.BackHandler(enabled = showFilterDialog) {
            showFilterDialog = false
        }

        // Scrim for Filter Panel
        androidx.compose.animation.AnimatedVisibility(
            visible = showFilterDialog,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        showFilterDialog = false
                    }
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            FilterBottomPanel(
                visible = showFilterDialog,
                currentFilter = currentFilter,
                availableTags = topTagsWithCounts,
                allTags = allAvailableTags,
                availableLists = lists,
                onDismiss = { showFilterDialog = false },
                onFilterChange = { filter ->
                    screenModel.applyFilter(filter)
                },
                onSaveFilter = { name, icon, color, isDefault ->
                    screenModel.saveFilter(name, icon = icon, color = color, isDefault = isDefault)
                },
                onReset = {
                    screenModel.applyFilter(FilterConfig())
                }
            )
        }

        // Bookmark Actions Menu
        if (selectedBookmarkForActions != null) {
            BookmarkActionsMenu(
                bookmark = selectedBookmarkForActions!!,
                availableLists = lists,
                onAction = { action ->
                    when (action) {
                        is BookmarkAction.ToggleArchive -> {
                            screenModel.toggleBookmarkArchive(selectedBookmarkForActions!!)
                        }
                        is BookmarkAction.ToggleFavorite -> {
                            screenModel.toggleBookmarkFavorite(selectedBookmarkForActions!!)
                        }
                        is BookmarkAction.ToggleRead -> {
                            screenModel.toggleBookmarkRead(selectedBookmarkForActions!!)
                        }
                        is BookmarkAction.MoveToList -> {
                            screenModel.moveBookmarkToList(selectedBookmarkForActions!!, action.listId)
                        }
                        is BookmarkAction.UpdateTags -> {
                            screenModel.updateBookmarkTags(selectedBookmarkForActions!!, action.tags)
                        }
                        is BookmarkAction.Delete -> {
                            screenModel.deleteBookmark(selectedBookmarkForActions!!)
                        }
                        is BookmarkAction.Share -> {
                            com.karakept.app.utils.ShareUtils.shareText(selectedBookmarkForActions!!.url, selectedBookmarkForActions!!.title)
                        }
                        is BookmarkAction.OpenInBrowser -> {
                            try {
                                uriHandler.openUri(selectedBookmarkForActions!!.url)
                                scope.launch {
                                    snackbarManager.showSnackbar("Opening in browser")
                                }
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarManager.showSnackbar("Could not open link")
                                }
                            }
                        }
                        is BookmarkAction.Refresh -> {
                            screenModel.refreshBookmark(selectedBookmarkForActions!!)
                        }
                    }
                },
                onDismiss = { selectedBookmarkForActions = null }
            )
        }
    }
}

@Composable
fun rememberSnackbarHostState(manager: ActionSnackbarManager): androidx.compose.material3.SnackbarHostState {
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
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
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        scope.launch { event.onUndo() }
                    }
                }
            }
        }
    }

    return snackbarHostState
}
