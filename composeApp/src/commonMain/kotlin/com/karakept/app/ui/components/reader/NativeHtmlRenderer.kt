package com.karakept.app.ui.components.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.ReaderFontFamily
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
    onLinkClick: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onHighlightPosition: (String, HighlightPosition) -> Unit = { _, _ -> },
    scrollToHighlightId: String? = null,
    selectedHighlightId: String? = null,
    onLoaded: (() -> Unit)? = null
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    val resolvedTextColor = textColor
        ?: if (surfaceColor.luminance() > 0.5f) Color.Black else Color.White

    val resolvedBackgroundColor = backgroundColor ?: surfaceColor

    val resolvedFont = fontFamily.rememberFontFamily()

    val theme = remember(resolvedTextColor, resolvedBackgroundColor, fontSize, resolvedFont, primaryColor) {
        ReaderThemeData(
            textColor = resolvedTextColor,
            backgroundColor = resolvedBackgroundColor,
            fontSize = fontSize.sp,
            fontFamily = resolvedFont,
            linkColor = primaryColor,
            codeBackgroundColor = if (resolvedBackgroundColor.luminance() > 0.5f) {
                Color(0x1A7F7F7F) // rgba(127,127,127,0.1) on light
            } else {
                Color(0x337F7F7F) // slightly more visible on dark
            }
        )
    }

    // Parse HTML once and cache
    val document = remember(html) {
        try {
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
            LocalTextToolbar provides textToolbar
        ) {
            // Block bringIntoView from propagating to the parent LazyColumn.
            // SelectionContainer initiates bringIntoView at its OWN layout level
            // (not from inside the Column), so the responder must be an ANCESTOR
            // of SelectionContainer to intercept the request.
            // Scroll-to-highlight uses explicit scrollState.animateScrollToItem()
            // so this is safe to block.
            Box(
                modifier = Modifier.bringIntoViewResponder(remember {
                    object : BringIntoViewResponder {
                        override fun calculateRectForParent(localRect: ComposeRect): ComposeRect = localRect
                        override suspend fun bringChildIntoView(localRect: () -> ComposeRect?) {
                            // Intentionally blocked — scroll-to-highlight uses
                            // explicit scrollState.animateScrollToItem() instead.
                        }
                    }
                })
            ) {
            HighlightContextMenuProvider(onHighlightRequested = highlightAction) {
            SelectionContainer {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 0.dp)
                        .padding(bottom = 28.dp)
                ) {
                // Reset offset at start of rendering
                textOffset.offset = 0

                // Build list of renderable children once
                val renderableChildren = remember(html) {
                    val result = mutableListOf<com.fleeksoft.ksoup.nodes.Node>()
                    for (child in body.childNodes()) {
                        if (child is com.fleeksoft.ksoup.nodes.Element && isBlockElement(child)) {
                            result.add(child)
                        } else if (child is com.fleeksoft.ksoup.nodes.TextNode && child.getWholeText().isNotBlank()) {
                            result.add(child)
                        }
                    }
                    result
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
                for (child in renderableChildren.take(visibleCount)) {
                    if (child is com.fleeksoft.ksoup.nodes.Element && isBlockElement(child)) {
                        RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId = selectedHighlightId)
                    } else if (child is com.fleeksoft.ksoup.nodes.TextNode) {
                        val text = child.getWholeText()
                        val currentTheme = LocalReaderTheme.current
                        val blockStart = textOffset.offset
                        textOffset.advance(text.length)
                        AnnotatedClickableText(
                            text = AnnotatedString(text),
                            onLinkClick = onLinkClick,
                            onHighlightClick = onHighlightClick,
                            onHighlightPosition = onHighlightPosition,
                            color = currentTheme.textColor,
                            fontSize = currentTheme.fontSize,
                            fontFamily = currentTheme.fontFamily,
                            lineHeight = (currentTheme.fontSize.value * 1.6f).sp,
                            selectedHighlightId = selectedHighlightId,
                            highlights = highlights
                        )
                    }
                }

                // Advance text offset for not-yet-visible nodes to keep highlight
                // offsets consistent once they become visible in subsequent frames
                for (child in renderableChildren.drop(visibleCount)) {
                    if (child is com.fleeksoft.ksoup.nodes.TextNode) {
                        textOffset.advance(child.getWholeText().length)
                    } else if (child is com.fleeksoft.ksoup.nodes.Element) {
                        textOffset.advance(child.text().length)
                    }
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
