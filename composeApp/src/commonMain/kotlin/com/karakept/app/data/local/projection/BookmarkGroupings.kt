package com.karakept.app.data.local.projection

/**
 * Rows collapsed into the few fields a count actually needs.
 *
 * Counting used to walk the whole table in memory, because the only thing that held every row's
 * list memberships or tags was every row. Grouping in SQL is the same walk done once, by the
 * database, over columns a few bytes wide — and what comes back is a handful of buckets rather
 * than a library.
 */
data class ListMembershipGroup(
    /** The comma-separated `listIds` this bucket's rows share. */
    val listIds: String,
    val isRead: Boolean,
    val rowCount: Int
)

/** The comma-separated `tags` a bucket's rows share, and how many there are. */
data class TagGroup(
    val tags: String,
    val rowCount: Int
)

/** The drawer's quick-filter counts, read in one pass. */
data class QuickFilterCountRow(
    val all_: Int,
    val favorites: Int,
    val archived: Int
)
