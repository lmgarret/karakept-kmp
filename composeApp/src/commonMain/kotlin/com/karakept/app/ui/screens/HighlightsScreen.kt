package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.ui.components.HighlightCard
import kotlinx.coroutines.launch

@Serializable
class HighlightsScreen : NavKey {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<HighlightsScreenModel>()
        val highlights by screenModel.highlights.collectAsState()
        val isSyncing by screenModel.isSyncing.collectAsState()
        val isLoadingMore by screenModel.isLoadingMore.collectAsState()
        val hasMoreItems by screenModel.hasMoreItems.collectAsState()
        val scope = rememberCoroutineScope()

        val listState = remember { LazyListState() }

        // Sync highlights when screen opens
        LaunchedEffect(Unit) {
            screenModel.syncHighlights()
        }

        // Detect when scrolled near end
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo }
                .collect { layoutInfo ->
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                    if (lastVisibleItem != null && totalItems > 0) {
                        val threshold = totalItems - 5
                        if (lastVisibleItem.index >= threshold && hasMoreItems && !isLoadingMore) {
                            screenModel.loadNextPage()
                        }
                    }
                }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("All Highlights") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = { screenModel.syncHighlights() },
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                if (highlights.isEmpty() && !isSyncing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No highlights yet", style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (highlights.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(highlights, key = { it.id }) { highlight ->
                            HighlightCard(
                                highlight = highlight,
                                onClick = {
                                    scope.launch {
                                        val bookmarkLocalId = screenModel.getBookmarkLocalIdForHighlight(highlight)
                                        if (bookmarkLocalId != null) {
                                            navigator.push(BookmarkViewerScreen(bookmarkLocalId, highlight.id))
                                        }
                                    }
                                },
                                onDelete = { screenModel.deleteHighlight(highlight) }
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
}
