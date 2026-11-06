package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [HtmlSanitizer].
 */
class HtmlSanitizerTest {

    @Test
    fun sanitize_nullInput_returnsEmpty() {
        assertEquals("", HtmlSanitizer.sanitize(null))
    }

    @Test
    fun sanitize_emptyInput_returnsEmpty() {
        assertEquals("", HtmlSanitizer.sanitize(""))
    }

    @Test
    fun sanitize_plainText_preservesContent() {
        val result = HtmlSanitizer.sanitize("<p>Hello world</p>")
        assertTrue(result.contains("Hello world"), "Plain text content should be preserved")
    }

    @Test
    fun sanitize_scriptTag_removesScript() {
        val result = HtmlSanitizer.sanitize("<p>Text</p><script>alert('xss')</script>")
        assertFalse(result.contains("script"), "Script tags should be removed")
        assertFalse(result.contains("alert"), "Script content should be removed")
        assertTrue(result.contains("Text"), "Safe content should remain")
    }

    @Test
    fun sanitize_onclickHandler_removesEventHandler() {
        val result = HtmlSanitizer.sanitize("<div onclick='alert()'>Click</div>")
        assertFalse(result.contains("onclick"), "Event handlers should be removed")
        assertTrue(result.contains("Click"), "Text content should remain")
    }

    @Test
    fun sanitize_safeTagsPreserved_keepsDivSpanMark() {
        val html = "<div>container</div><span>inline</span><mark>highlighted</mark>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("<div>"), "div tag should be preserved")
        assertTrue(result.contains("<span>"), "span tag should be preserved")
        assertTrue(result.contains("<mark"), "mark tag should be preserved")
    }

    @Test
    fun sanitize_imageTag_preservesSrcAlt() {
        val result = HtmlSanitizer.sanitize("<img src='https://example.com/img.jpg' alt='test'>")
        assertTrue(result.contains("src"), "img src should be preserved")
        assertTrue(result.contains("alt"), "img alt should be preserved")
    }

    @Test
    fun sanitize_removeFirstImage_removesOnlyFirst() {
        val html = "<p>Text</p><img src='https://first.jpg'><img src='https://second.jpg'>"
        val result = HtmlSanitizer.sanitize(html, removeFirstImage = true)
        assertFalse(result.contains("first.jpg"), "First image should be removed")
        assertTrue(result.contains("second.jpg"), "Second image should remain")
    }

    @Test
    fun isValidUrl_httpsUrl_returnsTrue() {
        assertTrue(HtmlSanitizer.isValidUrl("https://example.com"))
    }

    @Test
    fun isValidUrl_httpUrl_returnsTrue() {
        assertTrue(HtmlSanitizer.isValidUrl("http://example.com"))
    }

    @Test
    fun isValidUrl_dataUrl_returnsTrue() {
        assertTrue(HtmlSanitizer.isValidUrl("data:image/png;base64,abc"))
    }

    @Test
    fun isValidUrl_fileUrl_returnsTrue() {
        assertTrue(HtmlSanitizer.isValidUrl("file://path"))
    }

    @Test
    fun isValidUrl_javascriptUrl_returnsFalse() {
        assertFalse(HtmlSanitizer.isValidUrl("javascript:alert(1)"))
    }

    @Test
    fun isValidUrl_blankUrl_returnsFalse() {
        assertFalse(HtmlSanitizer.isValidUrl(""))
    }

    // --- <picture> / <source> / lazy-load attribute preservation ---

    @Test
    fun sanitize_pictureTag_isPreserved() {
        val html = "<picture><img src='https://example.com/img.jpg' alt='test'></picture>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("<picture>"), "picture tag should be preserved")
    }

    @Test
    fun sanitize_sourceTag_isPreserved() {
        val html = "<picture><source srcset='https://example.com/img.webp 1024w' type='image/webp'><img src='https://example.com/img.jpg' alt='test'></picture>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("<source"), "source tag should be preserved")
    }

    @Test
    fun sanitize_sourceSrcset_isPreserved() {
        val html = "<picture><source srcset='https://example.com/img.webp 1024w,https://example.com/img-sm.webp 512w' type='image/webp'></picture>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("srcset"), "source srcset attribute should be preserved")
        assertTrue(result.contains("example.com/img.webp"), "source srcset URL should be preserved")
    }

    @Test
    fun sanitize_sourceDataSrcset_isPreserved() {
        val html = "<picture><source data-srcset='https://example.com/img.webp 1024w' type='image/webp'></picture>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("data-srcset"), "source data-srcset should be preserved")
        assertTrue(result.contains("example.com/img.webp"), "source data-srcset URL should be preserved")
    }

    @Test
    fun sanitize_imgDataSrc_isPreserved() {
        val svgPlaceholder = "data:image/svg+xml;utf8,%3Csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20width=%271024%27%20height=%27576%27/%3E"
        val html = "<img src='$svgPlaceholder' data-src='https://example.com/real.jpg' alt='test'>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("data-src"), "img data-src should be preserved for lazy-load images")
        assertTrue(result.contains("example.com/real.jpg"), "data-src URL should be preserved")
    }

    @Test
    fun sanitize_imgDataSrcset_isPreserved() {
        val html = "<img src='https://example.com/img.jpg' data-srcset='https://example.com/img-1024.jpg 1024w,https://example.com/img-512.jpg 512w' alt='test'>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("data-srcset"), "img data-srcset should be preserved")
    }

    @Test
    fun sanitize_imgSrcset_isPreserved() {
        val html = "<img src='https://example.com/img.jpg' srcset='https://example.com/img-1024.jpg 1024w,https://example.com/img-512.jpg 512w' alt='test'>"
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("srcset"), "img srcset should be preserved")
    }

    @Test
    fun sanitize_fullPictureElement_preservesAllParts() {
        val html = """<figure><picture><source data-srcset="https://cdn.example.com/photo.webp 1024w" type="image/webp"><img src="data:image/svg+xml;utf8,%3Csvg/%3E" data-src="https://cdn.example.com/photo.jpg" alt="A photo"></picture><figcaption>Caption text</figcaption></figure>"""
        val result = HtmlSanitizer.sanitize(html)
        assertTrue(result.contains("<picture>"), "picture tag preserved")
        assertTrue(result.contains("<source"), "source tag preserved")
        assertTrue(result.contains("data-srcset"), "source data-srcset preserved")
        assertTrue(result.contains("data-src"), "img data-src preserved")
        assertTrue(result.contains("cdn.example.com/photo.jpg"), "img data-src URL preserved")
        assertTrue(result.contains("cdn.example.com/photo.webp"), "source URL preserved")
        assertTrue(result.contains("Caption text"), "figcaption text preserved")
    }
}
