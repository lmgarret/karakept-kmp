package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.entity.BookmarkEntity
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
 * [NoCaseCollationUtils] claims to be SQLite's `COLLATE NOCASE`. This is the claim, checked
 * against SQLite rather than against a reading of its documentation.
 *
 * The titles below are chosen to separate the three things that could differ: which characters
 * fold (ASCII only, not all of Unicode), what the surviving characters are compared as (code
 * points, not UTF-16 code units), and where a prefix lands.
 */
class NoCaseCollationSqliteTest {

    private lateinit var db: AppDatabase
    private val serverId = "server-1"

    private val titles = listOf(
        "", " leading space", "[bracket", "_underscore", "0 digit",
        "Alpha", "alpha", "ALPHA", "AlphaBet", "alphabet",
        "Beta", "beta", "Zebra", "zebra",
        "Über alles", "über alles", "Ångström", "ångström",
        "Émile", "émile", "Ωmega", "ωmega",
        "日本語のタイトル", "� replacement",
        "🚀 rocket", "🧪 flask"
    )

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        db.bookmarkDao().insertBookmarks(
            titles.mapIndexed { index, title ->
                BookmarkEntity(
                    remoteId = "remote-$index",
                    serverId = serverId,
                    url = "https://example.com/$index",
                    title = title,
                    content = null,
                    imageUrl = null,
                    bannerImageAssetId = null,
                    screenshotAssetId = null,
                    description = null,
                    createdAt = 1_700_000_000_000L,
                    isArchived = false,
                    isStarred = false,
                    readingTimeMinutes = 0
                )
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun rowsInDbOrder(sort: SortOption): List<BookmarkEntity> =
        db.bookmarkDao().getBookmarksPaged(
            BookmarkRepository.buildPagedQuery(
                serverId = serverId,
                filter = FilterConfig(status = FilterStatus.ALL, sort = sort),
                limit = titles.size * 2,
                after = null
            )
        )

    @Test
    fun `the client-side sort orders these titles the way SQLite does`() = runBlocking {
        // Through [BookmarkFilterUtils.applySorting] rather than the comparator alone, so the
        // collation and the localId tie-break are checked as the one thing production uses.
        for (sort in listOf(SortOption.TITLE_AZ, SortOption.TITLE_ZA)) {
            val fromDb = rowsInDbOrder(sort)
            val fromComparator = BookmarkFilterUtils.applySorting(fromDb.shuffled(), sort)

            assertEquals(
                fromDb.map { it.title to it.localId },
                fromComparator.map { it.title to it.localId },
                "$sort: the client-side sort must agree with COLLATE NOCASE"
            )
        }
    }
}
