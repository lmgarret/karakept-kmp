package com.karakept.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8LengthTest {

    @Test
    fun countsBytesTheWaySqliteDoes() {
        for (text in listOf("", "abc", "héllo", "日本語", "emoji 😀 here", "ࠀ߿")) {
            assertEquals(text.encodeToByteArray().size.toLong(), utf8Length(text), text)
        }
    }
}
