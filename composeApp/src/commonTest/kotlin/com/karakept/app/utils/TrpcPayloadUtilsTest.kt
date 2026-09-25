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

    @Test
    fun getReadingProgressBatchIndexesEveryBookmarkInOneEnvelope() {
        // tRPC names a batch's entries by index, and answers positionally, so the order the
        // ids go in is the order the results come back.
        val payload = TrpcPayloadUtils.getReadingProgressBatch(listOf("abc", "def", "ghi"))

        assertEquals(
            """{"0":{"json":{"bookmarkId":"abc"}},"1":{"json":{"bookmarkId":"def"}},""" +
                """"2":{"json":{"bookmarkId":"ghi"}}}""",
            payload
        )
    }

    @Test
    fun getReadingProgressBatchOfOneMatchesTheSingleForm() {
        assertEquals(
            TrpcPayloadUtils.getReadingProgress("abc"),
            TrpcPayloadUtils.getReadingProgressBatch(listOf("abc"))
        )
    }

    @Test
    fun batchPathRepeatsTheProcedureOncePerEntry() {
        assertEquals(
            "bookmarks.getReadingProgress,bookmarks.getReadingProgress",
            TrpcPayloadUtils.batchPath("bookmarks.getReadingProgress", 2)
        )
    }

    @Test
    fun adminRetagBookmarkCarriesOnlyTheBookmarkId() {
        assertEquals(
            """{"0":{"json":{"bookmarkId":"abc123"}}}""",
            TrpcPayloadUtils.adminRetagBookmark("abc123")
        )
    }

    @Test
    fun adminRetagBookmarkIdIsJsonEscaped() {
        assertEquals(
            """{"0":{"json":{"bookmarkId":"a\"b"}}}""",
            TrpcPayloadUtils.adminRetagBookmark("a\"b")
        )
    }

    @Test
    fun createApiKeyCarriesOnlyTheName() {
        assertEquals(
            """{"0":{"json":{"name":"Karakept"}}}""",
            TrpcPayloadUtils.createApiKey("Karakept")
        )
    }
}
