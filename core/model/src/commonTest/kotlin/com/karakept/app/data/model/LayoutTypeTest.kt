package com.karakept.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutTypeTest {

    @Test
    fun `fromString COMPACT_LIST returns LIST per migration`() {
        assertEquals(LayoutType.LIST, LayoutType.fromString("COMPACT_LIST"))
    }

    @Test
    fun `fromString LIST returns LIST`() {
        assertEquals(LayoutType.LIST, LayoutType.fromString("LIST"))
    }

    @Test
    fun `fromString CARD returns CARD`() {
        assertEquals(LayoutType.CARD, LayoutType.fromString("CARD"))
    }

    @Test
    fun `fromString unknown value returns LIST default`() {
        assertEquals(LayoutType.LIST, LayoutType.fromString("unknown"))
    }

    @Test
    fun `COMPACT_LIST enum value still exists for deserialization compatibility`() {
        // COMPACT_LIST must remain as an enum constant (marked @Deprecated)
        // so existing serialized data can be read without crashing.
        val value = LayoutType.COMPACT_LIST
        // fromString should never return it, but it must still be reachable
        assertEquals("COMPACT_LIST", value.name)
    }
}
