package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.domain.ListCountUtils
import com.karakept.app.domain.TagCountUtils
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.ListSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The queries that replaced reading the whole table into memory.
 *
 * Each one answers a question the app used to answer by walking every row: how many bookmarks a
 * list holds, which tags are in use, what the quick filters count, which rows a selection covers,
 * and what sits at one position in the view. What matters is that the answers are the same as
 * walking the rows would give, so each is checked against the rows themselves.
 */
class BookmarkCountingQueriesTest {

    private lateinit var db: AppDatabase
    private val serverId = "server-1"
    private val server = Server(id = serverId, url = "https://example.com", apiKey = "k", label = "T")

    private fun row(
        id: Int,
        tags: String = "",
        listIds: String = "",
        isRead: Boolean = false,
        isArchived: Boolean = false,
        isStarred: Boolean = false,
        title: String = "Bookmark $id",
        url: String = "https://example.com/$id",
        description: String? = null
    ) = BookmarkEntity(
        remoteId = "remote-$id",
        serverId = serverId,
        url = url,
        title = title,
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = description,
        createdAt = 1_700_000_000_000L + id,
        isArchived = isArchived,
        isStarred = isStarred,
        isRead = isRead,
        tags = tags,
        listIds = listIds
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

    /** Search over the default view, through the query the repository builds. */
    private suspend fun search(query: String, filter: FilterConfig = FilterConfig()) =
        if (query.isBlank()) emptyList()
        else db.bookmarkDao().getBookmarksPaged(
            BookmarkRepository.buildSearchQuery(serverId, filter, query, limit = 500)
        )

    @Test
    fun `grouped memberships count each list the way walking the rows does`() = runBlocking {
        val rows = listOf(
            row(1, listIds = "inbox"),
            row(2, listIds = "inbox,parent"),
            row(3, listIds = "child", isRead = true),
            row(4, listIds = "child"),
            row(5, listIds = "other,child"),
            row(6, listIds = "")
        )
        insert(*rows.toTypedArray())

        val lists = listOf(
            KarakeepList(id = "inbox", name = "inbox"),
            KarakeepList(id = "parent", name = "parent"),
            KarakeepList(id = "child", name = "child", parentId = "parent"),
            KarakeepList(id = "other", name = "other")
        )
        val settings = mapOf(
            "parent" to ListSettings(includeChildListBookmarks = true),
            "inbox" to ListSettings(countOnlyUnread = true)
        )

        val groups = db.bookmarkDao().listMembershipGroups(serverId).first()
        val fromGroups = ListCountUtils.countBookmarksPerList(lists, groups, settings)

        // The same numbers a per-list walk over the rows produces.
        val expected = lists.associate { list ->
            val id = list.id ?: ""
            val s = settings[id] ?: ListSettings()
            val counted = if (s.includeChildListBookmarks) setOf(id, "child") else setOf(id)
            id to rows.count { bookmark ->
                val ids = bookmark.listIds.split(",").filter { it.isNotBlank() }
                ids.any { it in counted } && (!s.countOnlyUnread || !bookmark.isRead)
            }
        }
        assertEquals(expected, fromGroups)
    }

    @Test
    fun `a bookmark in two counted lists is still one bookmark`() = runBlocking {
        insert(row(1, listIds = "parent,child"), row(2, listIds = "child"))

        val lists = listOf(
            KarakeepList(id = "parent", name = "parent"),
            KarakeepList(id = "child", name = "child", parentId = "parent")
        )
        val groups = db.bookmarkDao().listMembershipGroups(serverId).first()

        assertEquals(
            2,
            ListCountUtils.countBookmarksPerList(
                lists,
                groups,
                mapOf("parent" to ListSettings(includeChildListBookmarks = true))
            )["parent"]
        )
    }

    @Test
    fun `tag groups count each tag the way walking the rows does`() = runBlocking {
        val rows = listOf(
            row(1, tags = "kotlin,android"),
            row(2, tags = "kotlin"),
            row(3, tags = "kotlin,android"),
            row(4, tags = ""),
            row(5, tags = "rust")
        )
        insert(*rows.toTypedArray())

        val counts = TagCountUtils.countsByTag(db.bookmarkDao().tagGroups(serverId).first())

        val expected = rows
            .flatMap { it.tags.split(",").filter(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
        assertEquals(expected, counts)
    }

    @Test
    fun `quick filter counts match counting the rows`() = runBlocking {
        val rows = listOf(
            row(1),
            row(2, isStarred = true),
            row(3, isArchived = true),
            row(4, isArchived = true, isStarred = true),
            row(5, isStarred = true)
        )
        insert(*rows.toTypedArray())

        val counts = db.bookmarkDao().quickFilterCounts(serverId).first()

        assertEquals(rows.count { !it.isArchived }, counts.all_)
        assertEquals(rows.count { it.isStarred && !it.isArchived }, counts.favorites)
        assertEquals(rows.count { it.isArchived }, counts.archived)
        // A starred *and* archived bookmark belongs to archived, not to favourites.
        assertEquals(2, counts.favorites)
    }

    @Test
    fun `the unread rows of a list are matched by whole membership`() = runBlocking {
        insert(
            row(1, listIds = "feeds"),
            row(2, listIds = "feeds", isRead = true),
            row(3, listIds = "feeds-archive"),
            row(4, listIds = "other,feeds")
        )

        val unread = db.bookmarkDao().getUnreadInList(serverId, "feeds").map { it.remoteId }

        assertEquals(listOf("remote-1", "remote-4"), unread.sorted())
    }

    @Test
    fun `the view's identities are its rows' identities, in order`() = runBlocking {
        insert(row(1), row(2, isArchived = true), row(3))
        val filter = FilterConfig(sort = SortOption.OLDEST)

        val ids = db.bookmarkDao().selectRemoteIds(
            BookmarkRepository.buildViewIdsQuery(serverId, filter)
        )
        val rows = db.bookmarkDao().getBookmarksPaged(
            BookmarkRepository.buildPagedQuery(serverId, filter, limit = 100)
        )

        assertEquals(rows.map { it.remoteId }, ids, "same rows, same order, identities only")
        assertTrue("remote-2" !in ids, "an archived row is not in the default view")
    }

    @Test
    fun `the row at a position is the row the view holds there`() = runBlocking {
        insert(*(1..30).map { row(it) }.toTypedArray())
        val filter = FilterConfig(sort = SortOption.OLDEST)
        val view = db.bookmarkDao().getBookmarksPaged(
            BookmarkRepository.buildPagedQuery(serverId, filter, limit = 100)
        )

        for (index in listOf(0, 1, 17, 29)) {
            val at = db.bookmarkDao().getBookmarksPaged(
                BookmarkRepository.buildPageQuery(serverId, filter, offset = index, limit = 1)
            ).firstOrNull()
            assertEquals(view[index].remoteId, at?.remoteId, "row at $index")
        }

        assertNull(
            db.bookmarkDao().getBookmarksPaged(
                BookmarkRepository.buildPageQuery(serverId, filter, offset = 30, limit = 1)
            ).firstOrNull(),
            "past the end of the view there is no row"
        )
    }

    @Test
    fun `search matches title, url and description within the view`() = runBlocking {
        insert(
            row(1, title = "Kotlin coroutines"),
            row(2, title = "Something else", url = "https://kotlinlang.org/docs"),
            row(3, title = "Nothing", description = "a guide to kotlin"),
            row(4, title = "Unrelated"),
            row(5, title = "Kotlin archived", isArchived = true)
        )

        val hits = search("kotlin").map { it.remoteId }

        assertEquals(listOf("remote-1", "remote-2", "remote-3"), hits.sorted())
        assertTrue(
            "remote-5" !in hits,
            "search runs inside the view, so an archived row is not in the default one"
        )
    }

    @Test
    fun `a search term containing a wildcard matches it literally`() = runBlocking {
        insert(row(1, title = "50% off"), row(2, title = "50 things off"))

        assertEquals(
            listOf("remote-1"),
            search("50%").map { it.remoteId },
            "'%' is a percent sign, not 'anything'"
        )
    }

    /**
     * The narrowing that came with matching in the database.
     *
     * Kotlin's `lowercase()` folds the whole of Unicode; SQLite's `lower()` folds the 26 ASCII
     * letters. Matching in Kotlin needed every row in memory, which is what the move removes, so
     * this is the cost — pinned rather than left to be discovered. A stored case-folded column
     * would buy it back without bringing the rows back.
     */
    @Test
    fun `search folds case for ASCII only`() = runBlocking {
        insert(
            row(1, title = "Uber alles"),
            row(2, title = "\u00DCber alles"),
            row(3, title = "\u00FCber alles")
        )

        assertEquals(
            listOf("remote-1"),
            search("uber").map { it.remoteId },
            "ASCII case is folded, so a lowercase term finds the capitalised title"
        )
        assertEquals(
            listOf("remote-3"),
            search("\u00FCber").map { it.remoteId },
            "but a non-ASCII letter is not, so the capitalised \u00DC is missed — if this " +
                "starts returning both, the folding was widened and searchBookmarks' note is " +
                "out of date"
        )
    }

    @Test
    fun `a blank search matches nothing rather than everything`() = runBlocking {
        insert(row(1), row(2))

        // A blank term is refused before it reaches the database.
        assertEquals(emptyList(), search("  "))
    }
}
