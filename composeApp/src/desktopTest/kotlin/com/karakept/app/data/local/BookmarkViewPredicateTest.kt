package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ContentFilter
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
import com.karakept.app.data.repository.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The view predicate decides membership, and membership in a comma-separated column is the part
 * that is easy to get subtly wrong.
 *
 * A tag is free text: it can hold `%` or `_`, which `LIKE` reads as wildcards, and it can be a
 * substring of another tag. The fixture in [BookmarkViewIndexAgreementTest] uses ordinary tags
 * and so would pass under either spelling — these are the cases that separate them.
 */
class BookmarkViewPredicateTest {

    private lateinit var db: AppDatabase
    private val serverId = "server-1"

    private fun row(
        id: Int,
        tags: String = "",
        listIds: String = "",
        isRead: Boolean = false,
        readingProgress: Float = 0f,
        readingTimeMinutes: Int = 0,
        isArchived: Boolean = false,
        content: String? = null
    ) = BookmarkEntity(
        remoteId = "remote-$id",
        serverId = serverId,
        url = "https://example.com/$id",
        title = "Bookmark $id",
        content = content,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 1_700_000_000_000L + id,
        isArchived = isArchived,
        isStarred = false,
        isRead = isRead,
        tags = tags,
        listIds = listIds,
        readingTimeMinutes = readingTimeMinutes,
        readingProgress = readingProgress
    )

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(vararg rows: BookmarkEntity) =
        db.bookmarkDao().insertBookmarks(rows.toList())

    /** The remoteIds [filter] admits, read through the real query. */
    private suspend fun selected(filter: FilterConfig): List<String> =
        db.bookmarkDao().getBookmarksPaged(
            BookmarkRepository.buildPagedQuery(serverId, filter, limit = 1000)
        ).map { it.remoteId }

    private suspend fun counted(filter: FilterConfig): Int =
        db.bookmarkDao().countBookmarks(BookmarkRepository.buildCountQuery(serverId, filter))

    @Test
    fun `a tag matches only as a whole element`() = runBlocking {
        insert(
            row(1, tags = "kotlin"),
            row(2, tags = "kotlin,android"),
            row(3, tags = "android,kotlin"),
            row(4, tags = "rust,kotlin,go"),
            // Substring traps: each contains "kotlin" as text but is not the tag "kotlin".
            row(5, tags = "kotlinx"),
            row(6, tags = "not-kotlin"),
            row(7, tags = "kotlin-native"),
            row(8, tags = "")
        )

        assertEquals(
            listOf("remote-1", "remote-2", "remote-3", "remote-4"),
            selected(FilterConfig(tags = listOf("kotlin"))).sorted(),
            "a tag must match as a whole element, never as a substring"
        )
    }

    @Test
    fun `a tag holding a LIKE wildcard matches only itself`() = runBlocking {
        // The reason the predicate uses instr rather than four LIKE patterns: under LIKE, '%'
        // and '_' are wildcards, so selecting "50%" would also return "50-off" and "discount".
        insert(
            row(1, tags = "50%"),
            row(2, tags = "50-off"),
            row(3, tags = "discount"),
            row(4, tags = "a_b"),
            row(5, tags = "axb"),
            row(6, tags = "50%,sale")
        )

        assertEquals(
            listOf("remote-1", "remote-6"),
            selected(FilterConfig(tags = listOf("50%"))).sorted(),
            "'%' in a tag must be a literal percent sign, not a wildcard"
        )
        assertEquals(
            listOf("remote-4"),
            selected(FilterConfig(tags = listOf("a_b"))).sorted(),
            "'_' in a tag must be a literal underscore, not a single-character wildcard"
        )
    }

    @Test
    fun `several tags select a row carrying any of them`() = runBlocking {
        insert(
            row(1, tags = "kotlin"),
            row(2, tags = "rust"),
            row(3, tags = "go"),
            row(4, tags = "go,kotlin")
        )

        assertEquals(
            listOf("remote-1", "remote-2", "remote-4"),
            selected(FilterConfig(tags = listOf("kotlin", "rust"))).sorted()
        )
    }

    @Test
    fun `a list id matches only as a whole element`() = runBlocking {
        insert(
            row(1, listIds = "list-a"),
            row(2, listIds = "list-a,list-b"),
            row(3, listIds = "list-ab"),
            row(4, listIds = "other,list-a")
        )

        assertEquals(
            listOf("remote-1", "remote-2", "remote-4"),
            selected(
                FilterConfig(status = FilterStatus.ALL_INCLUDING_ARCHIVED, lists = listOf("list-a"))
            ).sorted(),
            "'list-ab' is a different list from 'list-a'"
        )
    }

