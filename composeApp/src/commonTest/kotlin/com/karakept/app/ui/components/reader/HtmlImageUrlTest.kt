package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for [pickBestUrlFromSrcset] and [resolveImageUrl].
 *
 * These cover the lazy-load pattern used by sites like Numerama where
 * <img src> is an SVG placeholder and the real URL lives in data-src or
 * data-srcset (or in a sibling <source srcset>).
 */
class HtmlImageUrlTest {

    // --- pickBestUrlFromSrcset ---

    @Test
    fun pickBestUrlFromSrcset_blank_returnsNull() {
        assertNull(pickBestUrlFromSrcset(""))
    }

    @Test
    fun pickBestUrlFromSrcset_singleUrl_returnsThatUrl() {
        val srcset = "https://cdn.example.com/img-1024.jpg 1024w"
        assertEquals("https://cdn.example.com/img-1024.jpg", pickBestUrlFromSrcset(srcset))
    }

    @Test
    fun pickBestUrlFromSrcset_multipleEntries_returnsLast() {
        // Typically srcset lists go from smallest to largest; we want the largest
        val srcset = "https://cdn.example.com/img-480.jpg 480w," +
                     "https://cdn.example.com/img-768.jpg 768w," +
                     "https://cdn.example.com/img-1024.jpg 1024w," +
                     "https://cdn.example.com/img-2048.jpg 2048w"
        assertEquals("https://cdn.example.com/img-2048.jpg", pickBestUrlFromSrcset(srcset))
    }

    @Test
    fun pickBestUrlFromSrcset_skipsDataSvgPlaceholders() {
        val placeholder = "data:image/svg+xml;utf8,%3Csvg/%3E"
        val srcset = "$placeholder 1x,https://cdn.example.com/img.jpg 2x"
        assertEquals("https://cdn.example.com/img.jpg", pickBestUrlFromSrcset(srcset))
    }

    @Test
    fun pickBestUrlFromSrcset_allPlaceholders_returnsNull() {
        val srcset = "data:image/svg+xml;base64,PHN2Zy8+ 1x"
        assertNull(pickBestUrlFromSrcset(srcset))
    }

    @Test
    fun pickBestUrlFromSrcset_withQueryParams_preservesUrl() {
        val srcset = "https://cdn.example.com/img.jpg?resize=1024,576&key=abc123 1024w"
        assertEquals("https://cdn.example.com/img.jpg?resize=1024,576&key=abc123", pickBestUrlFromSrcset(srcset))
    }

    @Test
    fun pickBestUrlFromSrcset_fileUrl_isAccepted() {
        val srcset = "file:///data/user/0/com.example/cache/img_abc.jpg 1x"
        assertEquals("file:///data/user/0/com.example/cache/img_abc.jpg", pickBestUrlFromSrcset(srcset))
    }

    @Test
    fun pickBestUrlFromSrcset_withSpacesAroundComma_parsesCorrectly() {
        val srcset = " https://cdn.example.com/small.jpg 512w , https://cdn.example.com/large.jpg 1024w "
        assertEquals("https://cdn.example.com/large.jpg", pickBestUrlFromSrcset(srcset))
    }

    @Test
    fun pickBestUrlFromSrcset_multipleUrlsWithCommasInQueryParams_parsesCorrectly() {
        // Real-world pattern from Numerama / WordPress photon CDN: every URL contains
        // a comma in its query string (?resize=W,H), and entries are themselves
        // separated by commas. The parser must distinguish the two.
        val srcset = "https://cdn.example.com/img-1024x576.jpg?resize=928,522&key=abc 928w," +
                     "https://cdn.example.com/img-1024x576.jpg?resize=768,432&key=abc 768w," +
                     "https://cdn.example.com/img-1024x576.jpg?resize=480,270&key=abc 480w"
        assertEquals(
            "https://cdn.example.com/img-1024x576.jpg?resize=480,270&key=abc",
            pickBestUrlFromSrcset(srcset)
        )
    }

    @Test
    fun pickBestUrlFromSrcset_singleUrlWithCommaInQuery_preservesFullUrl() {
        val srcset = "https://cdn.example.com/img.jpg?resize=1024,576&key=abc123 1024w"
        assertEquals(
            "https://cdn.example.com/img.jpg?resize=1024,576&key=abc123",
            pickBestUrlFromSrcset(srcset)
        )
    }

    // --- resolveImageUrl ---

    private fun imgElement(src: String, dataSrc: String = "", srcset: String = "", dataSrcset: String = "") =
        Ksoup.parse(buildString {
            append("<img")
            if (src.isNotEmpty()) append(" src=\"$src\"")
            if (dataSrc.isNotEmpty()) append(" data-src=\"$dataSrc\"")
            if (srcset.isNotEmpty()) append(" srcset=\"$srcset\"")
            if (dataSrcset.isNotEmpty()) append(" data-srcset=\"$dataSrcset\"")
            append(">")
        }).selectFirst("img")!!

