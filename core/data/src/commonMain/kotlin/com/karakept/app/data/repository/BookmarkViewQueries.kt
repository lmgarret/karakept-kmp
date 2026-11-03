package com.karakept.app.data.repository

import androidx.room3.RoomRawQuery
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.OFFLINE_PREDICATE
import com.karakept.app.data.local.projection.ListMembershipGroup
import com.karakept.app.data.local.projection.QuickFilterCountRow
import com.karakept.app.data.local.projection.TagGroup
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.ContentFilter
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SortOption
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.Flow

internal class BookmarkViewQueries(private val bookmarkDao: BookmarkDao) {
    /**
     * Reads the [limit] rows that follow [after] in the sort order, or the first [limit] rows
     * when it is null.
     *
     * Paging by cursor rather than by OFFSET is what keeps a walk whole while a sync commits
     * underneath it — see [BookmarkCursor].
     */
    suspend fun getBookmarksPaged(
        server: Server,
        status: FilterStatus,
        limit: Int,
        after: BookmarkCursor? = null,
        sort: SortOption = SortOption.NEWEST,
        listId: String? = null
    ): List<BookmarkEntity> {
        val query = buildPagedQuery(
            server.id,
            FilterConfig(status = status, sort = sort, lists = listOfNotNull(listId)),
            limit,
            after
        )
        val result = bookmarkDao.getBookmarksPaged(query)
        AppLogger.d("BookmarkRepository", "getBookmarksPaged: status=$status, sort=$sort, listId=$listId, limit=$limit, after=${after?.localId} -> returned ${result.size} bookmarks")
        return result
    }

    /**
     * How many rows [filter] admits, asked of the database rather than counted in memory.
     *
     * This is the list's total: the number of slots it renders, whether or not their rows have
     * been read.
     */
    suspend fun countBookmarksForView(server: Server, filter: FilterConfig): Int =
        bookmarkDao.countBookmarks(buildCountQuery(server.id, filter))

    /** List memberships and read state, grouped — see BookmarkDao.listMembershipGroups. */
    fun listMembershipGroups(serverId: String): Flow<List<ListMembershipGroup>> =
        bookmarkDao.listMembershipGroups(serverId)

    /** Tag sets and how many rows carry each. */
    fun tagGroups(serverId: String): Flow<List<TagGroup>> = bookmarkDao.tagGroups(serverId)

    /** The drawer's quick-filter counts, in one pass over the table. */
    fun quickFilterCounts(serverId: String): Flow<QuickFilterCountRow> =
        bookmarkDao.quickFilterCounts(serverId)

    /** Unread rows of one list, for "mark all as read". */
    suspend fun getUnreadInList(serverId: String, listId: String): List<BookmarkEntity> =
        bookmarkDao.getUnreadInList(serverId, listId)

    /**
     * The single row at [index] in [filter]'s view.
     *
     * For naming the row the fast-scroll cursor points at, which is a question about one row at a
     * position — not a reason to hold the view in memory.
     */
    suspend fun getBookmarkAt(server: Server, filter: FilterConfig, index: Int): BookmarkEntity? =
        if (index < 0) null
        else bookmarkDao.getBookmarksPaged(
            buildPageQuery(server.id, filter, offset = index, limit = 1)
        ).firstOrNull()

    /**
     * Rows of [filter]'s view whose title, URL or description contain [query].
     *
     * Matched by the database rather than over a resident copy of the table. One narrowing comes
     * with that: SQLite folds case for the 26 ASCII letters only, so searching `uber` no longer
     * matches a title spelled `Über`. Matching in Kotlin folded the whole of Unicode, but it
     * could only do so with every row in memory, which is what this removes. A stored
     * case-folded column would restore it without bringing the rows back.
     */
    suspend fun searchBookmarks(
        server: Server,
        filter: FilterConfig,
        query: String,
        limit: Int = SEARCH_RESULT_LIMIT
    ): List<BookmarkEntity> {
        if (query.isBlank()) return emptyList()
        return bookmarkDao.getBookmarksPaged(
            buildSearchQuery(server.id, filter, query, limit)
        )
    }

