package com.karakept.app.ui.screens.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.karakept.app.ui.icons.AppIcons
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ReadIndicatorStyle
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.data.model.SortOption
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.components.BusyIndicator
import com.karakept.app.ui.components.EinkAwareSmallFab
import com.karakept.app.ui.components.FloatingBusyCard
import com.karakept.app.ui.components.borderStroke
import com.karakept.app.ui.components.floatingSurfaceStyle
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.components.RefreshableBox
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.BookmarkCardLayout
import com.karakept.app.ui.components.BookmarkContextMenu
import com.karakept.app.ui.components.BookmarkListLayout
import com.karakept.app.ui.components.BookmarkPlaceholderItem
import com.karakept.app.ui.components.QuickActionBookmarkItem
import com.karakept.app.ui.components.ScrollCursorIndicator
import com.karakept.app.ui.components.SwipeableBookmarkItem
import com.karakept.app.ui.components.rememberBookmarkRowMetrics
import com.karakept.app.ui.components.rememberTiledRows
import com.karakept.app.ui.components.getEffectiveColor
import com.karakept.app.ui.components.scrollToTop
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.input.PageTurnScrollEffect
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.onDesktopModifiedClick
import com.karakept.app.ui.utils.tiledTurnAdjustment
import com.karakept.app.ui.utils.trailingPagePaddingFor
import com.karakept.app.ui.utils.onSecondaryClickWithPosition
import com.karakept.app.utils.FileUtils
import com.karakept.app.utils.ImageCacheManager
import com.karakept.app.utils.AssetUrlUtils
import com.karakept.app.utils.fileExists
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkListContent(
    bookmarks: List<BookmarkEntity>,
    isSyncing: Boolean,
    syncProgress: com.karakept.app.data.model.SyncProgress?,
    isLoadingMore: Boolean,
    isLoadingInitialPage: Boolean = false,
    hasMoreItems: Boolean,
    showScrollCursor: Boolean = false,
    sortOption: SortOption = SortOption.NEWEST,
    totalBookmarkCount: Int = 0,
    layoutType: LayoutType,
    swipeLeftAction: SwipeAction,
    swipeRightAction: SwipeAction,
    swipeLeftConfig: CustomSwipeActionConfig? = null,
    swipeRightConfig: CustomSwipeActionConfig? = null,
    dimReadBookmarks: Boolean,
    showReadingTimeBadge: Boolean,
    showReadingProgress: Boolean = true,
    showTags: Boolean,
    showDate: Boolean = true,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.ELAPSED,
    thumbnailSide: ThumbnailSide = ThumbnailSide.LEFT,
    showFavicon: Boolean = true,
    thumbnailSize: Int = 80,
    metadataPosition: MetadataPosition = MetadataPosition.BELOW,
    tagsScrollable: Boolean = false,
    quickActionPosition: QuickActionPosition = QuickActionPosition.RIGHT,
    showDescription: Boolean = true,
    descriptionPosition: DescriptionPosition = DescriptionPosition.BELOW_TITLE,
    showUrl: Boolean = false,
    urlDisplayMode: UrlDisplayMode = UrlDisplayMode.DOMAIN_ONLY,
    urlPosition: UrlPosition = UrlPosition.BELOW_TITLE,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    faviconByLinkSize: Int = 16,
    showThumbnail: Boolean = true,
    itemContainerStyle: ItemContainerStyle = ItemContainerStyle.CARD,
    readIndicatorStyle: ReadIndicatorStyle = ReadIndicatorStyle.DIM,
    showRowDivider: Boolean = true,
    titlePosition: TitlePosition = TitlePosition.BESIDE_THUMBNAIL,
    descriptionMaxLines: Int = BookmarkLayout.DESCRIPTION_LINES_DEFAULT,
    offlineMode: Boolean = false,
    pendingBookmarkRemoteIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    selectedBookmarkIds: Set<String> = emptySet(),
    activeBookmarkId: Long? = null,
    onBookmarkSelectionToggle: (BookmarkEntity) -> Unit = {},
    listState: LazyListState,
    isDesktop: Boolean = false,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkLongClick: (BookmarkEntity) -> Unit,
    onSwipeAction: (BookmarkEntity, SwipeAction, CustomSwipeActionConfig?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    serverUrl: String? = null,
    onCtrlClick: ((BookmarkEntity) -> Unit)? = null,
    onShiftClick: ((Int) -> Unit)? = null,
    contextMenuLists: List<com.karakept.api.model.KarakeepList> = emptyList(),
    contextMenuTags: List<String> = emptyList(),
    contextMenuAiCapabilities: AiCapabilities = AiCapabilities(),
    onContextMenuAction: ((BookmarkEntity, BookmarkAction) -> Unit)? = null,
    newBookmarksAbove: Int = 0,
    onClearNewBookmarksAbove: () -> Unit = {},
    /** The topmost bookmark on screen, which retires the "N new" count up to that row. */
    onTopBookmarkVisible: (String) -> Unit = {},
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
    // Detect when scrolled near end. The effect outlives the values it guards on, so they are
    // read through rememberUpdatedState — capturing them would freeze the guards at their
    // first-composition values and keep firing load-more while a reload is in flight.
    val currentHasMoreItems = rememberUpdatedState(hasMoreItems)
    val currentIsLoadingMore = rememberUpdatedState(isLoadingMore)
    val currentOnLoadMore = rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()

                if (lastVisibleItem != null && totalItems > 0) {
                    val threshold = totalItems - 10  // Load when 10 items from end
                    if (lastVisibleItem.index >= threshold &&
                        currentHasMoreItems.value && !currentIsLoadingMore.value
                    ) {
                        currentOnLoadMore.value()
                    }
                }
            }
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
    val animationGate = remember { ItemAnimationGate(bookmarks) }
    val animateItems = animationGate.update(bookmarks) && !einkMode.animationsDisabled

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
    val currentOnTopBookmarkVisible = rememberUpdatedState(onTopBookmarkVisible)
    LaunchedEffect(listState) {
        // Reported by key rather than index: a sync prepending rows re-indexes everything
        // below them, and the row at the top of the viewport is the same row either way.
        // The trailing loading/end rows are unkeyed, so anything that is not a remoteId is
        // not a bookmark.
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String }
            .collect { topRemoteId -> topRemoteId?.let { currentOnTopBookmarkVisible.value(it) } }
    }

    val hapticFeedback = LocalHapticFeedback.current
    val updatedBookmarks = rememberUpdatedState(bookmarks)
    val updatedSelectedIds = rememberUpdatedState(selectedBookmarkIds)

    // Desktop context menu state
    var contextMenuBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var contextMenuOffset by remember { mutableStateOf(androidx.compose.ui.unit.DpOffset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Tracks whether a drag-selection gesture is currently active (started from long press in selection mode)
    val isDragSelecting = remember { mutableStateOf(false) }
    // Scroll speed (px/frame) applied at the list edges during drag selection.
    // Negative = scroll up, positive = scroll down, 0 = no auto-scroll.
    val autoScrollSpeed = remember { mutableStateOf(0f) }

    // Edge-scroll loop: while a drag-selection is active, scroll the list at `autoScrollSpeed`
    // at ~60 fps. The speed is updated by the container's onDrag handler based on proximity to
    // the viewport edges (0 at the zone boundary → maxSpeed at the very edge, quadratic easing).
    LaunchedEffect(isDragSelecting.value) {
        if (!isDragSelecting.value) {
            autoScrollSpeed.value = 0f
            return@LaunchedEffect
        }
        while (isDragSelecting.value) {
            val speed = autoScrollSpeed.value
            if (speed != 0f) {
                listState.scrollBy(speed)
            }
            delay(16L) // ~60 fps
        }
        autoScrollSpeed.value = 0f
    }

    val listContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
        // Container-level drag selection: the gesture lives here (not per-item) so that
        // edge-scroll auto-scrolling does not recycle the item that owns the gesture detector,
        // which would kill the drag mid-gesture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isSelectionMode) {
                    if (!isSelectionMode) return@pointerInput
                    // Bidirectional range selection:
                    //  - dragStartIndex: fixed anchor (where the long-press started)
                    //  - lastDragIndex: updated on each drag event to the current finger position
                    // On each event the OLD range [dragStartIndex, lastDragIndex] and the NEW range
                    // [dragStartIndex, currentIndex] are diffed:
                    //  - items entering the new range → selected
                    //  - items leaving the new range → deselected
                    // "Pulling back" the finger therefore un-selects the overshoot items.
                    var dragStartIndex = -1
                    var lastDragIndex = -1
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val viewportStart = listState.layoutInfo.viewportStartOffset
                            val absoluteY = offset.y + viewportStart
                            val initialItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                absoluteY.toInt() in info.offset until (info.offset + info.size)
                            } ?: return@detectDragGesturesAfterLongPress
                            dragStartIndex = initialItem.index
                            lastDragIndex = dragStartIndex
                            isDragSelecting.value = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            val bm = updatedBookmarks.value.getOrNull(initialItem.index)
                            if (bm != null && bm.remoteId !in updatedSelectedIds.value) {
                                onBookmarkSelectionToggle(bm)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (dragStartIndex >= 0) {
                                val viewportStart = listState.layoutInfo.viewportStartOffset
                                val absoluteY = change.position.y + viewportStart
                                val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    absoluteY.toInt() in info.offset until (info.offset + info.size)
                                }
                                if (targetItem != null) {
                                    val currentIndex = targetItem.index
                                    if (currentIndex != lastDragIndex) {
                                        val oldRangeStart = minOf(dragStartIndex, lastDragIndex)
                                        val oldRangeEnd = maxOf(dragStartIndex, lastDragIndex)
                                        val newRangeStart = minOf(dragStartIndex, currentIndex)
                                        val newRangeEnd = maxOf(dragStartIndex, currentIndex)
                                        val bookmarkList = updatedBookmarks.value
                                        val selectedIds = updatedSelectedIds.value
                                        // Select items entering the range
                                        for (i in newRangeStart..newRangeEnd) {
                                            if (i < oldRangeStart || i > oldRangeEnd) {
                                                val bm = bookmarkList.getOrNull(i)
                                                if (bm != null && bm.remoteId !in selectedIds) {
                                                    onBookmarkSelectionToggle(bm)
                                                }
                                            }
                                        }
                                        // Deselect items leaving the range
                                        for (i in oldRangeStart..oldRangeEnd) {
                                            if (i < newRangeStart || i > newRangeEnd) {
                                                val bm = bookmarkList.getOrNull(i)
                                                if (bm != null && bm.remoteId in selectedIds) {
                                                    onBookmarkSelectionToggle(bm)
                                                }
                                            }
                                        }
                                        lastDragIndex = currentIndex
                                    }
                                }
                            }

                            // Edge-scroll: speed scales quadratically from 0 at zone boundary
                            // to maxSpeed at the very edge (15% zone on each side).
                            val viewportHeight = (listState.layoutInfo.viewportEndOffset -
                                listState.layoutInfo.viewportStartOffset).toFloat()
                            val edgeZone = viewportHeight * 0.15f
                            val maxSpeed = 25f
                            val y = change.position.y
                            autoScrollSpeed.value = when {
                                y < edgeZone -> {
                                    val fraction = ((edgeZone - y) / edgeZone).coerceIn(0f, 1f)
                                    -maxSpeed * fraction * fraction
                                }
                                y > viewportHeight - edgeZone -> {
                                    val fraction = ((y - (viewportHeight - edgeZone)) / edgeZone).coerceIn(0f, 1f)
                                    maxSpeed * fraction * fraction
                                }
                                else -> 0f
                            }
                        },
                        onDragEnd = {
                            dragStartIndex = -1
                            lastDragIndex = -1
                            autoScrollSpeed.value = 0f
                            isDragSelecting.value = false
                        },
                        onDragCancel = {
                            dragStartIndex = -1
                            lastDragIndex = -1
                            autoScrollSpeed.value = 0f
                            isDragSelecting.value = false
                        }
                    )
                }
        ) {
        // One measurement for the whole list: a row cannot ask for its own width without a
        // subcomposition, and the width is identical for every one of them. Only the automatic
        // description line count needs it.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rowWidth = maxWidth
        // The page is the list's own height, read off the constraints rather than off `layoutInfo`:
        // the tile is settled before the first row is laid out, so the list is never drawn once at
        // its natural height and then again at the tile.
        val tiledRows = rememberTiledRows(
            enabled = pagedRendering,
            viewportPx = with(density) { maxHeight.roundToPx() },
            layoutType = layoutType,
            metrics = rememberBookmarkRowMetrics(
                itemContainerStyle = itemContainerStyle,
                showThumbnail = showThumbnail,
                thumbnailSize = thumbnailSize,
                titlePosition = titlePosition,
                showDescription = showDescription,
                descriptionMaxLines = descriptionMaxLines,
                descriptionPosition = descriptionPosition,
                showUrl = showUrl,
                urlPosition = urlPosition,
                showTags = showTags,
                showDate = showDate,
                showReadingTime = showReadingTimeBadge,
                metadataPosition = metadataPosition,
                showRowDivider = showRowDivider
            )
        )
        // Room for the last turn to put the final rows at the top of the page. Without it that turn
        // clamps against the end of the content and the page it lands on is one the previous page
        // had already shown almost all of.
        val trailingPadPx by remember(tiledRows) {
            derivedStateOf {
                val tiling = tiledRows?.tiling ?: return@derivedStateOf 0
                trailingPagePaddingFor(listState.layoutInfo, tiling.rowHeightPx)
            }
        }
        // A page is a whole number of rows once they are tiled, so a turn can be landed on a row top
        // from wherever it starts — including a position the user dragged to. Untiled, the list
        // keeps its flat delta: rows of their own heights have no boundary to be pulled onto.
        val turnSnap: ((PageTurnDirection, Float) -> Float)? = tiledRows?.let { tiled ->
            { direction, pageDeltaPx ->
                // The offset is a distance into the *first visible item*, which is only a row while
                // the list is showing bookmarks rather than its loading or end-of-list footer.
                if (listState.firstVisibleItemIndex < bookmarks.size) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = with(density) { trailingPadPx.toDp() })
        ) {
            itemsIndexed(
                bookmarks,
                key = { _, bookmark -> bookmark.remoteId },
                contentType = { _, _ -> "bookmark" }
            ) { itemIndex, bookmark ->
                Box(
                    modifier = Modifier
                        // Uniform height is what lets an exact number of rows fill the page. The row
                        // absorbs the difference through its description budget and its own
                        // whitespace; the clip catches whatever still will not fit.
                        .then(
                            if (tiledRows != null) {
                                Modifier.height(tiledRows.itemHeight).clipToBounds()
                            } else Modifier
                        )
                        // fadeOutSpec = null: a fading-out item is kept in the layout and drawn
                        // over whatever replaces it, using the spec recorded on the *previous*
                        // measure pass — so a dataset swap renders the outgoing list on top of
                        // the incoming one, and no amount of switching animations off in time
                        // can undo it. Removed rows disappear at once; the rest still slide up
                        // via placementSpec.
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
                                    contextMenuBookmark = bookmark
                                    contextMenuOffset = with(density) {
                                        androidx.compose.ui.unit.DpOffset(
                                            position.x.toDp(),
                                            position.y.toDp()
                                        )
                                    }
                                }
                            } else Modifier
                        )
                ) {
                    // Desktop context menu anchored at right-click position.
                    // The menu is hosted in a zero-size Box offset to the cursor so the
                    // DropdownMenu opens at the click point rather than below the whole item.
                    if (isDesktop && contextMenuBookmark?.remoteId == bookmark.remoteId && onContextMenuAction != null) {
                        Box(
                            modifier = Modifier
                                .offset(contextMenuOffset.x, contextMenuOffset.y)
                                .size(0.dp)
                        ) {
                            BookmarkContextMenu(
                                expanded = true,
                                bookmark = bookmark,
                                availableLists = contextMenuLists,
                                availableTags = contextMenuTags,
                                aiCapabilities = contextMenuAiCapabilities,
                                onAction = { action ->
                                    onContextMenuAction.invoke(bookmark, action)
                                },
                                onDismiss = { contextMenuBookmark = null }
                            )
                        }
                    }

                    if (bookmark.remoteId in pendingBookmarkRemoteIds) {
                        BookmarkPlaceholderItem(
                            url = bookmark.url,
                            layoutType = layoutType,
                            itemContainerStyle = itemContainerStyle,
                            showThumbnail = showThumbnail,
                            thumbnailSize = thumbnailSize,
                            thumbnailSide = thumbnailSide,
                            titlePosition = titlePosition,
                            showRowDivider = showRowDivider
                        )
                        return@Box
                    }

                    // Dynamic icon logic for Mark Read/Unread
                    val leftIcon = if (swipeLeftAction == SwipeAction.MARK_READ) {
                        if (bookmark.isRead) AppIcons.Filled.VisibilityOff else AppIcons.Filled.Visibility
                    } else null

                    val rightIcon = if (swipeRightAction == SwipeAction.MARK_READ) {
                        if (bookmark.isRead) AppIcons.Filled.VisibilityOff else AppIcons.Filled.Visibility
                    } else null

                    // Compute whether each custom action is already applied to this bookmark
                    val leftIsApplied = when (swipeLeftAction) {
                        SwipeAction.ADD_TAG -> {
                            val tagName = swipeLeftConfig?.tagName
                            tagName != null && bookmark.tags.split(",").map { it.trim() }.contains(tagName)
                        }
                        SwipeAction.ADD_TO_LIST -> {
                            val listId = swipeLeftConfig?.listId
                            listId != null && bookmark.listIds.split(",").map { it.trim() }.contains(listId)
                        }
                        else -> false
                    }
                    val rightIsApplied = when (swipeRightAction) {
                        SwipeAction.ADD_TAG -> {
                            val tagName = swipeRightConfig?.tagName
                            tagName != null && bookmark.tags.split(",").map { it.trim() }.contains(tagName)
                        }
                        SwipeAction.ADD_TO_LIST -> {
                            val listId = swipeRightConfig?.listId
                            listId != null && bookmark.listIds.split(",").map { it.trim() }.contains(listId)
                        }
                        else -> false
                    }

                    // Compute short label text for custom swipe actions
                    val leftLabel = when (swipeLeftAction) {
                        SwipeAction.ADD_TAG -> swipeLeftConfig?.tagName
                        SwipeAction.ADD_TO_LIST -> swipeLeftConfig?.listName
                        else -> null
                    }
                    val rightLabel = when (swipeRightAction) {
                        SwipeAction.ADD_TAG -> swipeRightConfig?.tagName
                        SwipeAction.ADD_TO_LIST -> swipeRightConfig?.listName
                        else -> null
                    }

                    val isSelected = bookmark.remoteId in selectedBookmarkIds
                    val effectiveLeftAction = if (isSelectionMode) SwipeAction.NONE else swipeLeftAction
                    val effectiveRightAction = if (isSelectionMode) SwipeAction.NONE else swipeRightAction
                    val actionTriggered: (SwipeAction, Boolean) -> Unit = { action, isRightSwipe ->
                        val config = if (isRightSwipe) swipeRightConfig else swipeLeftConfig
                        onSwipeAction(bookmark, action, config)
                    }

                    val wrapper: @Composable (@Composable () -> Unit) -> Unit = { innerContent ->
                        if (isDesktop || rowActionMode == RowActionMode.BUTTONS) {
                            QuickActionBookmarkItem(
                                flat = itemContainerStyle == ItemContainerStyle.FLAT,
                                // On touch there is no hover to reveal them.
                                alwaysVisible = !isDesktop,
                                leftAction = effectiveLeftAction,
                                rightAction = effectiveRightAction,
                                leftIcon = leftIcon,
                                rightIcon = rightIcon,
                                leftIsApplied = leftIsApplied,
                                rightIsApplied = rightIsApplied,
                                position = quickActionPosition,
                                onActionTriggered = actionTriggered,
                                content = innerContent
                            )
                        } else {
                            SwipeableBookmarkItem(
                                flat = itemContainerStyle == ItemContainerStyle.FLAT,
                                leftSwipeAction = effectiveLeftAction,
                                rightSwipeAction = effectiveRightAction,
                                leftIcon = leftIcon,
                                rightIcon = rightIcon,
                                leftColor = swipeLeftConfig.getEffectiveColor(swipeLeftAction).takeIf {
                                    swipeLeftConfig?.colorHex != null
                                },
                                rightColor = swipeRightConfig.getEffectiveColor(swipeRightAction).takeIf {
                                    swipeRightConfig?.colorHex != null
                                },
                                leftLabel = leftLabel,
                                rightLabel = rightLabel,
                                leftIsApplied = leftIsApplied,
                                rightIsApplied = rightIsApplied,
                                onActionTriggered = actionTriggered,
                                content = innerContent
                            )
                        }
                    }

                    wrapper {
                        // Construct banner and screenshot URLs if available
                        val bannerImageUrl = if (serverUrl != null && bookmark.bannerImageAssetId != null) {
                            val remoteUrl = AssetUrlUtils.getAssetUrl(serverUrl, bookmark.bannerImageAssetId)
                            // Try to resolve local path for offline support
                            val fileName = ImageCacheManager.generateCacheFileName(remoteUrl)
                            val localPath = FileUtils.getImageCacheDirectory() + "/" + fileName
                            if (fileExists(localPath)) {
                                "file://$localPath"
                            } else {
                                remoteUrl
                            }
                        } else null

                        val screenshotUrl = if (serverUrl != null && bookmark.screenshotAssetId != null) {
                            val remoteUrl = AssetUrlUtils.getAssetUrl(serverUrl, bookmark.screenshotAssetId)
                            // Try to resolve local path for offline support
                            val fileName = ImageCacheManager.generateCacheFileName(remoteUrl)
                            val localPath = FileUtils.getImageCacheDirectory() + "/" + fileName
                            if (fileExists(localPath)) {
                                "file://$localPath"
                            } else {
                                remoteUrl
                            }
                        } else null

                        val isActiveBookmark = bookmark.localId == activeBookmarkId
                        when (layoutType) {
                            LayoutType.CARD -> BookmarkCardLayout(
                                bookmark = bookmark,
                                onClick = remember(bookmark.localId, isSelectionMode) {
                                    {
                                        if (isSelectionMode) onBookmarkSelectionToggle(bookmark)
                                        else onBookmarkClick(bookmark)
                                    }
                                },
                                // In selection mode, long press is handled by detectDragGesturesAfterLongPress
                                onLongClick = remember(bookmark.localId, isSelectionMode) {
                                    if (isSelectionMode) null else { { onBookmarkLongClick(bookmark) } }
                                },
                                showReadingTime = showReadingTimeBadge,
                                showReadingProgress = showReadingProgress,
                                showTags = showTags,
                                showDate = showDate,
                                dateDisplayMode = dateDisplayMode,
                                dimRead = dimReadBookmarks,
                                offlineMode = offlineMode,
                                bannerImageUrl = bannerImageUrl,
                                screenshotUrl = screenshotUrl,
                                isSelected = isSelected,
                                tagsScrollable = tagsScrollable,
                                isActive = isActiveBookmark,
                                showDescription = showDescription,
                                showUrl = showUrl,
                                urlDisplayMode = urlDisplayMode,
                                urlPosition = urlPosition,
                                urlIconMode = urlIconMode,
                                faviconByLinkSize = faviconByLinkSize,
                            )
                            LayoutType.LIST, @Suppress("DEPRECATION") LayoutType.COMPACT_LIST -> BookmarkListLayout(
                                bookmark = bookmark,
                                onClick = remember(bookmark.localId, isSelectionMode) {
                                    {
                                        if (isSelectionMode) onBookmarkSelectionToggle(bookmark)
                                        else onBookmarkClick(bookmark)
                                    }
                                },
                                // In selection mode, long press is handled by detectDragGesturesAfterLongPress
                                onLongClick = remember(bookmark.localId, isSelectionMode) {
                                    if (isSelectionMode) null else { { onBookmarkLongClick(bookmark) } }
                                },
                                showReadingTime = showReadingTimeBadge,
                                showReadingProgress = showReadingProgress,
                                showTags = showTags,
                                showDate = showDate,
                                dateDisplayMode = dateDisplayMode,
                                dimRead = dimReadBookmarks,
                                offlineMode = offlineMode,
                                bannerImageUrl = bannerImageUrl,
                                screenshotUrl = screenshotUrl,
                                isSelected = isSelected,
                                thumbnailSide = thumbnailSide,
                                showFavicon = showFavicon,
                                thumbnailSize = thumbnailSize,
                                metadataPosition = metadataPosition,
                                tagsScrollable = tagsScrollable,
                                isActive = isActiveBookmark,
                                showDescription = showDescription,
                                descriptionPosition = descriptionPosition,
                                showUrl = showUrl,
                                urlDisplayMode = urlDisplayMode,
                                urlPosition = urlPosition,
                                urlIconMode = urlIconMode,
                                faviconByLinkSize = faviconByLinkSize,
                                showThumbnail = showThumbnail,
                                itemContainerStyle = itemContainerStyle,
                                readIndicatorStyle = readIndicatorStyle,
                                showRowDivider = showRowDivider,
                                titlePosition = titlePosition,
                                descriptionMaxLines = descriptionMaxLines,
                                rowWidth = rowWidth,
                                fixedRowHeight = tiledRows?.rowHeight,
                                descriptionFillHeight = tiledRows?.bodyHeight,
                            )
                        }
                    }
                }
            }

            // An explicit empty state, but only once the first page has actually resolved —
            // otherwise every cold start flashes "nothing here" before the list arrives.
            if (bookmarks.isEmpty() && !isLoadingInitialPage) {
                item(contentType = "empty") {
                    EmptyBookmarkList()
                }
            }

            // Loading indicator at bottom
            if (isLoadingMore) {
                item(contentType = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BusyIndicator()
                    }
                }
            }

            // End of list indicator
            if (!hasMoreItems && bookmarks.isNotEmpty()) {
                item(contentType = "end") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
        } // end row-width BoxWithConstraints
        } // end drag-selection container Box

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

        // "N new bookmarks" pill — tap to jump to the newly synced items at the top.
        val pillStyle = floatingSurfaceStyle(4.dp)
        AnimatedVisibilityOrPlain(
            visible = showNewBookmarksPill,
            animated = !einkMode.animationsDisabled,
            durationMillis = 200,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        ) {
            Surface(
                onClick = {
                    scope.launch { listState.scrollToTop(einkMode.instantScroll) }
                    onClearNewBookmarksAbove()
                },
                shape = MaterialTheme.shapes.large,
                // `primary` is solid ink on the e-ink scheme. A filled pill would read, but it
                // lays down — and then ghosts — a block of ink for a transient prompt; an
                // outlined one on the page colour says the same thing for a fraction of it.
                color = if (pillStyle.outlined) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (pillStyle.outlined) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                border = pillStyle.borderStroke(),
                shadowElevation = pillStyle.shadowElevation
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (newBookmarksAbove == 1) "1 new bookmark"
                               else "$newBookmarksAbove new bookmarks",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Scroll-to-top FAB
        AnimatedVisibilityOrPlain(
            visible = showScrollToTop,
            animated = !einkMode.animationsDisabled,
            durationMillis = 300,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) {
            EinkAwareSmallFab(
                onClick = { scope.launch { listState.scrollToTop(einkMode.instantScroll) } }
            ) {
                Icon(
                    imageVector = AppIcons.Default.ArrowUpward,
                    contentDescription = "Scroll to top"
                )
            }
        }

        } // end inner Box
    }

    // ScrollCursorIndicator is intentionally placed OUTSIDE listContent so it renders
    // as the last child of the RefreshableBox. This guarantees it is drawn on top of
    // everything inside listContent (including the scroll-to-top FAB whose Material3
    // Surface creates a hardware-accelerated layer that ignores zIndex).
    RefreshableBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        enabled = !isDesktop,
        modifier = Modifier.fillMaxSize()
    ) {
        listContent()
        if (showScrollCursor) {
            ScrollCursorIndicator(
                listState = listState,
                bookmarks = bookmarks,
                sortOption = sortOption,
                totalBookmarkCount = totalBookmarkCount,
                // A drag can aim past the loaded window, and these are how it gets there.
                hasMoreItems = hasMoreItems,
                isLoadingMore = isLoadingMore,
                onLoadMore = onLoadMore,
                // padding(top) keeps the scrollbar clear of the sync progress bar
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 6.dp)
            )
        }
    }
}

