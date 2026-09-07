package com.karakept.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.utils.AppLogger
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.EinkAwareSmallFab
import com.karakept.app.ui.components.EinkAwareSnackbarHost
import com.karakept.app.ui.components.FloatingBusyCard
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.components.RefreshableBox
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.scrollToTop
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.input.PageTurnScrollEffect
import com.karakept.app.ui.input.handleDesktopPageKey
import com.karakept.app.ui.utils.BODY_LINE_HEIGHT_RATIO
import com.karakept.app.ui.utils.PagedPositionState
import com.karakept.app.ui.utils.READER_MAX_SNAP_FRACTION
import com.karakept.app.ui.utils.computeSnapAdjustment
import com.karakept.app.ui.utils.pageWorthTurning
import com.karakept.app.ui.utils.pagedBottomEdge
import com.karakept.app.ui.utils.trailingPagePaddingFor
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.components.reader.GalleryViewerState
import com.karakept.app.ui.components.reader.ImageGalleryOverlay
import com.karakept.app.ui.components.reader.LocalGalleryViewerState
import com.karakept.app.ui.components.reader.heroGalleryImage
import com.karakept.app.ui.components.reader.LocalReaderSnapRegistry
import com.karakept.app.ui.components.reader.ReaderSnapRegistry
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
    val highlightMask = remember { com.karakept.app.ui.components.HighlightMaskAccumulator() }
    var selectedHighlightText by remember { mutableStateOf<String?>(null) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableStateOf(0) }
    var lastScrolledMatchIndex by remember { mutableStateOf(-1) }
    val contentFocusRequester = remember { FocusRequester() }

    // Open reader search when triggered externally (desktop split-pane Ctrl+F).
    // lastHandledSearchTrigger is initialised from the current searchTrigger so that
    // stale non-zero values inherited across bookmark changes don't open search.
    val lastHandledSearchTrigger = remember { mutableStateOf(searchTrigger) }
    LaunchedEffect(searchTrigger) {
        if (searchTrigger != lastHandledSearchTrigger.value) {
            lastHandledSearchTrigger.value = searchTrigger
            showSearch = true
        }
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

    LaunchedEffect(selectedHighlightId) {
        highlightMask.clear()
        highlightPosition = null
    }

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
            // Scrolling forward by `pageDelta` moves content up by the same amount, so the line
            // that ends up at the fold is the one sitting `pageDelta` below it right now.
            val landingPoint = when (direction) {
                PageTurnDirection.NEXT -> foldInRoot + pageDelta
                PageTurnDirection.PREVIOUS -> foldInRoot - pageDelta
            }
            snapRegistry.lineTopAt(landingPoint)?.let { lineTop ->
                computeSnapAdjustment(
                    residualPx = landingPoint - lineTop,
                    pageDeltaPx = pageDelta,
                    maxSnapFraction = READER_MAX_SNAP_FRACTION
                )
            } ?: 0f
        },
        hasTrailingPadding = readerTrailingPadPx > 0,
        // The article box runs past its last line — paragraph padding plus the renderer's own
        // bottom margin — so a turn measured against the lazy item keeps going after the last
        // word is read and lands on blank space. The registry knows where the text actually ends.
        moreContentBelow = {
            val fold = listTopInRoot +
                scrollState.layoutInfo.viewportEndOffset - chromeInsets.bottomPx
            snapRegistry.contentEndInRoot?.let { contentEnd ->
                pageWorthTurning(
                    hasLineBelow = snapRegistry.hasLineBelow(fold),
                    contentEndGapPx = contentEnd - fold,
                    minAdvancePx = readerTailUnitPx.toFloat()
                )
            } ?: true
        },
        onScrolled = {
            scrollRestoration.approveCurrentPosition()
            pagedPosition.markSettled(scrollState)
        }
    )
    val (fabVisible, toggleFabVisible) = rememberFabVisibilityState(
        scrollState = scrollState, fabExpanded = fabExpanded, einkTapOnly = einkMode.enabled
    )
    val scrollToTopEnabled by screenModel.scrollToTopEnabled.collectAsState()
    val scrollToTopVisible = rememberScrollToTopVisibility(
        scrollState = scrollState, fabVisible = fabVisible, einkTapOnly = einkMode.enabled
    )
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
                    !isEmbedded && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.keyboardKey == Key.F -> {
                        showSearch = true
                        true
                    }
                    keyEvent.keyboardKey == Key.Escape && showSearch -> {
                        showSearch = false
                        searchQuery = ""
                        searchMatches = emptyList()
                        // Delay so the TextField releases focus before we claim it,
                        // ensuring the outer onPreviewKeyEvent sees the next Ctrl+F.
                        scope.launch { kotlinx.coroutines.delay(100); contentFocusRequester.requestFocus() }
                        true
                    }
                    // Desktop has no hardware back button, so Escape is the gallery's own
                    // dismiss key here (BackHandler is a no-op on desktop).
                    keyEvent.keyboardKey == Key.Escape && galleryViewerState.request != null -> {
                        galleryViewerState.close()
                        true
                    }
                    // Keyboard equivalents of the e-ink facade buttons, so page turning can be
                    // exercised on desktop without the device.
                    !showSearch && pageTurnDispatcher.handleDesktopPageKey(keyEvent) -> true
                    else -> false
                }
            } else false
        },
        snackbarHost = { EinkAwareSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibilityOrPlain(
                visible = fabVisible && !getPlatform().isDesktop &&
                    !showDetailsPanel && !showAppearancePanel && !showSearch &&
                    galleryViewerState.request == null,
                animated = !einkMode.animationsDisabled
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
                // The caller (bookmark list, highlights list, ...) often already has the title
                // and url in memory — passed through as initialTitle/initialUrl — so the top bar
                // can render immediately instead of waiting on this screen's own DB query to
                // resolve. Without it there is nothing to show up top, so fall back to the
                // full-screen skeleton/dots as before.
                if (initialTitle != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ViewerTopBar(
                            title = initialTitle,
                            url = initialUrl ?: "",
                            showStickyTitle = true,
                            showMenu = false,
                            toolbarHeight = toolbarHeight,
                            readingProgress = 0f,
                            onBackClick = onBack,
                            onMenuToggle = {},
                            onAppearanceClick = {},
                            onMoveToListClick = {},
                            onEditTagsClick = {},
                            onRefreshClick = {},
                            onDeleteClick = {},
                            isDesktop = getPlatform().isDesktop,
                            bookmark = null
                        )
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingDotsIndicator(label = "Loading…")
                        }
                    }
                } else {
                    // fillMaxSize so the e-ink dots land centered like every other loading slot —
                    // without a height the Box wraps them and they sit pinned to the top, then
                    // jump to the middle as soon as the next loading state takes over.
                    BookmarkContentLoader(
                        loadingState = state,
                        modifier = Modifier.padding(padding).fillMaxSize()
                    )
                }
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

                // Resolved here rather than inside the hero item: that item is disposed once
                // scrolled off, and an image tapped further down still opens on the banner.
                val heroImage = remember(
                    showHeroImage, title, bannerImageUrl, screenshotUrl,
                    bannerImageLocalPath, screenshotLocalPath
                ) {
                    if (!showHeroImage) null else heroGalleryImage(
                        title = title,
                        bannerImageUrl = bannerImageUrl,
                        screenshotUrl = screenshotUrl,
                        bannerImageLocalPath = bannerImageLocalPath,
                        screenshotLocalPath = screenshotLocalPath
                    )
                }
                SideEffect { galleryViewerState.heroImage = heroImage }

                val viewerContent: @Composable () -> Unit = {
                    Box(modifier = Modifier.fillMaxSize()
                        .then(if (getPlatform().isDesktop)
                            Modifier.focusRequester(contentFocusRequester).focusable()
                        else Modifier)
                    ) {
                    // Provided here so RenderResolvedImage (deep inside the LazyColumn's HTML
                    // content) can request the full-screen gallery below without a Dialog.
                    CompositionLocalProvider(
                        LocalGalleryViewerState provides galleryViewerState,
                        // Null switches the reporting off entirely in every text block, so the
                        // registry costs nothing when the user has snapping turned off.
                        LocalReaderSnapRegistry provides
                            snapRegistry.takeIf { pageTurnBindings.snapToContent }
                    ) {
                    val needsScrollRestore = trackReadingProgress && !scrollRestoration.hasRestoredScroll &&
                        loadingState is BookmarkLoadingState.FullyLoaded &&
                        ((loadingState as BookmarkLoadingState.FullyLoaded).bookmark.readingProgress > 0.02f || !serverProgressChecked)
                    val needsHighlightScroll = !highlightScrollDone
                    // Only the article body waits on scroll restoration; the hero and
                    // description render immediately so the screen is never blank.
                    val contentRevealed = computeContentRevealed(needsScrollRestore, needsHighlightScroll)
                    // For bookmarks with a saved reading position, cover the whole screen
                    // with a shimmer instead, so we land directly at that position rather
                    // than flashing the hero and then auto-scrolling down.
                    val restoringToSavedPosition = shouldShowRestoreOverlay(needsScrollRestore, state.bookmark.readingProgress)

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize()
                            // Anchors the snap registry's root coordinates: on the wide desktop
                            // layout this pane sits beside the bookmark list, so its top is not
                            // the window's.
                            .onGloballyPositioned { listTopInRoot = it.positionInRoot().y }
                            .then(
                                if (!getPlatform().isDesktop) {
                                    // Tap the reading surface to show/hide the FAB. Taps consumed by
                                    // links, highlights, or the highlight-dimming overlay never reach
                                    // here, so this only fires on plain content taps.
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            if (selectedHighlightId == null) toggleFabVisible()
                                        })
                                    }
                                } else Modifier
                            )
                            .pagedBottomEdge(
                                enabled = pagedRendering,
                                // What NativeHtmlRenderer paints behind the text itself, so the
                                // band is indistinguishable from the margin below the last line.
                                color = htmlBackgroundColor ?: MaterialTheme.colorScheme.surface,
                                maxSnapFraction = READER_MAX_SNAP_FRACTION,
                                obscuredTopPx = chromeInsets.topPx,
                                obscuredBottomPx = chromeInsets.bottomPx,
                                isSettledOnPage = { pagedPosition.isSettled(scrollState) },
                                residualPx = { visibleBottom ->
                                    // The registry is a plain map, invisible to the snapshot
                                    // system, so the scroll offset is read here to make the band
                                    // repaint as the article moves.
                                    scrollState.firstVisibleItemScrollOffset
                                    val edge = listTopInRoot + visibleBottom
                                    snapRegistry.lineTopAt(edge)?.let { edge - it } ?: 0f
                                }
                            ),
                        contentPadding = PaddingValues(
                            bottom = with(density) { readerTrailingPadPx.toDp() }
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val contentItemModifier = Modifier.widthIn(max = readerTypography.maxWidthDp.dp)

                        item(key = "hero_banner") {
                            Box(modifier = contentItemModifier) {
                                HeroBannerSection(
                                    title = title, url = url, tags = state.bookmark.tags,
                                    readingTimeMinutes = readingTimeMinutes, showTags = showTags,
                                    scrollState = scrollState, createdAt = state.bookmark.createdAt,
                                    dateDisplayMode = dateDisplayMode,
                                    showImage = showHeroImage,
                                    onImageClick = if (heroImage != null) {
                                        { galleryViewerState.openHero() }
                                    } else null,
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

                        // The AI summary is a separate field from the crawler's description, so
                        // both can be present. It goes first: it describes the article as a whole,
                        // where the meta description is usually a teaser.
                        if (!state.bookmark.summary.isNullOrBlank()) {
                            item(key = "summary_card") {
                                Box(modifier = contentItemModifier) {
                                    SummaryCard(
                                        summary = state.bookmark.summary,
                                        htmlBackgroundColor = htmlBackgroundColor,
                                        htmlTextColor = htmlTextColor,
                                        htmlFontSize = htmlFontSize,
                                        htmlFontFamily = htmlFontFamily
                                    )
                                }
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
                                    readerTypography = readerTypography,
                                    content = state.bookmark.content, viewerMode = viewerMode,
                                    removeFirstImage = hideArticleThumbnails,
                                    htmlTextColor = htmlTextColor, htmlBackgroundColor = htmlBackgroundColor,
                                    htmlFontSize = htmlFontSize, htmlFontFamily = htmlFontFamily,
                                    precrawledAssetPath = precrawledAssetPath,
                                    selectedSource = selectedSource,
                                    sourceContentOverride = sourceContentOverride,
                                    loadingState = state,
                                    contentFetchAttempted = contentFetchAttempted,
                                    contentRevealed = contentRevealed,
                                    archiveAvailableOnServer = archiveAvailable,
                                    isLoadingArchive = isLoadingSource,
                                    onFetchArchive = { screenModel.fetchAndCacheArchive(state.bookmark) },
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
                                        highlightPosition = highlightMask.accumulate(position, fallbackKey = id)
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
                                                    if (einkMode.instantScroll) {
                                                        scrollRestoration.safeScrollToItem(cbi, scrollOffset)
                                                    } else {
                                                        scrollState.animateScrollToItem(cbi, scrollOffset)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Full-page shimmer while restoring to a saved reading position. It sits
                    // above the LazyColumn (which scrolls to the saved offset underneath) but
                    // below the top bar, and fades out once restoration completes.
                    AnimatedVisibilityOrPlain(
                        visible = restoringToSavedPosition,
                        animated = !einkMode.animationsDisabled
                    ) {
                        BookmarkContentLoader(
                            loadingState = BookmarkLoadingState.Initial,
                            modifier = Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }

                    // Scroll-to-top button (READER-03)
                    val scrollToTopTarget = if (showSearch && !getPlatform().isDesktop) 76.dp else 16.dp
                    val animatedScrollToTopPadding by animateDpAsState(
                        targetValue = scrollToTopTarget,
                        animationSpec = tween(300),
                        label = "scrollToTopBottomPadding"
                    )
                    val scrollToTopBottomPadding =
                        if (einkMode.animationsDisabled) scrollToTopTarget else animatedScrollToTopPadding
                    AnimatedVisibilityOrPlain(
                        visible = scrollToTopVisible && scrollToTopEnabled,
                        animated = !einkMode.animationsDisabled,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, bottom = scrollToTopBottomPadding)
                    ) {
                        EinkAwareSmallFab(
                            onClick = {
                                scope.launch {
                                    scrollState.scrollToTop(einkMode.instantScroll)
                                    // Use safeScrollToItem to update the scroll guard's
                                    // approved position — without this, the guard detects
                                    // an "unintended jump" and snaps back to the old position
                                    scrollRestoration.safeScrollToItem(0, 0)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.Default.ArrowUpward,
                                contentDescription = "Scroll to top"
                            )
                        }
                    }

                    // Global Dimming Overlay
                    if (selectedHighlightId != null) {
                        var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
                        // A 60% black scrim is a page-sized ink dump that ghosts for several turns,
                        // and it leaves the rest of the article barely legible on a monochrome
                        // panel. Outlining the selection says the same thing for a hundredth of
                        // the ink.
                        val outlineOnly = einkMode.highContrast
                        val outlineColor = MaterialTheme.colorScheme.outline
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .onGloballyPositioned { coords -> overlayRootOffset = coords.positionInRoot() }
                                .pointerInput(Unit) { detectTapGestures { selectedHighlightId = null } }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.99f)) {
                                if (!outlineOnly) drawRect(Color.Black.copy(alpha = 0.6f))
                                val pos = highlightPosition
                                val posPath = pos?.path
                                if (posPath != null) {
                                    withTransform({ translate(pos.rootOffset.x - overlayRootOffset.x, pos.rootOffset.y - overlayRootOffset.y) }) {
                                        if (outlineOnly) {
                                            drawPath(
                                                path = posPath,
                                                color = outlineColor,
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        } else {
                                            drawPath(path = posPath, color = Color.Transparent, blendMode = BlendMode.Clear)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Top Bar
                    ViewerTopBar(
                        title = title, url = url,
                        showStickyTitle = shouldShowStickyTitle(showStickyTitle, restoringToSavedPosition),
                        showMenu = showMenu,
                        toolbarHeight = toolbarHeight,
                        readingProgress = if (trackReadingProgress) readingProgress else 0f,
                        isRefreshing = isRefreshing,
                        onBackClick = onBack, onMenuToggle = { showMenu = it },
                        onAppearanceClick = { showAppearancePanel = true },
                        onViewerModeClick = { showModeDialog = true },
                        onMoveToListClick = { showListPicker = true },
                        onEditTagsClick = { showTagEditor = true },
                        onRefreshClick = { screenModel.refreshBookmark() },
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
                        onDetailsClick = { showDetailsPanel = true },
                        aiCapabilities = aiCapabilitiesByServer[state.bookmark.serverId] ?: AiCapabilities(),
                        aiActionInFlight = aiActionInFlight,
                        onRunAiAction = { action -> screenModel.runAiAction(state.bookmark, action) }
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
                            scope.launch { kotlinx.coroutines.delay(100); contentFocusRequester.requestFocus() }
                        },
                        maxWidth = if (isDesktop) 520.dp else 360.dp,
                        modifier = if (isDesktop)
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                        else
                            Modifier.align(Alignment.BottomEnd)
                                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                                .padding(end = 80.dp, bottom = 14.dp, start = 16.dp)
                    )

                    // Full-screen image viewer. Rendered inline (not a Dialog) so it stays in
                    // the reader's own window — see GalleryViewerState's doc for why that
                    // matters for hardware page-turn keys — and drawn last so it paints above
                    // the top bar and search bar.
                    galleryViewerState.request?.let { request ->
                        ImageGalleryOverlay(
                            images = request.images,
                            initialIndex = request.initialIndex,
                            onDismiss = { galleryViewerState.close() }
                        )
                    }
                    } // end CompositionLocalProvider
                    } // end inner Box
                }

                // E-ink readers refresh from the overflow menu instead — see RefreshableBox.
                RefreshableBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { screenModel.refreshBookmark() },
                    enabled = !getPlatform().isDesktop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    viewerContent()
                    // Without the pull gesture's spinner, and with the top bar's indeterminate
                    // strip skipped on e-ink, this card is the only sign a refresh is running.
                    if (isRefreshing && einkMode.animationsDisabled) {
                        FloatingBusyCard(modifier = Modifier.align(Alignment.Center)) {
                            LoadingDotsIndicator(label = "Refreshing…", dotSize = 8.dp)
                        }
                    }
                }
            }
            is BookmarkLoadingState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.message}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Panels and dialogs. Inside the Scaffold content so the snackbar host, which the
        // Scaffold lays out after content, draws above the details panel rather than under it.
        ViewerContentPanels(
            loadingState = loadingState, viewerMode = viewerMode,
            htmlTextColor = htmlTextColor, htmlBackgroundColor = htmlBackgroundColor,
            htmlFontSize = htmlFontSize, htmlFontFamily = htmlFontFamily, lists = lists,
            showModeDialog = showModeDialog, onShowModeDialogChanged = { showModeDialog = it },
            selectedSource = selectedSource,
            showAppearancePanel = showAppearancePanel, onShowAppearancePanelChanged = { showAppearancePanel = it },
            showDetailsPanel = showDetailsPanel, onShowDetailsPanelChanged = { showDetailsPanel = it },
            showDeleteConfirmation = showDeleteConfirmation, onShowDeleteConfirmationChanged = { showDeleteConfirmation = it },
            showListPicker = showListPicker, onShowListPickerChanged = { showListPicker = it },
            showTagEditor = showTagEditor, onShowTagEditorChanged = { showTagEditor = it },
            selectedHighlightId = selectedHighlightId, onSelectedHighlightIdChanged = { selectedHighlightId = it },
            selectedHighlightText = selectedHighlightText, onSelectedHighlightTextChanged = { selectedHighlightText = it },
            selectedHighlight = selectedHighlight,
            assets = assets,
            showSearch = showSearch, onShowSearchChanged = { showSearch = it },
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