    /**
     * How many rows of [filter]'s view sort **before** [before].
     *
     * What the "N new" pill counts: a sync snapshots where the user was as a cursor, and this
     * says how many rows landed above it. Asking the database means the answer does not depend
     * on which rows happen to be loaded — the old count walked the rows in memory looking for an
     * anchor, and once the list started dropping pages it had scrolled away from, the anchor was
     * usually not among them.
     *
     * [excludeRead] leaves out rows the user has already read, for when reading marks a row as
     * faded: the pill offers a trip to what arrived, and something already read is not that.
     */
    suspend fun countBookmarksBefore(
        server: Server,
        filter: FilterConfig,
        before: BookmarkCursor,
        excludeRead: Boolean = false
    ): Int =
        bookmarkDao.countBookmarks(buildCountBeforeQuery(server.id, filter, before, excludeRead))

    /** [countBookmarksForView], re-read whenever the table changes. */
    fun countBookmarksForViewFlow(server: Server, filter: FilterConfig): Flow<Int> =
        bookmarkDao.countBookmarksFlow(buildCountQuery(server.id, filter))

    /**
     * The [limit] rows of [filter]'s view starting at [offset] — a page named by its position.
     *
     * Addressed by offset rather than by cursor because the list addresses rows by index: it
     * renders the view's whole length and asks for the page under the viewport, which may be
     * anywhere. A cursor names a position by the row at it, which is what a walk needs and what a
     * jump does not have.
     *
     * The drift that made OFFSET unusable for the walk (#333) needed a position *accumulated* by
     * that walk: a row committed above it shifted every page below, and a forward-only walk never
     * came back for what the shift displaced. Nothing accumulates here. Each read states the
     * offset it wants, and every write re-reads the pages on screen, so a row committed above
     * them moves the rows down and the next read reports them where they now are.
     */
    /**
     * Every row [filter] admits, by identity only.
     *
     * What "select all" needs: the ids to act on, without reading the rows to get them. It used
     * to read the whole view into the list first, because the only way to have a row's id was to
     * have the row.
     */
    suspend fun getViewRemoteIds(server: Server, filter: FilterConfig): List<String> =
        bookmarkDao.selectRemoteIds(buildViewIdsQuery(server.id, filter))

    /** The rows behind [remoteIds] — for acting on a selection that outruns what is loaded. */
    suspend fun getBookmarksByRemoteIds(
        serverId: String,
        remoteIds: List<String>
    ): List<BookmarkEntity> =
        if (remoteIds.isEmpty()) emptyList()
        else remoteIds.chunked(SQLITE_MAX_BIND_ARGS).flatMap {
            bookmarkDao.getBookmarksByRemoteIds(serverId, it)
        }

    suspend fun getBookmarkPage(
        server: Server,
        filter: FilterConfig,
        offset: Int,
        limit: Int
    ): List<BookmarkEntity> =
        bookmarkDao.getBookmarksPaged(buildPageQuery(server.id, filter, offset, limit))

    /**
     * Fetches all bookmarks matching [status] and [listId] without pagination.
     * Used by selectAll() to select beyond the first page.
     */
    suspend fun getAllBookmarks(
        server: Server,
        status: com.karakept.app.data.model.FilterStatus,
        listId: String? = null
    ): List<BookmarkEntity> {
        return if (listId != null) {
            bookmarkDao.getAllBookmarksForList(server.id, listId)
        } else {
            when (status) {
                com.karakept.app.data.model.FilterStatus.ALL ->
                    bookmarkDao.getAllNotArchivedForServer(server.id)
                com.karakept.app.data.model.FilterStatus.ALL_INCLUDING_ARCHIVED ->
                    bookmarkDao.getAllBookmarksForServerSuspend(server.id)
                com.karakept.app.data.model.FilterStatus.FAVORITES ->
                    bookmarkDao.getAllFavoritesForServer(server.id)
                com.karakept.app.data.model.FilterStatus.ARCHIVED ->
                    bookmarkDao.getAllArchivedForServer(server.id)
                com.karakept.app.data.model.FilterStatus.OFFLINE ->
                    bookmarkDao.getAllOfflineForServer(server.id)
            }
        }
    }

