package com.karakept.app.ui.components.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ReaderTypography
import com.karakept.app.ui.components.highlight.HighlightPosition
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.theme.rememberFontFamily
import androidx.compose.runtime.withFrameNanos
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Native Compose Multiplatform HTML renderer for READER mode.
 *
 * Replaces the WebView-based renderer with pure Compose composables.
 * Parses sanitized HTML via ksoup and emits Text, Image, and layout composables.
 *
 * Highlight offsets are compatible with the WebView's JavaScript TreeWalker
 * approach: both walk text nodes in document order and count characters.
 */
@Composable
fun NativeHtmlRenderer(
    html: String,
    modifier: Modifier = Modifier,
    highlights: List<Highlight> = emptyList(),
    textColor: Color? = null,
    fontSize: Int = 16,
    fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    onLinkClick: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onHighlightPosition: (String, HighlightPosition) -> Unit = { _, _ -> },
    scrollToHighlightId: String? = null,
    onLoaded: (() -> Unit)? = null
) {
    // Prepared in composition, on the main thread: only for callers with no coroutine to
    // prepare it in. The reader goes through HtmlContent, which prepares it in the background.
    val document = remember(html) { prepareReaderDocument(html) }
    if (document == null) {
        onLoaded?.invoke()
        return
    }
    NativeHtmlRenderer(
        document = document,
        modifier = modifier,
        highlights = highlights,
        textColor = textColor,
        fontSize = fontSize,
        fontFamily = fontFamily,
        onLinkClick = onLinkClick,
        onHighlightClick = onHighlightClick,
        onCreateHighlight = onCreateHighlight,
        onHighlightPosition = onHighlightPosition,
        scrollToHighlightId = scrollToHighlightId,
        onLoaded = onLoaded
    )
}

