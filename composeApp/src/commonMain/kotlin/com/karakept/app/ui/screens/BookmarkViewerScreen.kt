package com.karakept.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.WebModeBadge
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.HeroImageBanner
import com.karakept.app.ui.components.HtmlContent
import com.karakept.app.ui.components.ReaderAppearanceBottomPanel
import com.karakept.app.ui.components.ViewerModeToggle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

data class BookmarkViewerScreen(val bookmarkId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<BookmarkViewerScreenModel>()
        val scope = rememberCoroutineScope()

        val loadingState by screenModel.loadingState.collectAsState()
        val viewerMode by screenModel.viewerMode.collectAsState()
        val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
        val htmlTextColor by screenModel.htmlTextColor.collectAsState()
        val htmlBackgroundColor by screenModel.htmlBackgroundColor.collectAsState()
        val htmlFontSize by screenModel.htmlFontSize.collectAsState()
        val htmlFontFamily by screenModel.htmlFontFamily.collectAsState()

        var showModeDialog by remember { mutableStateOf(false) }
        var showAppearancePanel by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }

        LaunchedEffect(bookmarkId) {
            screenModel.loadBookmark(bookmarkId)
        }

        // Hoist state management OUTSIDE the when to prevent recomposition flash
        val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
        val bannerHeight = 320.dp
        val toolbarHeight = 56.dp
        
        // Calculate when to show sticky title based on banner height
        // Show when scrolled past banner minus the space for status bar + top bar
        val density = LocalDensity.current
        val bannerHeightPx = with(density) { bannerHeight.toPx() }
        val toolbarHeightPx = with(density) { toolbarHeight.toPx() }
        val statusBarInsets = WindowInsets.statusBars.getTop(density)
        val showStickyTitleThreshold = (bannerHeightPx - toolbarHeightPx - statusBarInsets).toInt()
        
        // Calculate scroll progress - this remains stable across state transitions
        val showStickyTitle by remember(bookmarkId) {
            androidx.compose.runtime.derivedStateOf {
                scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > showStickyTitleThreshold
            }
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
                is BookmarkLoadingState.FullyLoaded -> {
                    // Use same UI structure for both states to prevent flashing
                    
                    // Extract data based on state type
                    val title = when (state) {
                        is BookmarkLoadingState.FullyLoaded -> state.bookmark.title
                        else -> ""
                    }
                    val imageUrl = when (state) {
                        is BookmarkLoadingState.FullyLoaded -> state.bookmark.imageUrl
                        else -> null
                    }
                    val url = when (state) {
                        is BookmarkLoadingState.FullyLoaded -> state.bookmark.url
                        else -> ""
                    }
                    val isFullyLoaded = state is BookmarkLoadingState.FullyLoaded
                    
                    // Track when HTML content is truly ready (processed + rendered)
                    var htmlContentReady by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Parallax Header (Behind the list) - Stable across state transitions
                        if (scrollState.firstVisibleItemIndex == 0) {
                            Box(
                                modifier = Modifier
                                    .height(bannerHeight)
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = -scrollState.firstVisibleItemScrollOffset * 0.5f
                                        alpha = 1f - (scrollState.firstVisibleItemScrollOffset / 1000f).coerceIn(0f, 1f)
                                    }
                            ) {
                                HeroImageBanner(
                                    imageUrl = imageUrl,
                                    title = title,
                                    url = url,
                                    scrollProgress = (scrollState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
                                )
                            }
                        }

                        // Content List - Stable LazyColumn with stable scrollState
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Transparent spacer for the header
                            item(key = "header_spacer") {
                                Spacer(modifier = Modifier.height(bannerHeight))
                            }

                            // Content Body - Use Crossfade for smooth transition without recomposition
                            item(key = "content_body") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(16.dp)
                                ) {
                                    // Web mode badge
                                    if (htmlContentReady && viewerMode == ViewerMode.WEB) {
                                        WebModeBadge(
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                    }

                                    // Render content area with overlay approach
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        // Always render HtmlContent when data arrives (bottom layer)
                                        if (isFullyLoaded) {
                                            val fullyLoadedState = state as BookmarkLoadingState.FullyLoaded
                                            HtmlContent(
                                                html = fullyLoadedState.bookmark.content,
                                                viewerMode = viewerMode,
                                                hideArticleThumbnails = hideArticleThumbnails,
                                                onLinkClick = { url ->
                                                    navigator.push(WebViewScreen(url))
                                                },
                                                onReady = { htmlContentReady = true },
                                                customTextColor = htmlTextColor,
                                                customBackgroundColor = htmlBackgroundColor,
                                                customFontSize = htmlFontSize,
                                                customFontFamily = htmlFontFamily
                                            )
                                        }
                                        
                                        // Show skeleton on top until content ready (top layer)
                                        if (!htmlContentReady) {
                                            BookmarkContentLoader(
                                                loadingState = state,
                                                modifier = Modifier.background(MaterialTheme.colorScheme.background)
                                            )
                                        }
                                    }
                                    
                                    // Add extra padding at bottom
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                        
                        
                        
                        
                        // Status bar background - fades in with top bar for parallax effect
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                                .background(
                                    color = MaterialTheme.colorScheme.background.copy(
                                        alpha = if (showStickyTitle) 1f else 0f
                                    )
                                )
                                .align(Alignment.TopCenter)
                        )
                        
                        // Custom Top Bar (Overlay) - Stable across state transitions
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(toolbarHeight)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(
                                        alpha = if (showStickyTitle) 1f else 0f
                                    )
                                )
                                .align(Alignment.TopCenter)
                        ) {
                            // Back Button
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
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Menu button
                            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Reader Appearance") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Palette,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showAppearancePanel = true
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Viewer Mode") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showModeDialog = true
                                            showMenu = false
                                        }
                                    )
                                }
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

        // Reader appearance bottom panel overlay with click-outside-to-close
        if (showAppearancePanel) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Clickable overlay above the panel
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showAppearancePanel = false
                        }
                )

                // Bottom panel (not clickable to close)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ReaderAppearanceBottomPanel(
                        visible = showAppearancePanel,
                        textColor = htmlTextColor,
                        backgroundColor = htmlBackgroundColor,
                        fontSize = htmlFontSize,
                        fontFamily = htmlFontFamily,
                        onTextColorChange = { screenModel.setHtmlTextColor(it) },
                        onBackgroundColorChange = { screenModel.setHtmlBackgroundColor(it) },
                        onFontSizeChange = { screenModel.setHtmlFontSize(it) },
                        onFontFamilyChange = { screenModel.setHtmlFontFamily(it) },
                        onReset = {
                            scope.launch {
                                screenModel.resetReaderAppearance()
                            }
                        },
                        onDismiss = { showAppearancePanel = false }
                    )
                }
            }
        }
    }
}