    suspend fun getBookmarkCount(
        server: Server,
        status: com.karakept.app.data.model.FilterStatus,
        listId: String? = null
    ): Int {
        return if (listId != null) {
            // When filtering by list, use the list-specific count query
            bookmarkDao.getBookmarksForListCount(server.id, listId)
        } else {
            when (status) {
                // ALL shows non-archived bookmarks
                com.karakept.app.data.model.FilterStatus.ALL ->
                    bookmarkDao.getNotArchivedCount(server.id)
                // ALL_INCLUDING_ARCHIVED shows all bookmarks
                com.karakept.app.data.model.FilterStatus.ALL_INCLUDING_ARCHIVED ->
                    bookmarkDao.getTotalBookmarkCount(server.id)
                com.karakept.app.data.model.FilterStatus.FAVORITES ->
                    bookmarkDao.getFavoritesCount(server.id)
                com.karakept.app.data.model.FilterStatus.ARCHIVED ->
                    bookmarkDao.getArchivedCount(server.id)
                com.karakept.app.data.model.FilterStatus.OFFLINE ->
                    bookmarkDao.getOfflineCount(server.id)
            }
        }
    }

    companion object {
        /**
         * SQLite's default limit on host parameters, which a selection can exceed: `IN (?, ?, …)`
         * binds one per id, and selecting a four-thousand-row view is four thousand of them.
         */
        private const val SQLITE_MAX_BIND_ARGS = 900

        /**
         * How many search hits to read at once. Search is not paged — the results are a list the
         * user scans rather than a view they live in — so it is bounded instead.
         */
        internal const val SEARCH_RESULT_LIMIT = 500

        private const val BOOKMARK_SELECT = """localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content"""

        /**
         * Builds the paged query. Lives in the companion so a test can page a real SQLite
         * table with it rather than restating its SQL — the ORDER BY is what makes paging
         * well-defined, and a copy of it in a test proves nothing about the query the app runs.
         */
        internal fun buildPagedQuery(
            serverId: String,
            filter: FilterConfig,
            limit: Int,
            after: BookmarkCursor? = null
        ): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            val conditions = StringBuilder(where.sql)
            val binds = where.binds.toMutableList()

            // The keyset clause narrows the same view, so it is appended to its predicate rather
            // than folded into it — the view is what a count has to agree with, and "after this
            // row" is not part of the view.
            if (after != null) {
                conditions.append(" AND ").append(filter.sort.keysetPredicateSql())
                binds.addAll(filter.sort.keysetBinds(after))
            }
            binds += limit.toLong()

            val sql = "SELECT $BOOKMARK_SELECT FROM bookmarks " +
                "WHERE $conditions " +
                "ORDER BY ${filter.sort.toOrderBySql()} LIMIT ?"

            return rawQuery(sql, binds)
        }

        /**
         * The whole view as one predicate, shared by the rows and by anything counting them.
         *
         * Every clause the view is defined by lives here, including the four that used to be
         * applied in Kotlin after the read (tags, a multi-list selection, the read filter, the
         * content filter). Splitting them across the two was what made a row's position in the
         * view and its position in the query two different numbers — a read had to over-fetch and
         * guess how much the Kotlin side would discard. With the whole view in one `WHERE`, the
         * nth row the query returns is the nth row of the view, which is what lets the list ask
         * for a page by its index.
         */
        /** A `WHERE` body and the values its placeholders bind, in order. */
        internal data class SqlPredicate(val sql: String, val binds: List<Any>)

        internal fun buildViewPredicate(serverId: String, filter: FilterConfig): SqlPredicate {
            val binds = mutableListOf<Any>()
            val conditions = mutableListOf<String>()

            conditions += "serverId = ?"
            binds += serverId

            val singleListId = filter.lists.singleOrNull()
            if (singleListId != null) {
                // Membership in the comma-separated listIds column. A single-list view applies
                // no status clause, which is what it has always done.
                conditions += csvContainsSql("listIds")
                binds += singleListId
            } else {
                when (filter.status) {
                    FilterStatus.ALL -> conditions += "isArchived = 0"
                    FilterStatus.ALL_INCLUDING_ARCHIVED -> Unit
                    FilterStatus.FAVORITES -> conditions += "isStarred = 1"
                    FilterStatus.ARCHIVED -> conditions += "isArchived = 1"
                    FilterStatus.OFFLINE -> conditions += OFFLINE_PREDICATE
                }
                if (filter.lists.isNotEmpty()) {
                    // Several lists select a row that is in any of them.
                    conditions += filter.lists.joinToString(" OR ", "(", ")") {
                        csvContainsSql("listIds")
                    }
                    binds.addAll(filter.lists)
                }
            }

            if (filter.tags.isNotEmpty()) {
                conditions += filter.tags.joinToString(" OR ", "(", ")") { csvContainsSql("tags") }
                binds.addAll(filter.tags)
            }

            when (filter.readFilter) {
                ReadFilter.ALL -> Unit
                ReadFilter.UNREAD -> conditions += "isRead = 0"
                ReadFilter.READ -> conditions += "isRead = 1"
                ReadFilter.IN_PROGRESS -> conditions += "(readingProgress > 0 AND isRead = 0)"
            }

            when (filter.contentFilter) {
                ContentFilter.ALL -> Unit
                ContentFilter.DOWNLOADED -> conditions += "readingTimeMinutes > 0"
                ContentFilter.NOT_DOWNLOADED -> conditions += "readingTimeMinutes = 0"
            }

            return SqlPredicate(conditions.joinToString(" AND "), binds)
        }

