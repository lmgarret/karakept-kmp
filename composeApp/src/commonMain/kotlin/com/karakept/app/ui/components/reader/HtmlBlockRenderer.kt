package com.karakept.app.ui.components.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import coil3.compose.AsyncImage
import com.karakept.app.ui.components.HighlightPosition
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.karakept.app.data.model.Highlight

/**
 * Set of HTML tags that are treated as block-level elements.
 */
val BLOCK_TAGS = setOf(
    "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
    "blockquote", "pre", "ul", "ol", "li",
    "figure", "figcaption", "table", "thead", "tbody", "tr", "td", "th",
    "hr", "br", "img", "picture", "section", "article", "header", "footer",
    "nav", "aside", "main", "address", "dl", "dt", "dd"
)

/**
 * Checks if an element should be rendered as a block-level composable.
 *
 * `<a>` is transparent in HTML5: when it wraps block content (e.g. a `<picture>` used
 * as a lightbox link) it should be treated as a block so that RenderChildren dispatches
 * its children through RenderBlock rather than swallowing them as empty inline text.
 */
fun isBlockElement(element: Element): Boolean {
    val tag = element.tagName().lowercase()
    if (tag in BLOCK_TAGS) return true
    if (tag == "a" && hasBlockChildren(element)) return true
    return false
}

/**
 * Checks if an element contains any block-level children.
 */
private fun hasBlockChildren(element: Element): Boolean {
    return element.children().any { isBlockElement(it) }
}

private class YRef { var y: Float? = null }

@Composable
private fun rememberSearchScrollModifier(
    blockStart: Int,
    blockEnd: Int,
    searchState: Pair<List<SearchMatch>, Int>?,
    callback: ((Float) -> Unit)?
): Modifier {
    val isActive = searchState != null && callback != null &&
        searchState.first.getOrNull(searchState.second)
            ?.startOffset?.let { it in blockStart until blockEnd } == true

    val ref = remember { YRef() }
    when {
        searchState == null -> SideEffect { ref.y = null }
        isActive -> SideEffect { ref.y?.let { callback!!(it) } }
    }

    return if (searchState != null) {
        Modifier.onGloballyPositioned { coords ->
            val wasNull = ref.y == null
            ref.y = coords.positionInRoot().y
            if (isActive && wasNull) callback!!(ref.y!!)
        }
    } else {
        Modifier
    }
}

/**
 * Renders a block-level HTML element as a Compose composable.
 *
 * @param element          The block element to render.
 * @param highlights       All highlights for the bookmark.
 * @param textOffset       Running text offset counter for highlight mapping.
 * @param onLinkClick      Callback when a link is tapped.
 * @param onHighlightClick Callback when a highlight is tapped.
 * @param depth            Nesting depth for indentation.
 * @param selectedHighlightId The ID of the currently selected highlight, if any.
 */
