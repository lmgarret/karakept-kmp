package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.data.model.Highlight
import kotlin.math.abs

/**
 * Result of finding text offsets in the HTML document.
 */
data class TextOffsetResult(
    val startOffset: Int,
    val endOffset: Int,
    val matchedText: String
)

/**
 * Finds the first occurrence of [searchText] in [html]'s text stream.
 *
 * Offsets are the canonical ones described by [ReaderTextOffsets] — text-node
 * characters only. Matching itself is whitespace-insensitive and crosses block
 * boundaries, so a selection Compose handed back with newlines in it still
 * resolves against a stream that has none.
 */
fun findTextOffsets(html: String, searchText: String): TextOffsetResult? {
    val doc = try {
        Ksoup.parse(html)
    } catch (e: Exception) {
        return null
    }
    return findTextOffsets(buildReaderTextOffsets(doc.body()), searchText)
}

/** As above, against a document whose offsets have already been walked. */
fun findTextOffsets(offsets: ReaderTextOffsets, searchText: String): TextOffsetResult? =
    findTextMatches(offsets, searchText, limit = 1).firstOrNull()

/**
 * Every occurrence of [searchText] in the document's text stream, in document
 * order. [limit] caps the work done for a query that matches everywhere.
 */
fun findTextMatches(
    offsets: ReaderTextOffsets,
    searchText: String,
    limit: Int = Int.MAX_VALUE
): List<TextOffsetResult> {
    if (searchText.isBlank() || limit <= 0) return emptyList()

    val needle = normalizeForMatching(searchText).lowercase()
    if (needle.isEmpty()) return emptyList()

    val haystack = offsets.normalized.lowercase
    val results = mutableListOf<TextOffsetResult>()
    var from = 0

    while (results.size < limit) {
        val found = haystack.indexOf(needle, from)
        if (found == -1) break

        val start = offsets.normalized.streamStart(found)
        val end = offsets.normalized.streamEnd(found + needle.length)
        if (end > start) {
            results += TextOffsetResult(start, end, offsets.readableText(start, end))
        }
        from = found + 1
    }

    return results
}

/**
 * Checks each highlight's offsets against the text it was created from, and
 * re-resolves the ones that no longer line up.
 *
 * Offsets outlive the document they were taken in: a highlight may have been
 * recorded by another client, or before this app counted the stream the way
 * [ReaderTextOffsets] does, or the article may have been re-crawled since. The
 * stored text is the more durable record, so when the two disagree the text
 * wins — the occurrence nearest the stored offset, since the same phrase can
 * appear more than once. The WebView viewer has always fallen back this way.
 */
fun resolveHighlights(highlights: List<Highlight>, offsets: ReaderTextOffsets): List<Highlight> =
    highlights.map { highlight -> resolveHighlight(highlight, offsets) }

private fun resolveHighlight(highlight: Highlight, offsets: ReaderTextOffsets): Highlight {
    val expected = normalizeForMatching(highlight.text)
    if (expected.isEmpty()) return highlight

    val start = highlight.startOffset.coerceIn(0, offsets.text.length)
    val end = highlight.endOffset.coerceIn(start, offsets.text.length)
    val actual = normalizeForMatching(offsets.text.substring(start, end))
    if (actual.equals(expected, ignoreCase = true)) return highlight

    val nearest = findTextMatches(offsets, highlight.text)
        .minByOrNull { abs(it.startOffset - highlight.startOffset) }
        ?: return highlight

    return highlight.copy(startOffset = nearest.startOffset, endOffset = nearest.endOffset)
}
