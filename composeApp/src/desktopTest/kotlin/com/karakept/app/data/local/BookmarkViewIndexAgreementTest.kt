package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.ContentFilter
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.domain.BookmarkFilterUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The in-memory view and the paged query must agree on *which row sits at index n*.
 *
 * A virtualized list addresses rows by absolute index, while the paged query addresses them by
 * sort key. The bridge between the two is the in-memory view: if `orderedViewFor(all, filter)[n]`
 * is the row the walk returns at position `n`, then a jump to an arbitrary index can seed its
 * keyset cursor straight out of the resident rows — no walk, no OFFSET, and no page-boundary
 * index to invalidate on every write.
 *
 * That agreement is *built* — `applySorting` ties-break on `localId` to match `toOrderBySql`, and
 * `viewFor` mirrors the query's status clause — but built is not proven, and every place the two
 * disagree is a row rendered at the wrong index. These tests prove it against real SQLite, on
 * data shaped to hit the joins each side makes separately: ties on every sort key, mixed-case and
 * non-ASCII titles, tag and list filters that only the client side applies, and page boundaries
 * that fall inside a run of equal keys.
 */
class BookmarkViewIndexAgreementTest {

    private lateinit var db: AppDatabase
    private val serverId = "server-1"

    /**
     * Rows whose sort keys collide in every way the real table does: a batch import sharing one
     * `createdAt`, a library where most `readingTimeMinutes` are 0, and titles that differ only
     * by case. Non-ASCII titles are in deliberately — SQLite's `NOCASE` folds ASCII only, while
     * Kotlin's `lowercase()` folds all of Unicode, so this is where the two comparators part
     * company; the emoji is there because it is a surrogate pair, which UTF-16 and code-point
     * order disagree about.
     *
     * The count is prime so that no view built by taking every nth row draws a fixed subset of
     * titles. At 18 it shared a factor with the list cycle, and the single-list views happened
     * to hold no case-divergent pair at all — so they agreed for a reason that was not the one
     * being tested.
     */
    private val titles = listOf(
        "Alpha", "alpha", "ALPHA", "Beta", "beta",
        "Über alles", "uber alles", "Ångström", "ångström",
        "Zebra", "zebra", "", " leading space", "Émile", "émile",
        "日本語のタイトル", "Ωmega", "ωmega", "🚀 rocket"
    )

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        db.bookmarkDao().insertBookmarks(
            (1..180).map { id ->
                val hasContent = id % 4 == 0
                BookmarkEntity(
                    remoteId = "remote-$id",
                    serverId = serverId,
                    url = "https://example.com/$id",
                    title = titles[id % titles.size],
                    // Kept consistent with readingTimeMinutes: the OFFLINE view is the one
                    // place the two sides genuinely differ, and it has its own test below.
                    content = if (hasContent) "body of $id" else null,
                    imageUrl = null,
                    bannerImageAssetId = null,
                    screenshotAssetId = null,
                    description = null,
                    // Three distinct timestamps across 180 rows: every page boundary lands
                    // inside a run of tied keys.
                    createdAt = 1_700_000_000_000L + (id % 3) * 86_400_000L,
                    isArchived = id % 7 == 0,
                    isStarred = id % 5 == 0,
                    isRead = id % 3 == 0,
                    // Offset from isRead so IN_PROGRESS (progress > 0 and *not* read) is a
                    // non-empty view — every multiple of 6 is also a multiple of 3.
                    readingProgress = if (id % 6 == 2) 0.5f else 0f,
                    tags = when (id % 4) {
                        0 -> "kotlin,android"
                        1 -> "kotlin"
                        2 -> ""
                        else -> "rust,android"
                    },
                    listIds = when (id % 3) {
                        0 -> "list-a"
                        1 -> "list-a,list-b"
                        else -> "list-c"
                    },
                    readingTimeMinutes = if (hasContent) (id % 11) + 1 else 0
                )
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Every row as `MainScreenModel.allBookmarks` holds it — content stripped by the DAO. */
    private suspend fun allBookmarks(): List<BookmarkEntity> =
        db.bookmarkDao().getBookmarksForServer(serverId).first()

    /**
     * The whole view as the query alone defines it: keyset pages, with nothing applied after the
     * read. Every clause now lives in the `WHERE`, so what comes back is already the view.
     */
    private suspend fun pagedView(
        filter: FilterConfig,
        pageSize: Int = 20,
        maxReads: Int = 200
    ): List<BookmarkEntity> = buildList {
        var cursor: BookmarkCursor? = null
        var reads = 0
        while (true) {
            check(reads++ < maxReads) { "paging did not terminate — is the cursor strict?" }
            val raw = db.bookmarkDao().getBookmarksPaged(
                BookmarkRepository.buildPagedQuery(
                    serverId = serverId,
                    filter = filter,
                    limit = pageSize,
                    after = cursor
                )
            )
            if (raw.isEmpty()) break
            addAll(raw)
            cursor = BookmarkCursor.of(raw.last())
        }
    }

    /**
     * Asserts the paged walk and the in-memory view hold the same row at every index.
     *
     * Compared as whole entities, not just identities. The two queries project the same columns
     * — both stub `content` out with `'' as content` — so a row read by paging carries nothing
     * the resident row does not already have. That is a stronger statement than "same order",
     * and it is the one that decides whether a page read is needed for *content* or only ever
     * for rows the in-memory view does not hold.
     */
    private suspend fun assertAgrees(filter: FilterConfig, label: String) {
        val paged = pagedView(filter)
        val inMemory = BookmarkFilterUtils.orderedViewFor(allBookmarks(), filter)
        assertTrue(paged.isNotEmpty(), "$label: fixture produced an empty view, so it proves nothing")
        assertEquals(
            paged.map { it.localId },
            inMemory.map { it.localId },
            "$label: the row at each index must be the same on both sides"
        )
        assertEquals(
            paged,
            inMemory,
            "$label: and must carry the same fields — a paged read adds nothing to a resident row"
        )
    }

    @Test
    fun `every sort option places the same row at each index`() = runBlocking {
        for (sort in SortOption.entries) {
            val filter = FilterConfig(sort = sort)
            assertAgrees(filter, "sort=$sort")
        }
    }

    @Test
    fun `every status clause places the same row at each index`() = runBlocking {
        // OFFLINE is excluded on purpose and covered by its own test below.
        val statuses = FilterStatus.entries - FilterStatus.OFFLINE
        for (status in statuses) {
            for (sort in SortOption.entries) {
                val filter = FilterConfig(status = status, sort = sort)
                assertAgrees(filter, "status=$status sort=$sort")
            }
        }
    }

    @Test
    fun `a single-list view places the same row at each index`() = runBlocking {
        // The single-list branch is the one where the query filters and the in-memory view
        // does not apply a status clause at all — the two are built to meet in the middle.
        for (listId in listOf("list-a", "list-b", "list-c")) {
            for (sort in SortOption.entries) {
                val filter = FilterConfig(
                    status = FilterStatus.ALL_INCLUDING_ARCHIVED,
                    lists = listOf(listId),
                    sort = sort
                )
                assertAgrees(filter, "list=$listId sort=$sort")
            }
        }
    }

    @Test
    fun `client-side filters place the same row at each index`() = runBlocking {
        val filters = listOf(
            FilterConfig(tags = listOf("kotlin")),
            FilterConfig(tags = listOf("kotlin", "rust")),
            FilterConfig(readFilter = ReadFilter.UNREAD),
            FilterConfig(readFilter = ReadFilter.READ),
            FilterConfig(readFilter = ReadFilter.IN_PROGRESS),
            FilterConfig(contentFilter = ContentFilter.DOWNLOADED),
            FilterConfig(contentFilter = ContentFilter.NOT_DOWNLOADED),
            FilterConfig(lists = listOf("list-a", "list-c")),
            FilterConfig(
                tags = listOf("android"),
                readFilter = ReadFilter.UNREAD,
                sort = SortOption.TITLE_AZ
            )
        )
        for (filter in filters) {
            assertAgrees(filter, "filter=$filter")
        }
    }

    @Test
    fun `the page size never changes which row sits at an index`() = runBlocking {
        // A virtualized list reads a page starting anywhere, so a boundary can fall between any
        // two rows. If the order were not total, a different page size would move rows.
        val filter = FilterConfig(sort = SortOption.TITLE_AZ)
        val reference = pagedView(filter, pageSize = 20).map { it.localId }
        for (pageSize in listOf(1, 3, 7, 50, 1000)) {
            assertEquals(
                reference,
                pagedView(filter, pageSize = pageSize).map { it.localId },
                "pageSize=$pageSize must not move a row to a different index"
            )
        }
    }

    /**
     * Seeding a keyset cursor from the in-memory view lands the read on the same rows the walk
     * would have reached — the property the whole random-access design rests on.
     */
    @Test
    fun `a cursor taken from the in-memory view resumes the walk at that index`() = runBlocking {
        val pageSize = 20
        for (sort in SortOption.entries) {
            val filter = FilterConfig(sort = sort)
            val view = BookmarkFilterUtils.orderedViewFor(allBookmarks(), filter)

            for (index in listOf(0, 1, 19, 20, 21, 57, 100, view.size - 1)) {
                if (index !in view.indices) continue
                // The cursor is the row *before* the target, so the read starts at the target.
                val seed = if (index == 0) null else BookmarkCursor.of(view[index - 1])
                val page = db.bookmarkDao().getBookmarksPaged(
                    BookmarkRepository.buildPagedQuery(
                        serverId = serverId,
                        filter = filter,
                        limit = pageSize,
                        after = seed
                    )
                )
                assertEquals(
                    view.subList(index, minOf(index + pageSize, view.size)).map { it.localId },
                    page.map { it.localId },
                    "$sort: a read seeded at index $index must return the rows at $index onward"
                )
            }
        }
    }

    /**
     * The property the virtualized list rests on: the page at offset *n* holds the view's rows
     * from *n* onward.
     *
     * This is only true because the whole view is one `WHERE`. While four of its clauses were
     * applied in Kotlin after the read, a SQL offset counted rows the view did not contain, so a
     * read had to over-fetch and estimate how many the Kotlin side would discard.
     */
    @Test
    fun `a page read at an offset returns the view's rows from that offset`() = runBlocking {
        val pageSize = 20
        val filters = listOf(
            FilterConfig(),
            FilterConfig(sort = SortOption.TITLE_AZ),
            FilterConfig(tags = listOf("kotlin")),
            FilterConfig(readFilter = ReadFilter.UNREAD),
            FilterConfig(status = FilterStatus.ALL_INCLUDING_ARCHIVED, lists = listOf("list-a")),
            FilterConfig(contentFilter = ContentFilter.DOWNLOADED, sort = SortOption.OLDEST)
        )
        for (filter in filters) {
            val whole = pagedView(filter)
            assertTrue(whole.size > pageSize, "filter=$filter: need more than one page to prove it")

            for (offset in listOf(0, 1, pageSize - 1, pageSize, pageSize + 3, whole.size - 1)) {
                val page = db.bookmarkDao().getBookmarksPaged(
                    BookmarkRepository.buildPageQuery(serverId, filter, offset, pageSize)
                )
                assertEquals(
                    whole.subList(offset, minOf(offset + pageSize, whole.size)),
                    page,
                    "filter=$filter: the page at offset $offset must be the view from $offset"
                )
            }

            // And the pages tile the view exactly — no gap, no overlap.
            val tiled = (0 until whole.size step pageSize).flatMap { offset ->
                db.bookmarkDao().getBookmarksPaged(
                    BookmarkRepository.buildPageQuery(serverId, filter, offset, pageSize)
                )
            }
            assertEquals(whole, tiled, "filter=$filter: pages must tile the view")
        }
    }

    /**
     * "How many rows sort before this one" — what the "N new" pill counts after a sync.
     *
     * It has to be the row's own index in the view, for every sort: the pill says how many
     * arrived above where the user was standing, and the answer must not depend on which pages
     * happen to be loaded.
     */
    @Test
    fun `counting the rows before a cursor gives that row's index in the view`() = runBlocking {
        for (sort in SortOption.entries) {
            val filter = FilterConfig(sort = sort)
            val view = pagedView(filter)

            for (index in listOf(0, 1, 17, view.size / 2, view.size - 1)) {
                val counted = db.bookmarkDao().countBookmarks(
                    BookmarkRepository.buildCountBeforeQuery(
                        serverId,
                        filter,
                        BookmarkCursor.of(view[index]),
                        excludeRead = false
                    )
                )
                assertEquals(index, counted, "$sort: rows before the row at index $index")
            }
        }
    }

    @Test
    fun `counting before a cursor can leave out the rows already read`() = runBlocking {
        val filter = FilterConfig()
        val view = pagedView(filter)
        // Far enough down that read rows are above it. The fixture keys `createdAt` and `isRead`
        // off the same id, so the top of a NEWEST view is entirely unread — picking a fixed
        // index here would test nothing.
        val index = view.indexOfFirst { it.isRead }.let { it + 10 }
        val cursor = BookmarkCursor.of(view[index])

        val all = db.bookmarkDao().countBookmarks(
            BookmarkRepository.buildCountBeforeQuery(serverId, filter, cursor, excludeRead = false)
        )
        val unread = db.bookmarkDao().countBookmarks(
            BookmarkRepository.buildCountBeforeQuery(serverId, filter, cursor, excludeRead = true)
        )

        assertEquals(index, all)
        assertEquals(
            view.take(index).count { !it.isRead },
            unread,
            "the pill offers a trip to what arrived, and a row already read is not that"
        )
        assertTrue(unread < all, "the fixture must contain read rows for this to prove anything")
    }

    /**
     * A row removed by the sync must still be a usable position, which is why the anchor is a
     * cursor and not a row id.
     */
    @Test
    fun `a cursor still counts after its own row is gone`() = runBlocking {
        val filter = FilterConfig(sort = SortOption.OLDEST)
        val view = pagedView(filter)
        val cursor = BookmarkCursor.of(view[20])

        db.bookmarkDao().deleteBookmark(view[20])

        assertEquals(
            20,
            db.bookmarkDao().countBookmarks(
                BookmarkRepository.buildCountBeforeQuery(serverId, filter, cursor, excludeRead = false)
            ),
            "the rows above it did not move, so the position still means what it meant"
        )
    }

    /**
     * The one view that cannot be indexed from memory.
     *
     * `FilterStatus.OFFLINE` selects on a non-empty `content` column, and every query behind an
     * in-memory row strips that column — so the in-memory side stands on the `readingTimeMinutes`
     * proxy instead (see [BookmarkFilterUtils.countForView]). The proxy is not the same predicate,
     * and this pins what happens when they part: a row with content but no reading time is in the
     * query's view and not in memory's, which shifts every index below it.
     */
    @Test
    fun `the offline view cannot be indexed from the in-memory rows`() = runBlocking {
        db.bookmarkDao().insertBookmarks(
            listOf(
                BookmarkEntity(
                    remoteId = "offline-no-reading-time",
                    serverId = serverId,
                    url = "https://example.com/offline",
                    title = "Downloaded but unmeasured",
                    content = "body",
                    imageUrl = null,
                    bannerImageAssetId = null,
                    screenshotAssetId = null,
                    description = null,
                    createdAt = 1_900_000_000_000L,
                    isArchived = false,
                    isStarred = false,
                    readingTimeMinutes = 0
                )
            )
        )
        val filter = FilterConfig(status = FilterStatus.OFFLINE)
        val paged = pagedView(filter).map { it.localId }
        val inMemory = BookmarkFilterUtils
            .orderedViewFor(allBookmarks(), filter)
            .map { it.localId }

        assertTrue(
            paged != inMemory,
            "if the proxy has been replaced by a real predicate, index the offline view from " +
                "memory like every other view and delete this test"
        )
    }
}
