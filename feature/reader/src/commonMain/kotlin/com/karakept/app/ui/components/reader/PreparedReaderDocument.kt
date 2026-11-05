package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

/**
 * Everything the reader derives from an article's HTML before it can draw a line of it: the
 * parsed tree, the text-offset stream, the top-level blocks it renders and the page's gallery.
 *
 * Each of these is a walk over the whole document. On a long article they add up to the better
 * part of a second, so they are built together off the main thread by [prepareReaderDocument]
 * and handed to [NativeHtmlRenderer] finished, rather than `remember`ed one by one in composition.
 * Treat the tree as read-only once prepared: the offsets and spans point into it.
 */
class PreparedReaderDocument internal constructor(
    val html: String,
    val document: Document,
    val offsets: ReaderTextOffsets,
    val renderableChildren: List<ReaderTextSpan>,
    internal val galleryImages: List<GalleryImage>
) {
    val body: Element get() = document.body()
}

/** Parses [html] and runs every full-document walk the reader needs. Safe to call off the main thread. */
fun prepareReaderDocument(html: String): PreparedReaderDocument? {
    val document = try {
        Ksoup.parse(html)
    } catch (e: Exception) {
        return null
    }
    val body = document.body()
    val offsets = buildReaderTextOffsets(body)
    return PreparedReaderDocument(
        html = html,
        document = document,
        offsets = offsets,
        renderableChildren = computeReaderTextSpans(body, offsets).filter { it.isRenderable },
        galleryImages = collectGalleryImages(document)
    )
}
