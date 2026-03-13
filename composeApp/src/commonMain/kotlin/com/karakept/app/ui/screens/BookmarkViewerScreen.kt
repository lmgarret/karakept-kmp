package com.karakept.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.rememberCustomTabOpener
import com.karakept.app.ui.screens.viewer.*
import com.karakept.app.utils.ShareUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.SnackbarEvent

data class BookmarkViewerScreen(
    val bookmarkId: Long,
    val scrollToHighlightId: String? = null
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<BookmarkViewerScreenModel>()
        val mainScreenModel = koinInject<MainScreenModel>()
        val scope = rememberCoroutineScope()
        val serverRepository = koinInject<ServerRepository>()
        val uriHandler = LocalUriHandler.current
        val openInCustomTab = rememberCustomTabOpener()
        val snackbarManager = koinInject<ActionSnackbarManager>()

        val loadingState by screenModel.loadingState.collectAsState()
        val viewerMode by screenModel.viewerMode.collectAsState()
        val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
        val htmlTextColor by screenModel.htmlTextColor.collectAsState()
        val htmlBackgroundColor by screenModel.htmlBackgroundColor.collectAsState()
        val htmlFontSize by screenModel.htmlFontSize.collectAsState()
        val htmlFontFamily by screenModel.htmlFontFamily.collectAsState()
        val precrawledAssetPath by screenModel.precrawledAssetPath.collectAsState()
        val lists by screenModel.lists.collectAsState()
        val showTags by screenModel.showTags.collectAsState()
        val isRefreshing by screenModel.isRefreshing.collectAsState()
        val offlineMode by screenModel.offlineMode.collectAsState()
        val highlights by screenModel.highlights.collectAsState()
        val linkOpenMode by screenModel.linkOpenMode.collectAsState()
        val trackReadingProgress by screenModel.trackReadingProgress.collectAsState()

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = { screenModel.refreshBookmark(bookmarkId) }
        )

        var showModeDialog by remember { mutableStateOf(false) }
        var showAppearancePanel by remember { mutableStateOf(false) }
        var showDetailsPanel by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var fabExpanded by remember { mutableStateOf(false) }
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var showTagEditor by remember { mutableStateOf(false) }
        var showListPicker by remember { mutableStateOf(false) }
        var selectedHighlightId by remember { mutableStateOf<String?>(null) }
        var highlightPosition by remember { mutableStateOf<com.karakept.app.ui.components.HighlightPosition?>(null) }
        // Track the text of the selected highlight for matching after ID changes (temp -> server ID)
        var selectedHighlightText by remember { mutableStateOf<String?>(null) }

        // Use derivedStateOf for better reactivity when highlights list updates
        val selectedHighlight by remember {
            androidx.compose.runtime.derivedStateOf {
                val id = selectedHighlightId ?: return@derivedStateOf null
                // First try to find by exact ID
                highlights.find { it.id == id }
                    // If not found and we have a temp ID, try to find by text content
                    ?: if (id.startsWith("temp_") && selectedHighlightText != null) {
                        highlights.find { it.text == selectedHighlightText }
                    } else null
            }
        }

        // Update selectedHighlightId when the highlight is found by text (temp ID was replaced)
        LaunchedEffect(selectedHighlight?.id, selectedHighlightId) {
            val highlight = selectedHighlight
            val currentId = selectedHighlightId
            if (highlight != null && currentId != null && highlight.id != currentId) {
                // The highlight was found by text match but has a different ID (synced from server)
                selectedHighlightId = highlight.id
            }
        }

        val snackbarHostState = rememberSnackbarHostStateWithDelay(
            snackbarManager = snackbarManager,
            fabExpanded = fabExpanded
        )

        LaunchedEffect(bookmarkId) {
            screenModel.loadBookmark(bookmarkId)
        }

        LaunchedEffect(showListPicker) {
            if (showListPicker) {
                serverRepository.servers.first().firstOrNull()?.let { server ->
                    screenModel.loadLists(server)
                }
            }
        }

        // Use a plain (non-saveable) LazyListState so the list always starts at (0,0)
        // on a fresh open. rememberLazyListState() uses rememberSaveable internally,
        // which restores the previous scroll position from any earlier visit — causing a
        // visible jump before our explicit reading-progress restoration can run.
        // Our reading-progress code already handles scroll restoration from the DB, so
        // persisting via rememberSaveable is both redundant and harmful here.
        val scrollState = remember { LazyListState() }
        val density = LocalDensity.current

        // --- SCROLL GUARD ---
        // Prevents unexpected scroll jumps caused by internal Compose mechanisms
        // (e.g., SelectionContainer/Focus requesting item scroll).
        var approvedIndex by remember { mutableStateOf(0) }
        var approvedOffset by remember { mutableStateOf(0) }

        // Increase threshold for accidental jumps: focus jumps usually skip dozens of items or thousands of pixels.
        // Also: ensure scrollState.isScrollInProgress handles dragging.
        val safeScrollToItem: suspend (Int, Int) -> Unit = { index, offset ->
            approvedIndex = index
            approvedOffset = offset
            scrollState.scrollToItem(index, offset)
            kotlinx.coroutines.yield()
            approvedIndex = scrollState.firstVisibleItemIndex
            approvedOffset = scrollState.firstVisibleItemScrollOffset
        }

        LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
            if (scrollState.isScrollInProgress) {
                approvedIndex = scrollState.firstVisibleItemIndex
                approvedOffset = scrollState.firstVisibleItemScrollOffset
            } else {
                val jumped = (kotlin.math.abs(scrollState.firstVisibleItemIndex - approvedIndex) > 0) || 
                             (kotlin.math.abs(scrollState.firstVisibleItemScrollOffset - approvedOffset) > 50)
                if (jumped) {
                    println("ScrollGuard: Unintended jump to ${scrollState.firstVisibleItemIndex}:${scrollState.firstVisibleItemScrollOffset}. " +
                        "Snapping back to $approvedIndex:$approvedOffset")
                    scrollState.scrollToItem(approvedIndex, approvedOffset)
                } else {
                    // Gradual or small valid updates (e.g. layout shifts) become the new approved state
                    approvedIndex = scrollState.firstVisibleItemIndex
                    approvedOffset = scrollState.firstVisibleItemScrollOffset
                }
            }
        }
        // --------------------

        // Scroll the LazyColumn to the highlight when navigating from the Highlights screen.
        // highlightPositionReceived flips to true once the WebView responds with a position
        // (via onHighlightPosition), which triggers the scroll LaunchedEffect below.
        var highlightPositionReceived by remember { mutableStateOf(false) }
        LaunchedEffect(highlightPositionReceived) {
            if (!highlightPositionReceived) return@LaunchedEffect
            val state = loadingState as? BookmarkLoadingState.FullyLoaded ?: return@LaunchedEffect
            val contentBodyIndex = if (!state.bookmark.description.isNullOrBlank()) 2 else 1
            val position = highlightPosition
            if (position != null) {
                // Convert CSS pixels (from getBoundingClientRect) to screen pixels for LazyList offset.
                // Subtract 120dp so the highlight is not pinned flush to the top of the screen.
                val highlightCssPx = position.y + position.scrollY
                val offsetPx = with(density) {
                    maxOf(0, highlightCssPx.dp.roundToPx() - 120.dp.roundToPx())
                }
                scrollState.animateScrollToItem(contentBodyIndex, offsetPx)
            } else {
                // Highlight position unavailable – scroll to content body at least
                scrollState.animateScrollToItem(contentBodyIndex, 0)
            }
        }
        val bannerHeight = 320.dp
        val toolbarHeight = 56.dp

        // Track when the WebView has fully rendered its HTML content.
        // This is essential for scroll restoration: the LazyColumn item containing the
        // WebView has near-zero height until the WebView finishes loading and measuring,
        // so scrollToItem(index, offset) silently fails if called too early.
        var contentRendered by remember { mutableStateOf(false) }

        // Reset contentRendered when loading state changes away from FullyLoaded
        LaunchedEffect(loadingState) {
            if (loadingState !is BookmarkLoadingState.FullyLoaded) {
                contentRendered = false
            }
        }

        // Restore scroll position once content is fully rendered.
        // For READER mode (native renderer), content renders immediately — no delays needed.
        // For WEB mode (WebView), we must wait for the WebView to measure its height.
        var hasRestoredScroll by remember { mutableStateOf(false) }
        val isNativeRenderer = viewerMode == ViewerMode.READER
        LaunchedEffect(loadingState, trackReadingProgress, contentRendered) {
            if (!hasRestoredScroll && trackReadingProgress && loadingState is BookmarkLoadingState.FullyLoaded) {
                val bookmark = (loadingState as BookmarkLoadingState.FullyLoaded).bookmark
                if (bookmark.readingProgress > 0f && !bookmark.content.isNullOrBlank()) {
                    if (contentRendered) {
                        if (isNativeRenderer) {
                            // Native renderer content is composed immediately but
                            // LazyColumn needs one frame to measure item heights.
                            kotlinx.coroutines.yield()
                        } else {
                            // Allow the WebView's measured height to propagate through
                            // the Compose layout system before scrolling.
                            delay(300)
                        }
                        safeScrollToItem(
                            bookmark.readingScrollIndex,
                            bookmark.readingScrollOffset
                        )
                        if (!isNativeRenderer) {
                            // WebView may not have its full height yet — retry
                            for (attempt in 1..3) {
                                val offsetOk = bookmark.readingScrollOffset < 200 ||
                                    scrollState.firstVisibleItemScrollOffset >= bookmark.readingScrollOffset / 3
                                if (scrollState.firstVisibleItemIndex == bookmark.readingScrollIndex && offsetOk) break
                                delay(250)
                                safeScrollToItem(
                                    bookmark.readingScrollIndex,
                                    bookmark.readingScrollOffset
                                )
                            }
                        }
                        hasRestoredScroll = true
                    }
                    // If !contentRendered, this effect will re-fire when contentRendered changes.
                } else if (bookmark.readingProgress == 0f) {
                    // Nothing to restore
                    hasRestoredScroll = true
                }
                // If readingProgress > 0 but content is still blank, don't mark as
                // restored — the LaunchedEffect will re-fire when content loads.
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

        // Custom hooks for scroll behavior
        val fabVisible = rememberFabVisibilityState(
            scrollState = scrollState,
            fabExpanded = fabExpanded
        )

        val showStickyTitle = rememberStickyTitleVisibility(
            scrollState = scrollState,
            bannerHeight = bannerHeight,
            toolbarHeight = toolbarHeight
        )

        val readingProgress = rememberReadingProgress(scrollState, bannerHeight, toolbarHeight)

        // Debug: log every scroll position change with context flags.
        LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
            println("ViewerScroll: scroll → index=${scrollState.firstVisibleItemIndex} " +
                "offset=${scrollState.firstVisibleItemScrollOffset} " +
                "hasRestoredScroll=$hasRestoredScroll " +
                "contentRendered=$contentRendered " +
                "highlightPositionReceived=$highlightPositionReceived")
        }

        // Push reading state to the screen model on every scroll change.
        // The screen model debounces DB writes internally (500 ms).
        LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
            if (trackReadingProgress && hasRestoredScroll && loadingState is BookmarkLoadingState.FullyLoaded) {
                val currentState = loadingState as BookmarkLoadingState.FullyLoaded
                if (readingProgress > 0f || scrollState.firstVisibleItemIndex > 0) {
                    screenModel.onReadingStateChanged(
                        localId = currentState.bookmark.localId,
                        remoteId = currentState.bookmark.remoteId,
                        progress = readingProgress,
                        scrollIndex = scrollState.firstVisibleItemIndex,
                        scrollOffset = scrollState.firstVisibleItemScrollOffset
                    )
                }
            }
        }

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = fabVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    if (loadingState is BookmarkLoadingState.FullyLoaded) {
                        val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                        BookmarkFabMenu(
                            expanded = fabExpanded,
                            onExpandedChange = { fabExpanded = it },
                            bookmark = fullyLoadedState.bookmark,
                            onFavoriteClick = {
                                screenModel.toggleBookmarkFavorite(fullyLoadedState.bookmark)
                                fabExpanded = false
                            },
                            onArchiveClick = {
                                screenModel.toggleBookmarkArchive(fullyLoadedState.bookmark)
                                fabExpanded = false
                            },
                            onReadClick = {
                                screenModel.toggleBookmarkRead(fullyLoadedState.bookmark)
                                fabExpanded = false
                            },
                            onShareClick = {
                                ShareUtils.shareText(fullyLoadedState.bookmark.url, fullyLoadedState.bookmark.title)
                                scope.launch {
                                    snackbarManager.showSnackbar("Shared")
                                }
                                fabExpanded = false
                            },
                            onOpenInBrowserClick = {
                                uriHandler.openUri(fullyLoadedState.bookmark.url)
                                scope.launch {
                                    snackbarManager.showSnackbar("Opening in browser")
                                }
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
                    // Extract data
                    val title = state.bookmark.title
                    val imageUrl = state.bookmark.imageUrl
                    val url = state.bookmark.url
                    val readingTimeMinutes = state.bookmark.readingTimeMinutes
                    val description = state.bookmark.description

                    // Get server info for banner and screenshot URLs
                    val servers by serverRepository.servers.collectAsState(initial = emptyList())
                    val server = servers.firstOrNull()
                    val bannerImageUrl = if (server != null && state.bookmark.bannerImageAssetId != null) {
                        val url = com.karakept.app.utils.AssetUrlUtils.getAssetUrl(server.url, state.bookmark.bannerImageAssetId)
                        url
                    } else null

                    val screenshotUrl = if (server != null && state.bookmark.screenshotAssetId != null) {
                        val url = com.karakept.app.utils.AssetUrlUtils.getAssetUrl(server.url, state.bookmark.screenshotAssetId)
                        url
                    } else null

                    // Local asset paths for offline support
                    val bannerImageLocalPath by screenModel.bannerImageLocalPath.collectAsState()
                    val screenshotLocalPath by screenModel.screenshotLocalPath.collectAsState()

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Content List
                        // Global dimming overlay when a highlight is selected
                        // Hide content until scroll position is restored to prevent a flash
                        // where the top of the article shows before jumping to the saved position.
                        val needsScrollRestore = trackReadingProgress &&
                            !hasRestoredScroll &&
                            loadingState is BookmarkLoadingState.FullyLoaded &&
                            (loadingState as BookmarkLoadingState.FullyLoaded).bookmark.readingProgress > 0f &&
                            !(loadingState as BookmarkLoadingState.FullyLoaded).bookmark.content.isNullOrBlank()
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (needsScrollRestore) Modifier.alpha(0f) else Modifier)
                        ) {
                            // Hero banner as first item so tag/URL clicks are not blocked by the list
                            item(key = "hero_banner") {
                                HeroBannerSection(
                                    title = title,
                                    url = url,
                                    tags = state.bookmark.tags,
                                    readingTimeMinutes = readingTimeMinutes,
                                    showTags = showTags,
                                    scrollState = scrollState,
                                    onUrlClick = if (url.isNotEmpty()) {
                                        {
                                            try {
                                                when (linkOpenMode) {
                                                    LinkOpenMode.CUSTOM_TAB -> openInCustomTab(url)
                                                    LinkOpenMode.EXTERNAL_BROWSER -> uriHandler.openUri(url)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    } else null,
                                    onTagClick = { tag ->
                                        mainScreenModel.applyTagFilter(tag, bookmarkId)
                                        navigator.pop()
                                    },
                                    onInfoClick = { showDetailsPanel = true },
                                    bannerImageUrl = bannerImageUrl,
                                    screenshotUrl = screenshotUrl,
                                    bannerImageLocalPath = bannerImageLocalPath,
                                    screenshotLocalPath = screenshotLocalPath
                                )
                            }

                            // Description Card
                            if (!description.isNullOrBlank()) {
                                item(key = "description_card") {
                                    DescriptionCard(
                                        description = description,
                                        htmlBackgroundColor = htmlBackgroundColor,
                                        htmlTextColor = htmlTextColor,
                                        htmlFontSize = htmlFontSize,
                                        htmlFontFamily = htmlFontFamily
                                    )
                                }
                            }

                            // Content Body
                            item(key = "content_body") {
                                ContentBodySection(
                                    content = state.bookmark.content,
                                    viewerMode = viewerMode,
                                    removeFirstImage = hideArticleThumbnails,
                                    htmlTextColor = htmlTextColor,
                                    htmlBackgroundColor = htmlBackgroundColor,
                                    htmlFontSize = htmlFontSize,
                                    htmlFontFamily = htmlFontFamily,
                                    precrawledAssetPath = precrawledAssetPath,
                                    loadingState = state,
                                    highlights = highlights,
                                    onLinkClick = { linkUrl ->
                                        try {
                                            when (linkOpenMode) {
                                                LinkOpenMode.CUSTOM_TAB -> openInCustomTab(linkUrl)
                                                LinkOpenMode.EXTERNAL_BROWSER -> uriHandler.openUri(linkUrl)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    onCreateHighlight = { text, start, end, note, color ->
                                        screenModel.createHighlight(state.bookmark, text, start, end, note, color) { highlightId ->
                                            selectedHighlightText = text  // Store text for matching after sync
                                            selectedHighlightId = highlightId
                                        }
                                    },
                                    onDeleteHighlight = { highlightId ->
                                        screenModel.deleteHighlight(state.bookmark, highlightId)
                                    },
                                    onHighlightClick = { id ->
                                        selectedHighlightId = id
                                    },
                                    onHighlightPosition = { id, position ->
                                        highlightPosition = position
                                        if (id == scrollToHighlightId && !highlightPositionReceived) {
                                            highlightPositionReceived = true
                                        }
                                    },
                                    onContentReady = { contentRendered = true },
                                    scrollToHighlightId = scrollToHighlightId,
                                    selectedHighlightId = selectedHighlightId
                                )
                            }
                        }

                        // Global Dimming Overlay
                        if (selectedHighlightId != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { selectedHighlightId = null }
                                    }
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(alpha = 0.99f) // Required for BlendMode.Clear
                                ) {
                                    // Draw the dimming layer
                                    drawRect(Color.Black.copy(alpha = 0.6f))

                                    val pos = highlightPosition
                                    if (pos != null && pos.path != null) {
                                        // Punch through the dimming layer to reveal the highlight
                                        withTransform({
                                            translate(pos.rootOffset.x, pos.rootOffset.y)
                                        }) {
                                            drawPath(
                                                path = pos.path!!,
                                                color = Color.Transparent,
                                                blendMode = BlendMode.Clear
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Top Bar
                        ViewerTopBar(
                            title = title,
                            url = url,
                            showStickyTitle = showStickyTitle,
                            showMenu = showMenu,
                            toolbarHeight = toolbarHeight,
                            readingProgress = if (trackReadingProgress) readingProgress else 0f,
                            onBackClick = { navigator.pop() },
                            onMenuToggle = { showMenu = it },
                            onAppearanceClick = { showAppearancePanel = true },
                            onViewerModeClick = { showModeDialog = true },
                            onMoveToListClick = { showListPicker = true },
                            onEditTagsClick = { showTagEditor = true },
                            onDeleteClick = { showDeleteConfirmation = true }
                        )

                        PullRefreshIndicator(
                            refreshing = isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .padding(padding)
                        )
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
        com.karakept.app.ui.components.BackHandler(enabled = showAppearancePanel || showModeDialog || selectedHighlightId != null || showDetailsPanel) {
            if (showDetailsPanel) showDetailsPanel = false
            else if (showAppearancePanel) showAppearancePanel = false
            else if (showModeDialog) showModeDialog = false
            else if (selectedHighlightId != null) selectedHighlightId = null
        }

        // Viewer mode dialog
        ViewerModeDialog(
            visible = showModeDialog,
            viewerMode = viewerMode,
            onModeSelected = { mode ->
                screenModel.setViewerMode(mode)
                showModeDialog = false
            },
            onDismiss = { showModeDialog = false }
        )

        // Reader appearance panel
        ReaderAppearancePanel(
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

        // Delete Confirmation Dialog
        if (showDeleteConfirmation && loadingState is BookmarkLoadingState.FullyLoaded) {
            val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
            DeleteConfirmationDialog(
                visible = true,
                onConfirm = {
                    screenModel.deleteBookmark(fullyLoadedState.bookmark) {
                        navigator.pop()
                    }
                    showDeleteConfirmation = false
                },
                onDismiss = { showDeleteConfirmation = false }
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

        // Highlight Details Panel
        // Only show when both ID is set AND the highlight is found to prevent blinking
        HighlightDetailsPanel(
            visible = selectedHighlightId != null && selectedHighlight != null,
            highlight = selectedHighlight,
            fontFamily = htmlFontFamily,
            fontSize = htmlFontSize,
            onUpdateHighlight = { id, note, color ->
                if (loadingState is BookmarkLoadingState.FullyLoaded) {
                    val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                    screenModel.updateHighlight(fullyLoadedState.bookmark, id, note, color)
                }
            },
            onDeleteHighlight = { id ->
                if (loadingState is BookmarkLoadingState.FullyLoaded) {
                    val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                    screenModel.deleteHighlight(fullyLoadedState.bookmark, id)
                }
                selectedHighlightId = null
            },
            onDismiss = {
                selectedHighlightId = null
                selectedHighlightText = null
            }
        )

        // Bookmark Details Panel (slides from the right)
        val detailsBookmark = (loadingState as? BookmarkLoadingState.FullyLoaded)?.bookmark
        BookmarkDetailsPanel(
            visible = showDetailsPanel,
            bookmark = detailsBookmark,
            onDismiss = { showDetailsPanel = false }
        )
    }
}

@Composable
fun rememberSnackbarHostStateWithDelay(
    snackbarManager: ActionSnackbarManager,
    fabExpanded: Boolean
): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingEvent by remember { mutableStateOf<SnackbarEvent?>(null) }

    // Collect events from manager
    LaunchedEffect(Unit) {
        snackbarManager.snackbarEvents.collect { event ->
            if (fabExpanded) {
                // Store for later display
                pendingEvent = event
            } else {
                // Show immediately
                showSnackbarEvent(snackbarHostState, event, scope)
            }
        }
    }

    // Show pending event when FAB closes
    LaunchedEffect(fabExpanded, pendingEvent) {
        if (!fabExpanded && pendingEvent != null) {
            delay(400) // Wait for FAB animation
            showSnackbarEvent(snackbarHostState, pendingEvent!!, scope)
            pendingEvent = null
        }
    }

    // Dismiss snackbar when FAB opens
    LaunchedEffect(fabExpanded) {
        if (fabExpanded) {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    return snackbarHostState
}

private fun showSnackbarEvent(
    snackbarHostState: SnackbarHostState,
    event: SnackbarEvent,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        when (event) {
            is SnackbarEvent.Message -> {
                snackbarHostState.showSnackbar(
                    message = event.text,
                    duration = event.duration
                )
            }
            is SnackbarEvent.MessageWithUndo -> {
                val result = snackbarHostState.showSnackbar(
                    message = event.text,
                    actionLabel = "Undo",
                    duration = event.duration
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    event.onUndo()
                }
            }
        }
    }
}