        /** [filter]'s view as identities alone, in view order. */
        internal fun buildViewIdsQuery(serverId: String, filter: FilterConfig): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            return rawQuery(
                "SELECT remoteId FROM bookmarks WHERE ${where.sql} " +
                    "ORDER BY ${filter.sort.toOrderBySql()}",
                where.binds
            )
        }

        /**
         * Rows of [filter]'s view whose title, URL or description contain [query].
         *
         * Matched with `instr` on the folded values rather than `LIKE`, so a search term holding
         * `%` or `_` means those characters rather than "anything". `lower` folds the 26 ASCII
         * letters and no more — see [searchBookmarks].
         */
        internal fun buildSearchQuery(
            serverId: String,
            filter: FilterConfig,
            query: String,
            limit: Int
        ): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            val match = "(instr(lower(title), lower(?)) > 0 " +
                "OR instr(lower(url), lower(?)) > 0 " +
                "OR instr(lower(COALESCE(description, '')), lower(?)) > 0)"
            val sql = "SELECT $BOOKMARK_SELECT FROM bookmarks " +
                "WHERE ${where.sql} AND $match " +
                "ORDER BY ${filter.sort.toOrderBySql()} LIMIT ?"
            return rawQuery(sql, where.binds + query + query + query + limit.toLong())
        }

        /** The page of [filter]'s view at [offset], addressed by position. */
        internal fun buildPageQuery(
            serverId: String,
            filter: FilterConfig,
            offset: Int,
            limit: Int
        ): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            val sql = "SELECT $BOOKMARK_SELECT FROM bookmarks " +
                "WHERE ${where.sql} " +
                "ORDER BY ${filter.sort.toOrderBySql()} LIMIT ? OFFSET ?"
            return rawQuery(sql, where.binds + limit.toLong() + offset.toLong())
        }

        /** Rows of [filter]'s view sorting before [before] — see [countBookmarksBefore]. */
        internal fun buildCountBeforeQuery(
            serverId: String,
            filter: FilterConfig,
            before: BookmarkCursor,
            excludeRead: Boolean
        ): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            val conditions = StringBuilder(where.sql)
                .append(" AND ")
                .append(filter.sort.keysetBeforePredicateSql())
            if (excludeRead) conditions.append(" AND isRead = 0")
            return rawQuery(
                "SELECT COUNT(*) FROM bookmarks WHERE $conditions",
                where.binds + filter.sort.keysetBinds(before)
            )
        }

        /** How many rows [filter] admits — the view's size, without reading any of it. */
        internal fun buildCountQuery(serverId: String, filter: FilterConfig): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            return rawQuery("SELECT COUNT(*) FROM bookmarks WHERE ${where.sql}", where.binds)
        }

        /**
         * Membership of a bind value in a comma-separated [column].
         *
         * `instr` on the delimited column rather than four `LIKE` patterns: a tag is free text
         * and may hold `%` or `_`, which `LIKE` reads as wildcards, so `LIKE` would match tags
         * the user never selected. Wrapping both sides in commas makes it an exact element
         * match — `,kotlin,` is in `,kotlin,android,` and `,lin,` is not.
         */
        private fun csvContainsSql(column: String): String =
            "instr(',' || $column || ',', ',' || ? || ',') > 0"

        private fun rawQuery(sql: String, binds: List<Any>): RoomRawQuery =
            RoomRawQuery(sql) { stmt ->
                binds.forEachIndexed { index, value ->
                    when (value) {
                        is Long -> stmt.bindLong(index + 1, value)
                        is String -> stmt.bindText(index + 1, value)
                        else -> error("unbindable value $value")
                    }
                }
            }

        /**
         * "Strictly after the cursor" in this sort's own order — the same comparison
         * [toOrderBySql] sorts by, so the two cannot disagree about which rows are still to
         * come. The tie-break on `localId` is what makes it strict: without it a page boundary
         * falling inside a run of equal keys either repeats that run or skips it.
         *
         * The title comparison carries `COLLATE NOCASE` for the same reason — a predicate
         * comparing case-sensitively against a case-insensitive ORDER BY drops rows.
         */
        private fun SortOption.keysetPredicateSql(): String = when (this) {
            SortOption.NEWEST ->
                "(createdAt < ? OR (createdAt = ? AND localId < ?))"
            SortOption.OLDEST ->
                "(createdAt > ? OR (createdAt = ? AND localId > ?))"
            SortOption.TITLE_AZ ->
                "(title COLLATE NOCASE > ? OR (title COLLATE NOCASE = ? AND localId > ?))"
            SortOption.TITLE_ZA ->
                "(title COLLATE NOCASE < ? OR (title COLLATE NOCASE = ? AND localId < ?))"
            SortOption.READING_TIME_SHORT ->
                "(readingTimeMinutes > ? OR (readingTimeMinutes = ? AND localId > ?))"
            SortOption.READING_TIME_LONG ->
                "(readingTimeMinutes < ? OR (readingTimeMinutes = ? AND localId < ?))"
        }

        /**
         * "Strictly before the cursor" — [keysetPredicateSql] with every comparison mirrored.
         *
         * The two are inverses, so a row is before the cursor, after it, or is it. Written as the
         * mirror rather than as `NOT`, because `NOT` would also admit the cursor row itself.
         */
        private fun SortOption.keysetBeforePredicateSql(): String = when (this) {
            SortOption.NEWEST ->
                "(createdAt > ? OR (createdAt = ? AND localId > ?))"
            SortOption.OLDEST ->
                "(createdAt < ? OR (createdAt = ? AND localId < ?))"
            SortOption.TITLE_AZ ->
                "(title COLLATE NOCASE < ? OR (title COLLATE NOCASE = ? AND localId < ?))"
            SortOption.TITLE_ZA ->
                "(title COLLATE NOCASE > ? OR (title COLLATE NOCASE = ? AND localId > ?))"
            SortOption.READING_TIME_SHORT ->
                "(readingTimeMinutes < ? OR (readingTimeMinutes = ? AND localId < ?))"
            SortOption.READING_TIME_LONG ->
                "(readingTimeMinutes > ? OR (readingTimeMinutes = ? AND localId > ?))"
        }

        /** The three values [keysetPredicateSql] binds, in order. */
        private fun SortOption.keysetBinds(after: BookmarkCursor): List<Any> {
            val key: Any = when (this) {
                SortOption.NEWEST, SortOption.OLDEST -> after.createdAt
                SortOption.TITLE_AZ, SortOption.TITLE_ZA -> after.title
                SortOption.READING_TIME_SHORT, SortOption.READING_TIME_LONG ->
                    after.readingTimeMinutes.toLong()
            }
            return listOf(key, key, after.localId)
        }

        /**
         * Every sort ends on `localId`, which is unique, so the row order is total.
         *
         * Without it rows sharing a sort key are placed relative to one another by nothing:
         * two LIMIT/OFFSET reads of the same table are free to disagree about where a tied
         * row sits, and a row the two reads disagree about lands on both sides of a page
         * boundary or on neither. Ties are the norm — a feed imports a batch of bookmarks
         * carrying one timestamp, and `readingTimeMinutes` is 0 across most of a library.
         */
        private fun SortOption.toOrderBySql(): String = when (this) {
            SortOption.NEWEST             -> "createdAt DESC, localId DESC"
            SortOption.OLDEST             -> "createdAt ASC, localId ASC"
            SortOption.TITLE_AZ           -> "title COLLATE NOCASE ASC, localId ASC"
            SortOption.TITLE_ZA           -> "title COLLATE NOCASE DESC, localId DESC"
            SortOption.READING_TIME_SHORT -> "readingTimeMinutes ASC, localId ASC"
            SortOption.READING_TIME_LONG  -> "readingTimeMinutes DESC, localId DESC"
        }
    }

}