@Composable
fun RenderBlock(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int = 0,
    selectedHighlightId: String? = null
) {
    val theme = LocalReaderTheme.current
    val tag = element.tagName().lowercase()

    val selectedHighlight = remember(selectedHighlightId, highlights) {
        highlights.find { it.id == selectedHighlightId }
    }

    val blockStart = textOffset.offset
    // element.text().length is a good estimate for document order text walking
    val blockEnd = blockStart + element.text().length
    
    val isSelectedBlock = selectedHighlight != null && 
        selectedHighlight.startOffset < blockEnd && 
        selectedHighlight.endOffset > blockStart

    Box {
        when (tag) {
            "p" -> RenderParagraph(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId)
            "div", "section", "article", "header", "footer", "nav", "aside", "main", "address" ->
                RenderDiv(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
            "h1" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, 1, selectedHighlightId)
            "h2" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, 2, selectedHighlightId)
            "h3" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, 3, selectedHighlightId)
            "h4" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, 4, selectedHighlightId)
            "h5" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, 5, selectedHighlightId)
            "h6" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, 6, selectedHighlightId)
            "blockquote" -> RenderBlockquote(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "pre" -> RenderCodeBlock(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId)
            "ul" -> RenderUnorderedList(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "ol" -> RenderOrderedList(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "li" -> RenderListItem(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, bullet = "\u2022", selectedHighlightId = selectedHighlightId)
            "figure" -> RenderFigure(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "figcaption" -> RenderFigcaption(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId)
            "img" -> RenderImage(element)
            "picture" -> RenderPicture(element)
            "hr" -> {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }
            "table" -> RenderTable(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "thead", "tbody" -> RenderTableSection(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "tr" -> RenderTableRow(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "td", "th" -> RenderTableCell(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, isHeader = tag == "th", selectedHighlightId = selectedHighlightId)
            "dl" -> RenderDefinitionList(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
            "dt" -> {
                val dtSearchState = LocalSearchState.current
                val dtSearchCallback = LocalSearchMatchScrollCallback.current
                val dtBlockStart = textOffset.offset
                val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, dtSearchState)
                val dtBlockEnd = textOffset.offset
                val dtScrollMod = rememberSearchScrollModifier(dtBlockStart, dtBlockEnd, dtSearchState, dtSearchCallback)
                AnnotatedClickableText(
                    text = text,
                    onLinkClick = onLinkClick,
                    onHighlightClick = onHighlightClick,
                    onHighlightPosition = onHighlightPosition,
                    color = theme.textColor,
                    fontSize = theme.fontSize,
                    fontFamily = theme.fontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp).then(dtScrollMod),
                    selectedHighlightId = selectedHighlightId,
                    highlights = highlights
                )
            }
            "dd" -> {
                RenderDiv(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, modifier = Modifier.padding(start = 24.dp), selectedHighlightId = selectedHighlightId)
            }
            else -> {
                // Unknown block element — render children
                RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
            }
        }
    }
}

/**
 * Renders all children of an element, dispatching to block or inline rendering as appropriate.
 */
@Composable
fun RenderChildren(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int = 0,
    selectedHighlightId: String? = null
) {
    val theme = LocalReaderTheme.current
    // Group consecutive inline nodes together, render block elements individually
    val children = element.childNodes()
    var i = 0
    while (i < children.size) {
        val child = children[i]
        if (child is Element && isBlockElement(child)) {
            // Add a virtual newline offset before block elements (match Compose selection joining)
            if (textOffset.offset > 0) {
                textOffset.advance(1)
            }
            RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
            i++
        } else {
            // Collect consecutive inline nodes
            val inlineNodes = mutableListOf<Node>()
            while (i < children.size) {
                val node = children[i]
                if (node is Element && isBlockElement(node)) break
                // Skip whitespace-only text nodes between blocks
                if (node is TextNode && node.getWholeText().isBlank() && i > 0) {
                    // Still count the text for offset tracking
                    textOffset.advance(node.getWholeText().length)
                    i++
                    continue
                }
                inlineNodes.add(node)
                i++
            }
            if (inlineNodes.isNotEmpty() && inlineNodes.any {
                    (it is TextNode && it.getWholeText().isNotBlank()) || it is Element
                }) {
                // Create a virtual wrapper element to render inline content together
                // We'll use buildInlineAnnotatedString directly on the parent but
                // limit to just these nodes
                RenderInlineGroup(inlineNodes, element, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId)
            }
        }
    }
}

/**
 * Renders a group of consecutive inline nodes as a single Text composable.
 */
@Composable
private fun RenderInlineGroup(
    nodes: List<Node>,
    parent: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    selectedHighlightId: String? = null
) {
    // Create a temporary element containing just these nodes for the inline renderer
    // Since we can't easily subset, we build the annotated string manually
    val blockStartOffset = textOffset.offset
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()

    var groupTextLength = 0
    for (node in nodes) {
        when (node) {
            is TextNode -> {
                val text = node.getWholeText()
                builder.append(text)
                textOffset.advance(text.length)
                groupTextLength += text.length
            }
            is Element -> {
                val start = builder.length
                appendInlineElement(builder, node, theme, textOffset, onLinkClick)
                groupTextLength += (builder.length - start)
            }
        }
    }

    val blockEndOffset = textOffset.offset
    var result = builder.toAnnotatedString()

    // Apply highlights
    val overlapping = highlights.filter { h ->
        h.startOffset < blockEndOffset && h.endOffset > blockStartOffset
    }

    val inlineSearchState = LocalSearchState.current
    val inlineScrollCallback = LocalSearchMatchScrollCallback.current
    // Must be called unconditionally (contains remember) — always compute before any conditional return
    val inlineScrollMod = rememberSearchScrollModifier(blockStartOffset, blockEndOffset, inlineSearchState, inlineScrollCallback)

    if (overlapping.isNotEmpty() || inlineSearchState != null) {
        result = buildAnnotatedString {
            append(result)
            for (highlight in overlapping) {
                val localStart = (highlight.startOffset - blockStartOffset).coerceIn(0, result.length)
                val localEnd = (highlight.endOffset - blockStartOffset).coerceIn(0, result.length)
                if (localStart >= localEnd) continue
                val bgColor = theme.highlightColors[highlight.color ?: "yellow"] ?: theme.highlightColors["yellow"] ?: Color.Yellow
                addStyle(
                    SpanStyle(
                        background = bgColor,
                        color = ReaderThemeData.highlightTextColor
                    ),
                    localStart,
                    localEnd
                )
                addStringAnnotation(HIGHLIGHT_ANNOTATION_TAG, highlight.id, localStart, localEnd)
            }
            if (inlineSearchState != null) {
                val (searchMatches, activeIndex) = inlineSearchState
                for ((matchIndex, match) in searchMatches.withIndex()) {
                    if (match.startOffset >= blockEndOffset || match.endOffset <= blockStartOffset) continue
                    val localStart = (match.startOffset - blockStartOffset).coerceIn(0, result.length)
                    val localEnd = (match.endOffset - blockStartOffset).coerceIn(0, result.length)
                    if (localStart >= localEnd) continue
                    val bg = if (matchIndex == activeIndex) Color(0xCCFF9800) else Color(0x66FFC107)
                    addStyle(SpanStyle(background = bg, color = Color.Black), localStart, localEnd)
                }
            }
        }
    }

    if (result.isNotEmpty()) {
        AnnotatedClickableText(
            text = result,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            onHighlightPosition = onHighlightPosition,
            color = theme.textColor,
            fontSize = theme.fontSize,
            fontFamily = theme.fontFamily,
            lineHeight = (theme.fontSize.value * 1.6f).sp,
            modifier = inlineScrollMod,
            selectedHighlightId = selectedHighlightId,
            highlights = highlights
        )
    }
}

/**
 * Helper to append an inline element to a builder (used by RenderInlineGroup).
 * Mirrors the logic in HtmlInlineRenderer but works with raw builder.
 */
private fun appendInlineElement(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    element: Element,
    theme: ReaderThemeData,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit
) {
    val tag = element.tagName().lowercase()
    val start = builder.length
    // Recursively append children
    for (child in element.childNodes()) {
        when (child) {
            is TextNode -> {
                builder.append(child.getWholeText())
                textOffset.advance(child.getWholeText().length)
            }
            is Element -> appendInlineElement(builder, child, theme, textOffset, onLinkClick)
        }
    }
    val end = builder.length
    when (tag) {
        "b", "strong" -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
        "i", "em", "cite", "dfn" -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
        "u" -> builder.addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
        "del", "s", "strike" -> builder.addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), start, end)
        "code" -> builder.addStyle(
            SpanStyle(fontFamily = FontFamily.Monospace, background = theme.codeBackgroundColor, fontSize = (theme.fontSize.value * 0.875f).sp),
            start, end
        )
        "a" -> {
            val href = element.attr("href")
            if (href.isNotBlank() && start < end) {
                builder.addStyle(SpanStyle(color = theme.linkColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
                builder.addStringAnnotation(
                    tag = LINK_ANNOTATION_TAG,
                    annotation = href,
                    start = start,
                    end = end
                )
            }
        }
        "mark" -> {
            if (element.attr("data-id").isBlank()) {
                builder.addStyle(SpanStyle(background = androidx.compose.ui.graphics.Color(0xFFFFEB3B).copy(alpha = 0.4f)), start, end)
            }
        }
        "sup" -> builder.addStyle(SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript, fontSize = (theme.fontSize.value * 0.75f).sp), start, end)
        "sub" -> builder.addStyle(SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Subscript, fontSize = (theme.fontSize.value * 0.75f).sp), start, end)
    }
}

// --- Block renderers ---

@Composable
private fun RenderParagraph(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    if (hasBlockChildren(element)) {
        // <p> with block children (malformed HTML) — render as div
        Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
            RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, selectedHighlightId = selectedHighlightId)
        }
    } else {
        val blockStart = textOffset.offset
        val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
        val blockEnd = textOffset.offset
        val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
        if (text.isNotEmpty()) {
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                onHighlightPosition = onHighlightPosition,
                color = theme.textColor,
                fontSize = theme.fontSize,
                fontFamily = theme.fontFamily,
                lineHeight = (theme.fontSize.value * 1.6f).sp,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().then(scrollMod),
                selectedHighlightId = selectedHighlightId,
                highlights = highlights
            )
        }
    }
}

