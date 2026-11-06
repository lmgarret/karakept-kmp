package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.ui.components.EinkAwareSnackbarHost
import com.karakept.app.ui.components.reader.GalleryViewerState
import com.karakept.app.ui.components.reader.ReaderSnapRegistry
import com.karakept.app.ui.components.rememberCustomTabOpener
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.input.PageTurnScrollEffect
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.BODY_LINE_HEIGHT_RATIO
import com.karakept.app.ui.utils.PagedPositionState
import com.karakept.app.ui.utils.trailingPagePaddingFor
import getPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkViewerContent(
    bookmarkId: Long,
    scrollToHighlightId: String? = null,
    searchTrigger: Int = 0,
    initialTitle: String? = null,
    initialUrl: String? = null,
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
    val pageTurnDispatcher = koinInject<PageTurnDispatcher>()

    val loadingState by screenModel.loadingState.collectAsState()
    val viewerMode by screenModel.viewerMode.collectAsState()
    val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
    val htmlTextColor by screenModel.htmlTextColor.collectAsState()
    val htmlBackgroundColor by screenModel.htmlBackgroundColor.collectAsState()
    val htmlFontSize by screenModel.htmlFontSize.collectAsState()
    val htmlFontFamily by screenModel.htmlFontFamily.collectAsState()
    val precrawledAssetPath by screenModel.precrawledAssetPath.collectAsState()
    val selectedSource by screenModel.selectedSource.collectAsState()
    val archiveAvailable by screenModel.archiveAvailable.collectAsState()
    val sourceContentOverride by screenModel.sourceContentOverride.collectAsState()
    val isLoadingSource by screenModel.isLoadingSource.collectAsState()
    val assets by screenModel.assets.collectAsState()
    val lists by screenModel.lists.collectAsState()
    val showTags by screenModel.showTags.collectAsState()
    val dateDisplayMode by screenModel.dateDisplayMode.collectAsState()
    val isRefreshing by screenModel.isRefreshing.collectAsState()
    val aiActionInFlight by screenModel.aiActionInFlight.collectAsState()
    val aiCapabilitiesByServer by screenModel.aiCapabilities.collectAsState()
    val offlineMode by screenModel.offlineMode.collectAsState()
    val highlights by screenModel.highlights.collectAsState()
    val linkOpenMode by screenModel.linkOpenMode.collectAsState()
    val trackReadingProgress by screenModel.trackReadingProgress.collectAsState()
    val serverProgressChecked by screenModel.serverProgressChecked.collectAsState()
    val contentFetchAttempted by screenModel.contentFetchAttempted.collectAsState()
    val readerTypography by screenModel.readerTypography.collectAsState()
    val showHeroImage by screenModel.showHeroImage.collectAsState()

    val ui = rememberViewerUiState()

    // Open reader search when triggered externally (desktop split-pane Ctrl+F).
    // lastHandledSearchTrigger is initialised from the current searchTrigger so that
    // stale non-zero values inherited across bookmark changes don't open search.
    val lastHandledSearchTrigger = remember { mutableStateOf(searchTrigger) }
    LaunchedEffect(searchTrigger) {
        if (searchTrigger != lastHandledSearchTrigger.value) {
            lastHandledSearchTrigger.value = searchTrigger
            ui.showSearch = true
        }
    }
    val selectedHighlight by remember {
        androidx.compose.runtime.derivedStateOf {
            val id = ui.selectedHighlightId ?: return@derivedStateOf null
            highlights.find { it.id == id }
                ?: if (id.startsWith("temp_") && ui.selectedHighlightText != null) {
                    highlights.find { it.text == ui.selectedHighlightText }
                } else null
        }
    }

    // Update selectedHighlightId when found by text (temp ID replaced by server ID)
    LaunchedEffect(selectedHighlight?.id, ui.selectedHighlightId) {
        val highlight = selectedHighlight
        val currentId = ui.selectedHighlightId
        if (highlight != null && currentId != null && highlight.id != currentId) {
            ui.selectedHighlightId = highlight.id
        }
    }

    LaunchedEffect(ui.selectedHighlightId) {
        ui.highlightMask.clear()
        ui.highlightPosition = null
    }

    // Snackbar: embedded mode skips collection (parent handles it)
    val snackbarHostState = if (isEmbedded) {
        remember { SnackbarHostState() }
    } else {
        rememberSnackbarHostStateWithDelay(snackbarManager = snackbarManager, fabExpanded = ui.fabExpanded)
    }

    LaunchedEffect(bookmarkId) { screenModel.loadBookmark(bookmarkId) }

    LaunchedEffect(ui.showListPicker) {
        if (ui.showListPicker) {
            serverRepository.servers.first().firstOrNull()?.let { server ->
                screenModel.loadLists(server)
            }
        }
    }

    val einkMode = LocalEinkMode.current
    val scrollState = remember { LazyListState() }
    val density = LocalDensity.current
    val galleryViewerState = remember { GalleryViewerState() }

    val toolbarHeight = 56.dp
    // The top bar is painted over the list rather than reserved by the Scaffold, so a page is the
    // viewport minus that chrome — otherwise every turn hides a line or two behind it.
    val chromeInsets = rememberViewerChromeInsets(toolbarHeight = toolbarHeight)

    // Scroll guard + reading progress restoration
    val scrollRestoration = rememberScrollRestoration(
        scrollState = scrollState,
        loadingState = loadingState,
        viewerMode = viewerMode,
        trackReadingProgress = trackReadingProgress,
        serverProgressChecked = serverProgressChecked,
        contentFetchAttempted = contentFetchAttempted,
        scrollToHighlightId = scrollToHighlightId,
        obscuredBottomPx = chromeInsets.bottomPx
    )

    // When a highlight is clicked, scroll to center it
    LaunchedEffect(ui.selectedHighlightId) {
        val id = ui.selectedHighlightId ?: return@LaunchedEffect
        if (id == scrollToHighlightId) return@LaunchedEffect
        kotlinx.coroutines.delay(100)
        val position = ui.highlightPosition ?: return@LaunchedEffect
        val state = loadingState as? BookmarkLoadingState.FullyLoaded ?: return@LaunchedEffect
        val contentBodyIndex = contentBodyIndexFor(state.bookmark)
        val panelHeightPx = with(density) { 280.dp.toPx() }.toInt()
        val offsetPx = highlightCenteringOffset(scrollState.layoutInfo, contentBodyIndex, position.y, panelHeightPx)
        if (einkMode.instantScroll) {
            scrollRestoration.safeScrollToItem(contentBodyIndex, offsetPx)
        } else {
            scrollState.animateScrollToItem(contentBodyIndex, offsetPx)
        }
    }

    var highlightPositionReceived by remember { mutableStateOf(false) }
    var highlightScrollDone by remember { mutableStateOf(scrollToHighlightId == null) }
    // Only an estimate, and only the sticky-title threshold uses it: rememberReadingProgress
    // measures the real hero once it has been laid out. A text-only header is roughly this tall.
    val bannerHeight = if (showHeroImage) 320.dp else 120.dp
    val isNativeRenderer = viewerMode == com.karakept.app.data.model.ViewerMode.READER

    // Scroll to highlight when navigating from Highlights screen
    LaunchedEffect(highlightPositionReceived, scrollRestoration.contentRendered) {
        if (!highlightPositionReceived) return@LaunchedEffect
        if (!scrollRestoration.contentRendered) return@LaunchedEffect
        val state = loadingState as? BookmarkLoadingState.FullyLoaded ?: return@LaunchedEffect
        val contentBodyIndex = contentBodyIndexFor(state.bookmark)
        if (isNativeRenderer) kotlinx.coroutines.yield() else delay(300)
        val position = ui.highlightPosition
        if (position != null) {
            val panelHeightPx = with(density) { 280.dp.toPx() }.toInt()
            val offsetPx = highlightCenteringOffset(scrollState.layoutInfo, contentBodyIndex, position.y, panelHeightPx)
            scrollRestoration.safeScrollToItem(contentBodyIndex, offsetPx)
        } else {
            scrollRestoration.safeScrollToItem(contentBodyIndex, 0)
        }
        highlightScrollDone = true
        if (scrollToHighlightId != null) ui.selectedHighlightId = scrollToHighlightId
    }

    // Keep last valid FullyLoaded state to prevent error flash during navigation
    var lastValidState by remember { mutableStateOf<BookmarkLoadingState>(loadingState) }
    LaunchedEffect(loadingState) {
        if (loadingState is BookmarkLoadingState.FullyLoaded) lastValidState = loadingState
        else if (loadingState is BookmarkLoadingState.Initial) lastValidState = loadingState
    }
    val displayState = if (loadingState is BookmarkLoadingState.Error && lastValidState is BookmarkLoadingState.FullyLoaded) lastValidState else loadingState

    // Line boundaries for page snapping. The whole article is a single lazy item, so the snap
    // points cannot come from `layoutInfo` — the text blocks report themselves instead.
    val pageTurnBindings by pageTurnDispatcher.bindings.collectAsState()
    val snapRegistry = remember { ReaderSnapRegistry() }
    // The list container itself does not move while scrolling — only its content does — so this
    // settles after the first layout and changes again only on a resize.
    var listTopInRoot by remember { mutableStateOf(0f) }

    // Whole-page rendering — a blank bottom edge and a padded last page — changes how ordinary
    // scrolling looks, not just how a turn lands, so unlike the snap itself it waits for the
    // master e-ink switch.
    val pagedRendering = einkMode.enabled && pageTurnBindings.snapToContent
    val pagedPosition = remember { PagedPositionState() }
    // One line of body text: the last page can then go no deeper than the final line sitting at
    // the top, with blank space under it.
    val readerTailUnitPx = with(density) {
        (htmlFontSize * BODY_LINE_HEIGHT_RATIO * readerTypography.lineHeightScale).sp.toPx()
    }.toInt()
    val readerTrailingPadPx by remember(pagedRendering, readerTailUnitPx) {
        derivedStateOf {
            if (!pagedRendering) 0
            else trailingPagePaddingFor(scrollState.layoutInfo, readerTailUnitPx)
        }
    }

    PageTurnScrollEffect(
        listState = scrollState,
        // While the image gallery is open it owns the page-turn buttons (flips between
        // images instead), so the article underneath must not also scroll.
        enabled = galleryViewerState.request == null,
        obscuredTopPx = chromeInsets.topPx,
        obscuredBottomPx = chromeInsets.bottomPx,
        // Predictive rather than corrective: a second, separate scroll would leave an
        // intermediate position visible to the scroll guard, which reads it as an unintended
        // jump. Every block is eagerly composed, including those above and below the viewport,
        // so the registry can answer for a turn in either direction before it happens.
        predictiveSnap = { direction, pageDelta ->
            val foldInRoot = listTopInRoot +
                scrollState.layoutInfo.viewportStartOffset + chromeInsets.topPx
            readerPredictiveSnap(snapRegistry, foldInRoot, direction, pageDelta)
        },
        hasTrailingPadding = readerTrailingPadPx > 0,
        // The article box runs past its last line — paragraph padding plus the renderer's own
        // bottom margin — so a turn measured against the lazy item keeps going after the last
        // word is read and lands on blank space. The registry knows where the text actually ends.
        moreContentBelow = {
            val fold = listTopInRoot +
                scrollState.layoutInfo.viewportEndOffset - chromeInsets.bottomPx
            readerMoreContentBelow(snapRegistry, fold, readerTailUnitPx.toFloat())
        },
        onScrolled = {
            scrollRestoration.approveCurrentPosition()
            pagedPosition.markSettled(scrollState)
        }
    )
    val (fabVisible, toggleFabVisible) = rememberFabVisibilityState(
        scrollState = scrollState, fabExpanded = ui.fabExpanded, einkTapOnly = einkMode.enabled
    )
    val scrollToTopEnabled by screenModel.scrollToTopEnabled.collectAsState()
    val scrollToTopVisible = rememberScrollToTopVisibility(
        scrollState = scrollState, fabVisible = fabVisible, einkTapOnly = einkMode.enabled
    )
    val showStickyTitle = rememberStickyTitleVisibility(scrollState = scrollState, bannerHeight = bannerHeight, toolbarHeight = toolbarHeight)
    val readingProgress = rememberReadingProgress(scrollState, bannerHeight, toolbarHeight)

    // Push reading state to screen model on scroll changes
    LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
        // contentRendered: until the article has loaded its height is not final, and a progress
        // measured against it would be saved as the reader's position.
        if (trackReadingProgress && scrollRestoration.hasRestoredScroll && scrollRestoration.contentRendered &&
            loadingState is BookmarkLoadingState.FullyLoaded
        ) {
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
            handleViewerKeyEvent(keyEvent, isEmbedded, ui, galleryViewerState, pageTurnDispatcher, scope)
        },
        snackbarHost = { EinkAwareSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ViewerFab(
                visible = fabVisible && !getPlatform().isDesktop &&
                    !ui.showDetailsPanel && !ui.showAppearancePanel && !ui.showSearch &&
                    galleryViewerState.request == null,
                animated = !einkMode.animationsDisabled,
                loadingState = loadingState,
                ui = ui,
                screenModel = screenModel,
                onShareClick = { bookmark -> shareBookmark(bookmark, scope, snackbarManager) },
                onOpenInBrowserClick = { bookmark -> openBookmarkInBrowser(bookmark, uriHandler, scope, snackbarManager) }
            )
        }
    ) { padding ->
        when (val state = displayState) {
            is BookmarkLoadingState.Initial -> ViewerInitialContent(
                state = state,
                initialTitle = initialTitle,
                initialUrl = initialUrl,
                toolbarHeight = toolbarHeight,
                padding = padding,
                onBack = onBack
            )
            is BookmarkLoadingState.FullyLoaded -> ViewerLoadedContent(
                state = state,
                loadingState = loadingState,
                bookmarkId = bookmarkId,
                scrollToHighlightId = scrollToHighlightId,
                screenModel = screenModel,
                ui = ui,
                serverRepository = serverRepository,
                scope = scope,
                scrollState = scrollState,
                scrollRestoration = scrollRestoration,
                galleryViewerState = galleryViewerState,
                snapRegistry = snapRegistry,
                snapToContent = pageTurnBindings.snapToContent,
                listTopInRoot = { listTopInRoot },
                onListTopInRootChanged = { listTopInRoot = it },
                pagedRendering = pagedRendering,
                pagedPosition = pagedPosition,
                chromeInsets = chromeInsets,
                readerTrailingPadPx = readerTrailingPadPx,
                toolbarHeight = toolbarHeight,
                toggleFabVisible = toggleFabVisible,
                scrollToTopVisible = scrollToTopVisible,
                scrollToTopEnabled = scrollToTopEnabled,
                showStickyTitle = showStickyTitle,
                readingProgress = readingProgress,
                highlightScrollDone = highlightScrollDone,
                viewerMode = viewerMode,
                hideArticleThumbnails = hideArticleThumbnails,
                htmlTextColor = htmlTextColor,
                htmlBackgroundColor = htmlBackgroundColor,
                htmlFontSize = htmlFontSize,
                htmlFontFamily = htmlFontFamily,
                readerTypography = readerTypography,
                precrawledAssetPath = precrawledAssetPath,
                selectedSource = selectedSource,
                archiveAvailable = archiveAvailable,
                sourceContentOverride = sourceContentOverride,
                isLoadingSource = isLoadingSource,
                highlights = highlights,
                showTags = showTags,
                dateDisplayMode = dateDisplayMode,
                showHeroImage = showHeroImage,
                isRefreshing = isRefreshing,
                aiActionInFlight = aiActionInFlight,
                aiCapabilitiesByServer = aiCapabilitiesByServer,
                trackReadingProgress = trackReadingProgress,
                serverProgressChecked = serverProgressChecked,
                contentFetchAttempted = contentFetchAttempted,
                isFullscreen = isFullscreen,
                onFullscreenToggle = onFullscreenToggle,
                openLink = { linkUrl, failureMessage ->
                    openReaderLink(linkUrl, linkOpenMode, openInCustomTab, uriHandler, failureMessage)
                },
                onHighlightPosition = { id, position ->
                    ui.highlightPosition = ui.highlightMask.accumulate(position, fallbackKey = id)
                    if (id == scrollToHighlightId && !highlightPositionReceived) highlightPositionReceived = true
                },
                onSearchMatchPosition = { y ->
                    if (ui.currentMatchIndex != ui.lastScrolledMatchIndex) {
                        ui.lastScrolledMatchIndex = ui.currentMatchIndex
                        val st = loadingState as? BookmarkLoadingState.FullyLoaded
                        if (st != null) {
                            val cbi = contentBodyIndexFor(st.bookmark)
                            scope.launch {
                                val scrollOffset = searchMatchScrollOffset(scrollState.layoutInfo, cbi, y)
                                if (einkMode.instantScroll) {
                                    scrollRestoration.safeScrollToItem(cbi, scrollOffset)
                                } else {
                                    scrollState.animateScrollToItem(cbi, scrollOffset)
                                }
                            }
                        }
                    }
                },
                onShareClick = { bookmark -> shareBookmark(bookmark, scope, snackbarManager) },
                onOpenInBrowserClick = { bookmark -> openBookmarkInBrowser(bookmark, uriHandler, scope, snackbarManager) },
                onBack = onBack,
                onTagFilterApply = onTagFilterApply
            )
            is BookmarkLoadingState.Error -> ViewerErrorContent(message = state.message, padding = padding)
        }

        // Panels and dialogs. Inside the Scaffold content so the snackbar host, which the
        // Scaffold lays out after content, draws above the details panel rather than under it.
        ViewerContentPanels(
            loadingState = loadingState, viewerMode = viewerMode,
            htmlTextColor = htmlTextColor, htmlBackgroundColor = htmlBackgroundColor,
            htmlFontSize = htmlFontSize, htmlFontFamily = htmlFontFamily, lists = lists,
            showModeDialog = ui.showModeDialog, onShowModeDialogChanged = { ui.showModeDialog = it },
            selectedSource = selectedSource,
            showAppearancePanel = ui.showAppearancePanel, onShowAppearancePanelChanged = { ui.showAppearancePanel = it },
            showDetailsPanel = ui.showDetailsPanel, onShowDetailsPanelChanged = { ui.showDetailsPanel = it },
            showDeleteConfirmation = ui.showDeleteConfirmation, onShowDeleteConfirmationChanged = { ui.showDeleteConfirmation = it },
            showListPicker = ui.showListPicker, onShowListPickerChanged = { ui.showListPicker = it },
            showTagEditor = ui.showTagEditor, onShowTagEditorChanged = { ui.showTagEditor = it },
            selectedHighlightId = ui.selectedHighlightId, onSelectedHighlightIdChanged = { ui.selectedHighlightId = it },
            selectedHighlightText = ui.selectedHighlightText, onSelectedHighlightTextChanged = { ui.selectedHighlightText = it },
            selectedHighlight = selectedHighlight,
            assets = assets,
            showSearch = ui.showSearch, onShowSearchChanged = { ui.showSearch = it },
            screenModel = screenModel, scope = scope, onBack = onBack,
            onLinkCopied = {
                scope.launch { snackbarManager.showSnackbar("Copied to clipboard") }
            },
            onOpenLink = {
                scope.launch { snackbarManager.showSnackbar("Opening in browser") }
            }
        )
    }

}
