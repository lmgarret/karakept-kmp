package com.karakept.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
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

class MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<MainScreenModel>()
        val settingsScreenModel = koinScreenModel<SettingsScreenModel>()
        val lists by screenModel.lists.collectAsState()
        val bookmarks by screenModel.bookmarks.collectAsState()
        val layoutType by settingsScreenModel.layoutType.collectAsState()
        val isSyncing by screenModel.isSyncing.collectAsState()
        val syncProgress by screenModel.syncProgress.collectAsState()
        val isLoadingMore by screenModel.isLoadingMore.collectAsState()
        val hasMoreItems by screenModel.hasMoreItems.collectAsState()
        val savedFilters by screenModel.savedFilters.collectAsState()
        val currentFilter by screenModel.currentFilter.collectAsState()
        val offlineMode by settingsScreenModel.offlineMode.collectAsState()
        val showReadingTimeBadge by settingsScreenModel.showReadingTimeBadge.collectAsState()
        val showTags by settingsScreenModel.showTags.collectAsState()
        val swipeLeftAction by screenModel.swipeLeftAction.collectAsState()
        val swipeRightAction by screenModel.swipeRightAction.collectAsState()
        val dimReadBookmarks by screenModel.dimReadBookmarks.collectAsState()
        val expandedLists by screenModel.expandedLists.collectAsState()
        val listCounts by screenModel.listCounts.collectAsState()

        var showFilterDialog by remember { mutableStateOf(false) }
        var selectedBookmarkForActions by remember { mutableStateOf<com.karakept.app.data.local.entity.BookmarkEntity?>(null) }
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

        val listState = rememberLazyListState()
        val isDesktop = remember { getPlatform().name.contains("Java") }
        val uriHandler = LocalUriHandler.current
        val scrollToTopEvent by screenModel.scrollToTopEvent.collectAsState()

        // Auto-scroll to top when sync completes and new bookmarks arrive
        LaunchedEffect(scrollToTopEvent) {
            println("MainScreen: scrollToTopEvent changed to $scrollToTopEvent, bookmarks.size=${bookmarks.size}")
            // Only scroll if the event changed (sync completed) and we have bookmarks
            if (scrollToTopEvent > 0 && bookmarks.isNotEmpty()) {
                println("MainScreen: Scrolling to top...")
                // Small delay to ensure bookmarks are rendered
                kotlinx.coroutines.delay(100)
                println("MainScreen: After delay, firstVisibleItemIndex=${listState.firstVisibleItemIndex}")
                try {
                    listState.animateScrollToItem(0)
                    println("MainScreen: Scrolled to top successfully, firstVisibleItemIndex=${listState.firstVisibleItemIndex}")
                } catch (e: Exception) {
                    println("MainScreen: Failed to scroll: ${e.message}")
                }
            }
        }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isSyncing,
            onRefresh = { if (!offlineMode) screenModel.syncBookmarks() }
        )

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

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
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onFilterClick = { showFilterDialog = true },
                        onRefreshClick = { screenModel.syncBookmarks() },
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
                        listState = listState,
                        pullRefreshState = pullRefreshState,
                        onBookmarkClick = { bookmark ->
                            navigator.push(BookmarkViewerScreen(bookmark.localId))
                        },
                        onBookmarkLongClick = { bookmark ->
                            selectedBookmarkForActions = bookmark
                        },
                        onSwipeAction = { bookmark, action ->
                            when (action) {
                                SwipeAction.ARCHIVE -> {
                                    screenModel.toggleBookmarkArchive(bookmark, onActionComplete = { message ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = message,
                                                duration = androidx.compose.material3.SnackbarDuration.Short
                                            )
                                        }
                                    })
                                }
                                SwipeAction.MARK_READ -> {
                                    screenModel.toggleBookmarkRead(bookmark, onActionComplete = { message ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = message,
                                                duration = androidx.compose.material3.SnackbarDuration.Short
                                            )
                                        }
                                    })
                                }
                                SwipeAction.FAVOURITE -> {
                                    screenModel.toggleBookmarkFavorite(bookmark, onActionComplete = { message ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = message,
                                                duration = androidx.compose.material3.SnackbarDuration.Short
                                            )
                                        }
                                    })
                                }
                                SwipeAction.DELETE -> selectedBookmarkForActions = bookmark
                                SwipeAction.SHARE -> {
                                    com.karakept.app.utils.ShareUtils.shareText(bookmark.url, bookmark.title)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Shared",
                                            duration = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                    }
                                }
                                SwipeAction.OPEN_IN_BROWSER -> {
                                    try {
                                        uriHandler.openUri(bookmark.url)
                                    } catch (e: Exception) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Could not open link")
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
        androidx.activity.compose.BackHandler(enabled = showFilterDialog) {
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
                            screenModel.toggleBookmarkArchive(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.ToggleFavorite -> {
                            screenModel.toggleBookmarkFavorite(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.ToggleRead -> {
                            screenModel.toggleBookmarkRead(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.MoveToList -> {
                            screenModel.moveBookmarkToList(selectedBookmarkForActions!!, action.listId)
                        }
                        is BookmarkAction.UpdateTags -> {
                            screenModel.updateBookmarkTags(selectedBookmarkForActions!!, action.tags)
                        }
                        is BookmarkAction.Delete -> {
                            screenModel.deleteBookmark(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.Share -> {
                            com.karakept.app.utils.ShareUtils.shareText(selectedBookmarkForActions!!.url, selectedBookmarkForActions!!.title)
                        }
                        is BookmarkAction.OpenInBrowser -> {
                            // Open in browser - would need platform-specific implementation
                        }
                    }
                },
                onDismiss = { selectedBookmarkForActions = null }
            )
        }
    }
}
