package com.karakept.app.domain

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ListSettings
import com.karakept.api.model.KarakeepList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [ListCountUtils].
 *
 * The counts themselves are unremarkable; what they pin is that reading every row once produces
 * the same numbers the per-list walk did, membership parsing and all.
 */
class ListCountUtilsTest {

    private var nextId = 1L

    private fun bookmark(listIds: String, isRead: Boolean = false): BookmarkEntity {
        val id = nextId++
        return BookmarkEntity(
            localId = id,
            remoteId = "remote-$id",
            serverId = "server-1",
            url = "https://example.com/$id",
            title = "Bookmark $id",
            content = null,
            imageUrl = null,
            bannerImageAssetId = null,
            screenshotAssetId = null,
            description = null,
            createdAt = id,
            isArchived = false,
            isStarred = false,
            listIds = listIds,
            isRead = isRead
        )
    }

    private fun list(id: String, parentId: String? = null) =
        KarakeepList(id = id, name = id, icon = "", parentId = parentId)

    @Test
    fun countsDirectMembers() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(list("a"), list("b")),
            bookmarks = listOf(bookmark("a"), bookmark("a"), bookmark("b")),
            settings = emptyMap()
        )
        assertEquals(mapOf("a" to 2, "b" to 1), counts)
    }

    @Test
    fun countsAListWithNoMembersAsZero() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(list("a"), list("empty")),
            bookmarks = listOf(bookmark("a")),
            settings = emptyMap()
        )
        assertEquals(0, counts["empty"])
    }

    @Test
    fun readsMembershipOutOfACommaSeparatedColumn() {
        // Spacing and stray separators are what the column actually looks like in the wild.
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(list("a"), list("b"), list("c")),
            bookmarks = listOf(bookmark("a, b"), bookmark(",c,"), bookmark("")),
            settings = emptyMap()
        )
        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 1), counts)
    }

    @Test
    fun excludesNestedListsByDefault() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(list("parent"), list("child", parentId = "parent")),
            bookmarks = listOf(bookmark("parent"), bookmark("child")),
            settings = emptyMap()
        )
        assertEquals(1, counts["parent"])
    }

    @Test
    fun includesNestedListsWhenConfigured() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(
                list("parent"),
                list("child", parentId = "parent"),
                list("grandchild", parentId = "child")
            ),
            bookmarks = listOf(bookmark("parent"), bookmark("child"), bookmark("grandchild")),
            settings = mapOf("parent" to ListSettings(includeChildListBookmarks = true))
        )
        assertEquals(3, counts["parent"])
    }

    @Test
    fun countsABookmarkFiledUnderTwoCountedListsOnce() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(
                list("parent"),
                list("one", parentId = "parent"),
                list("two", parentId = "parent")
            ),
            bookmarks = listOf(bookmark("one,two")),
            settings = mapOf("parent" to ListSettings(includeChildListBookmarks = true))
        )
        assertEquals(1, counts["parent"])
    }

    @Test
    fun countsOnlyUnreadWhenConfigured() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(list("a")),
            bookmarks = listOf(bookmark("a"), bookmark("a", isRead = true)),
            settings = mapOf("a" to ListSettings(countOnlyUnread = true))
        )
        assertEquals(1, counts["a"])
    }

    @Test
    fun countsOnlyUnreadAcrossNestedLists() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(list("parent"), list("child", parentId = "parent")),
            bookmarks = listOf(
                bookmark("parent"),
                bookmark("child", isRead = true),
                bookmark("child")
            ),
            settings = mapOf(
                "parent" to ListSettings(includeChildListBookmarks = true, countOnlyUnread = true)
            )
        )
        assertEquals(2, counts["parent"])
    }

    @Test
    fun aCycleInTheHierarchyDoesNotHang() {
        val counts = ListCountUtils.countBookmarksPerList(
            lists = listOf(list("a", parentId = "b"), list("b", parentId = "a")),
            bookmarks = listOf(bookmark("a"), bookmark("b")),
            settings = mapOf("a" to ListSettings(includeChildListBookmarks = true))
        )
        assertEquals(2, counts["a"])
    }

    @Test
    fun noListsMeansNoCounts() {
        assertEquals(
            emptyMap(),
            ListCountUtils.countBookmarksPerList(emptyList(), listOf(bookmark("a")), emptyMap())
        )
    }

    /** The same numbers the per-list walk produced, on a shape with every feature at once. */
    @Test
    fun matchesTheStraightforwardPerListWalk() {
        val lists = listOf(
            list("inbox"),
            list("parent"),
            list("child", parentId = "parent"),
            list("other")
        )
        val bookmarks = listOf(
            bookmark("inbox"),
            bookmark("inbox,parent"),
            bookmark("child", isRead = true),
            bookmark("child"),
            bookmark("other,child"),
            bookmark("")
        )
        val settings = mapOf(
            "parent" to ListSettings(includeChildListBookmarks = true),
            "inbox" to ListSettings(countOnlyUnread = true)
        )

        val expected = lists.associate { list ->
            val id = list.id ?: ""
            val s = settings[id] ?: ListSettings()
            val relevant = if (s.includeChildListBookmarks) {
                setOf(id) + ListHierarchyUtils.getAllDescendantIds(id, lists)
            } else setOf(id)
            id to bookmarks.count { bookmark ->
                val ids = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                ids.any { it in relevant } && (!s.countOnlyUnread || !bookmark.isRead)
            }
        }

        assertEquals(expected, ListCountUtils.countBookmarksPerList(lists, bookmarks, settings))
    }
}
