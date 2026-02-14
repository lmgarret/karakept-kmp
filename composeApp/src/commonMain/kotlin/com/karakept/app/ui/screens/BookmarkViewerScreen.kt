package com.karakept.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.getColor
import com.karakept.app.ui.components.getIcon
import com.karakept.app.ui.screens.viewer.ScrollEndActionState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.ui.components.BookmarkContentLoader
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
        val screenModel = getScreenModel<BookmarkViewerScreenModel>()
        val scope = rememberCoroutineScope()
        val serverRepository = koinInject<ServerRepository>()
        val uriHandler = LocalUriHandler.current
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
        val autoMarkReadOnScroll by screenModel.autoMarkReadOnScroll.collectAsState()
        val scrollEndAction by screenModel.scrollEndAction.collectAsState()
        val showTags by screenModel.showTags.collectAsState()
        val isRefreshing by screenModel.isRefreshing.collectAsState()
        val offlineMode by screenModel.offlineMode.collectAsState()
        val highlights by screenModel.highlights.collectAsState()

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = { screenModel.refreshBookmark(bookmarkId) }
        )

        var showModeDialog by remember { mutableStateOf(false) }
        var showAppearancePanel by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var fabExpanded by remember { mutableStateOf(false) }
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var showTagEditor by remember { mutableStateOf(false) }
        var showListPicker by remember { mutableStateOf(false) }
        var selectedHighlightId by remember { mutableStateOf<String?>(null) }
        var highlightPosition by remember { mutableStateOf<com.karakept.app.ui.components.HighlightPosition?>(null) }
        var scrollEndActionState by remember { mutableStateOf<ScrollEndActionState?>(null) }

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
            // Get the first available server to load lists
            serverRepository.servers.first().firstOrNull()?.let { server ->
                screenModel.loadLists(server)
            }
        }

        // Auto-select highlight when navigating from Highlights screen
        LaunchedEffect(scrollToHighlightId, highlights) {
            if (scrollToHighlightId != null && highlights.isNotEmpty()) {
                // Find and select the highlight
                val targetHighlight = highlights.find { it.id == scrollToHighlightId }
                if (targetHighlight != null) {
                    selectedHighlightText = targetHighlight.text
                    selectedHighlightId = scrollToHighlightId
                }
            }
        }

        // Hoist state management OUTSIDE the when to prevent recomposition flash
        val scrollState = rememberLazyListState()
        val bannerHeight = 320.dp
        val toolbarHeight = 56.dp

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
        val fabVisible = if (loadingState is BookmarkLoadingState.FullyLoaded) {
            val fullyLoadedState = loadingState as BookmarkLoadingState.FullyLoaded
            rememberFabVisibilityState(
                scrollState = scrollState,
                fabExpanded = fabExpanded,
                autoMarkReadOnScroll = autoMarkReadOnScroll,
                isBookmarkRead = fullyLoadedState.bookmark.isRead,
                onMarkAsRead = {
                    screenModel.toggleBookmarkRead(fullyLoadedState.bookmark)
                },
                onUnmarkAsRead = {
                    screenModel.toggleBookmarkRead(fullyLoadedState.bookmark)
                },
                onShowSnackbarWithUndo = {
                    // Undo is now handled automatically by BookmarkActionController
                    // No need for manual implementation
                },
                scrollEndAction = scrollEndAction,
                onScrollEndAction = { action ->
                    when (action) {
                        SwipeAction.ARCHIVE -> screenModel.toggleBookmarkArchive(fullyLoadedState.bookmark)
                        SwipeAction.MARK_READ -> screenModel.toggleBookmarkRead(fullyLoadedState.bookmark)
                        SwipeAction.FAVOURITE -> screenModel.toggleBookmarkFavorite(fullyLoadedState.bookmark)
                        SwipeAction.DELETE -> showDeleteConfirmation = true
                        SwipeAction.SHARE -> {
                            ShareUtils.shareText(fullyLoadedState.bookmark.url, fullyLoadedState.bookmark.title)
                            scope.launch { snackbarManager.showSnackbar("Shared") }
                        }
                        SwipeAction.OPEN_IN_BROWSER -> {
                            uriHandler.openUri(fullyLoadedState.bookmark.url)
                            scope.launch { snackbarManager.showSnackbar("Opening in browser") }
                        }
                        SwipeAction.NONE -> {}
                    }
                },
                onScrollEndActionStateChanged = { state ->
                    scrollEndActionState = state
                }
            )
        } else {
            true
        }

        val showStickyTitle = rememberStickyTitleVisibility(
            scrollState = scrollState,
            bannerHeight = bannerHeight,
            toolbarHeight = toolbarHeight
        )

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

                    // Log which asset is being used for display (bannerImage preferred over imageUrl)
                    if (bannerImageUrl != null) {
                        println("📸 VIEWER: Using bannerImage asset for bookmark ${state.bookmark.remoteId}")
                    } else if (screenshotUrl != null) {
                        println("📸 VIEWER: Using screenshot asset for bookmark ${state.bookmark.remoteId}")
                    } else if (imageUrl != null) {
                        println("📸 VIEWER: No asset available for bookmark ${state.bookmark.remoteId}, imageUrl='$imageUrl' exists but not displayed")
                    } else {
                        println("📸 VIEWER: No image available for bookmark ${state.bookmark.remoteId}, showing emoji")
                    }

                    // Log HTML sanitization
                    if (hideArticleThumbnails) {
                        println("🧹 SANITIZER: Will remove first image element from HTML content")
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState, enabled = !offlineMode)
                    ) {
                        // Parallax Header (Behind the list)
                        HeroBannerSection(
                            title = title,
                            url = url,
                            tags = state.bookmark.tags,
                            readingTimeMinutes = readingTimeMinutes,
                            showTags = showTags,
                            scrollState = scrollState,
                            bannerHeight = bannerHeight,
                            onUrlClick = if (url.isNotEmpty()) {
                                {
                                    try {
                                        uriHandler.openUri(url)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            } else null,
                            bannerImageUrl = bannerImageUrl,
                            screenshotUrl = screenshotUrl,
                            bannerImageLocalPath = bannerImageLocalPath,
                            screenshotLocalPath = screenshotLocalPath
                        )

                        // Content List
                        // Only blur when the highlight is actually found and panel will show
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (selectedHighlightId != null && selectedHighlight != null) Modifier.blur(8.dp) else Modifier)
                        ) {
                            // Transparent spacer for the header
                            item(key = "header_spacer") {
                                Spacer(modifier = Modifier.height(bannerHeight))
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
                                        navigator.push(WebViewScreen(linkUrl))
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
                                    }
                                )
                            }
                        }

                        // Scroll-end action pull indicator
                        val endActionState = scrollEndActionState
                        if (endActionState != null && endActionState.action != SwipeAction.NONE) {
                            val progress = (endActionState.overscrollPx / 300f).coerceIn(0f, 1f)
                            ScrollEndActionIndicator(
                                state = endActionState,
                                progress = progress,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                            )
                        }

                        // Top Bar
                        ViewerTopBar(
                            title = title,
                            url = url,
                            showStickyTitle = showStickyTitle,
                            showMenu = showMenu,
                            toolbarHeight = toolbarHeight,
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
                                .padding(padding) // Adjust for status bar/top bar if needed, though usually align TopCenter is enough
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
                        androidx.compose.material3.Text(
                            text = "Error: ${state.message}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Back Handler for panels
        com.karakept.app.ui.components.BackHandler(enabled = showAppearancePanel || showModeDialog || selectedHighlightId != null) {
            if (showAppearancePanel) showAppearancePanel = false
            if (showModeDialog) showModeDialog = false
            if (selectedHighlightId != null) selectedHighlightId = null
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

/**
 * A pill-shaped indicator shown at the bottom of the article when the user pulls past the end.
 * Uses the same icon and color as the configured swipe action for visual consistency.
 */
@Composable
private fun ScrollEndActionIndicator(
    state: ScrollEndActionState,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = (progress * 2f).coerceIn(0f, 1f),
        animationSpec = tween(150),
        label = "indicator_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = 0.8f + progress * 0.2f,
        animationSpec = tween(150),
        label = "indicator_scale"
    )

    val actionColor = state.action.getColor()
    val actionIcon = state.action.getIcon()
    val label = if (state.triggered) state.action.displayName else "Pull to ${state.action.displayName}"

    Row(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(actionColor.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = actionIcon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}
