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
import com.karakept.app.data.model.CustomSwipeActionConfig
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
    offlineMode: Boolean = false,
    pendingBookmarkRemoteIds: Set<Long> = emptySet(),
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
                    if (bookmark.remoteId in pendingBookmarkRemoteIds) {
                        BookmarkPlaceholderItem(url = bookmark.url)
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

                    SwipeableBookmarkItem(
                        leftSwipeAction = swipeLeftAction,
                        rightSwipeAction = swipeRightAction,
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
                                onClick = remember(bookmark.localId) {
                                    { onBookmarkClick(bookmark) }
                                },
                                onLongClick = { onBookmarkLongClick(bookmark) },
                                showReadingTime = showReadingTimeBadge,
                                showReadingProgress = showReadingProgress,
                                showTags = showTags,
                                dimRead = dimReadBookmarks,
                                offlineMode = offlineMode,
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
                                showReadingProgress = showReadingProgress,
                                showTags = showTags,
                                dimRead = dimReadBookmarks,
                                offlineMode = offlineMode,
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
