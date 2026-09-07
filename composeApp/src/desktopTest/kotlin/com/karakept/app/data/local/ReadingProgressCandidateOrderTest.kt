package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A reading-progress pass covers a bounded slice of the library, so which rows it covers is
 * what decides whether the screen agrees with the server. The rotation is ordered by what the
 * user is most likely to be looking at rather than by staleness alone.
 */
class ReadingProgressCandidateOrderTest {

    private lateinit var db: AppDatabase
    private val serverId = "server-1"

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

    private suspend fun insert(
        id: Long,
        listIds: String = "",
        isRead: Boolean = false,
        progressSyncedAt: Long = 0L,
        modifiedAt: Long = 0L
    ) {
        db.bookmarkDao().insertBookmarks(
            listOf(
                BookmarkEntity(
                    remoteId = id,
                    originalRemoteId = "remote-$id",
                    serverId = serverId,
                    url = "https://example.com/$id",
                    title = "Bookmark $id",
                    content = null,
                    imageUrl = null,
                    bannerImageAssetId = null,
                    screenshotAssetId = null,
                    description = null,
                    createdAt = id,
                    isArchived = false,
                    isStarred = false,
                    isRead = isRead,
                    listIds = listIds,
                    progressSyncedAt = progressSyncedAt,
                    modifiedAt = modifiedAt
                )
            )
        )
    }

    private suspend fun candidates(listId: String?, limit: Int) =
        db.bookmarkDao().getReadingProgressPullCandidates(serverId, listId, limit)
            .map { it.remoteId }

    @Test
    fun `the list being synced comes first`() = runBlocking {
        insert(1L, progressSyncedAt = 0L)
        insert(2L, listIds = "list-a", progressSyncedAt = 999L)
        insert(3L, listIds = "other,list-a", progressSyncedAt = 999L)

        // Both list members outrank the fresher-cursor row, even though they were pulled later.
        assertEquals(listOf(2L, 3L), candidates("list-a", limit = 2))
    }

    @Test
    fun `unread rows come before read ones`() = runBlocking {
        insert(1L, isRead = true, progressSyncedAt = 0L)
        insert(2L, isRead = false, progressSyncedAt = 500L)

        // The unread row is the one whose progress decides a count the user can see.
        assertEquals(listOf(2L, 1L), candidates(listId = null, limit = 2))
    }

    @Test
    fun `least recently pulled wins among equals`() = runBlocking {
        insert(1L, progressSyncedAt = 900L)
        insert(2L, progressSyncedAt = 100L)
        insert(3L, progressSyncedAt = 500L)

        assertEquals(listOf(2L, 3L, 1L), candidates(listId = null, limit = 3))
    }

    @Test
    fun `no list context still returns candidates`() = runBlocking {
        insert(1L)
        insert(2L)

        assertTrue(candidates(listId = null, limit = 10).size == 2)
    }

    @Test
    fun `the projection carries what the pull needs`() = runBlocking {
        insert(7L, isRead = true)

        val target = db.bookmarkDao().getReadingProgressPullCandidates(serverId, null, 1).single()

        // The batch keys on the server's own id and compares against local progress, so both
        // travel with the candidate rather than costing a row read each.
        assertEquals("remote-7", target.originalRemoteId)
        assertEquals(0f, target.readingProgress)
    }
}
