package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.domain.BookmarkFilterUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The paged query must impose a *total* order on the rows it returns.
 *
 * A window is assembled from several LIMIT/OFFSET reads, and rows sharing the sort key are
 * only placed relative to one another by the ORDER BY. Without a unique column to break the
 * tie, nothing says two reads of the same table agree on where a tied row sits: one can
 * return it on both sides of a page boundary, or on neither — and the row that falls in the
 * gap is never displayed, even though the count in the drawer keeps counting it (#333).
 *
 * Ties are the norm rather than the exception here: a feed imports a batch of bookmarks
 * carrying one timestamp, and `readingTimeMinutes` is 0 for every bookmark whose content has
 * not been downloaded. These tests run against real SQLite, on a table where *every* sort key
 * is tied, so only the tiebreaker can order it.
 */
class BookmarkPagingOrderTest {

    private lateinit var db: AppDatabase
    private val serverId = "server-1"

    /** Every row identical apart from its identity — the worst case for every sort option. */
    private val rowCount = 60

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        db.bookmarkDao().insertBookmarks(
            (1..rowCount).map { id ->
                BookmarkEntity(
                    remoteId = "remote-$id",
                    serverId = serverId,
                    url = "https://example.com/$id",
                    title = "Tied title",
                    content = null,
                    imageUrl = null,
                    bannerImageAssetId = null,
                    screenshotAssetId = null,
                    description = null,
                    createdAt = 1_700_000_000_000L,
                    isArchived = false,
                    isStarred = false,
                    readingTimeMinutes = 0,
                    listIds = "list-a"
                )
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun read(
        sort: SortOption,
        after: BookmarkCursor?,
        limit: Int,
        listId: String? = null
    ) = db.bookmarkDao().getBookmarksPaged(
        BookmarkRepository.buildPagedQuery(
            serverId = serverId,
            filter = FilterConfig(
                status = FilterStatus.ALL,
                sort = sort,
                lists = listOfNotNull(listId)
            ),
            limit = limit,
            after = after
        )
    )

    /**
     * Pages the whole table by cursor, the way the walk does.
     *
     * [maxReads] bounds it because the failure mode of a keyset predicate that is not *strictly*
     * after the cursor is not a wrong answer but a page that keeps re-returning its own first
     * row. Left unbounded that hangs the suite instead of naming the bug.
     */
    private suspend fun pageAll(
        sort: SortOption,
        pageSize: Int,
        listId: String? = null,
        maxReads: Int = 100
    ): List<BookmarkEntity> = buildList {
        var cursor: BookmarkCursor? = null
        var reads = 0
        while (true) {
            check(reads++ < maxReads) { "$sort: paging did not terminate — is the cursor strict?" }
            val page = read(sort, cursor, pageSize, listId)
            if (page.isEmpty()) break
            addAll(page)
            cursor = BookmarkCursor.of(page.last())
        }
    }

    @Test
    fun `paging a fully tied table yields every row exactly once`() = runBlocking {
        val pageSize = 20
        for (sort in SortOption.entries) {
            val paged = pageAll(sort, pageSize).map { it.localId }

            assertEquals(
                (1L..rowCount).toList(),
                paged.sorted(),
                "$sort: paging must return every row, once — a row skipped by a page " +
                    "boundary is one the list can never show"
            )
        }
    }

    @Test
    fun `the query orders tied rows the same way the client-side comparator does`() = runBlocking {
        // The two must agree: a page appended to the loaded window is re-sorted client-side,
        // and a comparator that ordered ties differently would interleave it into the window
        // in an order no page boundary matches.
        for (sort in SortOption.entries) {
            val fromDb = read(sort, after = null, limit = rowCount * 2)
            val fromComparator = BookmarkFilterUtils.applySorting(fromDb.shuffled(), sort)

            assertEquals(
                fromComparator.map { it.localId },
                fromDb.map { it.localId },
                "$sort: DB order and client-side order must match"
            )
        }
    }

    @Test
    fun `a list query pages a fully tied list without losing a row`() = runBlocking {
        // The list branch of the query is built separately, so it is checked separately.
        val pageSize = 20
        val paged = pageAll(SortOption.NEWEST, pageSize, listId = "list-a").map { it.localId }

        assertEquals((1L..rowCount).toList(), paged.sorted(), "rows returned by the list query")
    }

    /**
     * The #333 case, against real SQLite: a sync commits rows *above* the read position while
     * the walk is partway down the table.
     *
     * Under OFFSET this is unrecoverable. Every inserted row shifts the rows below it down by
     * one, so the next read starts that many rows back: it re-returns rows the window already
     * holds, and an equal number of rows are pushed past the read position — where a walk that
     * only ever moves forward never asks for them again. They are in the table, they match the
     * filter, and the list cannot show them until a full reload starts over from the top, which
     * is exactly what toggling a filter does.
     *
     * A cursor names the position by the row itself, so rows inserted above it do not move it.
     */
    @Test
    fun `rows committed above the read position do not displace rows past the walk`() = runBlocking {
        val pageSize = 20
        val sort = SortOption.NEWEST
        val seen = mutableListOf<BookmarkEntity>()

        val firstPage = read(sort, after = null, limit = pageSize)
        seen += firstPage
        var cursor = BookmarkCursor.of(firstPage.last())

        // A sync lands mid-walk. Every row sorts above the cursor: same createdAt, higher
        // localId, and NEWEST breaks ties on localId DESC.
        db.bookmarkDao().insertBookmarks(
            (1..15).map { n ->
                BookmarkEntity(
                    remoteId = "synced-$n",
                    serverId = serverId,
                    url = "https://example.com/synced-$n",
                    title = "Tied title",
                    content = null,
                    imageUrl = null,
                    bannerImageAssetId = null,
                    screenshotAssetId = null,
                    description = null,
                    createdAt = 1_700_000_000_000L,
                    isArchived = false,
                    isStarred = false,
                    readingTimeMinutes = 0,
                    listIds = "list-a"
                )
            }
        )

        var reads = 0
        while (true) {
            check(reads++ < 100) { "paging did not terminate — is the cursor strict?" }
            val page = read(sort, cursor, pageSize)
            if (page.isEmpty()) break
            seen += page
            cursor = BookmarkCursor.of(page.last())
        }

        val originals = seen.map { it.remoteId }.filter { it.startsWith("remote-") }
        assertEquals(
            rowCount,
            originals.size,
            "every row present before the sync must still be reachable by scrolling"
        )
        assertEquals(
            originals.size,
            originals.distinct().size,
            "and none of them returned twice"
        )
    }
}
