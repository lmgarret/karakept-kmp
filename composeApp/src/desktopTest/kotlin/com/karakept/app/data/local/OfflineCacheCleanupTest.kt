package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.migrations.MIGRATION_14_15
import com.karakept.app.data.local.migrations.MIGRATION_15_16
import com.karakept.app.data.local.migrations.withAppSchema
import com.karakept.app.data.repository.CacheFileStore
import com.karakept.app.data.repository.OfflineCacheRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.domain.OfflineRetention.DAY_MILLIS
import com.karakept.app.utils.DefaultAppDispatchers
import com.karakept.app.utils.LocalFileInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Offline cache cleanup against a real database: the retention SQL, the orphan queries and the
 * file sweep all depend on what the rows actually hold, which a mocked DAO cannot say.
 */
class OfflineCacheCleanupTest {

    private val dbPath = "/tmp/karakept-offline-cleanup-${System.nanoTime()}.db"
    private val serverId = "s1"
    private val cacheDir = "/cache/image_cache"
    private var now = 100 * DAY_MILLIS

    @AfterTest
    fun cleanup() {
        java.io.File(dbPath).delete()
    }

    private val db = Room.databaseBuilder<AppDatabase>(name = dbPath)
        .withAppSchema()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private class FakeFiles(files: List<LocalFileInfo>) : CacheFileStore {
        val present = files.toMutableList()
        override fun list() = present.toList()
        override fun delete(path: String) {
            present.removeAll { it.path == path }
        }
    }

    private fun file(name: String, modifiedAt: Long = 0L, size: Long = 10) =
        LocalFileInfo("/cache/image_cache/$name", name, size, modifiedAt)

