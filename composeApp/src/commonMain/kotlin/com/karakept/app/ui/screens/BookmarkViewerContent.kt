package com.karakept.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import com.karakept.app.utils.AppLogger
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.reader.SearchMatch
import com.karakept.app.ui.components.rememberCustomTabOpener
import com.karakept.app.ui.screens.viewer.*
import com.karakept.app.utils.ShareUtils
import getPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.karakept.app.domain.action.ActionSnackbarManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkViewerContent(
    bookmarkId: Long,
    scrollToHighlightId: String? = null,
    screenModel: BookmarkViewerScreenModel,
    onBack: () -> Unit,
    onTagFilterApply: (tag: String) -> Unit,
    isEmbedded: Boolean = false,
    isFullscreen: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null
) {
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
    val dateDisplayMode by screenModel.dateDisplayMode.collectAsState()
    val isRefreshing by screenModel.isRefreshing.collectAsState()
    val offlineMode by screenModel.offlineMode.collectAsState()
    val highlights by screenModel.highlights.collectAsState()
    val linkOpenMode by screenModel.linkOpenMode.collectAsState()
    val trackReadingProgress by screenModel.trackReadingProgress.collectAsState()
    val serverProgressChecked by screenModel.serverProgressChecked.collectAsState()
    val contentFetchAttempted by screenModel.contentFetchAttempted.collectAsState()

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
    var selectedHighlightText by remember { mutableStateOf<String?>(null) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableStateOf(0) }
    var lastScrolledMatchIndex by remember { mutableStateOf(-1) }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (getPlatform().isDesktop) contentFocusRequester.requestFocus()
    }
    val selectedHighlight by remember {
        androidx.compose.runtime.derivedStateOf {
            val id = selectedHighlightId ?: return@derivedStateOf null
            highlights.find { it.id == id }
                ?: if (id.startsWith("temp_") && selectedHighlightText != null) {
                    highlights.find { it.text == selectedHighlightText }
                } else null
        }
    }

    // Update selectedHighlightId when found by text (temp ID replaced by server ID)
    LaunchedEffect(selectedHighlight?.id, selectedHighlightId) {
        val highlight = selectedHighlight
        val currentId = selectedHighlightId
        if (highlight != null && currentId != null && highlight.id != currentId) {
            selectedHighlightId = highlight.id
        }
    }

    LaunchedEffect(selectedHighlightId) { highlightPosition = null }

    // Snackbar: embedded mode skips collection (parent handles it)
    val snackbarHostState = if (isEmbedded) {
        remember { SnackbarHostState() }
    } else {
        rememberSnackbarHostStateWithDelay(snackbarManager = snackbarManager, fabExpanded = fabExpanded)
    }

    LaunchedEffect(bookmarkId) { screenModel.loadBookmark(bookmarkId) }

    LaunchedEffect(showListPicker) {
        if (showListPicker) {
            serverRepository.servers.first().firstOrNull()?.let { server ->
                screenModel.loadLists(server)
            }
        }
    }

    val scrollState = remember { LazyListState() }
    val density = LocalDensity.current

    // Scroll guard + reading progress restoration
    val scrollRestoration = rememberScrollRestoration(
        scrollState = scrollState,
        loadingState = loadingState,
        viewerMode = viewerMode,
        trackReadingProgress = trackReadingProgress,
        serverProgressChecked = serverProgressChecked,
        contentFetchAttempted = contentFetchAttempted,
        scrollToHighlightId = scrollToHighlightId
    )

    // When a highlight is clicked, scroll to center it
    LaunchedEffect(selectedHighlightId) {
        val id = selectedHighlightId ?: return@LaunchedEffect
        if (id == scrollToHighlightId) return@LaunchedEffect
        kotlinx.coroutines.delay(100)
        val position = highlightPosition ?: return@LaunchedEffect
        val state = loadingState as? BookmarkLoadingState.FullyLoaded ?: return@LaunchedEffect
        val contentBodyIndex = if (!state.bookmark.description.isNullOrBlank()) 2 else 1
        val layoutInfo = scrollState.layoutInfo
        val contentBodyItem = layoutInfo.visibleItemsInfo.find { it.index == contentBodyIndex }
        val contentBodyTop = contentBodyItem?.offset ?: 0
        val highlightOffsetInItem = (position.y - contentBodyTop).toInt()
        val panelHeightPx = with(density) { 280.dp.toPx() }.toInt()
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val availableHeight = viewportHeight - panelHeightPx
        val offsetPx = maxOf(0, highlightOffsetInItem - availableHeight / 2)
        scrollState.animateScrollToItem(contentBodyIndex, offsetPx)
    }

    var highlightPositionReceived by remember { mutableStateOf(false) }
    var highlightScrollDone by remember { mutableStateOf(scrollToHighlightId == null) }
    val bannerHeight = 320.dp
    val toolbarHeight = 56.dp
    val isNativeRenderer = viewerMode == com.karakept.app.data.model.ViewerMode.READER

    // Scroll to highlight when navigating from Highlights screen
    LaunchedEffect(highlightPositionReceived, scrollRestoration.contentRendered) {
        if (!highlightPositionReceived) return@LaunchedEffect
        if (!scrollRestoration.contentRendered) return@LaunchedEffect
        val state = loadingState as? BookmarkLoadingState.FullyLoaded ?: return@LaunchedEffect
        val contentBodyIndex = if (!state.bookmark.description.isNullOrBlank()) 2 else 1
        if (isNativeRenderer) kotlinx.coroutines.yield() else delay(300)
        val position = highlightPosition
        if (position != null) {
            val layoutInfo = scrollState.layoutInfo
            val contentBodyItem = layoutInfo.visibleItemsInfo.find { it.index == contentBodyIndex }
            val contentBodyTop = contentBodyItem?.offset ?: 0
            val highlightOffsetInItem = (position.y - contentBodyTop).toInt()
            val panelHeightPx = with(density) { 280.dp.toPx() }.toInt()
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val availableHeight = viewportHeight - panelHeightPx
            val offsetPx = maxOf(0, highlightOffsetInItem - availableHeight / 2)
            scrollRestoration.safeScrollToItem(contentBodyIndex, offsetPx)
        } else {
            scrollRestoration.safeScrollToItem(contentBodyIndex, 0)
        }
        highlightScrollDone = true
        if (scrollToHighlightId != null) selectedHighlightId = scrollToHighlightId
    }

    // Keep last valid FullyLoaded state to prevent error flash during navigation
    var lastValidState by remember { mutableStateOf<BookmarkLoadingState>(loadingState) }
    LaunchedEffect(loadingState) {
        if (loadingState is BookmarkLoadingState.FullyLoaded) lastValidState = loadingState
        else if (loadingState is BookmarkLoadingState.Initial) lastValidState = loadingState
    }
    val displayState = if (loadingState is BookmarkLoadingState.Error && lastValidState is BookmarkLoadingState.FullyLoaded) lastValidState else loadingState

    val fabVisible = rememberFabVisibilityState(scrollState = scrollState, fabExpanded = fabExpanded)
    val scrollToTopEnabled by screenModel.scrollToTopEnabled.collectAsState()
    val scrollToTopVisible = rememberScrollToTopVisibility(scrollState = scrollState, fabVisible = fabVisible)
    val showStickyTitle = rememberStickyTitleVisibility(scrollState = scrollState, bannerHeight = bannerHeight, toolbarHeight = toolbarHeight)
    val readingProgress = rememberReadingProgress(scrollState, bannerHeight, toolbarHeight)

    // Push reading state to screen model on scroll changes
    LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
        if (trackReadingProgress && scrollRestoration.hasRestoredScroll && loadingState is BookmarkLoadingState.FullyLoaded) {
            val currentState = loadingState as BookmarkLoadingState.FullyLoaded
            val meaningfulProgress = readingProgress > 0.02f
            if (meaningfulProgress) {
                screenModel.onReadingStateChanged(
                    localId = currentState.bookmark.localId, remoteId = currentState.bookmark.remoteId,
                    progress = readingProgress, scrollIndex = scrollState.firstVisibleItemIndex,
                    scrollOffset = scrollState.firstVisibleItemScrollOffset
                )
            } else if (scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 0) {
                screenModel.onReadingStateChanged(
                    localId = currentState.bookmark.localId, remoteId = currentState.bookmark.remoteId,
                    progress = 0f, scrollIndex = 0, scrollOffset = 0
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when {
                    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.keyboardKey == Key.F -> {
                        showSearch = true
                        true
                    }
                    keyEvent.keyboardKey == Key.Escape && showSearch -> {
                        showSearch = false
                        searchQuery = ""
                        searchMatches = emptyList()
                        scope.launch { contentFocusRequester.requestFocus() }
                        true
                    }
                    else -> false
                }
            } else false
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible && !getPlatform().isDesktop,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                if (loadingState is BookmarkLoadingState.FullyLoaded) {
                    val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
                    BookmarkFabMenu(
                        expanded = fabExpanded, onExpandedChange = { fabExpanded = it },
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
                            scope.launch { snackbarManager.showSnackbar(if (getPlatform().isDesktop) "Copied to clipboard" else "Shared") }
                            fabExpanded = false
                        },
                        onOpenInBrowserClick = {
                            uriHandler.openUri(fullyLoadedState.bookmark.url)
                            scope.launch { snackbarManager.showSnackbar("Opening in browser") }
                            fabExpanded = false
                        }
                    )
                }
            }
        }
    ) { padding ->
        when (val state = displayState) {
            is BookmarkLoadingState.Initial -> {
                BookmarkContentLoader(loadingState = state, modifier = Modifier.padding(padding))
            }
            is BookmarkLoadingState.FullyLoaded -> {
                val title = state.bookmark.title
                val url = state.bookmark.url
                val readingTimeMinutes = state.bookmark.readingTimeMinutes
                val description = state.bookmark.description

                val servers by serverRepository.servers.collectAsState(initial = emptyList())
                val server = servers.firstOrNull()
                val bannerImageUrl = if (server != null && state.bookmark.bannerImageAssetId != null)
                    com.karakept.app.utils.AssetUrlUtils.getAssetUrl(server.url, state.bookmark.bannerImageAssetId) else null
                val screenshotUrl = if (server != null && state.bookmark.screenshotAssetId != null)
                    com.karakept.app.utils.AssetUrlUtils.getAssetUrl(server.url, state.bookmark.screenshotAssetId) else null
                val bannerImageLocalPath by screenModel.bannerImageLocalPath.collectAsState()
                val screenshotLocalPath by screenModel.screenshotLocalPath.collectAsState()

                val viewerContent: @Composable () -> Unit = {
                    Box(modifier = Modifier.fillMaxSize()
                        .then(if (getPlatform().isDesktop)
                            Modifier.focusRequester(contentFocusRequester).focusable()
                        else Modifier)
                    ) {
                    val needsScrollRestore = trackReadingProgress && !scrollRestoration.hasRestoredScroll &&
                        loadingState is BookmarkLoadingState.FullyLoaded &&
                        ((loadingState as BookmarkLoadingState.FullyLoaded).bookmark.readingProgress > 0.02f || !serverProgressChecked)
                    val needsHighlightScroll = !highlightScrollDone

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize().then(if (needsScrollRestore || needsHighlightScroll) Modifier.alpha(0f) else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val contentItemModifier = Modifier.widthIn(max = 900.dp)

                        item(key = "hero_banner") {
                            Box(modifier = contentItemModifier) {
                                HeroBannerSection(
                                    title = title, url = url, tags = state.bookmark.tags,
                                    readingTimeMinutes = readingTimeMinutes, showTags = showTags,
                                    scrollState = scrollState, createdAt = state.bookmark.createdAt,
                                    dateDisplayMode = dateDisplayMode,
                                    onUrlClick = if (url.isNotEmpty()) { {
                                        try { when (linkOpenMode) { LinkOpenMode.CUSTOM_TAB -> openInCustomTab(url); LinkOpenMode.EXTERNAL_BROWSER -> uriHandler.openUri(url) } }
                                        catch (e: Exception) { AppLogger.e("ViewerScreen", "Failed to handle reader action: ${e.message}", e) }
                                    } } else null,
                                    onTagClick = { tag -> onTagFilterApply(tag) },
                                    onInfoClick = { showDetailsPanel = true },
                                    bannerImageUrl = bannerImageUrl, screenshotUrl = screenshotUrl,
                                    bannerImageLocalPath = bannerImageLocalPath, screenshotLocalPath = screenshotLocalPath
                                )
                            }
                        }

                        if (!description.isNullOrBlank()) {
                            item(key = "description_card") {
                                Box(modifier = contentItemModifier) {
                                    DescriptionCard(description = description, htmlBackgroundColor = htmlBackgroundColor,
                                        htmlTextColor = htmlTextColor, htmlFontSize = htmlFontSize, htmlFontFamily = htmlFontFamily)
                                }
                            }
                        }

                        item(key = "content_body") {
                            Box(modifier = contentItemModifier) {
                                ContentBodySection(
                                    content = state.bookmark.content, viewerMode = viewerMode,
                                    removeFirstImage = hideArticleThumbnails,
                                    htmlTextColor = htmlTextColor, htmlBackgroundColor = htmlBackgroundColor,
                                    htmlFontSize = htmlFontSize, htmlFontFamily = htmlFontFamily,
                                    precrawledAssetPath = precrawledAssetPath, loadingState = state,
                                    contentFetchAttempted = contentFetchAttempted,
                                    highlights = highlights,
                                    onLinkClick = { linkUrl ->
                                        try { when (linkOpenMode) { LinkOpenMode.CUSTOM_TAB -> openInCustomTab(linkUrl); LinkOpenMode.EXTERNAL_BROWSER -> uriHandler.openUri(linkUrl) } }
                                        catch (e: Exception) { AppLogger.e("ViewerScreen", "Failed to process highlight: ${e.message}", e) }
                                    },
                                    onCreateHighlight = { text, start, end, note, color ->
                                        screenModel.createHighlight(state.bookmark, text, start, end, note, color) { highlightId ->
                                            selectedHighlightText = text; selectedHighlightId = highlightId
                                        }
                                    },
                                    onDeleteHighlight = { highlightId -> screenModel.deleteHighlight(state.bookmark, highlightId) },
                                    onHighlightClick = { id -> selectedHighlightId = id },
                                    onHighlightPosition = { id, position ->
                                        val current = highlightPosition
                                        val currentPath = current?.path
                                        val positionPath = position.path
                                        if (currentPath != null && positionPath != null) {
                                            val mergedPath = androidx.compose.ui.graphics.Path().apply { addPath(currentPath); addPath(positionPath) }
                                            val mergedBounds = mergedPath.getBounds()
                                            highlightPosition = com.karakept.app.ui.components.HighlightPosition(
                                                x = mergedBounds.left, y = mergedBounds.top,
                                                width = mergedBounds.width, height = mergedBounds.height,
                                                scrollX = 0f, scrollY = 0f, path = mergedPath,
                                                rootOffset = androidx.compose.ui.geometry.Offset.Zero
                                            )
                                        } else { highlightPosition = position }
                                        if (id == scrollToHighlightId && !highlightPositionReceived) highlightPositionReceived = true
                                    },
                                    onContentReady = { scrollRestoration.onContentRendered() },
                                    scrollToHighlightId = scrollToHighlightId,
                                    selectedHighlightId = selectedHighlightId ?: scrollToHighlightId,
                                    parseDocument = { sanitizedHtml ->
                                        screenModel.getCachedOrParseDocument(bookmarkId, sanitizedHtml)
                                    },
                                    searchQuery = if (showSearch) searchQuery else "",
                                    activeSearchMatchIndex = currentMatchIndex,
                                    onSearchMatchesFound = { matches ->
                                        searchMatches = matches
                                        if (currentMatchIndex >= matches.size) currentMatchIndex = 0
                                    },
                                    onSearchMatchPosition = { y ->
                                        if (currentMatchIndex != lastScrolledMatchIndex) {
                                            lastScrolledMatchIndex = currentMatchIndex
                                            val st = loadingState as? BookmarkLoadingState.FullyLoaded
                                            if (st != null) {
                                                val cbi = if (!st.bookmark.description.isNullOrBlank()) 2 else 1
                                                scope.launch {
                                                    val li = scrollState.layoutInfo
                                                    val itemTop = li.visibleItemsInfo.find { it.index == cbi }?.offset ?: 0
                                                    val matchInItem = (y.toInt() - itemTop).coerceAtLeast(0)
                                                    val vpHeight = li.viewportEndOffset - li.viewportStartOffset
                                                    val scrollOffset = maxOf(0, matchInItem - vpHeight / 4)
                                                    scrollState.animateScrollToItem(cbi, scrollOffset)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Scroll-to-top button (READER-03)
                    val scrollToTopBottomPadding by animateDpAsState(
                        targetValue = if (showSearch && !getPlatform().isDesktop) 76.dp else 16.dp,
                        animationSpec = tween(300),
                        label = "scrollToTopBottomPadding"
                    )
                    AnimatedVisibility(
                        visible = scrollToTopVisible && scrollToTopEnabled,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, bottom = scrollToTopBottomPadding)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    scrollState.animateScrollToItem(0, 0)
                                    // Use safeScrollToItem to update the scroll guard's
                                    // approved position — without this, the guard detects
                                    // an "unintended jump" and snaps back to the old position
                                    scrollRestoration.safeScrollToItem(0, 0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Scroll to top"
                            )
                        }
                    }

                    // Global Dimming Overlay
                    if (selectedHighlightId != null) {
                        var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .onGloballyPositioned { coords -> overlayRootOffset = coords.positionInRoot() }
                                .pointerInput(Unit) { detectTapGestures { selectedHighlightId = null } }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.99f)) {
                                drawRect(Color.Black.copy(alpha = 0.6f))
                                val pos = highlightPosition
                                val posPath = pos?.path
                                if (posPath != null) {
                                    withTransform({ translate(pos.rootOffset.x - overlayRootOffset.x, pos.rootOffset.y - overlayRootOffset.y) }) {
                                        drawPath(path = posPath, color = Color.Transparent, blendMode = BlendMode.Clear)
                                    }
                                }
                            }
                        }
                    }

                    // Top Bar
                    ViewerTopBar(
                        title = title, url = url, showStickyTitle = showStickyTitle, showMenu = showMenu,
                        toolbarHeight = toolbarHeight,
                        readingProgress = if (trackReadingProgress) readingProgress else 0f,
                        isRefreshing = isRefreshing,
                        onBackClick = onBack, onMenuToggle = { showMenu = it },
                        onAppearanceClick = { showAppearancePanel = true },
                        onViewerModeClick = { showModeDialog = true },
                        onMoveToListClick = { showListPicker = true },
                        onEditTagsClick = { showTagEditor = true },
                        onRefreshClick = { screenModel.refreshBookmark(bookmarkId) },
                        onDeleteClick = { showDeleteConfirmation = true },
                        onSearchClick = { showSearch = true },
                        isDesktop = getPlatform().isDesktop, bookmark = state.bookmark,
                        onFavoriteClick = { screenModel.toggleBookmarkFavorite(state.bookmark) },
                        onArchiveClick = { screenModel.toggleBookmarkArchive(state.bookmark) },
                        onReadClick = { screenModel.toggleBookmarkRead(state.bookmark) },
                        onShareClick = {
                            ShareUtils.shareText(state.bookmark.url, state.bookmark.title)
                            scope.launch { snackbarManager.showSnackbar(if (getPlatform().isDesktop) "Copied to clipboard" else "Shared") }
                        },
                        onOpenInBrowserClick = {
                            uriHandler.openUri(state.bookmark.url)
                            scope.launch { snackbarManager.showSnackbar("Opening in browser") }
                        },
                        isFullscreen = isFullscreen, onFullscreenToggle = onFullscreenToggle,
                        onDetailsClick = { showDetailsPanel = true }
                    )

                    // Search bar — floating pill, to the left of FAB on Android
                    val isDesktop = getPlatform().isDesktop
                    ReaderSearchBar(
                        visible = showSearch,
                        query = searchQuery,
                        onQueryChange = { query ->
                            searchQuery = query
                            currentMatchIndex = 0
                            lastScrolledMatchIndex = -1
                        },
                        matchCount = searchMatches.size,
                        currentMatchIndex = currentMatchIndex,
                        onPrevious = {
                            if (searchMatches.isNotEmpty()) {
                                lastScrolledMatchIndex = -1
                                currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.size - 1
                            }
                        },
                        onNext = {
                            if (searchMatches.isNotEmpty()) {
                                lastScrolledMatchIndex = -1
                                currentMatchIndex = if (currentMatchIndex < searchMatches.size - 1) currentMatchIndex + 1 else 0
                            }
                        },
                        onClose = {
                            showSearch = false
                            searchQuery = ""
                            searchMatches = emptyList()
                            lastScrolledMatchIndex = -1
                        },
                        maxWidth = if (isDesktop) 520.dp else 360.dp,
                        modifier = if (isDesktop)
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                        else
                            Modifier.align(Alignment.BottomEnd)
                                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                                .padding(end = 80.dp, bottom = 14.dp, start = 16.dp)
                    )
                    } // end inner Box
                }

                if (!getPlatform().isDesktop) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { screenModel.refreshBookmark(bookmarkId) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        viewerContent()
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        viewerContent()
                    }
                }
            }
            is BookmarkLoadingState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.message}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // Panels and dialogs
    ViewerContentPanels(
        loadingState = loadingState, viewerMode = viewerMode,
        htmlTextColor = htmlTextColor, htmlBackgroundColor = htmlBackgroundColor,
        htmlFontSize = htmlFontSize, htmlFontFamily = htmlFontFamily, lists = lists,
        showModeDialog = showModeDialog, onShowModeDialogChanged = { showModeDialog = it },
        showAppearancePanel = showAppearancePanel, onShowAppearancePanelChanged = { showAppearancePanel = it },
        showDetailsPanel = showDetailsPanel, onShowDetailsPanelChanged = { showDetailsPanel = it },
        showDeleteConfirmation = showDeleteConfirmation, onShowDeleteConfirmationChanged = { showDeleteConfirmation = it },
        showListPicker = showListPicker, onShowListPickerChanged = { showListPicker = it },
        showTagEditor = showTagEditor, onShowTagEditorChanged = { showTagEditor = it },
        selectedHighlightId = selectedHighlightId, onSelectedHighlightIdChanged = { selectedHighlightId = it },
        selectedHighlightText = selectedHighlightText, onSelectedHighlightTextChanged = { selectedHighlightText = it },
        selectedHighlight = selectedHighlight,
        showSearch = showSearch, onShowSearchChanged = { showSearch = it },
        screenModel = screenModel, scope = scope, onBack = onBack
    )
}
