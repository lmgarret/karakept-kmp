package com.karakept.app.ui.components.reader

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