    private fun cleaner(
        retentionDays: Int?,
        files: CacheFileStore = FakeFiles(emptyList()),
        capMb: Int? = null
    ): OfflineCacheRepository {
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.activeOfflineRetentionDays } returns flowOf(retentionDays)
        every { settings.activeOfflineStorageCapMb } returns flowOf(capMb)
        return OfflineCacheRepository(
            bookmarkDao = db.bookmarkDao(),
            assetDao = db.assetDao(),
            settingsRepository = settings,
            appDispatchers = DefaultAppDispatchers(),
            files = files,
            clock = { now }
        )
    }

    private fun bookmark(
        id: String,
        content: String? = "<p>body</p>",
        isRead: Boolean = false,
        isArchived: Boolean = false,
        readOrArchivedAt: Long? = null,
        lastOpenedAt: Long? = null
    ) = BookmarkEntity(
        remoteId = id, serverId = serverId, url = "u", title = id, content = content,
        imageUrl = null, bannerImageAssetId = null, screenshotAssetId = null, description = null,
        createdAt = 1, isArchived = isArchived, isStarred = false, isRead = isRead,
        readOrArchivedAt = readOrArchivedAt, lastOpenedAt = lastOpenedAt
    )

    private fun asset(id: String, bookmarkId: String, localPath: String?) = AssetEntity(
        id = id, bookmarkRemoteId = bookmarkId, serverId = serverId, assetType = "bannerImage",
        fileName = null, contentType = null, localPath = localPath
    )

    private suspend fun row(id: String) = db.bookmarkDao().getBookmarkByRemoteId(id, serverId)!!

    @Test
    fun `stamps bookmarks when they become read or archived and clears the stamp when they go back`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("read", isRead = true))
        db.bookmarkDao().insertBookmark(bookmark("archived", isArchived = true))
        db.bookmarkDao().insertBookmark(bookmark("unread-again", readOrArchivedAt = 5L))
        db.bookmarkDao().insertBookmark(bookmark("already", isRead = true, readOrArchivedAt = 5L))

        cleaner(retentionDays = null).cleanUp()

        assertEquals(now, row("read").readOrArchivedAt)
        assertEquals(now, row("archived").readOrArchivedAt)
        assertNull(row("unread-again").readOrArchivedAt)
        assertEquals(5L, row("already").readOrArchivedAt, "the first time it was seen read is kept")
        db.close()
    }

    @Test
    fun `drops the body of bookmarks read longer ago than the retention period`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("old", isRead = true, readOrArchivedAt = now - 8 * DAY_MILLIS))
        db.bookmarkDao().insertBookmark(bookmark("recent", isArchived = true, readOrArchivedAt = now - 6 * DAY_MILLIS))
        db.bookmarkDao().insertBookmark(bookmark("unread"))

        val result = cleaner(retentionDays = 7).cleanUp()

        assertEquals(1, result.evictedBookmarks)
        with(row("old")) {
            assertNull(content)
            assertEquals(false, hasContent)
            assertEquals(true, isRead, "only the offline copy goes, the bookmark stays")
        }
        assertEquals("<p>body</p>", row("recent").content)
        assertEquals("<p>body</p>", row("unread").content)
        db.close()
    }

    @Test
    fun `retention off keeps every body`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("old", isRead = true, readOrArchivedAt = 1L))

        val result = cleaner(retentionDays = null).cleanUp()

        assertEquals(0, result.evictedBookmarks)
        assertEquals("<p>body</p>", row("old").content)
        db.close()
    }

    @Test
    fun `forgets the local files of retired bookmarks and the assets of deleted ones`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("old", isRead = true, readOrArchivedAt = 1L))
        db.bookmarkDao().insertBookmark(bookmark("kept"))
        db.assetDao().insertAssets(
            listOf(
                asset("a-old", "old", "$cacheDir/hero_banner_a-old"),
                asset("a-kept", "kept", "$cacheDir/hero_banner_a-kept"),
                asset("a-gone", "deleted-bookmark", "$cacheDir/hero_banner_a-gone")
            )
        )

        cleaner(retentionDays = 7).cleanUp()

        assertNull(db.assetDao().getAssetsForBookmark("old", serverId).single().localPath)
        assertNotNull(db.assetDao().getAssetsForBookmark("kept", serverId).single().localPath)
        assertTrue(db.assetDao().getAssetsForBookmark("deleted-bookmark", serverId).isEmpty())
        db.close()
    }

    @Test
    fun `sweeps only our own unreferenced files once they are past the grace period`() = runBlocking {
        db.bookmarkDao().insertBookmark(
            bookmark("b", content = """<img src="file://$cacheDir/img_abc.png"><img src="file://C:\Users\Jane Doe\cache\img_def">""")
        )
        db.assetDao().insertAssets(listOf(asset("a", "b", "$cacheDir/archive_a")))
        val files = FakeFiles(
            listOf(
                file("img_abc.png"),
                file("img_def"),
                file("archive_a"),
                file("img_orphan.jpg", size = 1000),
                file("hero_screenshot_gone", size = 500),
                file("img_just_written", modifiedAt = now - 60_000),
                file("0123abcd.1"),
                file("journal")
            )
        )

        val result = cleaner(retentionDays = null, files = files).cleanUp()

        assertEquals(2, result.deletedFiles)
        assertEquals(1500, result.freedBytes)
        assertEquals(
            listOf("img_abc.png", "img_def", "archive_a", "img_just_written", "0123abcd.1", "journal"),
            files.present.map { it.name }
        )
        db.close()
    }

    @Test
    fun `a body dropped by retention releases the images only it referenced`() = runBlocking {
        db.bookmarkDao().insertBookmark(
            bookmark("old", isRead = true, readOrArchivedAt = 1L,
                content = """<img src="file://$cacheDir/img_shared"><img src="file://$cacheDir/img_own">""")
        )
        db.bookmarkDao().insertBookmark(bookmark("kept", content = """<img src="file://$cacheDir/img_shared">"""))
        val files = FakeFiles(listOf(file("img_shared"), file("img_own")))

        cleaner(retentionDays = 7, files = files).cleanUp()

        assertEquals(listOf("img_shared"), files.present.map { it.name })
        db.close()
    }

    private fun img(name: String) = "<img src='file://$cacheDir/$name'>"

    @Test
    fun `under the storage cap nothing is evicted`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("a", isRead = true, content = img("img_a")))
        val files = FakeFiles(listOf(file("img_a", size = 500_000)))

        val result = cleaner(retentionDays = null, files = files, capMb = 1).cleanUp()

        assertEquals(0, result.evictedBookmarks)
        assertEquals(img("img_a"), row("a").content)
        db.close()
    }

    @Test
    fun `over the storage cap evicts read copies least recently opened first, then unread ones`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("read-old", isRead = true, lastOpenedAt = 10, content = img("img_a")))
        db.bookmarkDao().insertBookmark(bookmark("read-recent", isArchived = true, lastOpenedAt = 50, content = img("img_c")))
        db.bookmarkDao().insertBookmark(bookmark("unread-old", lastOpenedAt = 5, content = img("img_b")))
        val files = FakeFiles(
            listOf(file("img_a", size = 600_000), file("img_b", size = 600_000), file("img_c", size = 600_000))
        )

        val result = cleaner(retentionDays = null, files = files, capMb = 1).cleanUp()

        assertEquals(2, result.evictedBookmarks)
        with(row("read-old")) {
            assertNull(content)
            assertEquals(now, offlineEvictedAt)
        }
        assertNull(row("read-recent").content)
        assertEquals(img("img_b"), row("unread-old").content, "unread copies go last")
        assertNull(row("unread-old").offlineEvictedAt)
        assertEquals(listOf("img_b"), files.present.map { it.name })
        db.close()
    }

    @Test
    fun `a shared image is counted once and only freed by its last referrer`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("first", isRead = true, lastOpenedAt = 1, content = img("img_shared")))
        db.bookmarkDao().insertBookmark(bookmark("second", isRead = true, lastOpenedAt = 2, content = img("img_shared")))
        db.bookmarkDao().insertBookmark(bookmark("unread", lastOpenedAt = 0, content = "<p>small</p>"))
        val files = FakeFiles(listOf(file("img_shared", size = 1_500_000)))

        val result = cleaner(retentionDays = null, files = files, capMb = 1).cleanUp()

        assertEquals(2, result.evictedBookmarks)
        assertEquals("<p>small</p>", row("unread").content)
        assertTrue(files.present.isEmpty())
        db.close()
    }

    @Test
    fun `a downloaded asset alone counts as an offline copy`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("pdf", isRead = true, content = null))
        db.assetDao().insertAssets(listOf(asset("a", "pdf", "$cacheDir/asset_a.pdf")))
        val files = FakeFiles(listOf(file("asset_a.pdf", size = 2_000_000)))

        val result = cleaner(retentionDays = null, files = files, capMb = 1).cleanUp()

        assertEquals(1, result.evictedBookmarks)
        assertNull(db.assetDao().getAssetsForBookmark("pdf", serverId).single().localPath)
        assertTrue(files.present.isEmpty())
        db.close()
    }

    @Test
    fun `after a sync the storage cap waits for the sweep interval`() = runBlocking {
        val files = FakeFiles(emptyList())
        val cleaner = cleaner(retentionDays = null, files = files, capMb = 1)
        cleaner.cleanUpAfterSync()
        db.bookmarkDao().insertBookmark(bookmark("big", isRead = true, content = img("img_big")))
        files.present += file("img_big", size = 2_000_000)

        assertEquals(0, cleaner.cleanUpAfterSync()?.evictedBookmarks)
        assertEquals(img("img_big"), row("big").content)

        now += OfflineCacheRepository.SWEEP_INTERVAL_MILLIS
        assertEquals(1, cleaner.cleanUpAfterSync()?.evictedBookmarks)
        assertTrue(files.present.isEmpty())
        db.close()
    }

    @Test
    fun `storing the body again lifts the eviction`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("a", isRead = true, content = img("img_a")))
        cleaner(retentionDays = null, files = FakeFiles(listOf(file("img_a", size = 2_000_000))), capMb = 1).cleanUp()
        val evicted = row("a")
        assertNotNull(evicted.offlineEvictedAt)

        db.bookmarkDao().updateContent(evicted.localId, "<p>again</p>", 1)

        assertNull(row("a").offlineEvictedAt)
        db.close()
    }

    @Test
    fun `retention runs after every sync, and frees what it evicted straight away`() = runBlocking {
        val files = FakeFiles(emptyList())
        val cleaner = cleaner(retentionDays = 7, files = files)
        cleaner.cleanUpAfterSync()

        // A list sync moments later brings in a bookmark read long ago.
        db.bookmarkDao().insertBookmark(
            bookmark("old", isRead = true, readOrArchivedAt = 1L, content = "<img src='file://$cacheDir/img_old'>")
        )
        files.present += file("img_old")
        val result = cleaner.cleanUpAfterSync()

        assertEquals(1, result?.evictedBookmarks)
        assertNull(row("old").content)
        assertTrue(files.present.isEmpty(), "an eviction sweeps in the same pass")
        db.close()
    }

    @Test
    fun `usage counts stored bodies and only the files cleanup manages`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("a", content = "héllo"))
        db.bookmarkDao().insertBookmark(bookmark("b", content = "abc"))
        db.bookmarkDao().insertBookmark(bookmark("none", content = null))
        val files = FakeFiles(listOf(file("img_a", size = 100), file("asset_b.pdf", size = 50), file("0123abcd.1", size = 999)))

        val usage = cleaner(retentionDays = null, files = files).storageUsage()

        assertEquals(2, usage.bookmarkCount)
        assertEquals(9L, usage.bodyBytes, "bytes, not characters: é is two")
        assertEquals(150L, usage.fileBytes)
        db.close()
    }

    @Test
    fun `with nothing evicted the sweep waits for its interval`() = runBlocking {
        val files = FakeFiles(emptyList())
        val cleaner = cleaner(retentionDays = null, files = files)
        cleaner.cleanUpAfterSync()
        files.present += file("img_orphan")

        cleaner.cleanUpAfterSync()
        assertEquals(listOf("img_orphan"), files.present.map { it.name })

        now += OfflineCacheRepository.SWEEP_INTERVAL_MILLIS
        cleaner.cleanUpAfterSync()
        assertTrue(files.present.isEmpty())
        db.close()
    }

    @Test
    fun `clearing everything drops every copy and every managed file, however recent`() = runBlocking {
        db.bookmarkDao().insertBookmark(bookmark("a", content = "<img src='file://$cacheDir/img_a'>"))
        db.bookmarkDao().insertBookmark(bookmark("b", content = "<p>b</p>"))
        db.assetDao().insertAssets(listOf(asset("x", "b", "$cacheDir/archive_x")))
        val files = FakeFiles(listOf(file("img_a", modifiedAt = now), file("archive_x"), file("journal")))

        val result = cleaner(retentionDays = null, files = files).clearAll()

        assertEquals(2, result.evictedBookmarks)
        assertEquals(2, result.deletedFiles)
        assertNull(row("a").content)
        assertNull(row("b").content)
        assertNull(db.assetDao().getAssetsForBookmark("b", serverId).single().localPath)
        assertEquals(listOf("journal"), files.present.map { it.name })
        assertEquals("b", row("b").title, "the bookmark itself stays")
        db.close()
    }

    @Test
    fun `an explicit clean up always sweeps`() = runBlocking {
        val files = FakeFiles(emptyList())
        val cleaner = cleaner(retentionDays = null, files = files)
        cleaner.cleanUpAfterSync()
        files.present += file("img_orphan")

        cleaner.cleanUp()

        assertTrue(files.present.isEmpty())
        db.close()
    }

    @Test
    fun `the storage cap migration adds its columns empty`() = runBlocking {
        db.close()
        java.io.File(dbPath).delete()
        val c = BundledSQLiteDriver().open(dbPath)
        try {
            c.execSQL("CREATE TABLE bookmarks (localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            c.execSQL("INSERT INTO bookmarks DEFAULT VALUES")

            MIGRATION_15_16.migrate(c)

            val stmt = c.prepare("SELECT lastOpenedAt, offlineEvictedAt FROM bookmarks")
            assertTrue(stmt.step())
            assertTrue(stmt.isNull(0))
            assertTrue(stmt.isNull(1))
            stmt.close()
        } finally {
            c.close()
        }
    }

    @Test
    fun `the migration adds the column empty`() = runBlocking {
        db.close()
        java.io.File(dbPath).delete()
        val c = BundledSQLiteDriver().open(dbPath)
        try {
            c.execSQL(
                "CREATE TABLE bookmarks (localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "remoteId TEXT NOT NULL, isRead INTEGER NOT NULL)"
            )
            c.execSQL("INSERT INTO bookmarks (remoteId, isRead) VALUES ('a', 1)")

            MIGRATION_14_15.migrate(c)

            val stmt = c.prepare("SELECT readOrArchivedAt FROM bookmarks")
            assertTrue(stmt.step())
            assertTrue(stmt.isNull(0))
            stmt.close()
        } finally {
            c.close()
        }
    }
}