@Composable
private fun RenderDiv(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    modifier: Modifier = Modifier,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    if (hasBlockChildren(element)) {
        Column(modifier = modifier.fillMaxWidth()) {
            RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
        }
    } else {
        val blockStart = textOffset.offset
        val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
        val blockEnd = textOffset.offset
        val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
        if (text.isNotEmpty()) {
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                onHighlightPosition = onHighlightPosition,
                color = theme.textColor,
                fontSize = theme.fontSize,
                fontFamily = theme.fontFamily,
                lineHeight = (theme.fontSize.value * 1.6f).sp,
                modifier = modifier.then(scrollMod),
                selectedHighlightId = selectedHighlightId,
                highlights = highlights
            )
        }
    }
}

@Composable
private fun RenderHeading(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    level: Int,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    val scaleFactor = when (level) {
        1 -> 2.0f
        2 -> 1.5f
        3 -> 1.25f
        4 -> 1.1f
        5 -> 1.0f
        else -> 0.9f
    }
    val blockStart = textOffset.offset
    val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
    val blockEnd = textOffset.offset
    val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
    if (text.isNotEmpty()) {
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            onHighlightPosition = onHighlightPosition,
            color = theme.textColor,
            fontSize = (theme.fontSize.value * scaleFactor).sp,
            fontFamily = theme.fontFamily,
            fontWeight = FontWeight.Bold,
            lineHeight = (theme.fontSize.value * scaleFactor * 1.4f).sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).fillMaxWidth().then(scrollMod),
            selectedHighlightId = selectedHighlightId,
            highlights = highlights
        )
    }
}

