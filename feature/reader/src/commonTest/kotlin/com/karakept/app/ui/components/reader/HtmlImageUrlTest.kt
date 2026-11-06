package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [pickUrlsFromSrcset] and [resolveImageUrls].
 *
 * These cover the lazy-load pattern used by sites like Numerama where
 * <img src> is an SVG placeholder and the real URL lives in data-src or
 * data-srcset (or in a sibling <source srcset>). The returned list is
 * a priority-ordered fallback chain: index 0 is tried first, and Coil's
 * onError callback advances to the next entry on failure.
 */
class HtmlImageUrlTest {

    // --- pickUrlsFromSrcset ---

    @Test
    fun pickUrlsFromSrcset_blank_returnsEmpty() {
        assertTrue(pickUrlsFromSrcset("").isEmpty())
    }

    @Test
    fun pickUrlsFromSrcset_singleUrl_returnsListWithThatUrl() {
        val srcset = "https://cdn.example.com/img-1024.jpg 1024w"
        assertEquals(listOf("https://cdn.example.com/img-1024.jpg"), pickUrlsFromSrcset(srcset))
    }

    @Test
    fun pickUrlsFromSrcset_multipleEntries_returnsAllInDocumentOrder() {
        val srcset = "https://cdn.example.com/img-480.jpg 480w," +
                "https://cdn.example.com/img-768.jpg 768w," +
                "https://cdn.example.com/img-1024.jpg 1024w," +
                "https://cdn.example.com/img-2048.jpg 2048w"
        assertEquals(
            listOf(
                "https://cdn.example.com/img-480.jpg",
                "https://cdn.example.com/img-768.jpg",
                "https://cdn.example.com/img-1024.jpg",
                "https://cdn.example.com/img-2048.jpg",
            ),
            pickUrlsFromSrcset(srcset)
        )
    }

    @Test
    fun pickUrlsFromSrcset_skipsDataSvgPlaceholders() {
        val placeholder = "data:image/svg+xml;utf8,%3Csvg/%3E"
        val srcset = "$placeholder 1x,https://cdn.example.com/img.jpg 2x"
        assertEquals(listOf("https://cdn.example.com/img.jpg"), pickUrlsFromSrcset(srcset))
    }

    @Test
    fun pickUrlsFromSrcset_allPlaceholders_returnsEmpty() {
        val srcset = "data:image/svg+xml;base64,PHN2Zy8+ 1x"
        assertTrue(pickUrlsFromSrcset(srcset).isEmpty())
    }

    @Test
    fun pickUrlsFromSrcset_withQueryParams_preservesUrl() {
        val srcset = "https://cdn.example.com/img.jpg?resize=1024,576&key=abc123 1024w"
        assertEquals(
            listOf("https://cdn.example.com/img.jpg?resize=1024,576&key=abc123"),
            pickUrlsFromSrcset(srcset)
        )
    }

    @Test
    fun pickUrlsFromSrcset_fileUrl_isAccepted() {
        val srcset = "file:///data/user/0/com.example/cache/img_abc.jpg 1x"
        assertEquals(
            listOf("file:///data/user/0/com.example/cache/img_abc.jpg"),
            pickUrlsFromSrcset(srcset)
        )
    }

    @Test
    fun pickUrlsFromSrcset_withSpacesAroundComma_parsesCorrectly() {
        val srcset = " https://cdn.example.com/small.jpg 512w , https://cdn.example.com/large.jpg 1024w "
        assertEquals(
            listOf("https://cdn.example.com/small.jpg", "https://cdn.example.com/large.jpg"),
            pickUrlsFromSrcset(srcset)
        )
    }

    @Test
    fun pickUrlsFromSrcset_multipleUrlsWithCommasInQueryParams_parsesCorrectly() {
        // Real-world pattern from Numerama / WordPress photon CDN: every URL contains
        // a comma in its query string (?resize=W,H), and entries are themselves
        // separated by commas. The parser must distinguish the two.
        val srcset = "https://cdn.example.com/img-1024x576.jpg?resize=928,522&key=abc 928w," +
                "https://cdn.example.com/img-1024x576.jpg?resize=768,432&key=abc 768w," +
                "https://cdn.example.com/img-1024x576.jpg?resize=480,270&key=abc 480w"
        assertEquals(
            listOf(
                "https://cdn.example.com/img-1024x576.jpg?resize=928,522&key=abc",
                "https://cdn.example.com/img-1024x576.jpg?resize=768,432&key=abc",
                "https://cdn.example.com/img-1024x576.jpg?resize=480,270&key=abc",
            ),
            pickUrlsFromSrcset(srcset)
        )
    }

