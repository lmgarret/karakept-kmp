package com.karakept.app.ui.components.bookmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Bookmark
import kotlin.test.Test

/**
 * Offline, a row says whether it can be read. That has to come from whether a body is stored, not
 * from the reading time: evicting a copy keeps the reading time, and a row that went on showing it
 * opened onto "Content unavailable".
 */
@OptIn(ExperimentalTestApi::class)
class BookmarkRowOfflineBadgeTest {

    private fun bookmark(hasOfflineCopy: Boolean) = Bookmark(
        localId = 1,
        remoteId = "1",
        serverId = "server",
        url = "https://example.com/article",
        title = "An article",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false,
        readingTimeMinutes = 6,
        hasOfflineCopy = hasOfflineCopy
    )

    private enum class Row { LIST, CARD }

    @Composable
    private fun render(row: Row, bookmark: Bookmark, offlineMode: Boolean) {
        MaterialTheme {
            Box(modifier = Modifier.width(400.dp)) {
                when (row) {
                    Row.LIST -> BookmarkListLayout(
                        bookmark = bookmark,
                        onClick = {},
                        offlineMode = offlineMode,
                        rowWidth = 400.dp
                    )
                    Row.CARD -> BookmarkCardLayout(
                        bookmark = bookmark,
                        onClick = {},
                        offlineMode = offlineMode
                    )
                }
            }
        }
    }

    private fun assertBadges(
        row: Row,
        hasOfflineCopy: Boolean,
        offlineMode: Boolean,
        notSynced: Int,
        readingTime: Int
    ) = runComposeUiTest {
        setContent { render(row, bookmark(hasOfflineCopy), offlineMode) }
        onAllNodesWithContentDescription(NOT_SYNCED).assertCountEquals(notSynced)
        onAllNodesWithText("6mn").assertCountEquals(readingTime)
    }

    @Test
    fun `offline, an evicted row is not synced though it kept its reading time`() {
        for (row in Row.entries) {
            assertBadges(row, hasOfflineCopy = false, offlineMode = true, notSynced = 1, readingTime = 0)
        }
    }

    @Test
    fun `offline, a row with a stored copy shows its reading time`() {
        for (row in Row.entries) {
            assertBadges(row, hasOfflineCopy = true, offlineMode = true, notSynced = 0, readingTime = 1)
        }
    }

    @Test
    fun `online, the reading time shows whether or not a copy is stored`() {
        for (row in Row.entries) {
            assertBadges(row, hasOfflineCopy = false, offlineMode = false, notSynced = 0, readingTime = 1)
        }
    }

    private companion object {
        const val NOT_SYNCED = "Not synced for offline"
    }
}
