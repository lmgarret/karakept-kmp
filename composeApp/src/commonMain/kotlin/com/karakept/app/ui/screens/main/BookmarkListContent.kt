package com.karakept.app.ui.screens.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkWindow
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.SyncProgress
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.ui.components.BusyIndicator
import com.karakept.app.ui.components.RefreshableBox
import com.karakept.app.ui.components.bookmark.BookmarkAction
import com.karakept.app.ui.components.bookmark.BookmarkRowSkeleton
import com.karakept.app.ui.components.bookmark.ScrollCursorIndicator
import com.karakept.app.ui.components.bookmark.rememberBookmarkRowMetrics
import com.karakept.app.ui.components.bookmark.rememberTiledRows
import com.karakept.app.ui.components.scrollToTop
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.input.PageTurnScrollEffect
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.onDesktopModifiedClick
import com.karakept.app.ui.utils.onSecondaryClickWithPosition
import com.karakept.app.ui.utils.tiledTurnAdjustment
import com.karakept.app.ui.utils.trailingPagePaddingFor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkListContent(
    /**
     * The view as the list addresses it: one slot per row the view holds, whether or not that
     * row has been read. An index is therefore a position in the view, not in whatever has been
     * loaded, which is what lets a jump land without waiting for a read.
     */
    window: BookmarkWindow,
    isSyncing: Boolean,
    syncProgress: SyncProgress?,
    isLoadingMore: Boolean,
    isLoadingInitialPage: Boolean = false,
    showScrollCursor: Boolean = false,
    sortOption: SortOption = SortOption.NEWEST,
    totalBookmarkCount: Int = 0,
    /** Names the row at an absolute index, for the scroll cursor's tooltip. */
    bookmarkAtIndex: suspend (Int) -> Bookmark? = { null },
    /** The active layout's settings, resolved against the global defaults. */
    display: MainScreenDisplayConfig,
    swipeLeftAction: SwipeAction,
    swipeRightAction: SwipeAction,
    swipeLeftConfig: CustomSwipeActionConfig? = null,
    swipeRightConfig: CustomSwipeActionConfig? = null,
    showReadingProgress: Boolean = true,
    offlineMode: Boolean = false,
    pendingBookmarkRemoteIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    selectedBookmarkIds: Set<String> = emptySet(),
    activeBookmarkId: Long? = null,
    onBookmarkSelectionToggle: (Bookmark) -> Unit = {},
    listState: LazyListState,
    isDesktop: Boolean = false,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkLongClick: (Bookmark) -> Unit,
    onSwipeAction: (Bookmark, SwipeAction, CustomSwipeActionConfig?) -> Unit,
    onRefresh: () -> Unit,
    /** Reports the slots on screen, so the pages holding them are the ones read. */
    onVisibleSlotsChanged: (IntRange) -> Unit = {},
    serverUrl: String? = null,
    onCtrlClick: ((Bookmark) -> Unit)? = null,
    onShiftClick: ((Int) -> Unit)? = null,
    contextMenuLists: List<com.karakept.api.model.KarakeepList> = emptyList(),
    contextMenuTags: List<String> = emptyList(),
    contextMenuAiCapabilities: AiCapabilities = AiCapabilities(),
    onContextMenuAction: ((Bookmark, BookmarkAction) -> Unit)? = null,
    newBookmarksAbove: Int = 0,
    onClearNewBookmarksAbove: () -> Unit = {},
    /**
     * False when a reader pane is open beside the list (wide layout) — the reader owns the
     * hardware page buttons in that case, and both panes would otherwise scroll at once.
     */
    pageTurnEnabled: Boolean = true,
    /**
     * Whether quick actions are triggered by a swipe or by an always-visible button cluster.
     * Desktop always uses buttons regardless — there is nothing to swipe with.
     */
    rowActionMode: RowActionMode = RowActionMode.SWIPE,
    /**
     * Reports the bookmarks currently on screen, so their reading progress can be fetched
     * ahead of the sync rotation reaching them. Debounced — this fires on scroll.
     */
    onBookmarksVisible: (List<String>) -> Unit = {}
) {
    // Which slots are on screen, so the pages holding them can be read. Reported by index
    // rather than by key: a placeholder has no row to name, and it is exactly the slots with no
    // row that this has to ask for. Bucketing and de-duplication happen in the model, so an
    // ordinary scroll reports often and reads nothing.
    val currentOnVisibleSlots = rememberUpdatedState(onVisibleSlotsChanged)
    LaunchedEffect(listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) 0..0 else visible.first().index..visible.last().index
        }
            .distinctUntilChanged()
            .collect { currentOnVisibleSlots.value(it) }
    }

    // Report what is on screen once scrolling settles. Debounced rather than per-frame: the
    // callback issues one network request per never-pulled row, and a fling crosses hundreds.
    val currentOnBookmarksVisible = rememberUpdatedState(onBookmarksVisible)
    @OptIn(FlowPreview::class)
    LaunchedEffect(listState) {
        // Read the rows' keys rather than looking their indices up in the dataset: a sync
        // re-indexes everything below whatever it prepends, and layoutInfo lags that by a
        // snapshot, so indices resolved against the new list can name rows that are not on
        // screen — and their progress would be fetched instead of the ones that are.
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { info -> info.key as? String }
        }
            .debounce(400)
            .collect { ids -> if (ids.isNotEmpty()) currentOnBookmarksVisible.value(ids) }
    }

    val einkMode = LocalEinkMode.current
    // Holding every row to one height changes how the list looks while scrolling, not just where a
    // turn lands, so unlike the snap itself it waits for the master e-ink switch. The page-turn
    // wiring that reads it sits further down, where the page has been measured.
    val pageTurnBindings by koinInject<PageTurnDispatcher>().bindings.collectAsState()
    val pagedRendering = einkMode.enabled && pageTurnBindings.snapToContent
    // The gate compares the rows themselves — it is asking whether this is the same list
    // shifted or a different list entirely, which only the rows can answer.
    val animationGate = remember { ItemAnimationGate(window) }
    val animateItems = animationGate.update(window) && !einkMode.animationsDisabled

    val scope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }

    // "N new" pill: shown only while the user is scrolled away from the top. The counter is
    // retired by reporting the topmost row on screen — the model advances its "seen" anchor up
    // to it — so scrolling up through what arrived empties the pill as the rows go by, and
    // arrivals that land while the user is already at the top never raise it at all.
    // Only the scroll position goes through derivedStateOf (it reads snapshot state); the
    // newBookmarksAbove parameter must be read directly so recomposition picks up its changes.
    val scrolledAwayFromTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val showNewBookmarksPill = newBookmarksAbove > 0 && scrolledAwayFromTop

    // Desktop context menu: at most one open, on the row that was right-clicked.
    var contextMenuBookmarkId by remember { mutableStateOf<String?>(null) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    val dragSelection = rememberDragSelectionModifier(
        isSelectionMode = isSelectionMode,
        listState = listState,
        window = window,
        selectedBookmarkIds = selectedBookmarkIds,
        onBookmarkSelectionToggle = onBookmarkSelectionToggle
    )
    val rowActions = RowActionSettings(
        swipeLeftAction = swipeLeftAction,
        swipeRightAction = swipeRightAction,
        swipeLeftConfig = swipeLeftConfig,
        swipeRightConfig = swipeRightConfig,
        rowActionMode = rowActionMode,
    )

    // ScrollCursorIndicator is the last child of the RefreshableBox so it is drawn on top of
    // everything in the list, including the scroll-to-top FAB, whose Material3 Surface creates a
    // hardware-accelerated layer that ignores zIndex.
    RefreshableBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        enabled = !isDesktop,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // One measurement for the whole list: a row cannot ask for its own width without a
            // subcomposition, and the width is identical for every one of them. Only the automatic
            // description line count needs it.
            BoxWithConstraints(modifier = Modifier.fillMaxSize().then(dragSelection)) {
                val rowWidth = maxWidth
                // The page is the list's own height, read off the constraints rather than off
                // `layoutInfo`: the tile is settled before the first row is laid out, so the list is
                // never drawn once at its natural height and then again at the tile.
                val rowMetrics = rememberBookmarkRowMetrics(
                    itemContainerStyle = display.itemContainerStyle,
                    showThumbnail = display.showThumbnail,
                    thumbnailSize = display.thumbnailSize,
                    titlePosition = display.titlePosition,
                    showDescription = display.showDescription,
                    descriptionMaxLines = display.descriptionMaxLines,
                    descriptionPosition = display.descriptionPosition,
                    showUrl = display.showUrl,
                    urlPosition = display.urlPosition,
                    showTags = display.showTags,
                    showDate = display.showDate,
                    showReadingTime = display.showReadingTimeBadge,
                    metadataPosition = display.metadataPosition,
                    showRowDivider = display.showRowDivider
                )
                val tiledRows = rememberTiledRows(
                    enabled = pagedRendering,
                    viewportPx = with(density) { maxHeight.roundToPx() },
                    layoutType = display.layoutType,
                    metrics = rowMetrics
                )
                // A slot whose page has not arrived is drawn at the height its row will be,
                // declared by the same metrics the tiling uses. A placeholder of the wrong height
                // moves everything below it when the row lands, which on a list being scrolled is
                // the position jumping under the reader.
                val placeholderHeight = tiledRows?.itemHeight
                    ?: with(density) { rowMetrics.naturalHeightPx.toDp() }
                // Enough rows to fill the viewport, so an uncounted view looks like a list rather
                // than like a handful of bars at the top of an empty page.
                val placeholderScreenful = remember(placeholderHeight, maxHeight) {
                    if (placeholderHeight <= 0.dp) 0
                    else ((maxHeight / placeholderHeight).toInt() + 1).coerceIn(1, 20)
                }
                // Room for the last turn to put the final rows at the top of the page. Without it
                // that turn clamps against the end of the content and the page it lands on is one
                // the previous page had already shown almost all of.
                val trailingPadPx by remember(tiledRows) {
                    derivedStateOf {
                        val tiling = tiledRows?.tiling ?: return@derivedStateOf 0
                        trailingPagePaddingFor(listState.layoutInfo, tiling.rowHeightPx)
                    }
                }
                // A page is a whole number of rows once they are tiled, so a turn can be landed on
                // a row top from wherever it starts — including a position the user dragged to.
                // Untiled, the list keeps its flat delta: rows of their own heights have no
                // boundary to be pulled onto.
                val turnSnap: ((PageTurnDirection, Float) -> Float)? = tiledRows?.let { tiled ->
                    { direction, pageDeltaPx ->
                        // The offset is a distance into the *first visible item*, which is only a
                        // row while the list is showing bookmarks rather than its loading or
                        // end-of-list footer.
                        if (listState.firstVisibleItemIndex < window.total) {
                            tiledTurnAdjustment(
                                direction = direction,
                                pageDeltaPx = pageDeltaPx,
                                tiling = tiled.tiling,
                                firstVisibleOffsetPx = listState.firstVisibleItemScrollOffset
                            )
                        } else 0f
                    }
                }
                PageTurnScrollEffect(
                    listState = listState,
                    enabled = pageTurnEnabled,
                    predictiveSnap = turnSnap,
                    hasTrailingPadding = trailingPadPx > 0
                )
                val skeleton: @Composable () -> Unit = {
                    BookmarkRowSkeleton(
                        metrics = rowMetrics,
                        itemContainerStyle = display.itemContainerStyle,
                        thumbnailSide = display.thumbnailSide,
                        showRowDivider = display.showRowDivider,
                        fixedRowHeight = tiledRows?.rowHeight
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = with(density) { trailingPadPx.toDp() })
                ) {
                    items(
                        count = window.total,
                        key = { index -> window.keyAt(index) },
                        contentType = { index ->
                            if (window.bookmarkAt(index) != null) "bookmark" else "placeholder"
                        }
                    ) { itemIndex ->
                        val bookmark = window.bookmarkAt(itemIndex)
                        if (bookmark == null) {
                            skeleton()
                            return@items
                        }
                        Box(
                            modifier = Modifier
                                // Uniform height is what lets an exact number of rows fill the
                                // page. The row absorbs the difference through its description
                                // budget and its own whitespace; the clip catches whatever still
                                // will not fit.
                                .then(
                                    if (tiledRows != null) {
                                        Modifier.height(tiledRows.itemHeight).clipToBounds()
                                    } else Modifier
                                )
                                // fadeOutSpec = null: a fading-out item is kept in the layout and
                                // drawn over whatever replaces it, using the spec recorded on the
                                // *previous* measure pass — so a dataset swap renders the outgoing
                                // list on top of the incoming one, and no amount of switching
                                // animations off in time can undo it. Removed rows disappear at
                                // once; the rest still slide up via placementSpec.
                                .then(if (animateItems) Modifier.animateItem(fadeOutSpec = null) else Modifier)
                                .then(
                                    // Desktop: intercept Ctrl+Click and Shift+Click for multi-selection
                                    if (isDesktop && (onCtrlClick != null || onShiftClick != null)) {
                                        Modifier.onDesktopModifiedClick(
                                            key1 = bookmark.remoteId,
                                            key2 = itemIndex,
                                            onCtrlClick = onCtrlClick?.let { { it.invoke(bookmark) } },
                                            onShiftClick = onShiftClick?.let { { it.invoke(itemIndex) } }
                                        )
                                    } else Modifier
                                )
                                .then(
                                    // Desktop: right-click shows context menu
                                    if (isDesktop && onContextMenuAction != null) {
                                        Modifier.onSecondaryClickWithPosition { position ->
                                            contextMenuBookmarkId = bookmark.remoteId
                                            contextMenuOffset = with(density) {
                                                DpOffset(position.x.toDp(), position.y.toDp())
                                            }
                                        }
                                    } else Modifier
                                )
                        ) {
                            BookmarkListRow(
                                bookmark = bookmark,
                                display = display,
                                actions = rowActions,
                                showReadingProgress = showReadingProgress,
                                isPending = bookmark.remoteId in pendingBookmarkRemoteIds,
                                isSelectionMode = isSelectionMode,
                                isSelected = bookmark.remoteId in selectedBookmarkIds,
                                isActive = bookmark.localId == activeBookmarkId,
                                isDesktop = isDesktop,
                                offlineMode = offlineMode,
                                serverUrl = serverUrl,
                                rowWidth = rowWidth,
                                fixedRowHeight = tiledRows?.rowHeight,
                                descriptionFillHeight = tiledRows?.bodyHeight,
                                contextMenuOffset = contextMenuOffset.takeIf {
                                    isDesktop && contextMenuBookmarkId == bookmark.remoteId
                                },
                                contextMenuLists = contextMenuLists,
                                contextMenuTags = contextMenuTags,
                                contextMenuAiCapabilities = contextMenuAiCapabilities,
                                onContextMenuAction = onContextMenuAction,
                                onDismissContextMenu = { contextMenuBookmarkId = null },
                                onBookmarkClick = onBookmarkClick,
                                onBookmarkLongClick = onBookmarkLongClick,
                                onBookmarkSelectionToggle = onBookmarkSelectionToggle,
                                onSwipeAction = onSwipeAction,
                            )
                        }
                    }
                    bookmarkListFooters(
                        window = window,
                        isLoadingMore = isLoadingMore,
                        einkEnabled = einkMode.enabled,
                        placeholderScreenful = placeholderScreenful,
                        skeleton = skeleton
                    )
                }
            }

            if (isSyncing) {
                SyncProgressBar(
                    progress = syncProgress,
                    modifier = if (einkMode.animationsDisabled) {
                        Modifier.align(Alignment.Center)
                    } else {
                        Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    }
                )
            }

            NewBookmarksPill(
                visible = showNewBookmarksPill,
                count = newBookmarksAbove,
                onClick = {
                    scope.launch { listState.scrollToTop(einkMode.instantScroll) }
                    onClearNewBookmarksAbove()
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            )

            ScrollToTopFab(
                visible = showScrollToTop,
                onClick = { scope.launch { listState.scrollToTop(einkMode.instantScroll) } },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        if (showScrollCursor) {
            ScrollCursorIndicator(
                listState = listState,
                sortOption = sortOption,
                totalBookmarkCount = totalBookmarkCount,
                loadedAt = window::bookmarkAt,
                bookmarkAtIndex = bookmarkAtIndex,
                // padding(top) keeps the scrollbar clear of the sync progress bar
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 6.dp)
            )
        }
    }
}