@Composable
private fun RenderBlockquote(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Left border
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(theme.linkColor)
        )
        // Content
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            if (hasBlockChildren(element)) {
                RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth + 1, selectedHighlightId = selectedHighlightId)
            } else {
                val blockStart = textOffset.offset
                val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
                val blockEnd = textOffset.offset
                val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
                if (text.isNotEmpty()) {
                    AnnotatedClickableText(
                        text = text,
                        onLinkClick = onLinkClick,
                        onHighlightClick = onHighlightClick,
                        onHighlightPosition = onHighlightPosition,
                        color = theme.textColor,
                        fontSize = theme.fontSize,
                        fontFamily = theme.fontFamily,
                        fontStyle = FontStyle.Italic,
                        lineHeight = (theme.fontSize.value * 1.6f).sp,
                        modifier = scrollMod,
                        selectedHighlightId = selectedHighlightId,
                        highlights = highlights
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderCodeBlock(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    // Pre/code blocks: find the <code> child if it exists
    val codeElement = element.selectFirst("code") ?: element
    val blockStart = textOffset.offset
    val text = buildInlineAnnotatedString(codeElement, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
    val blockEnd = textOffset.offset
    val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
    // If the <pre> has a <code> child we already consumed its text.
    // If the <pre> has other children outside <code>, consume them too.
    if (codeElement != element) {
        // Walk the remaining children of <pre> that aren't the <code>
        for (child in element.childNodes()) {
            if (child is Element && child == codeElement) continue
            if (child is TextNode) {
                textOffset.advance(child.getWholeText().length)
            }
        }
    }

    HorizontallyScrollableContainer(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        scrollbarColor = theme.textColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(theme.codeBackgroundColor)
                .padding(8.dp)
        ) {
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                onHighlightPosition = onHighlightPosition,
                color = theme.textColor,
                fontSize = (theme.fontSize.value * 0.875f).sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (theme.fontSize.value * 0.875f * 1.4f).sp,
                modifier = scrollMod,
                selectedHighlightId = selectedHighlightId,
                highlights = highlights
            )
        }
    }
}

@Composable
private fun RenderUnorderedList(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp).fillMaxWidth()) {
        for (child in element.children()) {
            if (child.tagName().lowercase() == "li") {
                RenderListItem(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, bullet = "\u2022", selectedHighlightId = selectedHighlightId)
            } else {
                RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
            }
        }
    }
}

@Composable
private fun RenderOrderedList(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp).fillMaxWidth()) {
        var index = 1
        for (child in element.children()) {
            if (child.tagName().lowercase() == "li") {
                RenderListItem(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, bullet = "${index}.", selectedHighlightId = selectedHighlightId)
                index++
            } else {
                RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
            }
        }
    }
}

