package com.karakept.app.ui.screens.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.BookmarkCardLayout
import com.karakept.app.ui.components.BookmarkListLayout
import com.karakept.app.ui.components.BookmarkPlaceholderItem
import com.karakept.app.ui.components.SwipeableBookmarkItem
import com.karakept.app.ui.components.getEffectiveColor
import com.karakept.app.utils.FileUtils
import com.karakept.app.utils.ImageCacheManager
import com.karakept.app.utils.AssetUrlUtils
import com.karakept.app.utils.fileExists

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun BookmarkListContent(
    bookmarks: List<BookmarkEntity>,
    isSyncing: Boolean,
    syncProgress: com.karakept.app.data.model.SyncProgress?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
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
    offlineMode: Boolean = false,
    pendingBookmarkRemoteIds: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    selectedBookmarkIds: Set<Long> = emptySet(),
    onBookmarkSelectionToggle: (BookmarkEntity) -> Unit = {},
    listState: LazyListState,
    pullRefreshState: PullRefreshState,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkLongClick: (BookmarkEntity) -> Unit,
    onSwipeAction: (BookmarkEntity, SwipeAction, CustomSwipeActionConfig?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    serverUrl: String? = null
) {
    // Detect when scrolled near end
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()

                if (lastVisibleItem != null && totalItems > 0) {
                    val threshold = totalItems - 10  // Load when 10 items from end
                    if (lastVisibleItem.index >= threshold && hasMoreItems && !isLoadingMore) {
                        onLoadMore()
                    }
                }
            }
    }

    val hapticFeedback = LocalHapticFeedback.current
    val updatedBookmarks = rememberUpdatedState(bookmarks)
    val updatedSelectedIds = rememberUpdatedState(selectedBookmarkIds)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.remoteId }) { _, bookmark ->
                Box(
                    modifier = Modifier
                        .animateItem()
                ) {
                    if (bookmark.remoteId in pendingBookmarkRemoteIds) {
                        BookmarkPlaceholderItem(url = bookmark.url, layoutType = layoutType)
                        return@Box
                    }

                    // Dynamic icon logic for Mark Read/Unread
                    val leftIcon = if (swipeLeftAction == SwipeAction.MARK_READ) {
                        if (bookmark.isRead) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    } else null

                    val rightIcon = if (swipeRightAction == SwipeAction.MARK_READ) {
                        if (bookmark.isRead) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
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
                    SwipeableBookmarkItem(
                        leftSwipeAction = if (isSelectionMode) SwipeAction.NONE else swipeLeftAction,
                        rightSwipeAction = if (isSelectionMode) SwipeAction.NONE else swipeRightAction,
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
                        onActionTriggered = { action, isRightSwipe ->
                            val config = if (isRightSwipe) swipeRightConfig else swipeLeftConfig
                            onSwipeAction(bookmark, action, config)
                        }
                    ) {
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

                        // Log which asset is being used for display (bannerImage preferred over imageUrl)
                        if (bannerImageUrl != null) {
                            println("📸 LIST: Using bannerImage for bookmark ${bookmark.remoteId}")
                        } else if (screenshotUrl != null) {
                            println("📸 LIST: Using screenshot for bookmark ${bookmark.remoteId}")
                        } else if (bookmark.imageUrl != null) {
                            println("📸 LIST: No asset available for bookmark ${bookmark.remoteId}, imageUrl='${bookmark.imageUrl}' exists but not displayed")
                        } else {
                            println("📸 LIST: No image available for bookmark ${bookmark.remoteId}, showing emoji")
                        }

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
                                isSelected = isSelected
                            )
                            LayoutType.LIST -> BookmarkListLayout(
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
                                isSelected = isSelected
                            )
                        }
                    }
                }
            }

            // Loading indicator at bottom
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // End of list indicator
            if (!hasMoreItems && bookmarks.isNotEmpty()) {
                item {
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
        } // end drag-selection container Box

        if (isSyncing) {
            // Show determinate progress when available
            when (val progress = syncProgress) {
                is com.karakept.app.data.model.SyncProgress.FetchingContent -> {
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total.toFloat() },
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        )
                    }
                }
                is com.karakept.app.data.model.SyncProgress.FetchingMetadata -> {
                    // Show indeterminate but with visual feedback that something is happening
                    // We could show the bookmark count as text overlay if needed
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
                else -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isSyncing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
