package com.karakept.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlPositionTest {

    @Test
    fun `fromString BELOW_TITLE returns BELOW_TITLE`() {
        assertEquals(UrlPosition.BELOW_TITLE, UrlPosition.fromString("BELOW_TITLE"))
    }

    @Test
    fun `fromString METADATA_ROW returns METADATA_ROW`() {
        assertEquals(UrlPosition.METADATA_ROW, UrlPosition.fromString("METADATA_ROW"))
    }

    @Test
    fun `fromString invalid value returns BELOW_TITLE default`() {
        assertEquals(UrlPosition.BELOW_TITLE, UrlPosition.fromString("INVALID"))
    }
}
