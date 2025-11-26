package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            topBar = {
                TopAppBar(
                    title = {
                        when (val state = loadingState) {
                            is BookmarkLoadingState.TitleLoaded -> Text(state.title)
                            is BookmarkLoadingState.ThumbnailLoaded -> Text(state.title)
                            is BookmarkLoadingState.FullyLoaded -> Text(state.bookmark.title)
                            else -> Text("Loading...")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Viewer mode toggle button
                        IconButton(onClick = { showModeDialog = true }) {
                            Icon(Icons.Default.Visibility, contentDescription = "Viewer Mode")
                        }
                    }
                )
            }
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
                    Column(modifier = Modifier.padding(padding)) {
                        HeroImageBanner(
                            imageUrl = state.imageUrl,
                            title = state.title
                        )
                        BookmarkContentLoader(
                            loadingState = state,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is BookmarkLoadingState.FullyLoaded -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        HeroImageBanner(
                            imageUrl = state.bookmark.imageUrl,
                            title = state.bookmark.title
                        )

                        // Archive mode badge
                        if (viewerMode == ViewerMode.ARCHIVE) {
                            ArchiveModeBadge(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = state.bookmark.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Render HTML content
                            HtmlContent(
                                html = state.bookmark.content,
                                viewerMode = viewerMode,
                                onLinkClick = { url ->
                                    navigator.push(WebViewScreen(url))
                                }
                            )
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
