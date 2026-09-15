package com.karakept.app.domain

import com.karakept.app.data.local.projection.ListMembershipGroup
import com.karakept.app.data.model.ListSettings
import com.karakept.api.model.KarakeepList

/**
 * How many bookmarks each list holds, for the counts beside the drawer's list names.
 */
object ListCountUtils {

    /**
     * Counts [groups] into each of [lists], honouring that list's [ListSettings].
     *
     * [groups] are rows already collapsed by the database on `(listIds, isRead)`, which is the
     * only thing a count needs from them. Most of a library shares very few distinct
     * combinations, so this walks buckets rather than bookmarks — and the rows themselves never
     * have to be in memory, which is what they used to be here for.
     *
     * A list configured with `includeChildListBookmarks` counts its descendants' bookmarks too,
     * and a bookmark filed under two of them counts once — hence the union rather than a sum.
     * Grouping makes that exact rather than careful: a bucket belonging to both is one bucket.
     */
    fun countBookmarksPerList(
        lists: List<KarakeepList>,
        groups: List<ListMembershipGroup>,
        settings: Map<String, ListSettings>
    ): Map<String, Int> {
        if (lists.isEmpty()) return emptyMap()

        // Each bucket is split once, not once per list, and carries how many rows it stands for.
        val membership = groups.map { group ->
            val ids = HashSet<String>()
            forEachListId(group.listIds) { ids += it }
            Membership(ids, group.isRead, group.rowCount)
        }

        return lists.associate { list ->
            val listId = list.id ?: ""
            val listSettings = settings[listId] ?: ListSettings()
            val counted = if (listSettings.includeChildListBookmarks) {
                setOf(listId) + ListHierarchyUtils.getAllDescendantIds(listId, lists)
            } else {
                setOf(listId)
            }

            // A bucket is counted once however many of the counted lists it belongs to — the
            // rows in it are the same rows.
            val count = membership.sumOf { bucket ->
                if ((!listSettings.countOnlyUnread || !bucket.isRead) &&
                    bucket.listIds.any { it in counted }
                ) bucket.rowCount else 0
            }
            listId to count
        }
    }

    private class Membership(
        val listIds: Set<String>,
        val isRead: Boolean,
        val rowCount: Int
    )

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
