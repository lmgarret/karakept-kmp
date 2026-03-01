package com.karakept.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
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
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.AddBookmarkDialog
import com.karakept.app.ui.components.FilterBottomPanel
import com.karakept.app.ui.components.BookmarkActionsMenu
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.screens.main.BookmarkListContent
import com.karakept.app.ui.screens.main.MainScreenDrawer
import com.karakept.app.ui.screens.main.MainScreenTopBar
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import getPlatform
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.SnackbarEvent
import com.karakept.app.ui.screens.settings.PerListSettingsScreen
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
        val currentFilter by screenModel.currentFilter.collectAsState()
        val tagFilterSourceBookmarkId by screenModel.tagFilterSourceBookmarkId.collectAsState()
        val offlineMode by settingsScreenModel.offlineMode.collectAsState()
        val isAutoOffline by settingsScreenModel.isAutoOffline.collectAsState()
        val showReadingTimeBadge by settingsScreenModel.showReadingTimeBadge.collectAsState()
        val trackReadingProgress by settingsScreenModel.trackReadingProgress.collectAsState()
        val showTags by settingsScreenModel.showTags.collectAsState()
        val swipeLeftAction by screenModel.swipeLeftAction.collectAsState()
        val swipeRightAction by screenModel.swipeRightAction.collectAsState()
        val customSwipeActionConfigs by screenModel.customSwipeActionConfigs.collectAsState()
        val swipeLeftConfigId by screenModel.swipeLeftConfigId.collectAsState()
        val swipeRightConfigId by screenModel.swipeRightConfigId.collectAsState()
        val dimReadBookmarks by screenModel.dimReadBookmarks.collectAsState()
        val expandedLists by screenModel.expandedLists.collectAsState()
        val listCounts by screenModel.listCounts.collectAsState()
        val currentListScrollAction by screenModel.currentListScrollAction.collectAsState()
        val currentListScrollActionConfig by screenModel.currentListScrollActionConfig.collectAsState()

        val searchQuery by screenModel.searchQuery.collectAsState()

        val hasActiveFilter = currentFilter.tags.isNotEmpty() ||
            currentFilter.lists.isNotEmpty() ||
            currentFilter.status != com.karakept.app.data.model.FilterStatus.ALL

        var showFilterDialog by remember { mutableStateOf(false) }
        var showAddBookmarkDialog by remember { mutableStateOf(false) }
        // Rename list dialog state: Triple(listId, listName, currentIcon)
        var renameListTarget by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
        val pendingBookmarkRemoteIds by screenModel.pendingBookmarkRemoteIds.collectAsState()
        var isSearchActive by remember { mutableStateOf(false) }
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

        // Scroll-triggered action: when a bookmark scrolls off screen, apply the active list's scroll action
        LaunchedEffect(currentListScrollAction, currentListScrollActionConfig) {
            if (currentListScrollAction != SwipeAction.NONE) {
                var lastFirstVisibleIndex = listState.firstVisibleItemIndex
                snapshotFlow { listState.firstVisibleItemIndex }.collect { newIndex ->
                    if (newIndex > lastFirstVisibleIndex) {
                        // Items from lastFirstVisibleIndex to newIndex - 1 have scrolled off screen
                        for (i in lastFirstVisibleIndex until newIndex) {
                            val scrolledBookmark = bookmarks.getOrNull(i) ?: continue
                            when (currentListScrollAction) {
                                SwipeAction.MARK_READ -> {
                                    if (!scrolledBookmark.isRead) {
                                        screenModel.toggleBookmarkRead(scrolledBookmark)
                                    }
                                }
                                SwipeAction.ARCHIVE -> {
                                    if (!scrolledBookmark.isArchived) {
                                        screenModel.toggleBookmarkArchive(scrolledBookmark)
                                    }
                                }
                                SwipeAction.FAVOURITE -> {
                                    screenModel.toggleBookmarkFavorite(scrolledBookmark)
                                }
                                SwipeAction.ADD_TAG -> {
                                    val tagName = currentListScrollActionConfig?.tagName
                                    if (tagName != null) {
                                        val currentTags = scrolledBookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                        if (!currentTags.contains(tagName)) {
                                            screenModel.addBookmarkTag(scrolledBookmark, tagName)
                                        }
                                    }
                                }
                                SwipeAction.ADD_TO_LIST -> {
                                    val listId = currentListScrollActionConfig?.listId
                                    if (listId != null) {
                                        val bookmarkListIds = scrolledBookmark.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                        if (!bookmarkListIds.contains(listId)) {
                                            screenModel.moveBookmarkToList(scrolledBookmark, listId)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    lastFirstVisibleIndex = newIndex
                }
            }
        }

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

        MainScreenDrawer(
            drawerState = drawerState,
            lists = lists,
            listCounts = listCounts,
            expandedLists = expandedLists,
            currentFilter = currentFilter,
            onFilterApply = { filter ->
                screenModel.applyFilter(filter)
                scope.launch { drawerState.close() }
            },
            onClearFilter = {
                screenModel.clearFilter()
                scope.launch { drawerState.close() }
            },
            onToggleListExpanded = { listId ->
                screenModel.toggleListExpanded(listId)
            },
            onMarkAllAsRead = { listId ->
                screenModel.markAllBookmarksInListAsRead(listId)
                scope.launch { drawerState.close() }
            },
            onRenameList = { listId, listName ->
                val targetList = lists.find { it.id == listId }
                renameListTarget = Triple(listId, listName, targetList?.icon)
                scope.launch { drawerState.close() }
            },
            onNavigateToListSettings = { listId, listName ->
                navigator.push(PerListSettingsScreen(listId, listName))
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
            // Rename list dialog
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
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onFilterClick = { showFilterDialog = true },
                        onRefreshClick = { screenModel.syncBookmarks() },
                        onOfflineBadgeClick = {
                            // Navigate to server settings and highlight the offline mode row
                            navigator.push(com.karakept.app.ui.screens.settings.ServerSettingsScreen(highlightOfflineMode = true))
                        },
                        isDesktop = isDesktop,
                        hasActiveFilter = hasActiveFilter,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchClick = { isSearchActive = true },
                        onSearchQueryChange = { screenModel.updateSearchQuery(it) },
                        onSearchClose = {
                            isSearchActive = false
                            screenModel.clearSearch()
                        }
                    )
                },
                floatingActionButton = {
                    if (!offlineMode && !isAutoOffline) {
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
                        layoutType = layoutType,
                        swipeLeftAction = swipeLeftAction,
                        swipeRightAction = swipeRightAction,
                        swipeLeftConfig = customSwipeActionConfigs.find { it.id == swipeLeftConfigId },
                        swipeRightConfig = customSwipeActionConfigs.find { it.id == swipeRightConfigId },
                        dimReadBookmarks = dimReadBookmarks,
                        showReadingTimeBadge = showReadingTimeBadge,
                        showReadingProgress = trackReadingProgress,
                        showTags = showTags,
                        offlineMode = offlineMode || isAutoOffline,
                        pendingBookmarkRemoteIds = pendingBookmarkRemoteIds,
                        listState = listState,
                        pullRefreshState = pullRefreshState,
                        onBookmarkClick = { bookmark ->
                            navigator.push(BookmarkViewerScreen(bookmark.localId))
                        },
                        onBookmarkLongClick = { bookmark ->
                            selectedBookmarkForActions = bookmark
                        },
                        serverUrl = servers.firstOrNull()?.url,
                        onSwipeAction = { bookmark, action, config ->
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
                                SwipeAction.ADD_TAG -> {
                                    val tagName = config?.tagName
                                    if (tagName != null) {
                                        val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                        if (currentTags.contains(tagName)) {
                                            screenModel.removeBookmarkTag(bookmark, tagName)
                                            scope.launch {
                                                snackbarManager.showSnackbar("Removed tag '$tagName'")
                                            }
                                        } else {
                                            screenModel.addBookmarkTag(bookmark, tagName)
                                            scope.launch {
                                                snackbarManager.showSnackbar("Added tag '$tagName'")
                                            }
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
                                            scope.launch {
                                                snackbarManager.showSnackbar("Removed from '$listName'")
                                            }
                                        } else {
                                            screenModel.moveBookmarkToList(bookmark, listId)
                                            scope.launch {
                                                snackbarManager.showSnackbar("Added to '$listName'")
                                            }
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

        // Back Handler for search
        com.karakept.app.ui.components.BackHandler(enabled = isSearchActive) {
            isSearchActive = false
            screenModel.clearSearch()
        }

        // Back Handler for filter panel
        com.karakept.app.ui.components.BackHandler(enabled = showFilterDialog) {
            showFilterDialog = false
        }

        // Back Handler: when we arrived here from a tag click in a BookmarkViewerScreen,
        // pressing back returns to that viewer (and clears the tag filter).
        com.karakept.app.ui.components.BackHandler(
            enabled = tagFilterSourceBookmarkId != null && !showFilterDialog
        ) {
            val sourceId = screenModel.consumeTagFilterSource()
            screenModel.clearFilter()
            if (sourceId != null) {
                navigator.push(BookmarkViewerScreen(sourceId))
            }
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
                onReset = {
                    screenModel.applyFilter(FilterConfig())
                }
            )
        }

        // Add Bookmark Dialog
        if (showAddBookmarkDialog) {
            AddBookmarkDialog(
                onConfirm = { url ->
                    showAddBookmarkDialog = false
                    screenModel.createBookmark(url)
                    scope.launch { snackbarManager.showSnackbar("Adding bookmark…") }
                },
                onDismiss = { showAddBookmarkDialog = false }
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

@Composable
private fun RenameListDialog(
    initialName: String,
    initialIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Rename list") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { androidx.compose.material3.Text("List name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { androidx.compose.material3.Text("Icon (emoji)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), icon.trim()) },
                enabled = name.isNotBlank()
            ) {
                androidx.compose.material3.Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Cancel")
            }
        }
    )
}
