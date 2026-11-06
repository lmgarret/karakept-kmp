package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ReaderTypography
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.domain.action.AiAction
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.FloatingBusyCard
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.components.RefreshableBox
import com.karakept.app.ui.components.highlight.HighlightPosition
import com.karakept.app.ui.components.reader.BookmarkContentLoader
import com.karakept.app.ui.components.reader.GalleryViewerState
import com.karakept.app.ui.components.reader.ImageGalleryOverlay
import com.karakept.app.ui.components.reader.LocalGalleryViewerState
import com.karakept.app.ui.components.reader.LocalReaderSnapRegistry
import com.karakept.app.ui.components.reader.LocalReaderToc
import com.karakept.app.ui.components.reader.ReaderScrollCursor
import com.karakept.app.ui.components.reader.ReaderSnapRegistry
import com.karakept.app.ui.components.reader.ReaderTocState
import com.karakept.app.ui.components.reader.heroGalleryImage
import com.karakept.app.ui.components.scrollToTop
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.PagedPositionState
import com.karakept.app.ui.utils.READER_MAX_SNAP_FRACTION
import com.karakept.app.ui.utils.pagedBottomEdge
import getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ViewerLoadedContent(
    state: BookmarkLoadingState.FullyLoaded,
    loadingState: BookmarkLoadingState,
    bookmarkId: Long,
    scrollToHighlightId: String?,
    screenModel: BookmarkViewerScreenModel,
    ui: ViewerUiState,
    serverRepository: ServerRepository,
    scope: CoroutineScope,
    scrollState: LazyListState,
    scrollRestoration: ScrollRestorationState,
    galleryViewerState: GalleryViewerState,
    snapRegistry: ReaderSnapRegistry,
    snapToContent: Boolean,
    tocState: ReaderTocState,
    showScrollCursor: Boolean,
    listTopInRoot: () -> Float,
    onListTopInRootChanged: (Float) -> Unit,
    pagedRendering: Boolean,
    pagedPosition: PagedPositionState,
    chromeInsets: ViewerChromeInsets,
    readerTrailingPadPx: Int,
    toolbarHeight: Dp,
    toggleFabVisible: () -> Unit,
    scrollToTopVisible: Boolean,
    scrollToTopEnabled: Boolean,
    showStickyTitle: Boolean,
    readingProgress: Float,
    highlightScrollDone: Boolean,
    viewerMode: ViewerMode,
    hideArticleThumbnails: Boolean,
    htmlTextColor: Color?,
    htmlBackgroundColor: Color?,
    htmlFontSize: Int,
    htmlFontFamily: ReaderFontFamily,
    readerTypography: ReaderTypography,
    precrawledAssetPath: String?,
    selectedSource: ContentSource,
    archiveAvailable: Boolean,
    sourceContentOverride: String?,
    isLoadingSource: Boolean,
    highlights: List<Highlight>,
    showTags: Boolean,
    dateDisplayMode: DateDisplayMode,
    showHeroImage: Boolean,
    isRefreshing: Boolean,
    aiActionInFlight: AiAction?,
    aiCapabilitiesByServer: Map<String, AiCapabilities>,
    trackReadingProgress: Boolean,
    serverProgressChecked: Boolean,
    contentFetchAttempted: Boolean,
    isFullscreen: Boolean,
    onFullscreenToggle: (() -> Unit)?,
    openLink: (url: String, failureMessage: String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    onSearchMatchPosition: (Float) -> Unit,
    onShareClick: (Bookmark) -> Unit,
    onOpenInBrowserClick: (Bookmark) -> Unit,
    onBack: () -> Unit,
    onTagFilterApply: (tag: String) -> Unit
) {
    val einkMode = LocalEinkMode.current
    val density = LocalDensity.current

    val title = state.bookmark.title
    val url = state.bookmark.url
    val readingTimeMinutes = state.bookmark.readingTimeMinutes
    val description = state.bookmark.description

    val servers by serverRepository.servers.collectAsState(initial = emptyList())
    val server = servers.firstOrNull()
    val bannerAssetId = state.bookmark.bannerImageAssetId
    val bannerImageUrl = if (server != null && bannerAssetId != null)
        com.karakept.app.utils.AssetUrlUtils.getAssetUrl(server.url, bannerAssetId) else null
    val screenshotAssetId = state.bookmark.screenshotAssetId
    val screenshotUrl = if (server != null && screenshotAssetId != null)
        com.karakept.app.utils.AssetUrlUtils.getAssetUrl(server.url, screenshotAssetId) else null
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
                Modifier.focusRequester(ui.contentFocusRequester).focusable()
            else Modifier)
        ) {
        // Provided here so RenderResolvedImage (deep inside the LazyColumn's HTML
        // content) can request the full-screen gallery below without a Dialog.
        CompositionLocalProvider(
            LocalGalleryViewerState provides galleryViewerState,
            // Null switches the reporting off entirely in every text block, so the
            // registry costs nothing when the user has snapping turned off.
            LocalReaderSnapRegistry provides
                snapRegistry.takeIf { snapToContent },
            // Null switches the registration off in every heading, so the table of contents
            // costs nothing when the cursor is turned off.
            LocalReaderToc provides tocState.takeIf { showScrollCursor }
        ) {
        val needsScrollRestore = trackReadingProgress && !scrollRestoration.hasRestoredScroll &&
            loadingState is BookmarkLoadingState.FullyLoaded &&
            (loadingState.bookmark.readingProgress > 0.02f || !serverProgressChecked)
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
                .onGloballyPositioned { onListTopInRootChanged(it.positionInRoot().y) }
                .then(
                    if (!getPlatform().isDesktop) {
                        // Tap the reading surface to show/hide the FAB. Taps consumed by
                        // links, highlights, or the highlight-dimming overlay never reach
                        // here, so this only fires on plain content taps.
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                if (ui.selectedHighlightId == null) toggleFabVisible()
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
                        val edge = listTopInRoot() + visibleBottom
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
                            openLink(url, "Failed to handle reader action")
                        } } else null,
                        onTagClick = { tag -> onTagFilterApply(tag) },
                        onInfoClick = { ui.showDetailsPanel = true },
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
                            summary = state.bookmark.summary.orEmpty(),
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
                            openLink(linkUrl, "Failed to process highlight")
                        },
                        onCreateHighlight = { text, start, end, note, color ->
                            screenModel.createHighlight(state.bookmark, text, start, end, note, color) { highlightId ->
                                ui.selectedHighlightText = text; ui.selectedHighlightId = highlightId
                            }
                        },
                        onDeleteHighlight = { highlightId -> screenModel.deleteHighlight(state.bookmark, highlightId) },
                        onHighlightClick = { id -> ui.selectedHighlightId = id },
                        onHighlightPosition = onHighlightPosition,
                        onContentReady = { scrollRestoration.onContentRendered() },
                        scrollToHighlightId = scrollToHighlightId,
                        selectedHighlightId = ui.selectedHighlightId ?: scrollToHighlightId,
                        cachedDocument = { sanitizedHtml -> screenModel.cachedDocument(bookmarkId, sanitizedHtml) },
                        onDocumentPrepared = { document -> screenModel.cacheDocument(bookmarkId, document) },
                        searchQuery = if (ui.showSearch) ui.searchQuery else "",
                        activeSearchMatchIndex = ui.currentMatchIndex,
                        onSearchMatchesFound = { matches ->
                            ui.searchMatches = matches
                            if (ui.currentMatchIndex >= matches.size) ui.currentMatchIndex = 0
                        },
                        onSearchMatchPosition = onSearchMatchPosition
                    )
                }
            }
        }

        if (showScrollCursor && contentRevealed) {
            ReaderScrollCursor(
                listState = scrollState,
                toc = tocState,
                listTopInRoot = listTopInRoot,
                // Not scrollState.scrollToItem: the reader's scroll guard snaps back any move it
                // did not sanction, and a drag is a long run of them.
                onScrollTo = scrollRestoration.safeScrollToItem,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    // The top bar and the system bars are painted over the list, so the track has
                    // to stop short of both or its ends are ungrabbable.
                    .padding(
                        top = with(density) { chromeInsets.topPx.toDp() },
                        bottom = with(density) { chromeInsets.bottomPx.toDp() }
                    )
            )
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
        ViewerScrollToTopButton(
            visible = scrollToTopVisible && scrollToTopEnabled,
            showSearch = ui.showSearch,
            animationsDisabled = einkMode.animationsDisabled,
            onClick = {
                scope.launch {
                    scrollState.scrollToTop(einkMode.instantScroll)
                    // Use safeScrollToItem to update the scroll guard's
                    // approved position — without this, the guard detects
                    // an "unintended jump" and snaps back to the old position
                    scrollRestoration.safeScrollToItem(0, 0)
                }
            }
        )

        // Global Dimming Overlay
        if (ui.selectedHighlightId != null) {
            HighlightDimmingOverlay(ui = ui)
        }

        // Top Bar
        ViewerTopBar(
            title = title, url = url,
            showStickyTitle = shouldShowStickyTitle(showStickyTitle, restoringToSavedPosition),
            showMenu = ui.showMenu,
            toolbarHeight = toolbarHeight,
            readingProgress = if (trackReadingProgress) readingProgress else 0f,
            isRefreshing = isRefreshing,
            onBackClick = onBack, onMenuToggle = { ui.showMenu = it },
            onAppearanceClick = { ui.showAppearancePanel = true },
            onViewerModeClick = { ui.showModeDialog = true },
            onMoveToListClick = { ui.showListPicker = true },
            onEditTagsClick = { ui.showTagEditor = true },
            onRefreshClick = { screenModel.refreshBookmark() },
            onDeleteClick = { ui.showDeleteConfirmation = true },
            onSearchClick = { ui.showSearch = true },
            isDesktop = getPlatform().isDesktop, bookmark = state.bookmark,
            onFavoriteClick = { screenModel.toggleBookmarkFavorite(state.bookmark) },
            onArchiveClick = { screenModel.toggleBookmarkArchive(state.bookmark) },
            onReadClick = { screenModel.toggleBookmarkRead(state.bookmark) },
            onShareClick = { onShareClick(state.bookmark) },
            onOpenInBrowserClick = { onOpenInBrowserClick(state.bookmark) },
            isFullscreen = isFullscreen, onFullscreenToggle = onFullscreenToggle,
            onDetailsClick = { ui.showDetailsPanel = true },
            aiCapabilities = aiCapabilitiesByServer[state.bookmark.serverId] ?: AiCapabilities(),
            aiActionInFlight = aiActionInFlight,
            onRunAiAction = { action -> screenModel.runAiAction(state.bookmark, action) }
        )

        // Search bar — floating pill, to the left of FAB on Android
        ViewerSearchBarHost(ui = ui, scope = scope)

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