/**
 * Decides whether the LazyColumn should animate its item changes.
 *
 * Item animations are for *surgical* changes — an item removed by smart-list reconciliation,
 * an item updated in place by a sync, a page appended by load-more. Switching lists instead
 * replaces the whole dataset, and animating that fades a whole list in over the one being
 * replaced. The decision is derived from the data itself, and taken during composition, so it
 * lands in the very frame that renders the swap; a flag delivered by a separate flow, or read
 * from an effect, always arrives at least one frame too late to suppress anything.
 *
 * A prepend is excluded too. Rows inserted at the head displace every row below them, and
 * the viewport is held at the top for a user who has not scrolled — so the placement spring
 * animates the whole visible list down from the top edge, which reads as the order shuffling
 * and settling back rather than as new items arriving. Landing them in place is what looks
 * like an insertion.
 *
 * Plain fields rather than snapshot state: updating them must not invalidate the composition
 * that is reading them.
 */
internal class ItemAnimationGate(initial: List<BookmarkEntity>) {
    private var previous = initial
    private var enabled = true

    fun update(bookmarks: List<BookmarkEntity>): Boolean {
        if (bookmarks === previous) return enabled
        val previousIds = previous.mapTo(HashSet(previous.size)) { it.remoteId }
        val survivors = bookmarks.count { it.remoteId in previousIds }
        // Half of the shorter list surviving still reads as "the same list, changed".
        val sameList = previous.isEmpty() || bookmarks.isEmpty() ||
            survivors * 2 >= minOf(previous.size, bookmarks.size)
        enabled = sameList && !isPrepend(bookmarks)
        previous = bookmarks
        return enabled
    }

