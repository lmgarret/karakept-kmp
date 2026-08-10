package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Result of finding text offsets in the HTML document.
 */
data class TextOffsetResult(
    val startOffset: Int,
    val endOffset: Int,
    val matchedText: String
)

/**
 * Finds the start and end offsets of the first occurrence of [searchText] in the
 * text content of the given [html] document. Offsets are calculated by walking
 * text nodes in document order, matching the TreeWalker approach used in the
 * WebView JavaScript code.
 *
 * @param html      Sanitized HTML content.
 * @param searchText The text to find.
 * @return The offset result, or null if the text was not found.
 */
fun findTextOffsets(html: String, searchText: String): TextOffsetResult? {
    if (searchText.isBlank()) return null

    val doc = try {
        Ksoup.parse(html)
    } catch (e: Exception) {
        return null
    }

    val body = doc.body()

    // Collect all text content by walking text nodes in document order
    val fullText = StringBuilder()
    collectTextNodes(body, fullText)

    val docText = fullText.toString()

    // Normalize whitespace for matching
    // Replace NBSP with regular space first, then collapse all whitespace
    val normalizedSearch = searchText.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()
    val normalizedDoc = buildNormalizedMapping(docText)

    // Find in normalized text
    val normalizedSearchIndex = normalizedDoc.normalizedText.lowercase()
        .indexOf(normalizedSearch.lowercase())

    if (normalizedSearchIndex == -1) {
        return null
    }

    // Map back to original offsets
    val originalStart = normalizedDoc.normalizedToOriginal[normalizedSearchIndex]
    val searchEndNormalized = normalizedSearchIndex + normalizedSearch.length
    val originalEnd = if (searchEndNormalized < normalizedDoc.normalizedToOriginal.size) {
        normalizedDoc.normalizedToOriginal[searchEndNormalized]
    } else {
        docText.length
    }

    val resultText = docText.substring(originalStart, originalEnd)
    return TextOffsetResult(
        startOffset = originalStart,
        endOffset = originalEnd,
        matchedText = resultText
    )
}

/**
 * A direct child of the rendered root, paired with where its text begins in the
 * document's text stream.
 *
 * @param isRenderable whether the reader draws this node. Nodes that are not
 *   drawn — whitespace between tags, stray inline content — still occupy the
 *   stream, so they have to be measured even though nothing is emitted for them.
 */
data class ReaderTextSpan(
    val node: com.fleeksoft.ksoup.nodes.Node,
    val startOffset: Int,
    val isRenderable: Boolean
)

/**
 * Computes the start offset of every direct child of [root] in the same text
 * stream [findTextOffsets] searches.
 *
 * The reader renders the document's top-level children one at a time, so it has
 * to know where each one starts. Deriving those offsets here, from the same walk
 * that resolves a selection to offsets, is what keeps a highlight rendering
 * where it was created: the two used to be maintained separately, and the
 * renderer's copy left out both the newline that joins block elements and the
 * whitespace between them, so highlights drifted one character earlier per
 * block (#295).
 */
fun computeReaderTextSpans(root: Element): List<ReaderTextSpan> {
    val spans = mutableListOf<ReaderTextSpan>()
    var offset = 0

    for (child in root.childNodes()) {
        when (child) {
            is TextNode -> {
                val text = child.getWholeText()
                spans += ReaderTextSpan(child, offset, isRenderable = text.isNotBlank())
                offset += text.length
            }

            is Element -> {
                val isBlock = isBlockElement(child)
                if (isBlock && offset > 0) offset++
                spans += ReaderTextSpan(child, offset, isRenderable = isBlock)
                offset += readerTextLength(child)
            }
        }
    }

    return spans
}

/** Length of [node]'s subtree in the document text stream. */
private fun readerTextLength(node: com.fleeksoft.ksoup.nodes.Node): Int {
    val sb = StringBuilder()
    collectTextNodes(node, sb)
    return sb.length
}

private fun collectTextNodes(node: com.fleeksoft.ksoup.nodes.Node, sb: StringBuilder) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> sb.append(child.getWholeText())
            is Element -> {
                // Add a virtual newline before block elements (match Compose selection joining)
                if (isBlockElement(child) && sb.isNotEmpty()) {
                    sb.append("\n")
                }
                collectTextNodes(child, sb)
            }
        }
    }
}

private data class NormalizedMapping(
    val normalizedText: String,
    val normalizedToOriginal: List<Int>
)

private fun buildNormalizedMapping(text: String): NormalizedMapping {
    val normalizedToOriginal = mutableListOf<Int>()
    val normalized = StringBuilder()
    var lastWasSpace = false

    for (i in text.indices) {
        val char = text[i]
        val isSpace = char.isWhitespace()

        if (isSpace) {
            if (!lastWasSpace) {
                normalizedToOriginal.add(i)
                normalized.append(' ')
                lastWasSpace = true
            }
        } else {
            normalizedToOriginal.add(i)
            normalized.append(char)
            lastWasSpace = false
        }
    }
    // End marker
    normalizedToOriginal.add(text.length)

    return NormalizedMapping(normalized.toString(), normalizedToOriginal)
}