    @Test
    fun resolveImageUrl_realSrc_returnsSrc() {
        val el = imgElement(src = "https://cdn.example.com/img.jpg")
        assertEquals("https://cdn.example.com/img.jpg", resolveImageUrl(el))
    }

    @Test
    fun resolveImageUrl_svgPlaceholderSrc_prefersDataSrc() {
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg%20xmlns=%27http://www.w3.org/2000/svg%27/%3E",
            dataSrc = "https://cdn.example.com/real.jpg"
        )
        assertEquals("https://cdn.example.com/real.jpg", resolveImageUrl(el))
    }

    @Test
    fun resolveImageUrl_blankSrc_usesDataSrc() {
        val el = imgElement(src = "", dataSrc = "https://cdn.example.com/real.jpg")
        assertEquals("https://cdn.example.com/real.jpg", resolveImageUrl(el))
    }

    @Test
    fun resolveImageUrl_svgPlaceholderNoDataSrc_fallsBackToSrcset() {
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg/%3E",
            srcset = "https://cdn.example.com/img-512.jpg 512w,https://cdn.example.com/img-1024.jpg 1024w"
        )
        assertEquals("https://cdn.example.com/img-1024.jpg", resolveImageUrl(el))
    }

    @Test
    fun resolveImageUrl_svgPlaceholderNoDataSrc_fallsBackToDataSrcset() {
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg/%3E",
            dataSrcset = "https://cdn.example.com/img-512.jpg 512w,https://cdn.example.com/img-1024.jpg 1024w"
        )
        assertEquals("https://cdn.example.com/img-1024.jpg", resolveImageUrl(el))
    }

    @Test
    fun resolveImageUrl_noUsableUrl_returnsNull() {
        val el = imgElement(src = "data:image/svg+xml;utf8,%3Csvg/%3E")
        assertNull(resolveImageUrl(el))
    }

    @Test
    fun resolveImageUrl_dataSrcPreferredOverSrcset() {
        // data-src is more direct than parsing srcset; it should win
        val el = imgElement(
            src = "data:image/svg+xml;utf8,%3Csvg/%3E",
            dataSrc = "https://cdn.example.com/from-datasrc.jpg",
            srcset = "https://cdn.example.com/from-srcset.jpg 1024w"
        )
        assertEquals("https://cdn.example.com/from-datasrc.jpg", resolveImageUrl(el))
    }

    // --- Integration: sanitize then resolve ---

    @Test
    fun sanitizeAndResolve_lazyLoadedImg_picksDataSrc() {
        val html = """<img src="data:image/svg+xml;utf8,%3Csvg/%3E" data-src="https://cdn.example.com/photo.jpg" alt="Photo">"""
        val sanitized = com.karakept.app.utils.HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)
        val img = doc.selectFirst("img")!!
        assertEquals("https://cdn.example.com/photo.jpg", resolveImageUrl(img))
    }

    @Test
    fun sanitizeAndResolve_pictureWithSourceDataSrcset_picksSourceUrl() {
        val html = """<picture><source data-srcset="https://cdn.example.com/photo.webp 1024w" type="image/webp"><img src="data:image/svg+xml;utf8,%3Csvg/%3E" data-src="https://cdn.example.com/photo.jpg" alt="Photo"></picture>"""
        val sanitized = com.karakept.app.utils.HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)
        val source = doc.selectFirst("source")!!
        // Source's data-srcset should yield a real URL
        val srcset = source.attr("srcset").ifBlank { source.attr("data-srcset") }
        assertEquals("https://cdn.example.com/photo.webp", pickBestUrlFromSrcset(srcset))
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
        assertNull(extractImageDimensions(img("<img src='https://x'>")))
    }

    @Test
    fun extractImageDimensions_onlyWidth_returnsNull() {
        assertNull(extractImageDimensions(img("<img src='https://x' width='200'>")))
    }

    @Test
    fun extractImageDimensions_onlyHeight_returnsNull() {
        assertNull(extractImageDimensions(img("<img src='https://x' height='100'>")))
    }

    @Test
    fun extractImageDimensions_nonNumericValues_returnsNull() {
        assertNull(extractImageDimensions(img("<img src='https://x' width='100%' height='auto'>")))
    }

    @Test
    fun extractImageDimensions_zeroValues_returnsNull() {
        assertNull(extractImageDimensions(img("<img src='https://x' width='0' height='0'>")))
    }

    @Test
    fun extractImageDimensions_negativeValues_returnsNull() {
        assertNull(extractImageDimensions(img("<img src='https://x' width='-50' height='100'>")))
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
}