    /** True when rows were inserted above the row that used to be first. */
    private fun isPrepend(bookmarks: List<BookmarkEntity>): Boolean {
        val previousFirstId = previous.firstOrNull()?.remoteId ?: return false
        return bookmarks.indexOfFirst { it.remoteId == previousFirstId } > 0
    }
}

@Composable
private fun SyncProgressBar(
    progress: com.karakept.app.data.model.SyncProgress?,
    modifier: Modifier = Modifier
) {
    // Only the *indeterminate* bar is a problem on e-ink: it animates continuously. The
    // determinate one redraws once per progress change, which the panel handles fine.
    val einkMode = LocalEinkMode.current
    val fetched = (progress as? com.karakept.app.data.model.SyncProgress.FetchingContent)
        ?.takeIf { it.total > 0 }

    if (einkMode.animationsDisabled) {
        // A strip at the top edge of the list is easy to overlook on a monochrome panel, so the
        // whole sync state moves into one card in the middle of the page — determinate or not,
        // rather than splitting it across two places.
        FloatingBusyCard(modifier = modifier) {
            if (fetched != null) {
                Text(
                    text = "Syncing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { fetched.current.toFloat() / fetched.total.toFloat() },
                    modifier = Modifier.width(SyncCardProgressWidth)
                )
                Text(
                    text = "${fetched.current} / ${fetched.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LoadingDotsIndicator(label = "Syncing…", dotSize = 8.dp)
            }
        }
        return
    }

    Column(modifier = modifier) {
        if (fetched != null) {
            LinearProgressIndicator(
                progress = { fetched.current.toFloat() / fetched.total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${fetched.current} / ${fetched.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Wide enough to read as a progress bar, narrow enough to keep the card off the page edges. */
private val SyncCardProgressWidth = 160.dp

@Composable
private fun EmptyBookmarkList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = AppIcons.Default.BookmarkBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Nothing here yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Bookmarks you save will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
