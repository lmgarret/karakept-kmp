package com.karakept.app.ui.components.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.fleeksoft.ksoup.nodes.Element

/**
 * One heading of the article, in document order.
 *
 * [id] is where the heading's text starts in the document's text stream, which is what makes it an
 * identity the renderer can produce too: [RenderHeading] reads the same `offsets` and registers
 * its laid-out coordinates under the same number. A heading with no text is not in the stream and
 * is not a section either, so it never reaches here.
 */
data class ReaderTocEntry(
    val id: Int,
    /** 1 for `<h1>` … 6 for `<h6>`. */
    val level: Int,
    val title: String
)

private val HEADING_LEVELS = mapOf(
    "h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6
)

/** The heading level of [element], or null when it is not a heading. */
internal fun headingLevelOf(element: Element): Int? =
    HEADING_LEVELS[element.tagName().lowercase()]

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * The article's headings, in document order — the table of contents the scroll cursor previews.
 *
 * One more walk over the whole document, so it belongs to [prepareReaderDocument] with the rest of
 * them rather than to a `remember` in composition.
 *
 * Titles are whitespace-collapsed: a heading is often split across an `<a>` and a `<span>` with
 * newlines between them in the source, and the cursor draws it on one line.
 */
fun extractReaderToc(root: Element, offsets: ReaderTextOffsets): List<ReaderTocEntry> {
    val result = mutableListOf<ReaderTocEntry>()
    val seen = mutableSetOf<Int>()

    fun walk(element: Element) {
        val level = headingLevelOf(element)
        if (level != null) {
            val title = element.text().replace(WHITESPACE_RUN, " ").trim()
            val id = offsets.startOf(element)
            if (title.isNotEmpty() && seen.add(id)) {
                result.add(ReaderTocEntry(id = id, level = level, title = title))
            }
            return
        }
        for (child in element.children()) walk(child)
    }

    walk(root)
    return result
}

/**
 * The article's table of contents, and where each of its sections has been laid out.
 *
 * The two halves come from opposite ends of the reader, because neither end has both. [entries] is
 * read off the parsed document by [prepareReaderDocument] and published here by the renderer, the
 * same way the page's gallery reaches [GalleryViewerState]. Where each section *sits* cannot be
 * read from anywhere above the article: the whole of it is a single lazy item, so
 * `LazyListState.layoutInfo` knows nothing finer than "the body". The headings register themselves
 * instead.
 *
 * Positions are deliberately *not* snapshot state, and are *coordinates* rather than numbers, for
 * the same two reasons as [ReaderSnapRegistry]: nothing observes them reactively, and a position
 * pushed from `onGloballyPositioned` would cost one callback per heading per scroll frame. Read
 * lazily, scrolling writes nothing here at all.
 */
class ReaderTocState {
    var entries: List<ReaderTocEntry> by mutableStateOf(emptyList())

    private val positions = mutableMapOf<Int, () -> Float>()

    /** Registers the section headed by [id]. [topInRoot] returns NaN once the heading is detached. */
    fun report(id: Int, topInRoot: () -> Float) {
        positions[id] = topInRoot
    }

    fun forget(id: Int) {
        positions.remove(id)
    }

    /**
     * Where the section headed by [id] sits, in root coordinates, or null when it has not been
     * laid out or is no longer attached.
     */
    fun positionOf(id: Int): Float? = positions[id]?.invoke()?.takeUnless { it.isNaN() }
}

/**
 * Null when the reader's scroll cursor is off, which is also what makes the reporting free:
 * headings skip the whole registration when there is nothing to report to.
 */
val LocalReaderToc = staticCompositionLocalOf<ReaderTocState?> { null }