/** What follows the rows: placeholders for an uncounted view, the empty state, and the list's end. */
private fun LazyListScope.bookmarkListFooters(
    window: BookmarkWindow,
    isLoadingMore: Boolean,
    einkEnabled: Boolean,
    placeholderScreenful: Int,
    skeleton: @Composable () -> Unit
) {
    // Before the view has been counted there are no slots to render, so the list would
    // be blank. A screenful of the same skeletons says "rows are coming" in the shape
    // they will come in — and is what stops a list switch flashing the empty state,
    // since an uncounted view is not an empty one.
    //
    // On e-ink a placeholder row draws nothing, so a screenful of them says nothing —
    // a blank page, which is the one thing an uncounted view must not look like. One
    // indicator for the screen instead, which is what the rest of the app does there.
    if (!window.resolved) {
        if (einkEnabled) {
            item(contentType = "placeholder") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BusyIndicator()
                }
            }
        } else {
            items(count = placeholderScreenful, contentType = { "placeholder" }) { skeleton() }
        }
    }

    // An explicit empty state, and only once the view has actually been counted —
    // otherwise every switch flashes "nothing here" before the list arrives.
    if (window.isEmpty) {
        item(contentType = "empty") {
            EmptyBookmarkList()
        }
    }

    if (isLoadingMore) {
        item(contentType = "loading") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                BusyIndicator()
            }
        }
    }

    if (!window.isEmpty) {
        item(contentType = "end") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No more bookmarks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
