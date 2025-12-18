package com.karakept.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
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

data class BookmarkViewerScreen(val bookmarkId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
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
        val showTags by screenModel.showTags.collectAsState()

        var showModeDialog by remember { mutableStateOf(false) }
        var showAppearancePanel by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var fabExpanded by remember { mutableStateOf(false) }
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var showListPicker by remember { mutableStateOf(false) }
        var showTagEditor by remember { mutableStateOf(false) }

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

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Parallax Header (Behind the list)
                        HeroBannerSection(
                            imageUrl = imageUrl,
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
                            } else null
                        )

                        // Content List
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize()
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
                                    hideArticleThumbnails = hideArticleThumbnails,
                                    htmlTextColor = htmlTextColor,
                                    htmlBackgroundColor = htmlBackgroundColor,
                                    htmlFontSize = htmlFontSize,
                                    htmlFontFamily = htmlFontFamily,
                                    precrawledAssetPath = precrawledAssetPath,
                                    loadingState = state,
                                    onLinkClick = { linkUrl ->
                                        navigator.push(WebViewScreen(linkUrl))
                                    }
                                )
                            }
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
        androidx.activity.compose.BackHandler(enabled = showAppearancePanel || showModeDialog) {
            if (showAppearancePanel) showAppearancePanel = false
            if (showModeDialog) showModeDialog = false
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
