package com.karakept.app.ui.components.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ReaderTypography
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.theme.rememberFontFamily
import com.karakept.app.ui.components.HighlightPosition

/**
 * Native Compose Multiplatform HTML renderer for READER mode.
 *
 * Replaces the WebView-based renderer with pure Compose composables.
 * Parses sanitized HTML via ksoup and emits Text, Image, and layout composables.
 *
 * Highlight offsets are compatible with the WebView's JavaScript TreeWalker
 * approach: both walk text nodes in document order and count characters.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NativeHtmlRenderer(
    html: String,
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
    parseDocument: ((String) -> Document?)? = null,
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

    // Parse HTML once and cache
    val document = remember(html) {
        parseDocument?.invoke(html) ?: try {
            Ksoup.parse(html)
        } catch (e: Exception) {
            null
        }
    }

    if (document == null) {
        onLoaded?.invoke()
        return
    }

    val body = document.body()
    val textOffset = remember(html) { TextOffsetTracker() }

    // Pre-scanned once per document so the full-screen viewer can swipe between all of a
    // page's images, not just the one that was tapped — see LocalGalleryImages. The hero
    // banner is rendered outside this composable but belongs to the same gallery, first.
    val galleryViewerState = LocalGalleryViewerState.current
    val heroImage = galleryViewerState?.heroImage
    val galleryImages = remember(document, heroImage) {
        listOfNotNull(heroImage) + collectGalleryImages(document)
    }
    // So the hero banner, which cannot read the composition local below, opens the same list.
    SideEffect { galleryViewerState?.images = galleryImages }

    // Compute search matches whenever query or document changes
    val searchMatches = remember(document, searchQuery) {
        if (searchQuery.length < 2) emptyList()
        else findSearchMatchesInDocument(document, searchQuery)
    }

    LaunchedEffect(searchMatches) {
        onSearchMatchesFound(searchMatches)
    }

    val searchState = if (searchMatches.isNotEmpty()) Pair(searchMatches, activeSearchMatchIndex) else null
    val searchScrollCallback: ((Float) -> Unit)? = if (searchState != null) onSearchMatchPosition else null

    // Track whether we've reported the highlight position (only report once)
    var highlightPositionReported by remember(scrollToHighlightId) { mutableStateOf(false) }

    // Find the target highlight for scroll-to
    val targetHighlight = remember(scrollToHighlightId, highlights) {
        if (scrollToHighlightId != null) highlights.find { it.id == scrollToHighlightId } else null
    }

    // Shared highlight action used by both TextToolbar and ContextMenuDataProvider
    val highlightAction: (String) -> Unit = { selectedText ->
        val offsets = findTextOffsets(html, selectedText)
        if (offsets != null) {
            onCreateHighlight(offsets.matchedText, offsets.startOffset, offsets.endOffset, null, null)
        }
    }

    // Custom text toolbar with "Highlight" action (for drag-selection on desktop, ActionMode on Android)
    val highlightToolbar = rememberHighlightTextToolbar(onHighlightRequested = highlightAction)

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
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = typography.horizontalMarginDp.dp, vertical = 0.dp)
                        .padding(bottom = 28.dp)
                ) {
                // Reset offset at start of rendering
                textOffset.offset = 0

                // Build list of renderable children once, each carrying the offset
                // its text starts at, so a block's position never depends on how
                // much of the document has been revealed so far.
                val renderableChildren = remember(html) {
                    computeReaderTextSpans(body).filter { it.isRenderable }
                }

                // Progressive rendering: show first 20 blocks immediately, reveal rest in batches
                val initialChunkSize = 20
                var visibleCount by remember(html) { mutableStateOf(minOf(initialChunkSize, renderableChildren.size)) }

                if (visibleCount < renderableChildren.size) {
                    LaunchedEffect(html, renderableChildren.size) {
                        while (visibleCount < renderableChildren.size) {
                            kotlinx.coroutines.delay(16) // ~1 frame at 60fps
                            visibleCount = minOf(visibleCount + 10, renderableChildren.size)
                        }
                    }
                }

                // Render visible children
                for (span in renderableChildren.take(visibleCount)) {
                    // Absolute, so partially revealed documents place highlights
                    // exactly where a fully revealed one does.
                    textOffset.offset = span.startOffset
                    val child = span.node
                    if (child is com.fleeksoft.ksoup.nodes.Element && isBlockElement(child)) {
                        RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId = selectedHighlightId)
                    } else if (child is com.fleeksoft.ksoup.nodes.TextNode) {
                        val text = child.getWholeText()
                        val currentTheme = LocalReaderTheme.current
                        textOffset.advance(text.length)
                        AnnotatedClickableText(
                            text = AnnotatedString(text),
                            onLinkClick = onLinkClick,
                            onHighlightClick = onHighlightClick,
                            onHighlightPosition = onHighlightPosition,
                            color = currentTheme.textColor,
                            fontSize = currentTheme.fontSize,
                            fontFamily = currentTheme.fontFamily,
                            lineHeight = currentTheme.bodyLineHeight,
                            selectedHighlightId = selectedHighlightId,
                            highlights = highlights
                        )
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
                        Modifier.height(0.dp).onGloballyPositioned {
                            registry.reportContentEnd(it.positionInRoot().y)
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

    // Notify parent that content is ready
    LaunchedEffect(document) {
        onLoaded?.invoke()
    }
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
