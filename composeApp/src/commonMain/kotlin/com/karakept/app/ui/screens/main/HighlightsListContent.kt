package com.karakept.app.ui.screens.main

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Highlight
import com.karakept.app.ui.components.HighlightCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsListContent(
    highlights: List<Highlight>,
    isSyncing: Boolean,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    activeHighlightId: String?,
    onHighlightClick: (Highlight) -> Unit,
    onDeleteHighlight: (Highlight) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    showRefreshButton: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null
) {
    val listState = remember { LazyListState() }

    // Detect when scrolled near end
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                if (lastVisibleItem != null && totalItems > 0) {
                    val threshold = totalItems - 5
                    if (lastVisibleItem.index >= threshold && hasMoreItems && !isLoadingMore) {
                        onLoadMore()
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Highlights") },
                navigationIcon = {
                    if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (showRefreshButton && onRefresh != null) {
                        IconButton(onClick = onRefresh, enabled = !isSyncing) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { onRefresh?.invoke() },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (highlights.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator()
                    } else {
                        Text("No highlights yet", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(highlights, key = { it.id }) { highlight ->
                        val isActive = highlight.id == activeHighlightId
                        HighlightCard(
                            highlight = highlight,
                            onClick = { onHighlightClick(highlight) },
                            onDelete = { onDeleteHighlight(highlight) },
                            modifier = if (isActive) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.tertiary,
                                        RoundedCornerShape(12.dp)
                                    )
                            } else {
                                Modifier
                            }
                        )
                    }

                    // Loading indicator at bottom
                    if (isLoadingMore) {
                        item(contentType = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    // End of list indicator
                    if (!hasMoreItems && highlights.isNotEmpty()) {
                        item(contentType = "end") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No more highlights",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
