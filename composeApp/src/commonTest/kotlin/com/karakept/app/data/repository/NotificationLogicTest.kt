package com.karakept.app.data.repository

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.ListSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [findListsWithNewBookmarks] — the pure notification logic function.
 *
 * Tests the per-list notification query logic (NOTIF-02):
 * given list settings and new bookmarks, which lists should trigger a notification
 * and with what bookmark count?
 */
class NotificationLogicTest {

    private fun makeBookmarkEntity(
        localId: Long = 1L,
        remoteId: Long = 1L,
        originalRemoteId: String = "bk-1",
        serverId: String = "server1",
        listIds: String = ""
    ) = BookmarkEntity(
        localId = localId,
        remoteId = remoteId,
        originalRemoteId = originalRemoteId,
        serverId = serverId,
        title = "Test Bookmark",
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = "",
        listIds = listIds,
        isStarred = false,
        isArchived = false,
        isRead = false,
        createdAt = 1000L,
        readingTimeMinutes = 0,
        readingProgress = 0f,
        readingScrollIndex = 0,
        readingScrollOffset = 0,
        content = null
    )

    private fun makeListEntity(
        remoteId: String,
        name: String,
        serverId: String = "server1"
    ) = ListEntity(
        localId = 0L,
        remoteId = remoteId,
        serverId = serverId,
        name = name,
        icon = null,
        parentId = null,
        isPublic = false,
        description = null,
        type = "manual",
        query = null,
        updatedAt = 1000L
    )

    @Test
    fun listsWithNotifyFlag_andMatchingBookmarks_areReturned() {
        val settings = mapOf(
            "list-a" to ListSettings(notifyOnNewBookmarks = true),
            "list-b" to ListSettings(notifyOnNewBookmarks = true),
            "list-c" to ListSettings(notifyOnNewBookmarks = false)
        )
        val bookmarks = listOf(
            makeBookmarkEntity(remoteId = 1L, listIds = "list-a"),
            makeBookmarkEntity(remoteId = 2L, listIds = "list-b")
        )
        val lists = listOf(
            makeListEntity("list-a", "List A"),
            makeListEntity("list-b", "List B"),
            makeListEntity("list-c", "List C")
        )

        val result = findListsWithNewBookmarks(settings, bookmarks, lists)

        assertEquals(2, result.size)
        assertTrue(result.any { it.first == "list-a" && it.second == "List A" && it.third == 1 })
        assertTrue(result.any { it.first == "list-b" && it.second == "List B" && it.third == 1 })
    }

    @Test
    fun listsWithNotifyFlag_butNoMatchingBookmarks_returnEmpty() {
        val settings = mapOf(
            "list-a" to ListSettings(notifyOnNewBookmarks = true)
        )
        val bookmarks = listOf(
            makeBookmarkEntity(remoteId = 1L, listIds = "list-b")
        )
        val lists = listOf(
            makeListEntity("list-a", "List A"),
            makeListEntity("list-b", "List B")
        )

        val result = findListsWithNewBookmarks(settings, bookmarks, lists)

        assertTrue(result.isEmpty())
    }

    @Test
    fun listsWithoutNotifyFlag_areExcluded() {
        val settings = mapOf(
            "list-a" to ListSettings(notifyOnNewBookmarks = false)
        )
        val bookmarks = listOf(
            makeBookmarkEntity(remoteId = 1L, listIds = "list-a")
        )
        val lists = listOf(
            makeListEntity("list-a", "List A")
        )

        val result = findListsWithNewBookmarks(settings, bookmarks, lists)

        assertTrue(result.isEmpty())
    }

    @Test
    fun emptySettings_returnsEmpty() {
        val settings = emptyMap<String, ListSettings>()
        val bookmarks = listOf(
            makeBookmarkEntity(remoteId = 1L, listIds = "list-a")
        )
        val lists = listOf(
            makeListEntity("list-a", "List A")
        )

        val result = findListsWithNewBookmarks(settings, bookmarks, lists)

        assertTrue(result.isEmpty())
    }

    @Test
    fun bookmarkWithCommaSeparatedListIds_matchesCorrectList() {
        val settings = mapOf(
            "list-x" to ListSettings(notifyOnNewBookmarks = true)
        )
        val bookmarks = listOf(
            makeBookmarkEntity(remoteId = 1L, listIds = "list-other, list-x, list-another")
        )
        val lists = listOf(
            makeListEntity("list-x", "List X")
        )

        val result = findListsWithNewBookmarks(settings, bookmarks, lists)

        assertEquals(1, result.size)
        assertEquals(Triple("list-x", "List X", 1), result.first())
    }

    @Test
    fun multipleBookmarksInSameList_returnsCorrectCount() {
        val settings = mapOf(
            "list-a" to ListSettings(notifyOnNewBookmarks = true)
        )
        val bookmarks = listOf(
            makeBookmarkEntity(remoteId = 1L, listIds = "list-a"),
            makeBookmarkEntity(remoteId = 2L, listIds = "list-a"),
            makeBookmarkEntity(remoteId = 3L, listIds = "list-a")
        )
        val lists = listOf(
            makeListEntity("list-a", "List A")
        )

        val result = findListsWithNewBookmarks(settings, bookmarks, lists)

        assertEquals(1, result.size)
        assertEquals(Triple("list-a", "List A", 3), result.first())
    }

    @Test
    fun multipleListsWithDifferentCounts() {
        val settings = mapOf(
            "list-a" to ListSettings(notifyOnNewBookmarks = true),
            "list-b" to ListSettings(notifyOnNewBookmarks = true)
        )
        val bookmarks = listOf(
            makeBookmarkEntity(remoteId = 1L, listIds = "list-a"),
            makeBookmarkEntity(remoteId = 2L, listIds = "list-a"),
            makeBookmarkEntity(remoteId = 3L, listIds = "list-b")
        )
        val lists = listOf(
            makeListEntity("list-a", "List A"),
            makeListEntity("list-b", "List B")
        )

        val result = findListsWithNewBookmarks(settings, bookmarks, lists)

        assertEquals(2, result.size)
        assertTrue(result.any { it.first == "list-a" && it.third == 2 })
        assertTrue(result.any { it.first == "list-b" && it.third == 1 })
    }
}
