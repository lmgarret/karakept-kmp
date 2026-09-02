package com.karakept.app.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.entity.BookmarkEntity
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
                    remoteId = id.toLong(),
                    originalRemoteId = "remote-$id",
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

    private suspend fun read(sort: SortOption, offset: Int, limit: Int, listId: String? = null) =
        db.bookmarkDao().getBookmarksPaged(
            BookmarkRepository.buildPagedQuery(
                serverId = serverId,
                status = FilterStatus.ALL,
                sort = sort,
                listId = listId,
                limit = limit,
                offset = offset
            )
        )

    @Test
    fun `paging a fully tied table yields every row exactly once`() = runBlocking {
        val pageSize = 20
        for (sort in SortOption.entries) {
            val paged = buildList {
                var offset = 0
                while (offset < rowCount) {
                    addAll(read(sort, offset, pageSize))
                    offset += pageSize
                }
            }.map { it.localId }

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
            val fromDb = read(sort, offset = 0, limit = rowCount * 2)
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
        val paged = buildList {
            var offset = 0
            while (offset < rowCount) {
                addAll(read(SortOption.NEWEST, offset, pageSize, listId = "list-a"))
                offset += pageSize
            }
        }.map { it.localId }

        assertEquals((1L..rowCount).toList(), paged.sorted(), "rows returned by the list query")
    }
}
