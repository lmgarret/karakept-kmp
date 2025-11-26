package com.karakept.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.ArchiveModeBadge
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.HeroImageBanner
import com.karakept.app.ui.components.HtmlContent
import com.karakept.app.ui.components.ViewerModeToggle

data class BookmarkViewerScreen(val bookmarkId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<BookmarkViewerScreenModel>()

        val loadingState by screenModel.loadingState.collectAsState()
        val viewerMode by screenModel.viewerMode.collectAsState()

        var showModeDialog by remember { mutableStateOf(false) }

        LaunchedEffect(bookmarkId) {
            screenModel.loadBookmark(bookmarkId)
        }

        Scaffold(
            // topBar removed for custom parallax header implementation
        ) { padding ->
            when (val state = loadingState) {
                is BookmarkLoadingState.Initial -> {
                    BookmarkContentLoader(
                        loadingState = state,
                        modifier = Modifier.padding(padding)
                    )
                }
                is BookmarkLoadingState.TitleLoaded -> {
                    BookmarkContentLoader(
                        loadingState = state,
                        modifier = Modifier.padding(padding)
                    )
                }
                is BookmarkLoadingState.ThumbnailLoaded -> {
                    // Similar structure to FullyLoaded but with loading content
                    // We can reuse the parallax structure here too for consistency
                    Box(modifier = Modifier.fillMaxSize()) {
                         HeroImageBanner(
                            imageUrl = state.imageUrl,
                            title = state.title,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 300.dp) // Offset by banner height
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                             BookmarkContentLoader(
                                loadingState = state,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is BookmarkLoadingState.FullyLoaded -> {
                    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val bannerHeight = 320.dp
                    val toolbarHeight = 64.dp
                    
                    // Calculate scroll progress for parallax and sticky title
                    // We use derivedStateOf to minimize recompositions
                    val showStickyTitle by remember {
                        androidx.compose.runtime.derivedStateOf {
                            val firstVisibleItemIndex = scrollState.firstVisibleItemIndex
                            val firstVisibleItemScrollOffset = scrollState.firstVisibleItemScrollOffset
                            
                            // Show sticky title when we've scrolled past the banner
                            firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 300
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Parallax Header (Behind the list)
                        // We only render this if the first item is visible to save resources
                        if (scrollState.firstVisibleItemIndex == 0) {
                            Box(
                                modifier = Modifier
                                    .height(bannerHeight)
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        // Parallax effect: translate Y by half the scroll offset
                                        translationY = -scrollState.firstVisibleItemScrollOffset * 0.5f
                                        alpha = 1f - (scrollState.firstVisibleItemScrollOffset / 1000f).coerceIn(0f, 1f)
                                    }
                            ) {
                                HeroImageBanner(
                                    imageUrl = state.bookmark.imageUrl,
                                    title = state.bookmark.title,
                                    url = state.bookmark.url
                                )
                            }
                        }

                        // Content List
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Transparent spacer for the header
                            item {
                                Spacer(modifier = Modifier.height(bannerHeight))
                            }

                            // Content Body
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(16.dp)
                                ) {
                                    // Archive mode badge
                                    if (viewerMode == ViewerMode.ARCHIVE) {
                                        ArchiveModeBadge(
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                    }

                                    // Render HTML content
                                    HtmlContent(
                                        html = state.bookmark.content,
                                        viewerMode = viewerMode,
                                        onLinkClick = { url ->
                                            navigator.push(WebViewScreen(url))
                                        }
                                    )
                                    
                                    // Add extra padding at bottom for better scrolling experience
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                        
                        // Custom Top Bar (Overlay)
                        // We implement a custom top bar to handle the sticky title transition
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(toolbarHeight)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(
                                        alpha = if (showStickyTitle) 1f else 0f
                                    )
                                )
                                .align(Alignment.TopCenter)
                        ) {
                            // Back Button (Always visible, changes color based on background)
                            IconButton(
                                onClick = { navigator.pop() },
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            }
                            
                            // Sticky Title
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showStickyTitle,
                                enter = androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.fadeOut(),
                                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp)
                            ) {
                                Text(
                                    text = state.bookmark.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Viewer Mode Toggle
                            IconButton(
                                onClick = { showModeDialog = true },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Viewer Mode",
                                    tint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            }
                        }
                    }
                }
                is BookmarkLoadingState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Viewer mode dialog
        if (showModeDialog) {
            AlertDialog(
                onDismissRequest = { showModeDialog = false },
                title = { Text("Viewer Mode") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Choose how to display bookmark content:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ViewerModeToggle(
                            currentMode = viewerMode,
                            onModeChange = { mode ->
                                screenModel.setViewerMode(mode)
                                showModeDialog = false
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModeDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
