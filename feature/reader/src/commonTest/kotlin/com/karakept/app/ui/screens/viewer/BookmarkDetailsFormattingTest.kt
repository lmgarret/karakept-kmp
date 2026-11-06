package com.karakept.app.ui.screens.viewer

import com.karakept.app.data.model.BookmarkAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookmarkDetailsFormattingTest {

    @Test
    fun `crawl summary maps known statuses`() {
        assertEquals("Success", crawlSummary("success", null))
        assertEquals("Failed", crawlSummary("failure", null))
        assertEquals("Pending", crawlSummary("pending", null))
    }

    @Test
    fun `crawl summary capitalises an unknown status`() {
        assertEquals("Queued", crawlSummary("queued", null))
    }

    @Test
    fun `crawl summary with nothing known says unknown`() {
        assertEquals("Unknown", crawlSummary(null, null))
    }

    @Test
    fun `crawl summary joins status and time`() {
        val summary = crawlSummary("failure", 0L)
        assertTrue(summary.startsWith("Failed · "), summary)
        assertTrue(summary.length > "Failed · ".length, summary)
    }

    @Test
    fun `server asset label names each asset type`() {
        assertEquals("The full page archive", serverAssetLabel("fullPageArchive"))
        assertEquals("The crawled archive", serverAssetLabel("precrawledArchive"))
        assertEquals("The PDF", serverAssetLabel("pdf"))
        assertEquals("The banner image", serverAssetLabel("bannerImage"))
        assertEquals("The screenshot", serverAssetLabel("screenshot"))
        assertEquals("This asset", serverAssetLabel("linkHtmlContent"))
    }

    @Test
    fun `epoch millis format as a zero padded date`() {
        val formatted = formatEpochMillis(1_709_640_000_000L)
        assertTrue(Regex("""2024-03-0[456]""").matches(formatted), formatted)
    }

    @Test
    fun `download progress distinguishes absent from unknown size`() {
        val asset = BookmarkAsset(
            id = "a1",
            bookmarkRemoteId = "b1",
            serverId = "s1",
            assetType = "pdf",
            fileName = null,
            contentType = null,
            localPath = null
        )
        assertNull(downloadProgressFor(emptyMap(), asset))
        assertEquals(DownloadProgress(null), downloadProgressFor(mapOf("a1" to null), asset))
        assertEquals(DownloadProgress(0.5f), downloadProgressFor(mapOf("a1" to 0.5f), asset))
    }
}
