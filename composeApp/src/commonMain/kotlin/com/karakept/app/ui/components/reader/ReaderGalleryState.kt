package com.karakept.app.ui.components.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

/**
 * One `<img>`/`<picture>` on the page, in the document-order list built by
 * [collectGalleryImages]. [element] identifies which rendered image this entry
 * describes (compared by reference, not structural equality) so a tapped image
 * can find its own position in the page-wide list.
 */
internal data class GalleryImage(
    val element: Element,
    val urls: List<String>,
    val alt: String,
    val caption: String?,
    val dimensions: ImageDimensions?
)

/**
 * All images on the current page, in document order. Populated once per parsed
 * [Document] by [NativeHtmlRenderer] so the full-screen viewer can swipe between
 * them without the block-by-block renderer needing to know about its siblings.
 */
internal val LocalGalleryImages = staticCompositionLocalOf<List<GalleryImage>> { emptyList() }

/** One full-screen image viewer request: the page-wide image list plus which one was tapped. */
internal data class GalleryViewerRequest(val images: List<GalleryImage>, val initialIndex: Int)

/**
 * Holds the currently-open full-screen image viewer, if any. [ImageGalleryOverlay] is rendered
 * from this rather than a platform [androidx.compose.ui.window.Dialog]: a Dialog is a separate
 * platform window, and hardware page-turn keys stop reaching
 * [com.karakept.app.ui.input.PageTurnDispatcher] while one has focus (see that class's rule
 * against capturing keys inside dialogs). Staying inline keeps the reader's own window focused,
 * so the buttons can instead be repurposed to flip between images while the viewer is open.
 */
internal class GalleryViewerState {
    var request by mutableStateOf<GalleryViewerRequest?>(null)
        private set

    fun open(images: List<GalleryImage>, initialIndex: Int) {
        request = GalleryViewerRequest(images, initialIndex)
    }

    fun close() {
        request = null
    }
}

/** Provided by [com.karakept.app.ui.screens.BookmarkViewerContent], consumed by [com.karakept.app.ui.components.reader]'s image renderer to open the viewer. */
internal val LocalGalleryViewerState = staticCompositionLocalOf<GalleryViewerState?> { null }

/**
 * Resolves a bare `<img>` element into a [GalleryImage], or null if it has no
 * usable URL. Mirrors [RenderImage]'s own resolution so the pre-scanned list and
 * the live render agree on what each image actually is.
 */
internal fun resolveImgGalleryImage(element: Element): GalleryImage? {
    val urls = resolveImageUrls(element)
    if (urls.isEmpty()) return null
    return GalleryImage(
        element = element,
        urls = urls,
        alt = element.attr("alt"),
        caption = findFigureCaption(element),
        dimensions = extractImageDimensions(element)
    )
}

/**
 * Resolves a `<picture>` element into a [GalleryImage], or null if none of its
 * `<source>`/`<img>` candidates yield a usable URL. Mirrors [RenderPicture]'s own
 * resolution so the pre-scanned list and the live render agree on what each
 * image actually is.
 */
internal fun resolvePictureGalleryImage(element: Element): GalleryImage? {
    val img = element.selectFirst("img")
    val candidates = mutableListOf<String>()
    for (source in element.select("source")) {
        val srcset = source.attr("srcset").ifBlank { source.attr("data-srcset") }
        candidates += pickUrlsFromSrcset(srcset)
    }
    if (img != null) candidates += resolveImageUrls(img)
    val urls = candidates.distinct()
    if (urls.isEmpty()) return null
    return GalleryImage(
        element = element,
        urls = urls,
        alt = img?.attr("alt").orEmpty(),
        caption = findFigureCaption(element),
        dimensions = img?.let { extractImageDimensions(it) }
    )
}

/**
 * Walks [document]'s body once for every `<img>`/`<picture>` that the block
 * renderer will actually turn into an image (see [RenderBlock]'s dispatch on
 * "img"/"picture"), in document order. A `<picture>`'s own `<img>` child is
 * terminal — like the renderer, this never double-counts it.
 */
internal fun collectGalleryImages(document: Document): List<GalleryImage> {
    val result = mutableListOf<GalleryImage>()
    fun walk(element: Element) {
        when (element.tagName().lowercase()) {
            "picture" -> resolvePictureGalleryImage(element)?.let { result.add(it) }
            "img" -> resolveImgGalleryImage(element)?.let { result.add(it) }
            else -> for (child in element.children()) walk(child)
        }
    }
    walk(document.body())
    return result
}