    @Test
    fun pickUrlsFromSrcset_singleUrlWithCommaInQuery_preservesFullUrl() {
        val srcset = "https://cdn.example.com/img.jpg?resize=1024,576&key=abc123 1024w"
        assertEquals(
            listOf("https://cdn.example.com/img.jpg?resize=1024,576&key=abc123"),
            pickUrlsFromSrcset(srcset)
        )
    }

    @Test
    fun pickUrlsFromSrcset_base64JpegDataUri_isIncluded() {
        // Base64 content has no commas, so the srcset splitter never fragments a data URI.
        // Non-SVG data URIs (e.g. JPEG thumbnails) belong in the fallback chain.
        val base64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgAB"
        val srcset = "$base64 480w"
        assertEquals(listOf(base64), pickUrlsFromSrcset(srcset))
    }

    @Test
    fun pickUrlsFromSrcset_base64JpegMixedWithHttpUrl_includesBoth() {
        // Verifies that a srcset mixing a base64 data URI and an http URL is parsed
        // correctly: the data URI is not split mid-value and both entries are returned.
        val base64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgABYNS"
        val httpUrl = "https://cdn.example.com/img.jpg"
        val srcset = "$base64 480w,$httpUrl 1024w"
        val urls = pickUrlsFromSrcset(srcset)
        assertEquals(2, urls.size)
        assertEquals(base64, urls[0])
        assertEquals(httpUrl, urls[1])
    }

    // --- resolveImageUrls ---

    private fun imgElement(
        src: String,
        dataSrc: String = "",
        srcset: String = "",
        dataSrcset: String = ""
    ) = Ksoup.parse(buildString {
        append("<img")
        if (src.isNotEmpty()) append(" src=\"$src\"")
        if (dataSrc.isNotEmpty()) append(" data-src=\"$dataSrc\"")
        if (srcset.isNotEmpty()) append(" srcset=\"$srcset\"")
        if (dataSrcset.isNotEmpty()) append(" data-srcset=\"$dataSrcset\"")
        append(">")
    }).selectFirst("img")!!

    @Test
    fun resolveImageUrls_realSrc_returnsSrc() {
        val el = imgElement(src = "https://cdn.example.com/img.jpg")
        assertEquals(listOf("https://cdn.example.com/img.jpg"), resolveImageUrls(el))
    }

