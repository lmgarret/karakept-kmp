package com.karakept.app.ui.components.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.nodes.Element
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode

// Reader-mode rendering of <img> and <picture>: intrinsic sizing, srcset choice, tracking-pixel
// filtering, and the tap-to-open gallery entry.

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

/** Below this in either axis an image is a tracking pixel or a spacer, never content. */
internal const val MIN_RENDERABLE_IMAGE_PX = 8

/**
 * The decoded image's own pixel size, standing in for the `width`/`height` attributes the HTML
 * never declared. One image pixel maps to one dp, the same way a browser maps it to one CSS px —
 * matching what [extractImageDimensions] already feeds the renderer.
 *
 * Only the width is ever acted on (as a cap), never the height: see [RenderResolvedImage]. Coil
 * scales a decode down to the composable's constraints but never up (Compose's default
 * [coil3.size.Precision.INEXACT]), so this width is either the image's true intrinsic width or
 * roughly the container's in *device* pixels. Device pixels outnumber dp at any density ≥ 1, so a
 * downscaled image can never come out looking narrower than its column: only genuinely small
 * images take the shrink path.
 */
internal fun loadedImageDimensions(widthPx: Int, heightPx: Int): ImageDimensions? {
    if (widthPx <= 0 || heightPx <= 0) return null
    return ImageDimensions(widthPx, heightPx)
}

internal fun isTrackingPixel(dimensions: ImageDimensions): Boolean =
    dimensions.width < MIN_RENDERABLE_IMAGE_PX || dimensions.height < MIN_RENDERABLE_IMAGE_PX

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
 * when all URLs fail.
 *
 * Sizing follows the browser's `max-width: 100%; height: auto`: an image occupies its own
 * width and only shrinks once it is wider than the column. [dimensions] supplies that width
 * when the HTML declares it; otherwise the decoded image's own width caps it, which is why a
 * small icon is no longer blown up to the full column width.
 *
 * [element] identifies this image within [LocalGalleryImages] so tapping it opens the
 * full-screen gallery viewer positioned on this exact image, swipeable to its siblings.
 */
@Composable
private fun RenderResolvedImage(
    element: Element,
    urls: List<String>,
    alt: String,
    dimensions: ImageDimensions?,
    caption: String? = null
) {
    var idx by remember(urls) { mutableIntStateOf(0) }
    // Resets to true on every new URL attempt (idx change) and on new image (urls change).
    var isLoading by remember(urls, idx) { mutableStateOf(true) }
    // The decoded image's own size, resolved once Coil hands it over — the HTML declared none.
    var loadedDimensions by remember(urls) { mutableStateOf<ImageDimensions?>(null) }
    val galleryViewerState = LocalGalleryViewerState.current
    val galleryImages = LocalGalleryImages.current

    val loaded = loadedDimensions
    val effectiveDimensions = dimensions ?: loaded
    val sizeModifier = when {
        // Declared dimensions describe the box before anything loads, so the container can
        // safely claim that height up front and hold the layout still.
        dimensions != null -> Modifier
            .widthIn(max = dimensions.width.dp)
            .fillMaxWidth()
            .aspectRatio(dimensions.aspectRatio)
        // A decoded size only caps the width; the painter keeps driving the height, so the box
        // can never claim a height that disagrees with what is actually drawn into it.
        loaded != null -> Modifier
            .widthIn(max = loaded.width.dp)
            .fillMaxWidth()
        else -> Modifier.fillMaxWidth()
    }
    val shape = RoundedCornerShape(4.dp)
    // An image block has nothing to paint outside its own box, so clip it: a painter handed a
    // box shorter than its content draws past the edge rather than shrinking into it, and that
    // lands on the surrounding text.
    val baseModifier = sizeModifier.padding(vertical = 8.dp).clipToBounds()

    // A spacer or tracking pixel: drop it entirely rather than leave a padded sliver in the text.
    if (effectiveDimensions != null && isTrackingPixel(effectiveDimensions)) return

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
                imageVector = AppIcons.Default.ImageNotSupported,
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
        ) {
            val initialIndex = galleryImages.indexOfFirst { it.element === element }.coerceAtLeast(0)
            galleryViewerState?.open(
                images = galleryImages.ifEmpty {
                    listOf(GalleryImage(element, urls, alt, caption, dimensions))
                },
                initialIndex = initialIndex
            )
        }
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
            onSuccess = { state ->
                isLoading = false
                if (dimensions == null) {
                    val image = state.result.image
                    loadedDimensions = loadedImageDimensions(image.width, image.height)
                }
            },
            onError = { idx++ },
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .alpha(if (isLoading) 0f else 1f)
        )
    }
}

@Composable
private fun ImageLoadingSkeleton(modifier: Modifier) {
    if (LocalEinkMode.current.animationsDisabled) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            LoadingDotsIndicator(dotSize = 6.dp)
        }
        return
    }
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
internal fun RenderImage(element: Element) {
    val galleryImage = remember(element) { resolveImgGalleryImage(element) } ?: return
    RenderResolvedImage(
        element = element,
        urls = galleryImage.urls,
        alt = galleryImage.alt,
        dimensions = galleryImage.dimensions,
        caption = galleryImage.caption
    )
}

@Composable
internal fun RenderPicture(element: Element) {
    val galleryImage = remember(element) { resolvePictureGalleryImage(element) } ?: return
    RenderResolvedImage(
        element = element,
        urls = galleryImage.urls,
        alt = galleryImage.alt,
        dimensions = galleryImage.dimensions,
        caption = galleryImage.caption
    )
}
