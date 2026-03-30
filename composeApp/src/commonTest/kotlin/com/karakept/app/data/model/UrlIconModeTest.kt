package com.karakept.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlIconModeTest {

    @Test
    fun `fromString GLOBE_ONLY returns GLOBE_ONLY`() {
        assertEquals(UrlIconMode.GLOBE_ONLY, UrlIconMode.fromString("GLOBE_ONLY"))
    }

    @Test
    fun `fromString FAVICON returns FAVICON`() {
        assertEquals(UrlIconMode.FAVICON, UrlIconMode.fromString("FAVICON"))
    }

    @Test
    fun `fromString invalid value returns GLOBE_ONLY default`() {
        assertEquals(UrlIconMode.GLOBE_ONLY, UrlIconMode.fromString("UNKNOWN"))
    }
}
