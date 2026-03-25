package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [FaviconUtils].
 */
class FaviconUtilsTest {

    @Test
    fun getFaviconUrl_validHttpsUrl_returnsDuckDuckGoUrl() {
        val result = FaviconUtils.getFaviconUrl("https://example.com/page")
        assertEquals("https://icons.duckduckgo.com/ip3/example.com.ico", result)
    }

    @Test
    fun getFaviconUrl_validHttpUrl_extractsDomain() {
        val result = FaviconUtils.getFaviconUrl("http://blog.example.org/post/123")
        assertTrue(result.contains("blog.example.org"),
            "Should extract full domain including subdomain, got: $result")
    }

    @Test
    fun getFaviconUrl_urlWithPort_extractsDomain() {
        val result = FaviconUtils.getFaviconUrl("https://example.com:8080/page")
        assertTrue(result.contains("example.com"),
            "Should extract domain from URL with port, got: $result")
    }

    @Test
    fun getFaviconUrl_anyString_alwaysReturnsDuckDuckGoUrl() {
        // Ktor's Url parser is very permissive -- it interprets most strings
        // as relative URLs with host=localhost. The function only returns ""
        // if Url() throws, which is rare. Verify it always returns a valid DDG URL.
        val result = FaviconUtils.getFaviconUrl("not-a-url")
        assertTrue(result.startsWith("https://icons.duckduckgo.com/ip3/"),
            "Even unusual inputs produce a DDG favicon URL due to ktor's permissive parser, got: $result")
    }
}