/**
 * Renders an article [document] already prepared by [prepareReaderDocument], which is where every
 * full-document walk happens — nothing here walks the whole tree.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NativeHtmlRenderer(
    document: PreparedReaderDocument,
    modifier: Modifier = Modifier,
    highlights: List<Highlight> = emptyList(),
    textColor: Color? = null,
    backgroundColor: Color? = null,
    fontSize: Int = 16,
    fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    typography: ReaderTypography = ReaderTypography(),
    onLinkClick: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onHighlightPosition: (String, HighlightPosition) -> Unit = { _, _ -> },
    scrollToHighlightId: String? = null,
    selectedHighlightId: String? = null,
    onLoaded: (() -> Unit)? = null,
    searchQuery: String = "",
    activeSearchMatchIndex: Int = 0,
    onSearchMatchesFound: (List<SearchMatch>) -> Unit = {},
    onSearchMatchPosition: (Float) -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    val resolvedTextColor = textColor
        ?: if (surfaceColor.luminance() > 0.5f) Color.Black else Color.White

    val resolvedBackgroundColor = backgroundColor ?: surfaceColor

    val resolvedFont = fontFamily.rememberFontFamily()

    val monochromeHighlight = if (LocalEinkMode.current.highContrast) {
        MonochromeHighlight(
            fill = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer
        )
    } else {
        null
    }

    val theme = remember(
        resolvedTextColor, resolvedBackgroundColor, fontSize, resolvedFont, primaryColor,
        typography.lineHeightScale, monochromeHighlight
    ) {
        ReaderThemeData(
            textColor = resolvedTextColor,
            backgroundColor = resolvedBackgroundColor,
            fontSize = fontSize.sp,
            fontFamily = resolvedFont,
            linkColor = primaryColor,
            lineHeightScale = typography.lineHeightScale,
            monochromeHighlight = monochromeHighlight,
            codeBackgroundColor = if (resolvedBackgroundColor.luminance() > 0.5f) {
                Color(0x1A7F7F7F) // rgba(127,127,127,0.1) on light
            } else {
                Color(0x337F7F7F) // slightly more visible on dark
            }
        )
    }

    // Every offset the reader draws with, and the one a new selection is resolved to, come from here.
    val offsets = document.offsets

    // Offsets outlive the document they were taken in — another client, an older
    // build, a re-crawl — so each highlight is checked against the text it was
    // created from before it is drawn.
    val resolvedHighlights = remember(offsets, highlights) {
        resolveHighlights(highlights, offsets)
    }

    // Pre-scanned once per document so the full-screen viewer can swipe between all of a
    // page's images, not just the one that was tapped — see LocalGalleryImages. The hero
    // banner is rendered outside this composable but belongs to the same gallery, first.
    val galleryViewerState = LocalGalleryViewerState.current
    val heroImage = galleryViewerState?.heroImage
    val galleryImages = remember(document, heroImage) {
        listOfNotNull(heroImage) + document.galleryImages
    }
    // So the hero banner, which cannot read the composition local below, opens the same list.
    SideEffect { galleryViewerState?.images = galleryImages }

    // The scroll cursor's table of contents, walked with the rest of the document. Each section's
    // position is registered separately, by the heading itself — see ReaderTocState.
    val tocState = LocalReaderToc.current
    SideEffect { tocState?.entries = document.toc }

    // Compute search matches whenever query or document changes
    val searchMatches = remember(offsets, searchQuery) {
        if (searchQuery.length < 2) emptyList()
        else findSearchMatches(offsets, searchQuery)
    }

    LaunchedEffect(searchMatches) {
        onSearchMatchesFound(searchMatches)
    }

    val searchState = if (searchMatches.isNotEmpty()) Pair(searchMatches, activeSearchMatchIndex) else null
    val searchScrollCallback: ((Float) -> Unit)? = if (searchState != null) onSearchMatchPosition else null

    // Track whether we've reported the highlight position (only report once)
    var highlightPositionReported by remember(scrollToHighlightId) { mutableStateOf(false) }

    // Find the target highlight for scroll-to
    val targetHighlight = remember(scrollToHighlightId, resolvedHighlights) {
        if (scrollToHighlightId != null) resolvedHighlights.find { it.id == scrollToHighlightId } else null
    }

    // Shared highlight action used by both TextToolbar and ContextMenuDataProvider
    val highlightAction: (String) -> Unit = { selectedText ->
        val match = findTextOffsets(offsets, selectedText)
        if (match != null) {
            onCreateHighlight(match.matchedText, match.startOffset, match.endOffset, null, null)
        }
    }

    // Custom text toolbar with "Highlight" action (for drag-selection on desktop, ActionMode on Android)
    val highlightToolbar = rememberHighlightTextToolbar(onHighlightRequested = highlightAction)

    val revealMeter = remember(document) { RevealCostMeter() }

    ReaderThemeProvider(theme = theme) {
        val textToolbar = highlightToolbar ?: LocalTextToolbar.current
        CompositionLocalProvider(
            LocalTextToolbar provides textToolbar,
            LocalSearchState provides searchState,
            LocalSearchMatchScrollCallback provides searchScrollCallback,
            LocalGalleryImages provides galleryImages
        ) {
            Box(modifier = Modifier.then(BlockBringIntoViewElement)) {
            HighlightContextMenuProvider(onHighlightRequested = highlightAction) {
            SelectionContainer {
                // Built a frame's worth at a time so no single frame composes and measures the
                // whole article. The parent keeps the article invisible and is told it has loaded
                // only once every block is in (below): scroll restoration, page snapping and
                // progress tracking all read the article's full height, never a partial one.
                val renderableChildren = document.renderableChildren
                var visibleCount by remember(document) {
                    mutableStateOf(minOf(INITIAL_REVEAL_BLOCKS, renderableChildren.size))
                }
                val fullyRevealed = visibleCount >= renderableChildren.size
                val culler = remember(document) { ReaderDrawCuller(renderableChildren.size) }

                Column(
                    modifier = modifier
                        // The article's own layer: anything else in its list item that redraws —
                        // a skeleton leaving, a sibling changing size — then reuses the article's
                        // recorded drawing instead of re-recording all of its text.
                        .graphicsLayer()
                        // Inside the layer, which is re-recorded on every batch otherwise: nothing
                        // shows the article before it is complete, so it is drawn once, then.
                        .drawnOnlyWhen(fullyRevealed)
                        // One callback for the whole article, run on every scroll frame: it only
                        // moves the window, and re-draws just the blocks that enter or leave it.
                        .onGloballyPositioned(culler::onArticlePositioned)
                        // Measured for the reveal's frame budget.
                        .layout { measurable, constraints ->
                            val mark = TimeSource.Monotonic.markNow()
                            val placeable = measurable.measure(constraints)
                            revealMeter.record(mark.elapsedNow())
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        .drawWithContent {
                            val mark = TimeSource.Monotonic.markNow()
                            drawContent()
                            val ms = mark.elapsedNow().inWholeMilliseconds
                            if (ms >= 2) ReaderPerf.log("article draw: $ms ms")
                        }
                        .fillMaxWidth()
                        .padding(horizontal = typography.horizontalMarginDp.dp, vertical = 0.dp)
                        .padding(bottom = 28.dp)
                ) {
                // Each block carries the offset its text starts at, so a block's position
                // never depends on how much of the document has been revealed so far.
                // Timed, with the measure above, for the reveal's frame budget.
                val composeMark = TimeSource.Monotonic.markNow()

                if (!fullyRevealed) {
                    LaunchedEffect(document) {
                        var lastBatch = visibleCount
                        while (visibleCount < renderableChildren.size) {
                            withFrameNanos { }
                            val cost = revealMeter.take()
                            // Nothing measured since the last batch: it has not been laid out yet.
                            if (cost == Duration.ZERO) continue
                            lastBatch = nextRevealBatchSize(lastBatch, cost)
                            visibleCount = minOf(visibleCount + lastBatch, renderableChildren.size)
                        }
                    }
                } else {
                    LaunchedEffect(document) { onLoaded?.invoke() }
                }

                SideEffect { revealMeter.record(composeMark.elapsedNow()) }

                // Each block in a layer of its own, drawn only near the viewport: see ReaderDrawCuller.
                for ((index, span) in renderableChildren.take(visibleCount).withIndex()) {
                    Column(Modifier.graphicsLayer().culledBy(culler, index)) {
                        val child = span.node
                        val inlineNodes = span.inlineNodes
                        if (inlineNodes != null) {
                            RenderInlineGroup(
                                inlineNodes, LocalReaderTheme.current, resolvedHighlights, offsets,
                                onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId
                            )
                        } else if (child is com.fleeksoft.ksoup.nodes.Element && isBlockElement(child)) {
                            RenderBlock(child, resolvedHighlights, offsets, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId = selectedHighlightId)
                        } else if (child is com.fleeksoft.ksoup.nodes.TextNode) {
                            val text = child.getWholeText()
                            val currentTheme = LocalReaderTheme.current
                            val runs = TextRuns().apply { record(0, span.startOffset, text.length) }
                            AnnotatedClickableText(
                                text = applyHighlightSpans(
                                    AnnotatedString(text), runs, resolvedHighlights, currentTheme, searchState
                                ),
                                onLinkClick = onLinkClick,
                                onHighlightClick = onHighlightClick,
                                onHighlightPosition = onHighlightPosition,
                                color = currentTheme.textColor,
                                fontSize = currentTheme.fontSize,
                                fontFamily = currentTheme.fontFamily,
                                lineHeight = currentTheme.bodyLineHeight,
                                selectedHighlightId = selectedHighlightId,
                                highlights = resolvedHighlights
                            )
                        }
                    }
                }


                // Marks where the article itself stops, above the bottom margin below. Page turns
                // need that boundary: the lazy item holding this Column runs some 50dp further —
                // this Spacer plus the Column's own bottom padding plus the last block's — and a
                // turn measured against the item keeps going long after the last word is read.
                // One node rather than one per block, and it follows the spacing below if it ever
                // changes, which a hardcoded margin would not.
                LocalReaderSnapRegistry.current?.let { registry ->
                    Spacer(
                        Modifier.height(0.dp).onPlaced { coords ->
                            registry.reportContentEnd {
                                if (coords.isAttached) coords.positionInRoot().y else Float.NaN
                            }
                        }
                    )
                }

                // Bottom spacing
                Spacer(Modifier.height(16.dp))
            }
            } // SelectionContainer
            } // HighlightContextMenuProvider
            } // Box (bringIntoView blocker)
        }
    }
}

private const val INITIAL_REVEAL_BLOCKS = 20

/** Compose and measure time spent on the article since the reveal last looked. Not snapshot state: nothing redraws on it. */
private class RevealCostMeter {
    private var spent = Duration.ZERO

    fun record(duration: Duration) {
        spent += duration
    }

    fun take(): Duration = spent.also { spent = Duration.ZERO }
}

/**
 * Swallows `bringIntoView` requests so they never reach the article's parent LazyColumn.
 *
 * SelectionContainer raises the request at its OWN layout level rather than from inside the
 * Column, so the node has to sit ABOVE SelectionContainer to see it. Handling it here and doing
 * nothing ends the chain — scroll-to-highlight moves the reader with an explicit
 * `scrollState.animateScrollToItem()` instead.
 */
private object BlockBringIntoViewElement : ModifierNodeElement<BlockBringIntoViewNode>() {
    override fun create() = BlockBringIntoViewNode()
    override fun update(node: BlockBringIntoViewNode) = Unit
    override fun hashCode() = "BlockBringIntoView".hashCode()
    override fun equals(other: Any?) = other === this
}

private class BlockBringIntoViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> ComposeRect?
    ) = Unit
}
