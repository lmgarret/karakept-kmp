package com.karakept.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DescriptionPositionTest {

    @Test
    fun `fromString BELOW_TITLE returns BELOW_TITLE`() {
        assertEquals(DescriptionPosition.BELOW_TITLE, DescriptionPosition.fromString("BELOW_TITLE"))
    }

    @Test
    fun `fromString ABOVE_METADATA returns ABOVE_METADATA`() {
        assertEquals(DescriptionPosition.ABOVE_METADATA, DescriptionPosition.fromString("ABOVE_METADATA"))
    }

    @Test
    fun `fromString invalid value returns BELOW_TITLE default`() {
        assertEquals(DescriptionPosition.BELOW_TITLE, DescriptionPosition.fromString("INVALID"))
    }
}
