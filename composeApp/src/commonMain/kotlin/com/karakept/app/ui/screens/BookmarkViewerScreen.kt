package com.karakept.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.components.BookmarkActionsMenu
import com.karakept.app.ui.components.WebModeBadge
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.HeroImageBanner
import com.karakept.app.ui.components.HtmlContent
import com.karakept.app.ui.components.ReaderAppearanceBottomPanel
import com.karakept.app.ui.components.ViewerModeToggle
import com.karakept.app.utils.ShareUtils
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import org.koin.compose.koinInject

data class BookmarkViewerScreen(val bookmarkId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<BookmarkViewerScreenModel>()
        val scope = rememberCoroutineScope()
        val serverRepository = koinInject<ServerRepository>()
        val uriHandler = LocalUriHandler.current

        val loadingState by screenModel.loadingState.collectAsState()
        val viewerMode by screenModel.viewerMode.collectAsState()
        val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
        val htmlTextColor by screenModel.htmlTextColor.collectAsState()
        val htmlBackgroundColor by screenModel.htmlBackgroundColor.collectAsState()
        val htmlFontSize by screenModel.htmlFontSize.collectAsState()
        val htmlFontFamily by screenModel.htmlFontFamily.collectAsState()
        val precrawledAssetPath by screenModel.precrawledAssetPath.collectAsState()
        val lists by screenModel.lists.collectAsState()
        val autoMarkReadOnScroll by screenModel.autoMarkReadOnScroll.collectAsState()

        var showModeDialog by remember { mutableStateOf(false) }
        var showAppearancePanel by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var fabExpanded by remember { mutableStateOf(false) }
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var showListPicker by remember { mutableStateOf(false) }
        var showTagEditor by remember { mutableStateOf(false) }

        val snackbarHostState = remember { SnackbarHostState() }
        var pendingSnackbarMessage by remember { mutableStateOf<String?>(null) }

        // Show pending snackbar when FAB menu closes (with delay for animation)
        LaunchedEffect(fabExpanded, pendingSnackbarMessage) {
            if (!fabExpanded && pendingSnackbarMessage != null) {
                kotlinx.coroutines.delay(400) // Wait for FAB close animation (300ms + buffer)
                snackbarHostState.showSnackbar(pendingSnackbarMessage!!)
                pendingSnackbarMessage = null
            }
        }

        // Dismiss any visible snackbar when FAB menu opens
        LaunchedEffect(fabExpanded) {
            if (fabExpanded) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }

        LaunchedEffect(bookmarkId) {
            screenModel.loadBookmark(bookmarkId)
            // Get the first available server to load lists
            serverRepository.servers.first().firstOrNull()?.let { server ->
                screenModel.loadLists(server)
            }
        }

        // Hoist state management OUTSIDE the when to prevent recomposition flash
        val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
        val bannerHeight = 320.dp
        val toolbarHeight = 56.dp

        // Track scroll direction for FAB visibility
        var previousScrollOffset by remember { mutableStateOf(0) }
        var fabVisible by remember { mutableStateOf(true) }

        // Track if we've already triggered auto-read for this session to prevent spam
        var hasTriggeredAutoRead by remember(bookmarkId) { mutableStateOf(false) }

        LaunchedEffect(scrollState.firstVisibleItemScrollOffset, scrollState.firstVisibleItemIndex) {
            val currentOffset = scrollState.firstVisibleItemIndex * 1000 + scrollState.firstVisibleItemScrollOffset
            val scrollingDown = currentOffset > previousScrollOffset

            // Hide FAB when scrolling down, show when scrolling up
            if (currentOffset > 100) { // Only hide after scrolling past 100px
                fabVisible = !scrollingDown || fabExpanded // Keep visible if expanded
            } else {
                fabVisible = true // Always show at top
            }

            previousScrollOffset = currentOffset
            
            // Check for auto-mark read
            if (!hasTriggeredAutoRead && autoMarkReadOnScroll && loadingState is BookmarkLoadingState.FullyLoaded) {
                 val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                 if (!fullyLoadedState.bookmark.isRead) {
                     val layoutInfo = scrollState.layoutInfo
                     val totalItems = layoutInfo.totalItemsCount
                     val visibleItemsInfo = layoutInfo.visibleItemsInfo
                     
                     if (visibleItemsInfo.isNotEmpty()) {
                         val lastVisibleItem = visibleItemsInfo.last()
                         // Check if we are near the end (last item is visible AND its bottom edge is near the viewport bottom)
                         val isLastItem = lastVisibleItem.index == totalItems - 1
                         
                         if (isLastItem) {
                             // layoutInfo.viewportEndOffset gives the height of the viewport
                             // lastVisibleItem.offset is the top position relative to viewport start
                             // lastVisibleItem.size is the height of the item
                             // So (offset + size) is the position of the bottom edge relative to viewport start
                             val itemBottom = lastVisibleItem.offset + lastVisibleItem.size
                             val viewportBottom = layoutInfo.viewportEndOffset
                             
                             // Trigger if the bottom of the content is within the viewport (with a small buffer of 50px)
                             if (itemBottom <= viewportBottom + 50) {
                                 screenModel.toggleBookmarkRead(fullyLoadedState.bookmark)
                                 pendingSnackbarMessage = "Marked as read"
                                 hasTriggeredAutoRead = true
                             }
                         }
                     }
                 }
            }
        }

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

        // Keep the last valid FullyLoaded state to prevent error flash during navigation
        var lastValidState by remember { mutableStateOf<BookmarkLoadingState>(loadingState) }

        LaunchedEffect(loadingState) {
            // Only update if we get a FullyLoaded state
            // This prevents Error states during navigation from showing
            if (loadingState is BookmarkLoadingState.FullyLoaded) {
                lastValidState = loadingState
            } else if (loadingState is BookmarkLoadingState.Initial) {
                lastValidState = loadingState
            }
            // Skip Error states during navigation - they'll be ignored
        }

        // Use the stable state for rendering
        val displayState = if (loadingState is BookmarkLoadingState.Error && lastValidState is BookmarkLoadingState.FullyLoaded) {
            lastValidState
        } else {
            loadingState
        }

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            floatingActionButton = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = fabVisible,
                    enter = androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) + androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ),
                    exit = androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                ) {
                    if (loadingState is BookmarkLoadingState.FullyLoaded) {
                        val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                        BookmarkFabMenu(
                            expanded = fabExpanded,
                            onExpandedChange = { fabExpanded = it },
                            bookmark = fullyLoadedState.bookmark,
                        onFavoriteClick = {
                            val willBeFavorited = !fullyLoadedState.bookmark.isStarred
                            screenModel.toggleBookmarkFavorite(fullyLoadedState.bookmark)
                            pendingSnackbarMessage = if (willBeFavorited) "Added to favorites" else "Removed from favorites"
                            fabExpanded = false
                        },
                        onArchiveClick = {
                            val willBeArchived = !fullyLoadedState.bookmark.isArchived
                            screenModel.toggleBookmarkArchive(fullyLoadedState.bookmark)
                            pendingSnackbarMessage = if (willBeArchived) "Archived" else "Unarchived"
                            fabExpanded = false
                        },
                        onReadClick = {
                            val willBeRead = !fullyLoadedState.bookmark.isRead
                            screenModel.toggleBookmarkRead(fullyLoadedState.bookmark)
                            pendingSnackbarMessage = if (willBeRead) "Marked as read" else "Marked as unread"
                            fabExpanded = false
                        },
                        onShareClick = {
                            ShareUtils.shareText(fullyLoadedState.bookmark.url, fullyLoadedState.bookmark.title)
                            pendingSnackbarMessage = "Shared"
                            fabExpanded = false
                        },
                        onOpenInBrowserClick = {
                            uriHandler.openUri(fullyLoadedState.bookmark.url)
                            pendingSnackbarMessage = "Opening in browser"
                            fabExpanded = false
                        }
                    )
                    }
                }
            }
        ) { padding ->
            when (val state = displayState) {
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
                    val readingTimeMinutes = when (state) {
                        is BookmarkLoadingState.FullyLoaded -> state.bookmark.readingTimeMinutes
                        else -> 0
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
                                    readingTimeMinutes = readingTimeMinutes,
                                    scrollProgress = (scrollState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f),
                                    onUrlClick = if (url.isNotEmpty()) {
                                        {
                                            try {
                                                uriHandler.openUri(url)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    } else null
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

                            // Description Card
                            if (isFullyLoaded) {
                                val fullyLoadedState = state as BookmarkLoadingState.FullyLoaded
                                val description = fullyLoadedState.bookmark.description
                                if (!description.isNullOrBlank()) {
                                    item(key = "description_card") {
                                        // Mix reader background with accent color for subtle highlight (20% accent, 80% reader background)
                                        val readerBg = htmlBackgroundColor ?: MaterialTheme.colorScheme.background
                                        val accent = MaterialTheme.colorScheme.primaryContainer
                                        val descriptionBgColor = androidx.compose.ui.graphics.lerp(readerBg, accent, 0.2f)

                                        // Mix reader text color with accent color for text (20% accent, 80% reader text)
                                        val readerTextColor = htmlTextColor ?: MaterialTheme.colorScheme.onBackground
                                        val accentTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        val descriptionTextColor = androidx.compose.ui.graphics.lerp(readerTextColor, accentTextColor, 0.2f)

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(htmlBackgroundColor ?: MaterialTheme.colorScheme.background)
                                        ) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = descriptionBgColor
                                                )
                                            ) {
                                                Text(
                                                    text = description,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = androidx.compose.ui.unit.TextUnit(
                                                            htmlFontSize.toFloat(),
                                                            androidx.compose.ui.unit.TextUnitType.Sp
                                                        )
                                                    ),
                                                    color = descriptionTextColor,
                                                    fontFamily = htmlFontFamily.composeFontFamily,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp)
                                                )
                                        }
                                        }
                                    }
                                }
                            }

                            // Content Body - Use Crossfade for smooth transition without recomposition
                            item(key = "content_body") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(htmlBackgroundColor ?: MaterialTheme.colorScheme.background)
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
                                                customFontFamily = htmlFontFamily,
                                                localFilePath = if (viewerMode == ViewerMode.WEB) precrawledAssetPath else null
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (url.isNotEmpty()) {
                                        AsyncImage(
                                            model = com.karakept.app.utils.FaviconUtils.getFaviconUrl(url),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
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

                                    // Divider to separate sections
                                    androidx.compose.material3.HorizontalDivider()

                                    // Move to List
                                    DropdownMenuItem(
                                        text = { Text("Move to List") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showListPicker = true
                                            showMenu = false
                                        }
                                    )

                                    // Edit Tags
                                    DropdownMenuItem(
                                        text = { Text("Edit Tags") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showTagEditor = true
                                            showMenu = false
                                        }
                                    )

                                    // Delete (destructive action)
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showDeleteConfirmation = true
                                            showMenu = false
                                        },
                                        colors = androidx.compose.material3.MenuDefaults.itemColors(
                                            textColor = MaterialTheme.colorScheme.error
                                        )
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

        // Back Handler for panels
        androidx.activity.compose.BackHandler(enabled = showAppearancePanel || showModeDialog) {
            if (showAppearancePanel) showAppearancePanel = false
            if (showModeDialog) showModeDialog = false
        }

        // Viewer mode dialog
        if (showModeDialog) {
            AlertDialog(
                onDismissRequest = { showModeDialog = false },
                title = { Text("Viewer Mode") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Choose how to display bookmark content:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        ViewerModeOptionCard(
                            title = "Reader",
                            description = "Sanitized content with safe HTML only",
                            icon = Icons.AutoMirrored.Filled.ChromeReaderMode,
                            isSelected = viewerMode == ViewerMode.READER,
                            onClick = {
                                screenModel.setViewerMode(ViewerMode.READER)
                                showModeDialog = false
                            }
                        )

                        ViewerModeOptionCard(
                            title = "Web",
                            description = "Web view with original HTML and stylesheets (JavaScript disabled)",
                            icon = Icons.Default.Public,
                            isSelected = viewerMode == ViewerMode.WEB,
                            onClick = {
                                screenModel.setViewerMode(ViewerMode.WEB)
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

        // Reader appearance bottom panel
        // Scrim
        androidx.compose.animation.AnimatedVisibility(
            visible = showAppearancePanel,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        showAppearancePanel = false
                    }
            )
        }

        // Panel (always in composition, visibility controlled by prop for animation)
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

        // Delete Confirmation Dialog
        if (showDeleteConfirmation && loadingState is BookmarkLoadingState.FullyLoaded) {
            val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Bookmark?") },
                text = { Text("This action cannot be undone. The bookmark will be permanently deleted from the server.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.deleteBookmark(fullyLoadedState.bookmark) {
                                navigator.pop()
                            }
                            showDeleteConfirmation = false
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // List Picker Dialog
        if (showListPicker && loadingState is BookmarkLoadingState.FullyLoaded && lists.isNotEmpty()) {
            val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
            com.karakept.app.ui.components.ListPickerDialog(
                lists = lists,
                currentListIds = fullyLoadedState.bookmark.listIds.split(",").filter { it.isNotBlank() },
                onListSelected = { listId ->
                    screenModel.moveBookmarkToList(fullyLoadedState.bookmark, listId)
                    showListPicker = false
                },
                onDismiss = { showListPicker = false }
            )
        }

        // Tag Editor Dialog
        if (showTagEditor && loadingState is BookmarkLoadingState.FullyLoaded) {
            val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
            com.karakept.app.ui.components.TagEditorDialog(
                currentTags = fullyLoadedState.bookmark.tags.split(",").filter { it.isNotBlank() },
                onTagsUpdated = { newTags ->
                    screenModel.updateBookmarkTags(fullyLoadedState.bookmark, newTags)
                    showTagEditor = false
                },
                onDismiss = { showTagEditor = false }
            )
        }
    }
}

@Composable
private fun ViewerModeOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BookmarkFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bookmark: com.karakept.app.data.local.entity.BookmarkEntity,
    onFavoriteClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onReadClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action FABs (5 actions - within M3 guidelines)
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorite toggle - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onFavoriteClick,
                    containerColor = if (bookmark.isStarred) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (bookmark.isStarred) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = bookmark.isStarred,
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) { isStarred ->
                        Icon(
                            imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.animation.Crossfade(
                        targetState = bookmark.isStarred,
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) { isStarred ->
                        Text(if (isStarred) "Unfavorite" else "Favorite")
                    }
                }

                // Archive toggle - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onArchiveClick,
                    containerColor = if (bookmark.isArchived) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (bookmark.isArchived) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = bookmark.isArchived,
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) { isArchived ->
                        Icon(
                            imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.animation.Crossfade(
                        targetState = bookmark.isArchived,
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) { isArchived ->
                        Text(if (isArchived) "Unarchive" else "Archive")
                    }
                }

                // Read toggle - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onReadClick,
                    containerColor = if (bookmark.isRead) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (bookmark.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = bookmark.isRead,
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) { isRead ->
                        Icon(
                            imageVector = if (isRead) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.animation.Crossfade(
                        targetState = bookmark.isRead,
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) { isRead ->
                        Text(if (isRead) "Mark unread" else "Mark read")
                    }
                }

                // Share action - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onShareClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }

                // Open in browser action - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onOpenInBrowserClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open")
                }
            }
        }

        // Main FAB with rotation animation
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) }
        ) {
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            )

            Box(
                modifier = Modifier.graphicsLayer {
                    rotationZ = rotation
                }
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = expanded,
                    animationSpec = androidx.compose.animation.core.tween(200)
                ) { isExpanded ->
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Bookmark,
                        contentDescription = if (isExpanded) "Close menu" else "Bookmark actions"
                    )
                }
            }
        }
    }
}