@Composable
private fun RenderListItem(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    bullet: String,
    selectedHighlightId: String? = null
) {
    val theme = LocalReaderTheme.current
    Row(
        modifier = Modifier
            .padding(start = 24.dp, top = 2.dp, bottom = 2.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "$bullet ",
            color = theme.textColor,
            fontSize = theme.fontSize,
            fontFamily = theme.fontFamily,
            modifier = Modifier.width(24.dp)
        )
        val searchState = LocalSearchState.current
        val searchCallback = LocalSearchMatchScrollCallback.current
        Column(modifier = Modifier.weight(1f)) {
            if (hasBlockChildren(element)) {
                RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth + 1, selectedHighlightId = selectedHighlightId)
            } else {
                val blockStart = textOffset.offset
                val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
                val blockEnd = textOffset.offset
                val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
                AnnotatedClickableText(
                    text = text,
                    onLinkClick = onLinkClick,
                    onHighlightClick = onHighlightClick,
                    onHighlightPosition = onHighlightPosition,
                    color = theme.textColor,
                    fontSize = theme.fontSize,
                    fontFamily = theme.fontFamily,
                    lineHeight = (theme.fontSize.value * 1.6f).sp,
                    modifier = scrollMod,
                    selectedHighlightId = selectedHighlightId,
                    highlights = highlights
                )
            }
        }
    }
}

@Composable
private fun RenderFigure(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    Column(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    ) {
        // Use RenderChildren to handle all nodes (elements + text) without double-counting
        RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
    }
}

@Composable
private fun RenderFigcaption(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    val blockStart = textOffset.offset
    val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
    val blockEnd = textOffset.offset
    val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
    if (text.isNotEmpty()) {
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            onHighlightPosition = onHighlightPosition,
            color = theme.textColor.copy(alpha = 0.8f),
            fontSize = (theme.fontSize.value * 0.875f).sp,
            fontFamily = theme.fontFamily,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth().then(scrollMod),
            selectedHighlightId = selectedHighlightId,
            highlights = highlights
        )
    }
}

/**
 * Intrinsic dimensions declared on an `<img>` element via its `width`/`height` attributes.
 */
internal data class ImageDimensions(val width: Int, val height: Int) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

/**
 * Reads the `width`/`height` attributes off an `<img>` element. Returns null if either
 * is missing, non-numeric (e.g. "100%", "auto"), or zero — in which case the renderer
 * falls back to filling the available width.
 */
internal fun extractImageDimensions(element: Element): ImageDimensions? {
    val width = element.attr("width").toIntOrNull() ?: return null
    val height = element.attr("height").toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    return ImageDimensions(width, height)
}

/**
 * Finds the caption for an image, if it sits inside a `<figure>` with a `<figcaption>`.
 * Walks up from [element] to the nearest `<figure>` ancestor rather than only checking the
 * immediate parent, since the image may be wrapped in an `<a>` lightbox link (see
 * [isBlockElement]'s handling of anchor-wrapped pictures).
 */