    @Test
    fun `several lists select a row in any of them, under the status clause`() = runBlocking {
        insert(
            row(1, listIds = "list-a"),
            row(2, listIds = "list-b"),
            row(3, listIds = "list-c"),
            // A multi-list view still applies the status clause, unlike a single-list one.
            row(4, listIds = "list-a", isArchived = true)
        )

        assertEquals(
            listOf("remote-1", "remote-2"),
            selected(FilterConfig(lists = listOf("list-a", "list-b"))).sorted()
        )
    }

    @Test
    fun `a single list admits archived rows and a multi-list selection does not`() = runBlocking {
        insert(
            row(1, listIds = "list-a"),
            row(2, listIds = "list-a", isArchived = true)
        )

        assertEquals(
            listOf("remote-1", "remote-2"),
            selected(FilterConfig(lists = listOf("list-a"))).sorted(),
            "a single-list view applies no status clause, which is what it has always done"
        )
        assertEquals(
            listOf("remote-1"),
            selected(FilterConfig(lists = listOf("list-a", "list-b"))).sorted()
        )
    }

    @Test
    fun `the read filter selects on read state and progress`() = runBlocking {
        insert(
            row(1, isRead = true),
            row(2, isRead = false),
            row(3, isRead = false, readingProgress = 0.4f),
            // Read wins over progress: a finished bookmark is not "in progress".
            row(4, isRead = true, readingProgress = 0.9f)
        )

        assertEquals(listOf("remote-1", "remote-4"), selected(FilterConfig(readFilter = ReadFilter.READ)).sorted())
        assertEquals(listOf("remote-2", "remote-3"), selected(FilterConfig(readFilter = ReadFilter.UNREAD)).sorted())
        assertEquals(listOf("remote-3"), selected(FilterConfig(readFilter = ReadFilter.IN_PROGRESS)).sorted())
    }

    @Test
    fun `the content filter selects on reading time`() = runBlocking {
        insert(row(1, readingTimeMinutes = 5), row(2, readingTimeMinutes = 0))

        assertEquals(listOf("remote-1"), selected(FilterConfig(contentFilter = ContentFilter.DOWNLOADED)).sorted())
        assertEquals(listOf("remote-2"), selected(FilterConfig(contentFilter = ContentFilter.NOT_DOWNLOADED)).sorted())
    }

    @Test
    fun `filters compose`() = runBlocking {
        insert(
            row(1, tags = "kotlin", isRead = false, readingTimeMinutes = 5),
            row(2, tags = "kotlin", isRead = true, readingTimeMinutes = 5),
            row(3, tags = "rust", isRead = false, readingTimeMinutes = 5),
            row(4, tags = "kotlin", isRead = false, readingTimeMinutes = 0)
        )

        assertEquals(
            listOf("remote-1"),
            selected(
                FilterConfig(
                    tags = listOf("kotlin"),
                    readFilter = ReadFilter.UNREAD,
                    contentFilter = ContentFilter.DOWNLOADED
                )
            ).sorted()
        )
    }

    @Test
    fun `the offline view selects on stored content, which no proxy stands in for`() = runBlocking {
        // With the whole view in SQL there is no proxy left: OFFLINE reads the column it means.
        insert(
            row(1, content = "body", readingTimeMinutes = 0),
            row(2, content = "", readingTimeMinutes = 9),
            row(3, content = null, readingTimeMinutes = 9)
        )

        assertEquals(
            listOf("remote-1"),
            selected(FilterConfig(status = FilterStatus.OFFLINE)).sorted(),
            "downloaded means content is stored, not that a reading time was estimated"
        )
    }

    @Test
    fun `the count agrees with the rows for every filter shape`() = runBlocking {
        insert(
            row(1, tags = "kotlin", listIds = "list-a", isRead = true, readingTimeMinutes = 3),
            row(2, tags = "kotlin,rust", listIds = "list-a,list-b"),
            row(3, tags = "rust", listIds = "list-b", readingProgress = 0.2f),
            row(4, tags = "", listIds = "", isArchived = true),
            row(5, tags = "50%", listIds = "list-c", content = "body")
        )

        val filters = listOf(
            FilterConfig(),
            FilterConfig(status = FilterStatus.ALL_INCLUDING_ARCHIVED),
            FilterConfig(status = FilterStatus.ARCHIVED),
            FilterConfig(status = FilterStatus.OFFLINE),
            FilterConfig(tags = listOf("kotlin")),
            FilterConfig(tags = listOf("50%")),
            FilterConfig(lists = listOf("list-a")),
            FilterConfig(lists = listOf("list-a", "list-b")),
            FilterConfig(readFilter = ReadFilter.UNREAD),
            FilterConfig(readFilter = ReadFilter.IN_PROGRESS),
            FilterConfig(contentFilter = ContentFilter.DOWNLOADED),
            FilterConfig(tags = listOf("rust"), readFilter = ReadFilter.UNREAD)
        )
        for (filter in filters) {
            assertEquals(
                selected(filter).size,
                counted(filter),
                "count and rows must agree for $filter — they are one predicate"
            )
        }
    }
}
