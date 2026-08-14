package com.karakept.app.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.karakept.app.ui.screens.markAllBookmarksInListAsRead
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setDrawerWidthDp
import com.karakept.app.data.repository.setListColumnFraction
import com.karakept.app.ui.components.DraggableDivider
import com.karakept.app.ui.components.FilterSidePanel
import com.karakept.app.ui.screens.BookmarkViewerContent
import com.karakept.app.ui.screens.BookmarkViewerScreenModel
import com.karakept.app.ui.screens.HighlightsScreenModel
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.app.ui.screens.QuickFilterCounts
import com.karakept.app.ui.screens.SettingsScreen
import com.karakept.app.ui.screens.settings.PerListSettingsContent
import com.karakept.app.ui.screens.settings.PerListSettingsScreenModel
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.ExpandedDrawerDefaultWidth
import com.karakept.app.ui.utils.coerceExpandedDrawerWidth
import org.koin.core.parameter.parametersOf
import com.karakept.api.model.KarakeepList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Three-column expanded layout for wide screens (>= 840dp):
 * drawer | bookmark list | reader pane
 */
@OptIn(FlowPreview::class)
@Composable
fun MainScreenExpandedLayout(
    maxWidth: Dp,
    lists: List<KarakeepList>,
    listCounts: Map<String, Int>,
    expandedLists: Set<String>,
    currentFilter: FilterConfig,
    topTagsWithCounts: List<String>,
    allAvailableTags: List<String>,
    offlineMode: Boolean,
    isDrawerVisible: Boolean,
    onDrawerVisibilityChanged: (Boolean) -> Unit,
    isReaderFullscreen: Boolean,
    onReaderFullscreenChanged: (Boolean) -> Unit,
    selectedBookmarkId: Long?,
    onSelectedBookmarkIdChanged: (Long?) -> Unit,
    showHighlights: Boolean,
    onShowHighlightsChanged: (Boolean) -> Unit,
    scrollToHighlightId: String?,
    onScrollToHighlightIdChanged: (String?) -> Unit,
    activeHighlightId: String?,
    onActiveHighlightIdChanged: (String?) -> Unit,
    showFilterDialog: Boolean,
    onShowFilterDialogChanged: (Boolean) -> Unit,
    showAddBookmarkDialog: Boolean,
    onShowAddBookmarkDialogChanged: (Boolean) -> Unit,
    renameListTarget: Triple<String, String, String?>?,
    onRenameListTargetChanged: (Triple<String, String, String?>?) -> Unit,
    screenModel: MainScreenModel,
    navigateTo: (androidx.navigation3.runtime.NavKey) -> Unit,
    isDesktop: Boolean,
    quickFilterCounts: QuickFilterCounts = QuickFilterCounts(),
    highlightsCount: Int = 0,
    listSyncStatuses: Map<SyncKey, ListSyncStatus> = emptyMap(),
    scaffoldContent: @Composable (isExpandedLayout: Boolean) -> Unit
) {
    // Column width state -- persisted via SettingsRepository
    val layoutSettingsRepository = koinInject<SettingsRepository>()
    var drawerWidthDp by remember { mutableFloatStateOf(ExpandedDrawerDefaultWidth.value) }
    var listFraction by remember { mutableFloatStateOf(0.4f) }

    // Load persisted column widths once
    LaunchedEffect(Unit) {
        drawerWidthDp = layoutSettingsRepository.drawerWidthDp.first()
        listFraction = layoutSettingsRepository.listColumnFraction.first()
    }

    // Debounced persistence -- write 500ms after drag stops
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
    val drawerTotalWidth = if (isDrawerVisible) drawerWidth else 0.dp
    val remainingWidth = maxWidth - drawerTotalWidth - dividerWidth - dividerWidth
    val minListWidth = 250.dp
    val minReaderWidth = 300.dp
    val maxListWidth = (remainingWidth - minReaderWidth).coerceAtLeast(minListWidth)
    val listWidth = (remainingWidth * listFraction).coerceIn(minListWidth, maxListWidth)

    // Reset fullscreen when bookmark is deselected
    LaunchedEffect(selectedBookmarkId) {
        if (selectedBookmarkId == null) onReaderFullscreenChanged(false)
    }

    // Panes slide open and shut; on e-ink that is a full-panel refresh per frame.
    val einkMode = LocalEinkMode.current
    val paneEnter = if (einkMode.animationsDisabled) EnterTransition.None else expandHorizontally()
    val paneExit = if (einkMode.animationsDisabled) ExitTransition.None else shrinkHorizontally()

    var activeListSettings by remember { mutableStateOf<Pair<String, String>?>(null) }

    val scope = rememberCoroutineScope()
    var readerSearchTrigger by remember { mutableStateOf(0) }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .onPreviewKeyEvent { keyEvent ->
            if (selectedBookmarkId != null &&
                keyEvent.type == KeyEventType.KeyDown &&
                (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                keyEvent.keyboardKey == Key.F) {
                readerSearchTrigger++
                true
            } else false
        }
    ) {
        // Drawer column (collapsible)
        AnimatedVisibility(
            visible = isDrawerVisible && !isReaderFullscreen,
            enter = paneEnter,
            exit = paneExit
        ) {
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
                        activeListSettings = null
                        screenModel.applyFilter(filter)
                        onShowHighlightsChanged(false)
                        onScrollToHighlightIdChanged(null)
                        onActiveHighlightIdChanged(null)
                    },
                    onClearFilter = {
                        activeListSettings = null
                        screenModel.clearFilter()
                        onShowHighlightsChanged(false)
                        onScrollToHighlightIdChanged(null)
                        onActiveHighlightIdChanged(null)
                    },
                    onToggleListExpanded = { listId -> screenModel.toggleListExpanded(listId) },
                    onMarkAllAsRead = { listId -> screenModel.markAllBookmarksInListAsRead(listId) },
                    onRenameList = { listId, listName ->
                        val targetList = lists.find { it.id == listId }
                        onRenameListTargetChanged(Triple(listId, listName, targetList?.icon))
                    },
                    onNavigateToListSettings = { listId, listName ->
                        activeListSettings = listId to listName
                        onShowHighlightsChanged(false)
                        onScrollToHighlightIdChanged(null)
                        onActiveHighlightIdChanged(null)
                    },
                    onSetAsDefault = { listId -> screenModel.setDefaultList(listId) },
                    onSetAsDefaultType = { type -> screenModel.setDefaultListType(type) },
                    onNavigateToSettings = { navigateTo(SettingsScreen()) },
                    onNavigateToHighlights = {
                        activeListSettings = null
                        onShowHighlightsChanged(true)
                        onSelectedBookmarkIdChanged(null)
                        onScrollToHighlightIdChanged(null)
                        onActiveHighlightIdChanged(null)
                    },
                    isHighlightsSelected = showHighlights,
                    onAddBookmark = if (isDesktop && !offlineMode) {
                        { onShowAddBookmarkDialogChanged(true) }
                    } else null,
                    quickFilterCounts = quickFilterCounts,
                    highlightsCount = highlightsCount,
                    listSyncStatuses = listSyncStatuses
                )
            }
        }

        // Divider between drawer and list (draggable).
        // Line aligned to start so it sits flush against the drawer edge.
        AnimatedVisibility(
            visible = isDrawerVisible && !isReaderFullscreen,
            enter = paneEnter,
            exit = paneExit
        ) {
            DraggableDivider(
                lineAlignment = Alignment.CenterStart,
                onDrag = { delta ->
                    drawerWidthDp = coerceExpandedDrawerWidth(drawerWidthDp + delta)
                }
            )
        }

        // Middle column: bookmark list or highlights list
        AnimatedVisibility(
            visible = !isReaderFullscreen,
            enter = paneEnter,
            exit = paneExit
        ) {
            Box(modifier = Modifier.width(listWidth).fillMaxHeight()) {
                val listSettingsTarget = activeListSettings
                if (listSettingsTarget != null) {
                    androidx.compose.runtime.key(listSettingsTarget.first) {
                        val settingsScreenModel = koinInject<PerListSettingsScreenModel>(
                            parameters = { parametersOf(listSettingsTarget.first) }
                        )
                        PerListSettingsContent(
                            listId = listSettingsTarget.first,
                            listName = listSettingsTarget.second,
                            screenModel = settingsScreenModel,
                            onBack = { activeListSettings = null },
                            onNavigateTo = { screen -> navigateTo(screen) }
                        )
                    }
                } else if (showHighlights) {
                    val highlightsScreenModel = koinInject<HighlightsScreenModel>()
                    val highlightsList by highlightsScreenModel.highlights.collectAsState()
                    val isHighlightsSyncing by highlightsScreenModel.isSyncing.collectAsState()
                    val isHighlightsLoadingMore by highlightsScreenModel.isLoadingMore.collectAsState()
                    val hasMoreHighlights by highlightsScreenModel.hasMoreItems.collectAsState()

                    LaunchedEffect(showHighlights) {
                        if (showHighlights) highlightsScreenModel.syncHighlights()
                    }

                    HighlightsListContent(
                        highlights = highlightsList,
                        isSyncing = isHighlightsSyncing,
                        isLoadingMore = isHighlightsLoadingMore,
                        hasMoreItems = hasMoreHighlights,
                        activeHighlightId = activeHighlightId,
                        onHighlightClick = { highlight ->
                            scope.launch {
                                val bookmarkLocalId = highlightsScreenModel.getBookmarkLocalIdForHighlight(highlight)
                                if (bookmarkLocalId != null) {
                                    onSelectedBookmarkIdChanged(bookmarkLocalId)
                                    onScrollToHighlightIdChanged(highlight.id)
                                    onActiveHighlightIdChanged(highlight.id)
                                }
                            }
                        },
                        onDeleteHighlight = { highlightsScreenModel.deleteHighlight(it) },
                        onLoadMore = { highlightsScreenModel.loadNextPage() },
                        showRefreshButton = true,
                        onRefresh = { highlightsScreenModel.syncHighlights() },
                        onBack = {
                            onShowHighlightsChanged(false)
                            onSelectedBookmarkIdChanged(null)
                            onScrollToHighlightIdChanged(null)
                            onActiveHighlightIdChanged(null)
                        }
                    )
                } else {
                    scaffoldContent(true)
                }
            }
        } // end AnimatedVisibility for list

        // Divider between list and reader (draggable)
        AnimatedVisibility(
            visible = !isReaderFullscreen,
            enter = paneEnter,
            exit = paneExit
        ) {
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
        }

        // Reader pane column -- shows filter side panel when active on desktop
        // Uses weight(1f) to fill remaining Row space, so it smoothly
        // resizes as the drawer / list panes animate in or out.
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (showFilterDialog) {
                FilterSidePanel(
                    currentFilter = currentFilter,
                    availableTags = topTagsWithCounts,
                    allTags = allAvailableTags,
                    availableLists = lists,
                    onDismiss = { onShowFilterDialogChanged(false) },
                    onFilterChange = { filter ->
                        screenModel.applyFilter(filter)
                    },
                    onReset = {
                        screenModel.applyFilter(FilterConfig())
                    }
                )
            } else {
                val currentBookmarkId = selectedBookmarkId
                val currentScrollToHighlightId = scrollToHighlightId
                if (currentBookmarkId != null) {
                    // Already in memory from the list pane — lets the reader's top bar show a
                    // title immediately instead of waiting on its own DB query to resolve.
                    val bookmarks by screenModel.bookmarks.collectAsState()
                    val selectedBookmark = remember(bookmarks, currentBookmarkId) {
                        bookmarks.find { it.localId == currentBookmarkId }
                    }
                    // Use key() to force fresh composition when bookmark or highlight changes
                    androidx.compose.runtime.key(currentBookmarkId, currentScrollToHighlightId) {
                        // koinViewModel, not koinInject: an injected ViewModel is never put in
                        // a ViewModelStore, so onCleared never runs and its viewModelScope is
                        // never cancelled — every bookmark opened here would leave its
                        // observeBookmarkById collector and any in-flight request running for
                        // the rest of the process.
                        val viewerScreenModel = koinViewModel<BookmarkViewerScreenModel>(
                            key = "viewer-$currentBookmarkId-$currentScrollToHighlightId"
                        )
                        DisposableEffect(currentBookmarkId) {
                            onDispose { viewerScreenModel.flushOnDispose() }
                        }
                        BookmarkViewerContent(
                            bookmarkId = currentBookmarkId,
                            scrollToHighlightId = currentScrollToHighlightId,
                            searchTrigger = readerSearchTrigger,
                            initialTitle = selectedBookmark?.title,
                            initialUrl = selectedBookmark?.url,
                            screenModel = viewerScreenModel,
                            onBack = {
                                if (isReaderFullscreen) {
                                    onReaderFullscreenChanged(false)
                                } else {
                                    onSelectedBookmarkIdChanged(null)
                                    onScrollToHighlightIdChanged(null)
                                    onActiveHighlightIdChanged(null)
                                }
                            },
                            onTagFilterApply = { tag ->
                                screenModel.applyTagFilter(tag, currentBookmarkId)
                                onSelectedBookmarkIdChanged(null)
                                onScrollToHighlightIdChanged(null)
                                onActiveHighlightIdChanged(null)
                                onShowHighlightsChanged(false)
                            },
                            isEmbedded = true,
                            isFullscreen = isReaderFullscreen,
                            onFullscreenToggle = { onReaderFullscreenChanged(!isReaderFullscreen) }
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
    }
}