internal fun findFigureCaption(element: Element): String? {
    var ancestor: Element? = element.parent()
    while (ancestor != null && ancestor.tagName().lowercase() != "figure") {
        ancestor = ancestor.parent()
    }
    return ancestor?.selectFirst("figcaption")?.text()?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * Returns all usable URLs from a `srcset` string in their original order.
 * Skips SVG placeholder data URIs; passes non-SVG base64 data URIs through to Coil.
 *
 * Splits on commas that introduce a new URL (lookahead for a protocol prefix) so
 * that commas inside URL query parameters — common in CDN URLs like
 * `?resize=928,522` — are preserved instead of fragmenting the URL.
 * Base64 is comma-free so `data:image/jpeg;base64,...` is never split mid-value.
 */
internal fun pickUrlsFromSrcset(srcset: String): List<String> {
    if (srcset.isBlank()) return emptyList()
    return srcset.split(Regex(",\\s*(?=(?:https?://|file://|data:))"))
        .mapNotNull { entry ->
            entry.trim().split(Regex("\\s+")).firstOrNull()?.trim()
                ?.takeIf { url ->
                    url.isNotBlank() &&
                    !url.startsWith("data:image/svg") &&
                    (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://") || url.startsWith("data:"))
                }
        }
}

/**
 * Resolves all candidate URLs from an `<img>` element in priority order:
 * src → data-src → srcset/data-srcset entries.
 * Callers try each URL in sequence and fall back on network error.
 */
internal fun resolveImageUrls(element: Element): List<String> {
    val candidates = mutableListOf<String>()

    val src = element.attr("src")
    if (src.isNotBlank() && !src.startsWith("data:image/svg")) candidates += src

    val dataSrc = element.attr("data-src")
    if (dataSrc.isNotBlank() && !dataSrc.startsWith("data:")) candidates += dataSrc

    val srcset = element.attr("srcset").ifBlank { element.attr("data-srcset") }
    candidates += pickUrlsFromSrcset(srcset)

    return candidates.distinct()
}

/**
 * Renders an image trying each URL in [urls] in order, falling back to the next on
 * Coil error. Shows a pulsing skeleton while loading and a broken-image placeholder
 * when all URLs fail. Capping width to declared dimensions prevents upscaling small icons.
 */
@Composable
private fun RenderResolvedImage(
    urls: List<String>,
    alt: String,
    dimensions: ImageDimensions?,
    caption: String? = null
) {
    var idx by remember(urls) { mutableIntStateOf(0) }
    // Resets to true on every new URL attempt (idx change) and on new image (urls change).
    var isLoading by remember(urls, idx) { mutableStateOf(true) }
    var showFullscreen by remember(urls) { mutableStateOf(false) }

    val sizeModifier = if (dimensions != null) {
        Modifier
            .widthIn(max = dimensions.width.dp)
            .fillMaxWidth()
            .aspectRatio(dimensions.aspectRatio)
    } else {
        Modifier.fillMaxWidth()
    }
    val shape = RoundedCornerShape(4.dp)
    val baseModifier = sizeModifier.padding(vertical = 8.dp)

    if (idx >= urls.size) {
        // All candidate URLs failed — show broken-image placeholder.
        Box(
            modifier = baseModifier
                .then(if (dimensions == null) Modifier.height(100.dp) else Modifier)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        return
    }

    Box(
        modifier = baseModifier.clickable(
            enabled = !isLoading,
            onClickLabel = "View image full-screen"
        ) { showFullscreen = true }
    ) {
        // Skeleton shown while the current URL is loading.
        if (isLoading) {
            ImageLoadingSkeleton(
                modifier = if (dimensions != null) {
                    Modifier.matchParentSize()
                } else {
                    Modifier.fillMaxWidth().height(200.dp)
                }.clip(shape)
            )
        }
        AsyncImage(
            model = urls[idx],
            contentDescription = alt.ifBlank { null },
            contentScale = if (dimensions != null) ContentScale.Fit else ContentScale.FillWidth,
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = { idx++ },
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .alpha(if (isLoading) 0f else 1f)
        )
    }

    if (showFullscreen) {
        ZoomableImageDialog(
            url = urls[idx],
            alt = alt,
            caption = caption,
            onDismiss = { showFullscreen = false }
        )
    }
}

@Composable
private fun ImageLoadingSkeleton(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "imgSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "imgSkeletonAlpha"
    )
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
}

@Composable
private fun RenderImage(element: Element) {
    val urls = resolveImageUrls(element)
    if (urls.isEmpty()) return
    RenderResolvedImage(
        urls = urls,
        alt = element.attr("alt"),
        dimensions = extractImageDimensions(element),
        caption = findFigureCaption(element)
    )
}

@Composable
private fun RenderPicture(element: Element) {
    val img = element.selectFirst("img")
    // Collect all candidates: <source> srcsets first, then <img> src/data-src/srcset.
    val candidates = mutableListOf<String>()
    for (source in element.select("source")) {
        val srcset = source.attr("srcset").ifBlank { source.attr("data-srcset") }
        candidates += pickUrlsFromSrcset(srcset)
    }
    if (img != null) candidates += resolveImageUrls(img)
    val urls = candidates.distinct()
    if (urls.isEmpty()) return
    RenderResolvedImage(
        urls = urls,
        alt = img?.attr("alt").orEmpty(),
        dimensions = img?.let { extractImageDimensions(it) },
        caption = findFigureCaption(element)
    )
}

// --- Table support ---

/**
 * Collects all `<tr>` elements from a `<table>`, traversing `<thead>`, `<tbody>`, and `<tfoot>`.
 * Preserves DOM order for correct text-offset tracking.
 */
private fun collectTableRows(table: Element): List<Element> {
    val rows = mutableListOf<Element>()
    for (child in table.children()) {
        when (child.tagName().lowercase()) {
            "thead", "tbody", "tfoot" -> {
                for (grandChild in child.children()) {
                    if (grandChild.tagName().lowercase() == "tr") rows.add(grandChild)
                }
            }
            "tr" -> rows.add(child)
        }
    }
    return rows
}

private fun isHeaderRow(row: Element): Boolean {
    val parentTag = row.parent()?.tagName()?.lowercase()
    if (parentTag == "thead") return true
    val cells = row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
    return cells.isNotEmpty() && cells.all { it.tagName().lowercase() == "th" }
}

@Composable
private fun RenderTable(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    val allRows = remember(element) { collectTableRows(element) }
    val columnCount = remember(allRows) {
        allRows.maxOfOrNull { row ->
            row.children().count { it.tagName().lowercase() in setOf("td", "th") }
        } ?: 0
    }

    val captionSearchState = LocalSearchState.current
    // Render caption if present
    for (child in element.children()) {
        if (child.tagName().lowercase() == "caption") {
            val text = buildInlineAnnotatedString(child, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, captionSearchState)
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                onHighlightPosition = onHighlightPosition,
                color = theme.textColor,
                fontSize = theme.fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
                selectedHighlightId = selectedHighlightId,
                highlights = highlights
            )
        }
    }

    if (columnCount == 0) return

    // Estimate column widths based on the longest text content in each column
    val columnWidths = remember(allRows, columnCount, theme.fontSize) {
        val widths = IntArray(columnCount) { 80 }
        for (row in allRows) {
            val cells = row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
            cells.forEachIndexed { index, cell ->
                if (index < columnCount) {
                    val textLength = cell.text().length
                    val estimated = (textLength * theme.fontSize.value * 0.6f + 32).toInt().coerceIn(80, 400)
                    widths[index] = maxOf(widths[index], estimated)
                }
            }
        }
        widths.toList()
    }

    val borderColor = theme.textColor.copy(alpha = 0.2f)
    val headerBgColor = theme.textColor.copy(alpha = 0.08f)

    HorizontallyScrollableContainer(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        scrollbarColor = theme.textColor
    ) {
        Column(
            modifier = Modifier
                .border(0.5.dp, borderColor, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        ) {
            allRows.forEachIndexed { rowIndex, row ->
                val isHeader = isHeaderRow(row)
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .then(if (isHeader) Modifier.background(headerBgColor) else Modifier)
                ) {
                    val cells = row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
                    cells.forEachIndexed { cellIndex, cell ->
                        if (cellIndex > 0) {
                            VerticalDivider(color = borderColor, thickness = 0.5.dp)
                        }
                        val colWidth = columnWidths.getOrElse(cellIndex) { 80 }
                        Box(
                            modifier = Modifier
                                .width(colWidth.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            RenderTableCell(
                                cell, theme, highlights, textOffset,
                                onLinkClick, onHighlightClick, onHighlightPosition,
                                depth, isHeader = cell.tagName().lowercase() == "th" || isHeader,
                                selectedHighlightId = selectedHighlightId
                            )
                        }
                    }
                }
                if (rowIndex < allRows.lastIndex) {
                    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun RenderTableSection(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    for (child in element.children()) {
        if (child.tagName().lowercase() == "tr") {
            RenderTableRow(child, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
        }
    }
}

@Composable
private fun RenderTableRow(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        for (child in element.children()) {
            val tag = child.tagName().lowercase()
            if (tag == "td" || tag == "th") {
                Box(
                    modifier = Modifier
                        .widthIn(min = 80.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    RenderTableCell(child, theme, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, isHeader = tag == "th", selectedHighlightId = selectedHighlightId)
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun RenderTableCell(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    isHeader: Boolean,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    if (hasBlockChildren(element)) {
        Column {
            RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
        }
    } else {
        val blockStart = textOffset.offset
        val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
        val blockEnd = textOffset.offset
        val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            onHighlightPosition = onHighlightPosition,
            color = theme.textColor,
            fontSize = theme.fontSize,
            fontFamily = theme.fontFamily,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            lineHeight = (theme.fontSize.value * 1.4f).sp,
            modifier = scrollMod,
            selectedHighlightId = selectedHighlightId,
            highlights = highlights
        )
    }
}

@Composable
private fun RenderDefinitionList(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        for (child in element.children()) {
            RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
        }
    }
}
