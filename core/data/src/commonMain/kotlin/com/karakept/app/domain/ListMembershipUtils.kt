package com.karakept.app.domain

import com.karakept.app.data.model.Bookmark

/** How much of a selection of bookmarks is in one list. */
enum class ListMembership { ALL, SOME, NONE }

/** The lists an edit puts every selected bookmark into, and the ones it takes them all out of. */
data class ListMembershipChange(
    val added: Set<String> = emptySet(),
    val removed: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
}

/** One list gaining or losing [bookmarks] — only the ones whose membership actually changes. */
data class ListMembershipOperation(
    val listId: String,
    val bookmarks: List<Bookmark>,
    val add: Boolean
) {
    fun inverted(): ListMembershipOperation = copy(add = !add)
}

object ListMembershipUtils {

    fun parseListIds(csv: String): Set<String> =
        csv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    fun membershipOf(listId: String, bookmarksListIds: List<Set<String>>): ListMembership {
        val count = bookmarksListIds.count { listId in it }
        return when {
            bookmarksListIds.isEmpty() || count == 0 -> ListMembership.NONE
            count == bookmarksListIds.size -> ListMembership.ALL
            else -> ListMembership.SOME
        }
    }

    /**
     * The state a tap moves a list to. A list only some of the selection is in can be put back
     * to that mixed state, which leaves it untouched; any other list just flips.
     */
    fun toggled(current: ListMembership, initial: ListMembership): ListMembership = when (current) {
        ListMembership.SOME -> ListMembership.ALL
        ListMembership.ALL -> ListMembership.NONE
        ListMembership.NONE -> if (initial == ListMembership.SOME) ListMembership.SOME else ListMembership.ALL
    }

    fun changeBetween(
        initial: Map<String, ListMembership>,
        edited: Map<String, ListMembership>
    ): ListMembershipChange {
        val added = mutableSetOf<String>()
        val removed = mutableSetOf<String>()
        edited.forEach { (listId, state) ->
            val before = initial[listId] ?: ListMembership.NONE
            if (state == before) return@forEach
            when (state) {
                ListMembership.ALL -> added += listId
                ListMembership.NONE -> removed += listId
                ListMembership.SOME -> Unit
            }
        }
        return ListMembershipChange(added, removed)
    }

    /**
     * Breaks [change] down per list, keeping for each only the bookmarks it changes — so undoing
     * the result by inverting it puts back exactly the memberships that were there before.
     */
    fun operationsFor(bookmarks: List<Bookmark>, change: ListMembershipChange): List<ListMembershipOperation> {
        val idsByBookmark = bookmarks.associateWith { parseListIds(it.listIds) }
        val adds = change.added.map { listId ->
            ListMembershipOperation(listId, bookmarks.filter { listId !in idsByBookmark.getValue(it) }, add = true)
        }
        val removes = change.removed.map { listId ->
            ListMembershipOperation(listId, bookmarks.filter { listId in idsByBookmark.getValue(it) }, add = false)
        }
        return (adds + removes).filter { it.bookmarks.isNotEmpty() }
    }

    /** Snackbar text for an applied change: names the list when there is only one. */
    fun describeChange(change: ListMembershipChange, bookmarkCount: Int, listName: (String) -> String?): String {
        fun named(listId: String) = listName(listId)?.let { "'$it'" } ?: "list"
        val many = bookmarkCount > 1
        return when {
            change.added.size == 1 && change.removed.isEmpty() ->
                if (many) "Added $bookmarkCount bookmarks to ${named(change.added.first())}"
                else "Added to ${named(change.added.first())}"
            change.removed.size == 1 && change.added.isEmpty() ->
                if (many) "Removed $bookmarkCount bookmarks from ${named(change.removed.first())}"
                else "Removed from ${named(change.removed.first())}"
            else -> if (many) "Lists updated for $bookmarkCount bookmarks" else "Lists updated"
        }
    }
}
