package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.utils.HtmlSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for figure/image rendering where `<a>` wraps `<picture>` or `<img>`.
 *
 * Many sites (e.g. Numerama) use:
 *   <figure><a href="...lightbox..."><picture><source ...><img ...></picture></a></figure>
 *
 * `<a>` is not in BLOCK_TAGS but is transparent per the HTML5 spec — when it wraps block
 * content it must be treated as a block, otherwise the entire picture is routed through
 * appendInlineElement which contributes 0 chars and the image silently disappears.
 */
class HtmlFigureRenderingTest {

    // --- isBlockElement: <a> transparency ---

    @Test
    fun isBlockElement_plainAnchor_returnsFalse() {
        val doc = Ksoup.parse("<a href='https://example.com'>click me</a>")
        val a = doc.selectFirst("a")!!
        assertFalse(isBlockElement(a), "<a> with only text children should be inline")
    }

    @Test
    fun isBlockElement_anchorWrappingPicture_returnsTrue() {
        val doc = Ksoup.parse("<a href='https://example.com'><picture><img src='https://example.com/img.jpg'></picture></a>")
        val a = doc.selectFirst("a")!!
        assertTrue(isBlockElement(a), "<a> wrapping <picture> should be treated as block")
    }

    @Test
    fun isBlockElement_anchorWrappingImg_returnsTrue() {
        val doc = Ksoup.parse("<a href='https://example.com'><img src='https://example.com/img.jpg'></a>")
        val a = doc.selectFirst("a")!!
        assertTrue(isBlockElement(a), "<a> wrapping <img> should be treated as block")
    }

    @Test
    fun isBlockElement_anchorWrappingDiv_returnsTrue() {
        val doc = Ksoup.parse("<a href='https://example.com'><div>content</div></a>")
        val a = doc.selectFirst("a")!!
        assertTrue(isBlockElement(a), "<a> wrapping <div> should be treated as block")
    }

    // --- Full pipeline: sanitize → parse → find image URL ---

    @Test
    fun figureWithAnchorWrappingPicture_realSrc_urlReachable() {
        val html = """<figure><a href="https://example.com/full.jpg"><picture><source srcset="https://cdn.example.com/img.webp 1024w" type="image/webp"><img src="https://cdn.example.com/img.jpg" alt="A photo"></picture></a><figcaption>Caption</figcaption></figure>"""
        val sanitized = HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)

        val img = doc.selectFirst("img")
        assertNotNull(img, "img should survive sanitization")
        assertEquals("https://cdn.example.com/img.jpg", resolveImageUrls(img).firstOrNull())
    }

    @Test
    fun figureWithAnchorWrappingPicture_lazyLoadSrc_resolvesDataSrc() {
        val svgPlaceholder = "data:image/svg+xml;utf8,%3Csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20width=%271024%27%20height=%27576%27/%3E"
        val html = """<figure><a href="https://example.com/full.jpg"><picture><source data-srcset="https://cdn.example.com/img.webp 1024w" type="image/webp"><img src="$svgPlaceholder" data-src="https://cdn.example.com/img.jpg" alt="A photo"></picture></a><figcaption>Caption</figcaption></figure>"""
        val sanitized = HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)

        // <a> wrapping <picture> must be classified as a block element
        val a = doc.selectFirst("a")
        assertNotNull(a, "<a> should survive sanitization")
        assertTrue(isBlockElement(a), "<a> wrapping <picture> should be a block after sanitization")

        // Image URL should resolve via data-src
        val img = doc.selectFirst("img")
        assertNotNull(img, "img should survive sanitization")
        assertEquals("https://cdn.example.com/img.jpg", resolveImageUrls(img).firstOrNull())
    }

    @Test
    fun figureWithAnchorWrappingPicture_lazyLoadSourceDataSrcset_resolves() {
        val svgPlaceholder = "data:image/svg+xml;utf8,%3Csvg/%3E"
        val html = """<figure><a href="https://example.com/full.jpg"><picture><source data-srcset="https://cdn.example.com/img-1024.jpg 1024w,https://cdn.example.com/img-2048.jpg 2048w"><img src="$svgPlaceholder" alt="A photo"></picture></a></figure>"""
        val sanitized = HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)

        val source = doc.selectFirst("source")
        assertNotNull(source, "source should survive sanitization")
        val srcset = source.attr("srcset").ifBlank { source.attr("data-srcset") }
        assertEquals("https://cdn.example.com/img-2048.jpg", pickUrlsFromSrcset(srcset).last())
    }

    @Test
    fun numeramaStyleFigure_fullPipeline_imageUrlResolvable() {
        // Mirrors the exact HTML pattern from the bug report
        val html = """
            <figure>
              <a href="https://www.numerama.com/wp-content/uploads/2026/04/ufo-cacther.jpg">
                <picture>
                  <source data-srcset="https://c0.lestechnophiles.com/www.numerama.com/wp-content/uploads/2026/04/ufo-cacther-1024x576.jpg?webp=1&amp;key=abc 1024w,https://c0.lestechnophiles.com/www.numerama.com/wp-content/uploads/2026/04/ufo-cacther.jpg?webp=1&amp;key=def 2048w" type="image/webp">
                  <img src="data:image/svg+xml;utf8,%3Csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20width=%271024%27%20height=%27576%27/%3E"
                       data-src="https://c0.lestechnophiles.com/www.numerama.com/wp-content/uploads/2026/04/ufo-cacther-1024x576.jpg?resize=1024,576&amp;key=abc"
                       alt="Des UFO Catchers au Japon // Source : Taito">
                </picture>
              </a>
              <figcaption>Des UFO Catchers au Japon. // Source : Taito</figcaption>
            </figure>
        """.trimIndent()

        val sanitized = HtmlSanitizer.sanitize(html)
        val doc = Ksoup.parse(sanitized)

        // <a> containing <picture> must be a block
        val a = doc.selectFirst("figure > a")
        assertNotNull(a, "<a> should be a direct child of <figure> after sanitization")
        assertTrue(isBlockElement(a), "<a> wrapping <picture> should be block")

        // Source data-srcset should be preserved and yield a URL
        val source = doc.selectFirst("source")
        assertNotNull(source, "<source> should survive sanitization")
        val srcset = source.attr("srcset").ifBlank { source.attr("data-srcset") }
        val sourceUrl = pickUrlsFromSrcset(srcset).firstOrNull()
        assertNotNull(sourceUrl, "Should extract a URL from source srcset/data-srcset")
        assertTrue(sourceUrl.startsWith("https://"), "Extracted URL should be https")

        // img data-src should also be preserved as fallback
        val img = doc.selectFirst("img")
        assertNotNull(img, "img should survive sanitization")
        val imgUrl = resolveImageUrls(img).firstOrNull()
        assertNotNull(imgUrl, "resolveImageUrls should find a URL in data-src")
        assertTrue(imgUrl.startsWith("https://"), "Resolved URL should be https")

        // figcaption should be intact
        assertTrue(sanitized.contains("Des UFO Catchers"), "figcaption text should be preserved")
    }
}
