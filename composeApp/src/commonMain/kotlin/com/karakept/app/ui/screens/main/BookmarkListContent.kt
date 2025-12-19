package com.karakept.app.ui.screens.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.BookmarkCardLayout
import com.karakept.app.ui.components.BookmarkListLayout
import com.karakept.app.ui.components.SwipeableBookmarkItem

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
    dimReadBookmarks: Boolean,
    showReadingTimeBadge: Boolean,
    showTags: Boolean,
    listState: LazyListState,
    pullRefreshState: PullRefreshState,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkLongClick: (BookmarkEntity) -> Unit,
    onSwipeAction: (BookmarkEntity, SwipeAction) -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            items(bookmarks, key = { it.remoteId }) { bookmark ->
                Box(modifier = Modifier.animateItemPlacement()) {
                    // Dynamic icon logic for Mark Read/Unread
                    val leftIcon = if (swipeLeftAction == SwipeAction.MARK_READ) {
                        if (bookmark.isRead) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    } else null

                    val rightIcon = if (swipeRightAction == SwipeAction.MARK_READ) {
                        if (bookmark.isRead) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    } else null

                    SwipeableBookmarkItem(
                        leftSwipeAction = swipeLeftAction,
                        rightSwipeAction = swipeRightAction,
                        leftIcon = leftIcon,
                        rightIcon = rightIcon,
                        onActionTriggered = { action ->
                            onSwipeAction(bookmark, action)
                        }
                    ) {
                        // Construct banner and screenshot URLs if available
                        val bannerImageUrl = if (serverUrl != null && bookmark.bannerImageAssetId != null) {
                            com.karakept.app.utils.AssetUrlUtils.getAssetUrl(serverUrl, bookmark.bannerImageAssetId)
                        } else null

                        val screenshotUrl = if (serverUrl != null && bookmark.screenshotAssetId != null) {
                            com.karakept.app.utils.AssetUrlUtils.getAssetUrl(serverUrl, bookmark.screenshotAssetId)
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
                                onClick = remember(bookmark.localId) {
                                    { onBookmarkClick(bookmark) }
                                },
                                onLongClick = { onBookmarkLongClick(bookmark) },
                                showReadingTime = showReadingTimeBadge,
                                showTags = showTags,
                                dimRead = dimReadBookmarks,
                                bannerImageUrl = bannerImageUrl,
                                screenshotUrl = screenshotUrl
                            )
                            LayoutType.LIST -> BookmarkListLayout(
                                bookmark = bookmark,
                                onClick = remember(bookmark.localId) {
                                    { onBookmarkClick(bookmark) }
                                },
                                onLongClick = { onBookmarkLongClick(bookmark) },
                                showReadingTime = showReadingTimeBadge,
                                showTags = showTags,
                                dimRead = dimReadBookmarks,
                                bannerImageUrl = bannerImageUrl,
                                screenshotUrl = screenshotUrl
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
