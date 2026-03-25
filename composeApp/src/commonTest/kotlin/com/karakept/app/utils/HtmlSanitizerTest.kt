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
}
