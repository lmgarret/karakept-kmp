package com.karakept.app.ui.components.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
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
 */
fun isBlockElement(element: Element): Boolean {
    return element.tagName().lowercase() in BLOCK_TAGS
}

/**
 * Checks if an element contains any block-level children.
 */
private fun hasBlockChildren(element: Element): Boolean {
    return element.children().any { isBlockElement(it) }
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
 */
@Composable
fun RenderBlock(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    depth: Int = 0
) {
    val theme = LocalReaderTheme.current
    val tag = element.tagName().lowercase()

    when (tag) {
        "p" -> RenderParagraph(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
        "div", "section", "article", "header", "footer", "nav", "aside", "main", "address" ->
            RenderDiv(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "h1" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, 1)
        "h2" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, 2)
        "h3" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, 3)
        "h4" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, 4)
        "h5" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, 5)
        "h6" -> RenderHeading(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, 6)
        "blockquote" -> RenderBlockquote(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "pre" -> RenderCodeBlock(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
        "ul" -> RenderUnorderedList(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "ol" -> RenderOrderedList(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "li" -> RenderListItem(element, highlights, textOffset, onLinkClick, onHighlightClick, depth, bullet = "\u2022")
        "figure" -> RenderFigure(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "figcaption" -> RenderFigcaption(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
        "img" -> RenderImage(element)
        "picture" -> RenderPicture(element)
        "hr" -> {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }
        "table" -> RenderTable(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "thead", "tbody" -> RenderTableSection(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "tr" -> RenderTableRow(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "td", "th" -> RenderTableCell(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth, isHeader = tag == "th")
        "dl" -> RenderDefinitionList(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        "dt" -> {
            val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                color = theme.textColor,
                fontSize = theme.fontSize,
                fontFamily = theme.fontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        "dd" -> {
            RenderDiv(element, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth, modifier = Modifier.padding(start = 24.dp))
        }
        else -> {
            // Unknown block element — render children
            RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
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
    depth: Int = 0
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
            RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, depth)
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
                RenderInlineGroup(inlineNodes, element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
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
    onHighlightClick: (String) -> Unit
) {
    // Create a temporary element containing just these nodes for the inline renderer
    // Since we can't easily subset, we build the annotated string manually
    val blockStartOffset = textOffset.offset
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()

    for (node in nodes) {
        when (node) {
            is TextNode -> {
                val text = node.getWholeText()
                builder.append(text)
                textOffset.advance(text.length)
            }
            is Element -> {
                appendInlineElement(builder, node, theme, textOffset, onLinkClick)
            }
        }
    }

    val blockEndOffset = textOffset.offset
    var result = builder.toAnnotatedString()

    // Apply highlights
    val overlapping = highlights.filter { h ->
        h.startOffset < blockEndOffset && h.endOffset > blockStartOffset
    }
    if (overlapping.isNotEmpty()) {
        result = buildAnnotatedString {
            append(result)
            for (highlight in overlapping) {
                val localStart = (highlight.startOffset - blockStartOffset).coerceIn(0, result.length)
                val localEnd = (highlight.endOffset - blockStartOffset).coerceIn(0, result.length)
                if (localStart >= localEnd) continue
                val bgColor = theme.highlightColors[highlight.color ?: "yellow"]
                    ?: theme.highlightColors["yellow"]!!
                addStyle(SpanStyle(background = bgColor.copy(alpha = 0.4f), color = ReaderThemeData.highlightTextColor), localStart, localEnd)
                addStringAnnotation(HIGHLIGHT_ANNOTATION_TAG, highlight.id, localStart, localEnd)
            }
        }
    }

    if (result.isNotEmpty()) {
        AnnotatedClickableText(
            text = result,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            color = theme.textColor,
            fontSize = theme.fontSize,
            fontFamily = theme.fontFamily,
            lineHeight = (theme.fontSize.value * 1.6f).sp
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
    onHighlightClick: (String) -> Unit
) {
    if (hasBlockChildren(element)) {
        // <p> with block children (malformed HTML) — render as div
        Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
            RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick)
        }
    } else {
        val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
        if (text.isNotEmpty()) {
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                color = theme.textColor,
                fontSize = theme.fontSize,
                fontFamily = theme.fontFamily,
                lineHeight = (theme.fontSize.value * 1.6f).sp,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
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
    depth: Int,
    modifier: Modifier = Modifier
) {
    if (hasBlockChildren(element)) {
        Column(modifier = modifier.fillMaxWidth()) {
            RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        }
    } else {
        val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
        if (text.isNotEmpty()) {
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                color = theme.textColor,
                fontSize = theme.fontSize,
                fontFamily = theme.fontFamily,
                lineHeight = (theme.fontSize.value * 1.6f).sp,
                modifier = modifier
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
    level: Int
) {
    val scaleFactor = when (level) {
        1 -> 2.0f
        2 -> 1.5f
        3 -> 1.25f
        4 -> 1.1f
        5 -> 1.0f
        else -> 0.9f
    }
    val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
    if (text.isNotEmpty()) {
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            color = theme.textColor,
            fontSize = (theme.fontSize.value * scaleFactor).sp,
            fontFamily = theme.fontFamily,
            fontWeight = FontWeight.Bold,
            lineHeight = (theme.fontSize.value * scaleFactor * 1.4f).sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).fillMaxWidth()
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
    depth: Int
) {
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
                RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, depth + 1)
            } else {
                val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
                if (text.isNotEmpty()) {
                    AnnotatedClickableText(
                        text = text,
                        onLinkClick = onLinkClick,
                        onHighlightClick = onHighlightClick,
                        color = theme.textColor,
                        fontSize = theme.fontSize,
                        fontFamily = theme.fontFamily,
                        fontStyle = FontStyle.Italic,
                        lineHeight = (theme.fontSize.value * 1.6f).sp
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
    onHighlightClick: (String) -> Unit
) {
    // Pre/code blocks: find the <code> child if it exists
    val codeElement = element.selectFirst("code") ?: element
    val text = buildInlineAnnotatedString(codeElement, theme, highlights, textOffset, onLinkClick, onHighlightClick)
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

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(theme.codeBackgroundColor)
            .horizontalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            color = theme.textColor,
            fontSize = (theme.fontSize.value * 0.875f).sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = (theme.fontSize.value * 0.875f * 1.4f).sp
        )
    }
}

@Composable
private fun RenderUnorderedList(
    element: Element,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    depth: Int
) {
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp).fillMaxWidth()) {
        for (child in element.children()) {
            if (child.tagName().lowercase() == "li") {
                RenderListItem(child, highlights, textOffset, onLinkClick, onHighlightClick, depth, bullet = "\u2022")
            } else {
                RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, depth)
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
    depth: Int
) {
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp).fillMaxWidth()) {
        var index = 1
        for (child in element.children()) {
            if (child.tagName().lowercase() == "li") {
                RenderListItem(child, highlights, textOffset, onLinkClick, onHighlightClick, depth, bullet = "${index}.")
                index++
            } else {
                RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, depth)
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
    depth: Int,
    bullet: String
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
        Column(modifier = Modifier.weight(1f)) {
            if (hasBlockChildren(element)) {
                RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, depth + 1)
            } else {
                val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
                AnnotatedClickableText(
                    text = text,
                    onLinkClick = onLinkClick,
                    onHighlightClick = onHighlightClick,
                    color = theme.textColor,
                    fontSize = theme.fontSize,
                    fontFamily = theme.fontFamily,
                    lineHeight = (theme.fontSize.value * 1.6f).sp
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
    depth: Int
) {
    Column(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    ) {
        // Use RenderChildren to handle all nodes (elements + text) without double-counting
        RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
    }
}

@Composable
private fun RenderFigcaption(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit
) {
    val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
    if (text.isNotEmpty()) {
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            color = theme.textColor.copy(alpha = 0.8f),
            fontSize = (theme.fontSize.value * 0.875f).sp,
            fontFamily = theme.fontFamily,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun RenderImage(element: Element) {
    val src = element.attr("src")
    if (src.isBlank()) return

    val alt = element.attr("alt")
    AsyncImage(
        model = src,
        contentDescription = alt.ifBlank { null },
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
    )
}

@Composable
private fun RenderPicture(element: Element) {
    // <picture> contains <source> and <img>. We just render the <img> fallback.
    val img = element.selectFirst("img")
    if (img != null) {
        RenderImage(img)
    }
}

// --- Table support (simple) ---

@Composable
private fun RenderTable(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    depth: Int
) {
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        for (child in element.children()) {
            val tag = child.tagName().lowercase()
            when (tag) {
                "thead", "tbody", "tfoot" -> RenderTableSection(child, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
                "tr" -> RenderTableRow(child, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
                "caption" -> {
                    val text = buildInlineAnnotatedString(child, theme, highlights, textOffset, onLinkClick, onHighlightClick)
                    AnnotatedClickableText(text = text, onLinkClick = onLinkClick, onHighlightClick = onHighlightClick, color = theme.textColor, fontSize = theme.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                }
                else -> RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, depth)
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
    depth: Int
) {
    for (child in element.children()) {
        if (child.tagName().lowercase() == "tr") {
            RenderTableRow(child, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth)
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
    depth: Int
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
                    RenderTableCell(child, theme, highlights, textOffset, onLinkClick, onHighlightClick, depth, isHeader = tag == "th")
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
    depth: Int,
    isHeader: Boolean
) {
    if (hasBlockChildren(element)) {
        Column {
            RenderChildren(element, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        }
    } else {
        val text = buildInlineAnnotatedString(element, theme, highlights, textOffset, onLinkClick, onHighlightClick)
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            color = theme.textColor,
            fontSize = theme.fontSize,
            fontFamily = theme.fontFamily,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            lineHeight = (theme.fontSize.value * 1.4f).sp
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
    depth: Int
) {
    Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        for (child in element.children()) {
            RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick, depth)
        }
    }
}
