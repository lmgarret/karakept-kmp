package com.karakept.app.ui.components.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveInjectionTest {

    private fun inject(html: String) = injectArchiveScripts(html, "mark{}", "/*app*/", "abc123")

    @Test
    fun `CSP is the first child of head`() {
        val result = inject("""<html><head lang="en"><style>p{}</style></head><body><p>x</p></body></html>""")
        assertTrue(result.contains("""<head lang="en"><meta http-equiv="Content-Security-Policy""""))
    }

    @Test
    fun `script-src admits only the nonce, never unsafe-inline`() {
        val result = inject("<html><head></head><body></body></html>")
        assertTrue(result.contains("script-src 'nonce-abc123';"))
        assertFalse(result.substringAfter("script-src").substringBefore(";").contains("unsafe-inline"))
        assertTrue(result.contains("""<script nonce="abc123">/*app*/</script></body>"""))
    }

    @Test
    fun `header element is not mistaken for head`() {
        val result = inject("<html><head></head><body><header>h</header></body></html>")
        assertTrue(result.contains("<head><meta http-equiv"))
        assertTrue(result.contains("<header>h</header>"))
    }

    @Test
    fun `document without head still gets the CSP first`() {
        val result = inject("<p>x</p>")
        assertTrue(result.startsWith("<head><meta http-equiv=\"Content-Security-Policy\""))
    }
}
