package com.karakept.app.ui.screens.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onRefresh: () -> Unit
) {
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
                        when (layoutType) {
                            LayoutType.CARD -> BookmarkCardLayout(
                                bookmark = bookmark,
                                onClick = remember(bookmark.localId) {
                                    { onBookmarkClick(bookmark) }
                                },
                                onLongClick = { onBookmarkLongClick(bookmark) },
                                showReadingTime = showReadingTimeBadge,
                                showTags = showTags,
                                dimRead = dimReadBookmarks
                            )
                            LayoutType.LIST -> BookmarkListLayout(
                                bookmark = bookmark,
                                onClick = remember(bookmark.localId) {
                                    { onBookmarkClick(bookmark) }
                                },
                                onLongClick = { onBookmarkLongClick(bookmark) },
                                showReadingTime = showReadingTimeBadge,
                                showTags = showTags,
                                dimRead = dimReadBookmarks
                            )
                        }
                    }
                }
            }
        }

        if (isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            )
        }

        PullRefreshIndicator(
            refreshing = isSyncing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
