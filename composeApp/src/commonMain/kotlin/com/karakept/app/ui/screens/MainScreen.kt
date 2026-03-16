package com.karakept.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.AddBookmarkDialog
import com.karakept.app.ui.components.DraggableDivider
import com.karakept.app.ui.components.FilterBottomPanel
import com.karakept.app.ui.components.BookmarkActionsMenu
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.components.ListPickerDialog
import com.karakept.app.ui.components.TagEditorDialog
import com.karakept.app.ui.screens.main.BookmarkListContent
import com.karakept.app.ui.screens.main.DrawerContent
import com.karakept.app.ui.screens.main.HighlightsListContent
import com.karakept.app.ui.screens.main.MainScreenDrawer
import com.karakept.app.ui.screens.main.MainScreenTopBar
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import com.karakept.app.data.repository.SettingsRepository
import getPlatform
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.SnackbarEvent
import com.karakept.app.ui.screens.settings.PerListSettingsScreen
import org.koin.compose.koinInject

object MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, FlowPreview::class)
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
        // Rename list dialog state: Triple(listId, listName, currentIcon)
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

        // Scroll-triggered action: apply the active list's scroll action silently (no snackbar)
        // when bookmarks scroll off the top, or when reaching the bottom of the list.
        //
        // How we distinguish real scrolling from list mutations:
        // LazyList uses stable keys (bookmark remoteIds). When items are prepended above the
        // viewport, Compose adjusts firstVisibleItemIndex so the same item stays on screen —
        // the key at the current position is unchanged, only the index grows. When the user
        // actually scrolls down, a new item becomes first-visible (different key). We track
        // the "anchor" (key + index of the first visible item) and compare it on each emission.
        //
        // Prepend at absolute top (firstVisibleItemIndex stays 0):
        // When the user is at index 0 with no scroll offset, Compose does NOT shift the index
        // on prepend — the new items appear above and the key at index 0 changes. We detect this
        // by searching the full bookmarks list for the old anchor key: if it moved to a higher
        // index, N items were prepended. We record this as newItemsUntil so the fire loop skips
        // those slots until the user explicitly scrolls past them.
        //
        // List replacement (sync / filter change):
        // MainScreenModel increments bookmarkListVersion on every resetPaginationAndLoad. When
        // the version changes we run the same old-anchor search so newly inserted items are
        // protected by newItemsUntil — preventing them from being bulk-fired before the user
        // has scrolled past them individually. processedIds persists across replacements to
        // avoid double-firing on bookmarks that survive the swap.
        LaunchedEffect(currentListScrollAction, currentListScrollActionConfig) {
            if (currentListScrollAction != SwipeAction.NONE) {
                var anchorKey: Any? = null   // key of the first visible item we're tracking
                var anchorIndex = 0          // current index of that anchor item
                var bottomReached = false
                var wasScrolling = false
                // Indices [0, newItemsUntil) contain items that appeared via prepend/sync.
                // The fire loop skips those slots so the action isn't triggered on bookmarks
                // the user hasn't explicitly scrolled past.
                var newItemsUntil = 0
                var lastSeenListVersion = bookmarkListVersion
                // Guard against double-firing: tracks remoteIds we've already acted on.
                // Cleared for items that become visible again when the user scrolls back up,
                // so a manually-unread bookmark can be re-triggered on the next scroll-down.
                // Kept across list replacements so surviving bookmarks are not re-fired by sync.
                val processedIds = mutableSetOf<Long>()

                data class ScrollSnapshot(
                    val firstIndex: Int,
                    val firstKey: Any?,
                    val lastVisibleIndex: Int,
                    val currentBookmarks: List<com.karakept.app.data.local.entity.BookmarkEntity>,
                    val isScrolling: Boolean,
                    val listVersion: Int
                )

                snapshotFlow {
                    ScrollSnapshot(
                        firstIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0,
                        firstKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key,
                        lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                        currentBookmarks = bookmarks,
                        isScrolling = listState.isScrollInProgress,
                        listVersion = bookmarkListVersion
                    )
                }.collect { snapshot ->
                    val currentBookmarks = snapshot.currentBookmarks
                    val totalBookmarks = currentBookmarks.size
                    val newFirstIndex = snapshot.firstIndex
                    val newFirstKey = snapshot.firstKey
                    val isScrolling = snapshot.isScrolling

                    // scrollJustStopped is used only for the short-list bottom case below.
                    val scrollJustStopped = wasScrolling && !isScrolling
                    wasScrolling = isScrolling

                    // List replacement detection: bookmarkListVersion is incremented on every
                    // resetPaginationAndLoad (sync, filter change, server switch). When it
                    // changes we must re-initialize the anchor AND apply the same prepend-
                    // detection logic used in the else branch below: if the old anchor survived
                    // in the new list at a higher index, new items were inserted above it and
                    // must be protected by newItemsUntil so they aren't bulk-fired before the
                    // user has scrolled past each one individually. processedIds is kept intact
                    // so bookmarks that survived the swap are never acted on twice.
                    if (snapshot.listVersion != lastSeenListVersion) {
                        lastSeenListVersion = snapshot.listVersion
                        val oldAnchorNewIndex = if (anchorKey != null) {
                            currentBookmarks.indexOfFirst { it.remoteId == anchorKey }
                        } else -1
                        if (oldAnchorNewIndex > anchorIndex) {
                            // Items were inserted above the old anchor — protect the new slots.
                            newItemsUntil = maxOf(newItemsUntil, oldAnchorNewIndex)
                        } else {
                            // Full replacement or anchor not found — reset all guards.
                            newItemsUntil = 0
                        }
                        anchorKey = newFirstKey
                        anchorIndex = newFirstIndex
                        bottomReached = false
                        return@collect
                    }

                    if (anchorKey == null) {
                        // First emission: initialise anchor without firing any actions.
                        anchorKey = newFirstKey
                        anchorIndex = newFirstIndex
                        return@collect
                    }

                    when {
                        newFirstIndex > anchorIndex -> {
                            if (newFirstKey == anchorKey) {
                                // Same item at a higher index: Compose shifted the index because
                                // items were prepended above the viewport. Protect those new slots.
                                newItemsUntil = maxOf(newItemsUntil, newFirstIndex)
                                anchorIndex = newFirstIndex
                            } else {
                                // A different item is now first-visible — the user actually
                                // scrolled down. Items [anchorIndex, newFirstIndex) left the top.
                                for (i in anchorIndex until newFirstIndex) {
                                    if (i < newItemsUntil) continue  // skip newly prepended items
                                    val scrolledBookmark = currentBookmarks.getOrNull(i) ?: continue
                                    if (scrolledBookmark.remoteId in processedIds) continue
                                    processedIds.add(scrolledBookmark.remoteId)
                                    screenModel.executeScrollAction(scrolledBookmark, currentListScrollAction, currentListScrollActionConfig)
                                }
                                anchorIndex = newFirstIndex
                                anchorKey = newFirstKey
                                bottomReached = false
                                if (anchorIndex >= newItemsUntil) newItemsUntil = 0
                            }
                        }
                        newFirstIndex < anchorIndex -> {
                            // Scrolled back up. Items [newFirstIndex, anchorIndex) are now
                            // visible again — remove them from processedIds so a bookmark that
                            // was manually marked as unread can be re-triggered on the next
                            // scroll-down.
                            for (i in newFirstIndex until anchorIndex) {
                                val bookmark = currentBookmarks.getOrNull(i) ?: continue
                                processedIds.remove(bookmark.remoteId)
                            }
                            anchorIndex = newFirstIndex
                            anchorKey = newFirstKey ?: anchorKey
                            bottomReached = false
                        }
                        else -> {
                            // firstVisibleItemIndex is unchanged. The key may have changed if
                            // items were prepended while the user was at the absolute top
                            // (index 0, zero scroll offset): Compose keeps the index at 0 and
                            // the new items slide in above, making a different key appear at 0.
                            if (newFirstKey != null && newFirstKey != anchorKey) {
                                // Search the FULL bookmarks list for the old anchor — not just
                                // visible items — so we catch prepends larger than the viewport.
                                val oldAnchorNewIndex = currentBookmarks.indexOfFirst {
                                    it.remoteId == anchorKey
                                }
                                if (oldAnchorNewIndex > anchorIndex) {
                                    // Old anchor moved down: N items were prepended. Protect
                                    // those new slots so they are skipped until intentionally
                                    // scrolled past.
                                    newItemsUntil = maxOf(newItemsUntil, oldAnchorNewIndex)
                                } else {
                                    // Old anchor not found or unchanged — unexpected state;
                                    // reset the skip guard conservatively.
                                    newItemsUntil = 0
                                }
                                anchorKey = newFirstKey
                            }
                        }
                    }

                    // When the last visible item is the last bookmark, apply the action to all
                    // remaining visible items that haven't been processed yet.
                    // For long lists: anchorIndex > 0 means the user has scrolled at least one
                    // item off the top in the current direction. This naturally resets to false
                    // when the user scrolls back to the top (anchorIndex returns to 0), preventing
                    // newly prepended items from being fired when the user hasn't scrolled.
                    // For short lists where no item ever leaves the top: fires when the user
                    // finishes a scroll gesture at the bottom (scrollJustStopped).
                    val atBottom = totalBookmarks > 0 && snapshot.lastVisibleIndex >= totalBookmarks - 1
                    if (!bottomReached && atBottom && (anchorIndex > 0 || scrollJustStopped)) {
                        for (i in anchorIndex until totalBookmarks) {
                            if (i < newItemsUntil) continue
                            val scrolledBookmark = currentBookmarks.getOrNull(i) ?: continue
                            if (scrolledBookmark.remoteId in processedIds) continue
                            processedIds.add(scrolledBookmark.remoteId)
                            screenModel.executeScrollAction(scrolledBookmark, currentListScrollAction, currentListScrollActionConfig)
                        }
                        anchorIndex = totalBookmarks
                        bottomReached = true
                        if (anchorIndex >= newItemsUntil) newItemsUntil = 0
                    }
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
                            // Navigate to server settings and highlight the offline mode row
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
                            // Track last clicked index for Shift+Click range selection anchor
                            val idx = bookmarks.indexOfFirst { it.remoteId == bookmark.remoteId }
                            if (idx >= 0) screenModel.trackLastClickedIndex(idx)
                            if (isExpandedLayout) {
                                selectedBookmarkId = bookmark.localId
                            } else {
                                navigator.push(BookmarkViewerScreen(bookmark.localId))
                            }
                        },
                        onBookmarkLongClick = { bookmark ->
                            if (isSelectionMode) {
                                screenModel.toggleBookmarkSelection(bookmark)
                            } else if (!isDesktop) {
                                // On mobile, long-press shows bottom sheet
                                // On desktop, right-click context menu is used instead
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
                                // Enter selection mode with range from the last clicked
                                // bookmark (tracked outside selection mode) to this one
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
                                // Move to List and Edit Tags need sub-dialogs — open the bottom sheet
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedLayout = maxWidth >= 840.dp

            if (isExpandedLayout) {
                // Expanded: permanent drawer + bookmark list + reader pane
                // Column width state — persisted via SettingsRepository
                val layoutSettingsRepository = koinInject<SettingsRepository>()
                var drawerWidthDp by remember { mutableFloatStateOf(280f) }
                var listFraction by remember { mutableFloatStateOf(0.4f) }

                // Load persisted column widths once
                LaunchedEffect(Unit) {
                    drawerWidthDp = layoutSettingsRepository.drawerWidthDp.first()
                    listFraction = layoutSettingsRepository.listColumnFraction.first()
                }

                // Debounced persistence — write 500ms after drag stops
                LaunchedEffect(Unit) {
                    snapshotFlow { drawerWidthDp }
                        .debounce(500)
                        .collect { layoutSettingsRepository.setDrawerWidthDp(it) }
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { listFraction }
                        .debounce(500)
                        .collect { layoutSettingsRepository.setListColumnFraction(it) }
                }

                // Calculate column widths
                val dividerWidth = 12.dp // DraggableDivider hit target width (1dp visible line)
                val drawerWidth = drawerWidthDp.dp
                // Drawer includes its own divider, so only one standalone divider (between list and reader)
                val drawerTotalWidth = if (isDrawerVisible) drawerWidth + dividerWidth else 0.dp
                val remainingWidth = maxWidth - drawerTotalWidth - dividerWidth
                val minListWidth = 250.dp
                val minReaderWidth = 300.dp
                val maxListWidth = remainingWidth - minReaderWidth
                val listWidth = (remainingWidth * listFraction).coerceIn(minListWidth, maxListWidth)
                val readerWidth = remainingWidth - listWidth

                Row(modifier = Modifier.fillMaxSize()) {
                    // Drawer column + divider (collapsible as one unit to avoid color gap)
                    AnimatedVisibility(
                        visible = isDrawerVisible,
                        enter = expandHorizontally(),
                        exit = shrinkHorizontally()
                    ) {
                        Row {
                            Surface(
                                modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                DrawerContent(
                                    lists = lists,
                                    listCounts = listCounts,
                                    expandedLists = expandedLists,
                                    currentFilter = currentFilter,
                                    onFilterApply = { filter ->
                                        screenModel.applyFilter(filter)
                                        showHighlights = false
                                    },
                                    onClearFilter = {
                                        screenModel.clearFilter()
                                        showHighlights = false
                                    },
                                    onToggleListExpanded = drawerToggleListExpanded,
                                    onMarkAllAsRead = { listId -> screenModel.markAllBookmarksInListAsRead(listId) },
                                    onRenameList = { listId, listName ->
                                        val targetList = lists.find { it.id == listId }
                                        renameListTarget = Triple(listId, listName, targetList?.icon)
                                    },
                                    onNavigateToListSettings = { listId, listName ->
                                        navigator.push(PerListSettingsScreen(listId, listName))
                                    },
                                    onSetAsDefault = { listId -> screenModel.setDefaultList(listId) },
                                    onSetAsDefaultType = { type -> screenModel.setDefaultListType(type) },
                                    onNavigateToSettings = { navigator.push(SettingsScreen()) },
                                    onNavigateToHighlights = {
                                        showHighlights = true
                                        selectedBookmarkId = null
                                        scrollToHighlightId = null
                                        activeHighlightId = null
                                    },
                                    isHighlightsSelected = showHighlights,
                                    onAddBookmark = if (isDesktop && !offlineMode && !isAutoOffline) {
                                        { showAddBookmarkDialog = true }
                                    } else null
                                )
                            }
                            DraggableDivider(
                                onDrag = { delta ->
                                    drawerWidthDp = (drawerWidthDp + delta).coerceIn(200f, 400f)
                                }
                            )
                        }
                    }

                    // Middle column: bookmark list or highlights list
                    Box(modifier = Modifier.width(listWidth).fillMaxHeight()) {
                        if (showHighlights) {
                            val highlightsScreenModel = koinInject<HighlightsScreenModel>()
                            val highlightsList by highlightsScreenModel.highlights.collectAsState()
                            val isHighlightsSyncing by highlightsScreenModel.isSyncing.collectAsState()

                            LaunchedEffect(showHighlights) {
                                if (showHighlights) highlightsScreenModel.syncHighlights()
                            }

                            HighlightsListContent(
                                highlights = highlightsList,
                                isSyncing = isHighlightsSyncing,
                                activeHighlightId = activeHighlightId,
                                onHighlightClick = { highlight ->
                                    scope.launch {
                                        val bookmarkLocalId = highlightsScreenModel.getBookmarkLocalIdForHighlight(highlight)
                                        if (bookmarkLocalId != null) {
                                            selectedBookmarkId = bookmarkLocalId
                                            scrollToHighlightId = highlight.id
                                            activeHighlightId = highlight.id
                                        }
                                    }
                                },
                                onDeleteHighlight = { highlightsScreenModel.deleteHighlight(it) },
                                onBack = {
                                    showHighlights = false
                                    selectedBookmarkId = null
                                    scrollToHighlightId = null
                                    activeHighlightId = null
                                }
                            )
                        } else {
                            MainScaffoldContent(isExpandedLayout = true)
                        }
                    }

                    // Divider between list and reader (draggable)
                    DraggableDivider(
                        onDrag = { delta ->
                            // Compute fraction change directly from delta to avoid stale captures
                            val remainingDp = remainingWidth.value
                            if (remainingDp > 0f) {
                                val minFraction = minListWidth.value / remainingDp
                                val maxFraction = maxListWidth.value / remainingDp
                                listFraction = (listFraction + delta / remainingDp).coerceIn(minFraction, maxFraction)
                            }
                        }
                    )

                    // Reader pane column
                    Surface(
                        modifier = Modifier.width(readerWidth).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        val currentBookmarkId = selectedBookmarkId
                        val currentScrollToHighlightId = scrollToHighlightId
                        if (currentBookmarkId != null) {
                            // Use key() to force fresh composition when bookmark or highlight changes
                            androidx.compose.runtime.key(currentBookmarkId, currentScrollToHighlightId) {
                                val viewerScreenModel = koinInject<BookmarkViewerScreenModel>()
                                DisposableEffect(currentBookmarkId) {
                                    onDispose { viewerScreenModel.onDispose() }
                                }
                                BookmarkViewerContent(
                                    bookmarkId = currentBookmarkId,
                                    scrollToHighlightId = currentScrollToHighlightId,
                                    screenModel = viewerScreenModel,
                                    onBack = {
                                        selectedBookmarkId = null
                                        scrollToHighlightId = null
                                        activeHighlightId = null
                                    },
                                    onTagFilterApply = { tag ->
                                        screenModel.applyTagFilter(tag, currentBookmarkId)
                                        selectedBookmarkId = null
                                        scrollToHighlightId = null
                                        activeHighlightId = null
                                        showHighlights = false
                                    },
                                    isEmbedded = true
                                )
                            }
                        } else {
                            // Placeholder
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.BookmarkBorder,
                                        contentDescription = null,
                                        modifier = Modifier.padding(bottom = 16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "Select a bookmark to read",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
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

        // Back Handler for selection mode
        com.karakept.app.ui.components.BackHandler(enabled = isSelectionMode) {
            screenModel.clearSelection()
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

        // Bookmark Actions Menu (ModalBottomSheet provides its own scrim)
        if (selectedBookmarkForActions != null) {
            BookmarkActionsMenu(
                bookmark = selectedBookmarkForActions!!,
                availableLists = lists,
                availableTags = allAvailableTags,
                onAction = { action ->
                    when (action) {
                        is BookmarkAction.ToggleArchive -> screenModel.toggleBookmarkArchive(selectedBookmarkForActions!!)
                        is BookmarkAction.ToggleFavorite -> screenModel.toggleBookmarkFavorite(selectedBookmarkForActions!!)
                        is BookmarkAction.ToggleRead -> screenModel.toggleBookmarkRead(selectedBookmarkForActions!!)
                        is BookmarkAction.MoveToList -> screenModel.moveBookmarkToList(selectedBookmarkForActions!!, action.listId)
                        is BookmarkAction.UpdateTags -> screenModel.updateBookmarkTags(selectedBookmarkForActions!!, action.tags)
                        is BookmarkAction.Delete -> screenModel.deleteBookmark(selectedBookmarkForActions!!)
                        is BookmarkAction.Share -> {
                            com.karakept.app.utils.ShareUtils.shareText(selectedBookmarkForActions!!.url, selectedBookmarkForActions!!.title)
                        }
                        is BookmarkAction.OpenInBrowser -> {
                            try {
                                uriHandler.openUri(selectedBookmarkForActions!!.url)
                                scope.launch { snackbarManager.showSnackbar("Opening in browser") }
                            } catch (e: Exception) {
                                scope.launch { snackbarManager.showSnackbar("Could not open link") }
                            }
                        }
                        is BookmarkAction.Select -> screenModel.enterSelectionMode(selectedBookmarkForActions!!)
                    }
                },
                onDismiss = { selectedBookmarkForActions = null }
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

        // Batch delete confirmation
        if (showBatchDeleteConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = { androidx.compose.material3.Text("Delete ${selectedBookmarkIds.size} bookmark${if (selectedBookmarkIds.size > 1) "s" else ""}?") },
                text = { androidx.compose.material3.Text("This action cannot be undone. The selected bookmarks will be permanently deleted from the server.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            screenModel.batchDelete()
                            showBatchDeleteConfirm = false
                        }
                    ) {
                        androidx.compose.material3.Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        androidx.compose.material3.Text("Cancel")
                    }
                }
            )
        }

        // Batch move-to-list picker
        if (showBatchListPicker) {
            ListPickerDialog(
                lists = lists,
                currentListIds = emptyList(),
                onListSelected = { listId ->
                    screenModel.batchMoveToList(listId)
                    showBatchListPicker = false
                },
                onDismiss = { showBatchListPicker = false }
            )
        }

        // Batch set-tags dialog
        if (showBatchTagEditor) {
            // Pre-populate with tags shared by ALL selected bookmarks (intersection)
            val selectedBookmarks = remember(selectedBookmarkIds, bookmarks) {
                bookmarks.filter { it.remoteId in selectedBookmarkIds }
            }
            val commonTags = remember(selectedBookmarks) {
                if (selectedBookmarks.isEmpty()) emptyList()
                else {
                    val first = selectedBookmarks.first().tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    selectedBookmarks.drop(1).fold(first) { acc, bm ->
                        val bmTags = bm.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                        acc intersect bmTags
                    }.toList()
                }
            }
            TagEditorDialog(
                currentTags = commonTags,
                availableTags = allAvailableTags,
                title = "Set Tags (${selectedBookmarkIds.size} bookmarks)",
                confirmLabel = "Apply",
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
): androidx.compose.material3.SnackbarHostState {
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
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
