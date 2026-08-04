package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class TrpcPayloadUtilsTest {

    @Test
    fun recrawlBookmarkPlainRefreshSendsBothFlagsFalse() {
        val payload = TrpcPayloadUtils.recrawlBookmark("abc123", archiveFullPage = false, storePdf = false)

        assertEquals(
            """{"0":{"json":{"bookmarkId":"abc123","archiveFullPage":false,"storePdf":false}}}""",
            payload
        )
    }

    @Test
    fun recrawlBookmarkArchiveVariantSetsArchiveFullPage() {
        val payload = TrpcPayloadUtils.recrawlBookmark("abc123", archiveFullPage = true, storePdf = false)

        assertEquals(
            """{"0":{"json":{"bookmarkId":"abc123","archiveFullPage":true,"storePdf":false}}}""",
            payload
        )
    }

    @Test
    fun recrawlBookmarkPdfVariantSetsStorePdf() {
        val payload = TrpcPayloadUtils.recrawlBookmark("abc123", archiveFullPage = false, storePdf = true)

        assertEquals(
            """{"0":{"json":{"bookmarkId":"abc123","archiveFullPage":false,"storePdf":true}}}""",
            payload
        )
    }

    @Test
    fun bookmarkIdIsJsonEscaped() {
        // Karakeep ids are opaque strings; a quote in one must not break out of the envelope.
        val payload = TrpcPayloadUtils.recrawlBookmark("a\"b", archiveFullPage = false, storePdf = false)

        assertEquals(
            """{"0":{"json":{"bookmarkId":"a\"b","archiveFullPage":false,"storePdf":false}}}""",
            payload
        )
    }

    @Test
    fun updateReadingProgressKeepsTheShapeTheServerExpects() {
        val payload = TrpcPayloadUtils.updateReadingProgress("xyz", 42)

        assertEquals(
            """{"0":{"json":{"bookmarkId":"xyz","readingProgressOffset":0,""" +
                """"readingProgressAnchor":null,"readingProgressPercent":42}}}""",
            payload
        )
    }

    @Test
    fun getReadingProgressWrapsOnlyTheBookmarkId() {
        assertEquals(
            """{"0":{"json":{"bookmarkId":"xyz"}}}""",
            TrpcPayloadUtils.getReadingProgress("xyz")
        )
    }
}
