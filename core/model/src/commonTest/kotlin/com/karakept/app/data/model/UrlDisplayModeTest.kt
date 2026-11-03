package com.karakept.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlDisplayModeTest {

    @Test
    fun `fromString DOMAIN_ONLY returns DOMAIN_ONLY`() {
        assertEquals(UrlDisplayMode.DOMAIN_ONLY, UrlDisplayMode.fromString("DOMAIN_ONLY"))
    }

    @Test
    fun `fromString FULL_URL returns FULL_URL`() {
        assertEquals(UrlDisplayMode.FULL_URL, UrlDisplayMode.fromString("FULL_URL"))
    }

    @Test
    fun `fromString invalid value returns DOMAIN_ONLY default`() {
        assertEquals(UrlDisplayMode.DOMAIN_ONLY, UrlDisplayMode.fromString("INVALID"))
    }
}
