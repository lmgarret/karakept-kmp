package com.karakept.app.data.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [HtmlArchiveProcessor] -- verifies dangerous element stripping
 * and preservation of safe HTML elements.
 */
class HtmlArchiveProcessorTest {

    // -- Dangerous element stripping ------------------------------------------

    @Test
    fun `processForArchive strips all iframe tags`() {
        val html = """<html><body><div>Hello</div><iframe src="https://evil.com"></iframe></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("<iframe"), "iframe tags should be stripped")
        assertFalse(result.contains("</iframe>"), "iframe closing tags should be stripped")
    }

    @Test
    fun `processForArchive strips all object tags`() {
        val html = """<html><body><p>Content</p><object data="malware.swf" type="application/x-shockwave-flash"></object></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("<object"), "object tags should be stripped")
        assertFalse(result.contains("</object>"), "object closing tags should be stripped")
    }

    @Test
    fun `processForArchive strips all embed tags`() {
        val html = """<html><body><div>Safe</div><embed src="plugin.swf" type="application/x-shockwave-flash"></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("<embed"), "embed tags should be stripped")
    }

    @Test
    fun `processForArchive strips all applet tags`() {
        val html = """<html><body><div>Safe</div><applet code="Evil.class" width="300" height="300"></applet></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("<applet"), "applet tags should be stripped")
        assertFalse(result.contains("</applet>"), "applet closing tags should be stripped")
    }

    @Test
    fun `processForArchive strips all form tags and child inputs`() {
        val html = """<html><body><div>Safe</div><form action="https://phishing.com"><input type="text" name="password"><button type="submit">Submit</button></form></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("<form"), "form tags should be stripped")
        assertFalse(result.contains("</form>"), "form closing tags should be stripped")
    }

    // -- Preservation of safe elements ----------------------------------------

    @Test
    fun `processForArchive preserves structural HTML after stripping`() {
        val html = """
            <html>
            <head><style>body { color: red; }</style></head>
            <body>
                <div class="wrapper">
                    <h1>Title</h1>
                    <p>Paragraph text</p>
                    <a href="https://example.com">Link</a>
                    <img src="https://example.com/img.png" alt="image">
                    <table><tr><td>Cell</td></tr></table>
                </div>
                <iframe src="https://evil.com"></iframe>
            </body>
            </html>
        """.trimIndent()
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertTrue(result.contains("<div"), "div should be preserved")
        assertTrue(result.contains("<h1>"), "h1 should be preserved")
        assertTrue(result.contains("<p>"), "p should be preserved")
        assertTrue(result.contains("<a "), "a should be preserved")
        assertTrue(result.contains("<img "), "img should be preserved")
        assertTrue(result.contains("<table>"), "table should be preserved")
        assertTrue(result.contains("<style>"), "style should be preserved")
    }

    // -- Regression tests -----------------------------------------------------

    @Test
    fun `processForArchive still removes script tags`() {
        val html = """<html><body><div>Safe</div><script>alert('xss')</script></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("<script"), "script tags should be stripped")
        assertFalse(result.contains("alert("), "script content should be stripped")
    }

    @Test
    fun `processForArchive still removes event handlers`() {
        val html = """<html><body><div onclick="alert('xss')" onload="malicious()">Content</div></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("onclick"), "onclick handler should be stripped")
        assertFalse(result.contains("onload"), "onload handler should be stripped")
    }

    @Test
    fun `processForArchive strips every on-attribute, not only common ones`() {
        val html = """<html><body><div onanimationstart="a()" onpointerover="b()" ontoggle="c()">x</div><svg><rect onbegin="d()"/></svg></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        listOf("onanimationstart", "onpointerover", "ontoggle", "onbegin").forEach {
            assertFalse(result.contains(it), "$it should be stripped")
        }
    }

    @Test
    fun `processForArchive removes meta http-equiv and base`() {
        val html = """<html><head><meta http-equiv="refresh" content="0;url=https://evil.com"><base href="https://evil.com/"><meta charset="utf-8"></head><body>x</body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("http-equiv"), "meta http-equiv should be removed")
        assertFalse(result.contains("<base"), "base should be removed")
        assertTrue(result.contains("charset"), "plain meta should be kept")
    }

    @Test
    fun `processForArchive keeps stylesheet links and drops other links`() {
        val html = """<html><head><link rel="stylesheet" href="https://example.com/a.css"><link rel="preload" href="https://example.com/b.js"><link rel="import" href="https://evil.com/c.html"></head><body>x</body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertTrue(result.contains("a.css"), "stylesheet should be kept")
        assertFalse(result.contains("b.js"), "preload should be removed")
        assertFalse(result.contains("c.html"), "import should be removed")
    }

    @Test
    fun `processForArchive drops javascript URLs in href, xlink href and action attributes`() {
        val html = """<html><body><a href="javascript:alert(1)">a</a><svg><a xlink:href="javascript:alert(2)"><text>t</text></a></svg><button formaction="javascript:alert(3)">b</button></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("javascript:"), "javascript: URLs should be removed")
    }

    @Test
    fun `processForArchive removes SVG animation that can rewrite attributes`() {
        val html = """<html><body><svg><a><animate attributeName="href" to="javascript:alert(1)"/><set attributeName="href" to="javascript:alert(2)"/><text>t</text></a></svg></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertFalse(result.contains("<animate"), "animate should be removed")
        assertFalse(result.contains("<set"), "set should be removed")
    }

    @Test
    fun `processForArchive keeps in-page anchors and data images`() {
        val html = """<html><body><a href="#top">top</a><img src="data:image/png;base64,AAAA"></body></html>"""
        val result = HtmlArchiveProcessor.processForArchive(html)
        assertTrue(result.contains("href=\"#top\""))
        assertTrue(result.contains("data:image/png;base64,AAAA"))
    }

    @Test
    fun `processForArchive returns empty string for null input`() {
        assertEquals("", HtmlArchiveProcessor.processForArchive(null))
    }

    @Test
    fun `processForArchive returns empty string for blank input`() {
        assertEquals("", HtmlArchiveProcessor.processForArchive(""))
        assertEquals("", HtmlArchiveProcessor.processForArchive("   "))
    }
}
