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

    // Normalize whitespace for matching (same as JS highlightByText)
    val normalizedSearch = searchText.replace(Regex("\\s+"), " ").trim()
    val normalizedDoc = buildNormalizedMapping(docText)

    // Find in normalized text
    val normalizedSearchIndex = normalizedDoc.normalizedText.lowercase()
        .indexOf(normalizedSearch.lowercase())

    if (normalizedSearchIndex == -1) return null

    // Map back to original offsets
    val originalStart = normalizedDoc.normalizedToOriginal[normalizedSearchIndex]
    val searchEndNormalized = normalizedSearchIndex + normalizedSearch.length
    val originalEnd = if (searchEndNormalized < normalizedDoc.normalizedToOriginal.size) {
        normalizedDoc.normalizedToOriginal[searchEndNormalized]
    } else {
        docText.length
    }

    return TextOffsetResult(
        startOffset = originalStart,
        endOffset = originalEnd,
        matchedText = docText.substring(originalStart, originalEnd)
    )
}

private fun collectTextNodes(node: com.fleeksoft.ksoup.nodes.Node, sb: StringBuilder) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> sb.append(child.getWholeText())
            is Element -> collectTextNodes(child, sb)
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
