package com.karakept.app.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlUtilsTest {

    @Test
    fun `extractDomain strips scheme and www prefix`() {
        assertEquals("github.com", extractDomain("https://www.github.com/repo"))
    }

    @Test
    fun `extractDomain strips scheme without www`() {
        assertEquals("github.com", extractDomain("https://github.com/repo"))
    }

    @Test
    fun `extractDomain strips port from host`() {
        assertEquals("example.com", extractDomain("http://example.com:8080/path"))
    }

    @Test
    fun `extractDomain returns input as fallback for non-url`() {
        assertEquals("not-a-url", extractDomain("not-a-url"))
    }

    @Test
    fun `extractDomain returns empty string for empty input`() {
        assertEquals("", extractDomain(""))
    }

    @Test
    fun `extractDomain preserves subdomain without stripping www`() {
        assertEquals("sub.domain.co.uk", extractDomain("https://sub.domain.co.uk/page"))
    }
}