    @Test
    fun resolveImageUrls_svgPlaceholderSrc_skipsItAndUsesDataSrc() {
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg%20xmlns=%27http://www.w3.org/2000/svg%27/%3E",
            dataSrc = "https://cdn.example.com/real.jpg"
        )
        // SVG placeholder is filtered; only data-src is returned
        assertEquals(listOf("https://cdn.example.com/real.jpg"), resolveImageUrls(el))
    }

    @Test
    fun resolveImageUrls_blankSrc_usesDataSrc() {
        val el = imgElement(src = "", dataSrc = "https://cdn.example.com/real.jpg")
        assertEquals(listOf("https://cdn.example.com/real.jpg"), resolveImageUrls(el))
    }

    @Test
    fun resolveImageUrls_svgPlaceholderNoDataSrc_fallsBackToSrcset() {
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg/%3E",
            srcset = "https://cdn.example.com/img-512.jpg 512w,https://cdn.example.com/img-1024.jpg 1024w"
        )
        val urls = resolveImageUrls(el)
        // All srcset entries are returned in document order for the fallback chain
        assertEquals(2, urls.size)
        assertEquals("https://cdn.example.com/img-512.jpg", urls[0])
        assertEquals("https://cdn.example.com/img-1024.jpg", urls[1])
    }

    @Test
    fun resolveImageUrls_svgPlaceholderNoDataSrc_fallsBackToDataSrcset() {
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg/%3E",
            dataSrcset = "https://cdn.example.com/img-512.jpg 512w,https://cdn.example.com/img-1024.jpg 1024w"
        )
        val urls = resolveImageUrls(el)
        assertEquals(2, urls.size)
        assertEquals("https://cdn.example.com/img-512.jpg", urls[0])
        assertEquals("https://cdn.example.com/img-1024.jpg", urls[1])
    }

    @Test
    fun resolveImageUrls_noUsableUrl_returnsEmpty() {
        val el = imgElement(src = "data:image/svg+xml;utf8,%3Csvg/%3E")
        assertTrue(resolveImageUrls(el).isEmpty())
    }

    @Test
    fun resolveImageUrls_dataSrcBeforeSrcset() {
        // data-src is a more direct pointer than srcset; it should appear first
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg/%3E",
            dataSrc = "https://cdn.example.com/from-datasrc.jpg",
            srcset = "https://cdn.example.com/from-srcset.jpg 1024w"
        )
        val urls = resolveImageUrls(el)
        assertEquals("https://cdn.example.com/from-datasrc.jpg", urls.first())
        assertTrue(urls.contains("https://cdn.example.com/from-srcset.jpg"))
    }

    @Test
    fun resolveImageUrls_unreachableSrcWithBase64Srcset_includesBothForFallback() {
        // Real-world pattern: src is a private-network URL (e.g. NAS or router IP)
        // unreachable outside the LAN; srcset holds a base64-encoded JPEG that is
        // always decodable locally. The fallback chain must include both so Coil
        // attempts the private IP first and silently falls back to the embedded data URI.
        val base64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgAB"
        val el = imgElement(
            src = "http://192.168.3.250/media/photo.jpg",
            srcset = "$base64 480w"
        )
        val urls = resolveImageUrls(el)
        assertEquals(2, urls.size)
        assertEquals("http://192.168.3.250/media/photo.jpg", urls[0])
        assertEquals(base64, urls[1])
    }

    @Test
    fun resolveImageUrls_deduplicatesCandidates() {
        // If the same URL appears as both src and srcset, it should only be tried once.
        val url = "https://cdn.example.com/img.jpg"
        val el = imgElement(src = url, srcset = "$url 1024w")
        assertEquals(listOf(url), resolveImageUrls(el))
    }

    // --- Integration: sanitize then resolve ---

    @Test
    fun sanitizeAndResolve_lazyLoadedImg_picksDataSrc() {
        val html = """<img src="data:image/svg+xml;utf8,%3Csvg/%3E" data-src="https://cdn.example.com/photo.jpg" alt="Photo">"""
        val sanitized = com.karakept.app.utils.HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)
        val img = doc.selectFirst("img")!!
        assertEquals("https://cdn.example.com/photo.jpg", resolveImageUrls(img).firstOrNull())
    }

    @Test
    fun sanitizeAndResolve_pictureWithSourceDataSrcset_picksSourceUrl() {
        val html = """<picture><source data-srcset="https://cdn.example.com/photo.webp 1024w" type="image/webp"><img src="data:image/svg+xml;utf8,%3Csvg/%3E" data-src="https://cdn.example.com/photo.jpg" alt="Photo"></picture>"""
        val sanitized = com.karakept.app.utils.HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)
        val source = doc.selectFirst("source")!!
        val srcset = source.attr("srcset").ifBlank { source.attr("data-srcset") }
        assertEquals("https://cdn.example.com/photo.webp", pickUrlsFromSrcset(srcset).firstOrNull())
    }

    // --- extractImageDimensions ---

    private fun img(html: String) = Ksoup.parse(html).selectFirst("img")!!

    @Test
    fun extractImageDimensions_validIntegerDimensions_returnsDims() {
        val dims = extractImageDimensions(img("<img src='https://x' width='200' height='100'>"))
        assertNotNull(dims)
        assertEquals(200, dims.width)
        assertEquals(100, dims.height)
        assertEquals(2.0f, dims.aspectRatio)
    }

    @Test
    fun extractImageDimensions_noAttributes_returnsNull() {
        assertTrue(extractImageDimensions(img("<img src='https://x'>")) == null)
    }

    @Test
    fun extractImageDimensions_onlyWidth_returnsNull() {
        assertTrue(extractImageDimensions(img("<img src='https://x' width='200'>")) == null)
    }

    @Test
    fun extractImageDimensions_onlyHeight_returnsNull() {
        assertTrue(extractImageDimensions(img("<img src='https://x' height='100'>")) == null)
    }

    @Test
    fun extractImageDimensions_nonNumericValues_returnsNull() {
        assertTrue(extractImageDimensions(img("<img src='https://x' width='100%' height='auto'>")) == null)
    }

    @Test
    fun extractImageDimensions_zeroValues_returnsNull() {
        assertTrue(extractImageDimensions(img("<img src='https://x' width='0' height='0'>")) == null)
    }

    @Test
    fun extractImageDimensions_negativeValues_returnsNull() {
        assertTrue(extractImageDimensions(img("<img src='https://x' width='-50' height='100'>")) == null)
    }

    @Test
    fun extractImageDimensions_squareIcon_returnsAspectRatioOne() {
        val dims = extractImageDimensions(img("<img src='https://x' width='32' height='32'>"))
        assertNotNull(dims)
        assertEquals(1.0f, dims.aspectRatio)
    }

    @Test
    fun sanitizeAndExtract_dimensionsSurviveSanitizer() {
        val html = "<img src='https://example.com/icon.png' width='48' height='48' alt='icon'>"
        val sanitized = com.karakept.app.utils.HtmlSanitizer.sanitize(html)
        val dims = extractImageDimensions(Ksoup.parse(sanitized).selectFirst("img")!!)
        assertNotNull(dims, "width/height should survive sanitization")
        assertEquals(48, dims.width)
        assertEquals(48, dims.height)
    }

    // --- loadedImageDimensions / isTrackingPixel ---

    @Test
    fun loadedImageDimensions_decodedSize_returnsDims() {
        val dims = loadedImageDimensions(320, 240)
        assertNotNull(dims)
        assertEquals(320, dims.width)
        assertEquals(240, dims.height)
        assertEquals(320f / 240f, dims.aspectRatio)
    }

    @Test
    fun loadedImageDimensions_zeroOrNegative_returnsNull() {
        assertTrue(loadedImageDimensions(0, 100) == null)
        assertTrue(loadedImageDimensions(100, 0) == null)
        assertTrue(loadedImageDimensions(-1, -1) == null)
    }

    @Test
    fun isTrackingPixel_onePixelGif_returnsTrue() {
        assertTrue(isTrackingPixel(ImageDimensions(1, 1)))
        assertTrue(isTrackingPixel(ImageDimensions(2, 2)))
    }

    @Test
    fun isTrackingPixel_thinSpacer_returnsTrue() {
        // A 1x600 spacer is only degenerate on one axis.
        assertTrue(isTrackingPixel(ImageDimensions(1, 600)))
        assertTrue(isTrackingPixel(ImageDimensions(600, 1)))
    }

    @Test
    fun isTrackingPixel_realImages_returnsFalse() {
        assertFalse(isTrackingPixel(ImageDimensions(MIN_RENDERABLE_IMAGE_PX, MIN_RENDERABLE_IMAGE_PX)))
        assertFalse(isTrackingPixel(ImageDimensions(200, 60)))
        assertFalse(isTrackingPixel(ImageDimensions(1920, 1080)))
    }

    // --- findFigureCaption ---

    @Test
    fun findFigureCaption_directParentFigure_returnsCaptionText() {
        val html = "<figure><img src='https://x'><figcaption>A cat</figcaption></figure>"
        val img = Ksoup.parse(html).selectFirst("img")!!
        assertEquals("A cat", findFigureCaption(img))
    }

    @Test
    fun findFigureCaption_imageWrappedInAnchorLightboxLink_walksUpToFigure() {
        // Matches the <figure><a><picture>...</picture></a><figcaption>...</figcaption></figure>
        // lightbox-link pattern isBlockElement() special-cases.
        val html = "<figure><a href='https://x/full.jpg'><img src='https://x'></a><figcaption>Caption text</figcaption></figure>"
        val img = Ksoup.parse(html).selectFirst("img")!!
        assertEquals("Caption text", findFigureCaption(img))
    }

    @Test
    fun findFigureCaption_pictureElement_returnsCaptionText() {
        val html = "<figure><picture><source srcset='https://x.webp'><img src='https://x.jpg'></picture><figcaption>Photo caption</figcaption></figure>"
        val picture = Ksoup.parse(html).selectFirst("picture")!!
        assertEquals("Photo caption", findFigureCaption(picture))
    }

    @Test
    fun findFigureCaption_noFigureAncestor_returnsNull() {
        val html = "<div><img src='https://x'></div>"
        val img = Ksoup.parse(html).selectFirst("img")!!
        assertTrue(findFigureCaption(img) == null)
    }

    @Test
    fun findFigureCaption_figureWithoutFigcaption_returnsNull() {
        val html = "<figure><img src='https://x'></figure>"
        val img = Ksoup.parse(html).selectFirst("img")!!
        assertTrue(findFigureCaption(img) == null)
    }

    @Test
    fun findFigureCaption_blankFigcaption_returnsNull() {
        val html = "<figure><img src='https://x'><figcaption>   </figcaption></figure>"
        val img = Ksoup.parse(html).selectFirst("img")!!
        assertTrue(findFigureCaption(img) == null)
    }

    @Test
    fun findFigureCaption_trimsWhitespace() {
        val html = "<figure><img src='https://x'><figcaption>  Padded caption  </figcaption></figure>"
        val img = Ksoup.parse(html).selectFirst("img")!!
        assertEquals("Padded caption", findFigureCaption(img))
    }
}
