package com.karakept.app.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
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
import com.karakept.app.ui.screens.settings.PerListSettingsScreen
import com.karakept.api.model.KarakeepList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    isAutoOffline: Boolean,
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
    navigateTo: (cafe.adriel.voyager.core.screen.Screen) -> Unit,
    isDesktop: Boolean,
    quickFilterCounts: QuickFilterCounts = QuickFilterCounts(),
    highlightsCount: Int = 0,
    scaffoldContent: @Composable (isExpandedLayout: Boolean) -> Unit
) {
    // Column width state -- persisted via SettingsRepository
    val layoutSettingsRepository = koinInject<SettingsRepository>()
    var drawerWidthDp by remember { mutableFloatStateOf(280f) }
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
    val maxListWidth = remainingWidth - minReaderWidth
    val listWidth = (remainingWidth * listFraction).coerceIn(minListWidth, maxListWidth)

    // Reset fullscreen when bookmark is deselected
    LaunchedEffect(selectedBookmarkId) {
        if (selectedBookmarkId == null) onReaderFullscreenChanged(false)
    }

    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Drawer column (collapsible)
        AnimatedVisibility(
            visible = isDrawerVisible && !isReaderFullscreen,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
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
                        screenModel.applyFilter(filter)
                        onShowHighlightsChanged(false)
                        onScrollToHighlightIdChanged(null)
                        onActiveHighlightIdChanged(null)
                    },
                    onClearFilter = {
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
                        navigateTo(PerListSettingsScreen(listId, listName))
                    },
                    onSetAsDefault = { listId -> screenModel.setDefaultList(listId) },
                    onSetAsDefaultType = { type -> screenModel.setDefaultListType(type) },
                    onNavigateToSettings = { navigateTo(SettingsScreen()) },
                    onNavigateToHighlights = {
                        onShowHighlightsChanged(true)
                        onSelectedBookmarkIdChanged(null)
                        onScrollToHighlightIdChanged(null)
                        onActiveHighlightIdChanged(null)
                    },
                    isHighlightsSelected = showHighlights,
                    onAddBookmark = if (isDesktop && !offlineMode && !isAutoOffline) {
                        { onShowAddBookmarkDialogChanged(true) }
                    } else null,
                    quickFilterCounts = quickFilterCounts,
                    highlightsCount = highlightsCount
                )
            }
        }

        // Divider between drawer and list (draggable).
        // Line aligned to start so it sits flush against the drawer edge.
        AnimatedVisibility(
            visible = isDrawerVisible && !isReaderFullscreen,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            DraggableDivider(
                lineAlignment = Alignment.CenterStart,
                onDrag = { delta ->
                    drawerWidthDp = (drawerWidthDp + delta).coerceIn(200f, 400f)
                }
            )
        }

        // Middle column: bookmark list or highlights list
        AnimatedVisibility(
            visible = !isReaderFullscreen,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            Box(modifier = Modifier.width(listWidth).fillMaxHeight()) {
                if (showHighlights) {
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
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
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
