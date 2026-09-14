package com.karakept.app.domain

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ListSettings
import com.karakept.api.model.KarakeepList

/**
 * How many bookmarks each list holds, for the counts beside the drawer's list names.
 */
object ListCountUtils {

    /**
     * Counts [bookmarks] into each of [lists], honouring that list's [ListSettings].
     *
     * Reads the rows **once**, not once per list. The straightforward nesting — for every list,
     * walk every bookmark and split its `listIds` to see whether it belongs — re-splits the same
     * column as many times as there are lists: sixteen lists over four thousand bookmarks is
     * ~69,000 splits and a quarter of a million allocations to produce sixteen numbers, which
     * measured at 25-57ms and ran on every database write. Inverted, each row is split once and
     * dropped into the buckets it names.
     *
     * A list configured with `includeChildListBookmarks` counts its descendants' bookmarks too,
     * and a bookmark filed under two of them counts once — hence the union rather than a sum.
     */
    fun countBookmarksPerList(
        lists: List<KarakeepList>,
        bookmarks: List<BookmarkEntity>,
        settings: Map<String, ListSettings>
    ): Map<String, Int> {
        if (lists.isEmpty()) return emptyMap()

        val membersByList = HashMap<String, MutableList<BookmarkEntity>>()
        bookmarks.forEach { bookmark ->
            forEachListId(bookmark.listIds) { listId ->
                membersByList.getOrPut(listId) { mutableListOf() } += bookmark
            }
        }

        return lists.associate { list ->
            val listId = list.id ?: ""
            val listSettings = settings[listId] ?: ListSettings()
            val counted = if (listSettings.includeChildListBookmarks) {
                setOf(listId) + ListHierarchyUtils.getAllDescendantIds(listId, lists)
            } else {
                setOf(listId)
            }

            val count = if (counted.size == 1) {
                val members = membersByList[listId].orEmpty()
                if (listSettings.countOnlyUnread) members.count { !it.isRead } else members.size
            } else {
                // One bookmark filed under two counted lists is still one bookmark.
                val seen = HashSet<String>()
                counted.sumOf { id ->
                    membersByList[id].orEmpty().count { bookmark ->
                        (!listSettings.countOnlyUnread || !bookmark.isRead) && seen.add(bookmark.remoteId)
                    }
                }
            }
            listId to count
        }
    }

    /**
     * Visits each id in a comma-separated `listIds` column without building the intermediate
     * lists `split(",").map { it.trim() }.filter { … }` allocates for every row.
     */
    private inline fun forEachListId(listIds: String, action: (String) -> Unit) {
        if (listIds.isEmpty()) return
        var start = 0
        while (start <= listIds.length) {
            val comma = listIds.indexOf(',', start)
            val end = if (comma < 0) listIds.length else comma
            val id = listIds.substring(start, end).trim()
            if (id.isNotEmpty()) action(id)
            if (comma < 0) return
            start = comma + 1
        }
    }
}
