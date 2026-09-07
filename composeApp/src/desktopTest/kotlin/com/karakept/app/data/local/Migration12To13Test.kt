package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.local.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migration 12 → 13 retires the 32-bit hash that stood in for a bookmark's identity (#281).
 *
 * The v12 DDL below is `schemas/…/12.json` verbatim, so these run against exactly what an
 * upgrading install has on disk. What is being checked is the *rewrite*: `bookmarks.remoteId`
 * becomes the server's own string id, and the two tables that pointed at the hash —
 * `pending_actions` and `assets` — are repointed at it. Each of the three populations
 * `pending_actions.bookmarkRemoteId` could hold gets its own case, because the column did not
 * mean the same thing in every row.
 */
class Migration12To13Test {

    private val dbPath = "/tmp/karakept-migration-12-13-${System.nanoTime()}.db"

    @AfterTest
    fun cleanup() {
        java.io.File(dbPath).delete()
    }

    @Test
    fun bookmarkRowsAreRekeyedOnTheServersOwnId(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertBookmark(localId = 1, remoteId = "bk-alpha".hashCode(), originalRemoteId = "bk-alpha")
            connection.insertBookmark(localId = 2, remoteId = "bk-beta".hashCode(), originalRemoteId = "bk-beta")
        }

        openMigrated().use { db ->
            val alpha = db.bookmarkDao().getBookmarkByRemoteId("bk-alpha", SERVER_ID)
            assertNotNull(alpha, "the row survives the rebuild")
            assertEquals(1L, alpha.localId, "and keeps its local id, so scroll state still points at it")
            assertEquals("Title bk-alpha", alpha.title)
            assertNotNull(db.bookmarkDao().getBookmarkByRemoteId("bk-beta", SERVER_ID))
        }
    }

    /**
     * The reason for the migration. Two ids whose 32-bit `hashCode` collides cannot both be in
     * the v12 table at all: the unique index is on the hash, so the second insert REPLACEs the
     * first and one bookmark disappears with no error anywhere. Post-v13 the index is on the
     * real ids and the pair coexists.
     */
    @Test
    fun idsSharingA32BitHashCoexistAfterTheMigration(): Unit = runBlocking {
        assertEquals(
            COLLIDING_A.hashCode(), COLLIDING_B.hashCode(),
            "fixture precondition: these two ids collide in the old 32-bit key space"
        )

        writeV12 { connection ->
            connection.insertBookmark(localId = 1, remoteId = COLLIDING_A.hashCode(), originalRemoteId = COLLIDING_A)
            // What the app did on sync: an upsert keyed on the hash, which here silently
            // evicts the row above rather than storing a second bookmark.
            connection.insertBookmark(
                localId = 2, remoteId = COLLIDING_B.hashCode(), originalRemoteId = COLLIDING_B, orReplace = true
            )
        }

        openMigrated().use { db ->
            val afterMigration = db.bookmarkDao().getBookmarksForServer(SERVER_ID).first()
            assertEquals(
                listOf(COLLIDING_B), afterMigration.map { it.remoteId },
                "v12 could only hold one of the pair — that loss is what this migration stops repeating"
            )

            // The evicted bookmark comes back on the next sync. Under the old index that insert
            // would have evicted the other one right back; under the new one both are kept.
            db.bookmarkDao().insertBookmark(
                afterMigration.single().copy(localId = 0, remoteId = COLLIDING_A, title = "Recovered")
            )

            val rows = db.bookmarkDao().getBookmarksForServer(SERVER_ID).first()
            assertEquals(2, rows.size, "the real ids are distinct, so both bookmarks are stored")
            assertEquals(
                setOf(COLLIDING_A, COLLIDING_B),
                rows.mapTo(mutableSetOf()) { it.remoteId }
            )
        }
    }

    /** An ordinary queued action stored the hash, so it resolves through the bookmarks table. */
    @Test
    fun anActionKeyedOnTheHashIsRepointedAtTheStringId(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertBookmark(localId = 7, remoteId = "bk-alpha".hashCode(), originalRemoteId = "bk-alpha")
            connection.insertAction(
                id = 1,
                bookmarkRemoteId = "bk-alpha".hashCode().toLong(),
                actionType = PendingActionType.ARCHIVE,
                actionData = "{}"
            )
        }

        openMigrated().use { db ->
            val action = db.pendingActionDao().getPendingActionsList(SERVER_ID).single()
            assertEquals("bk-alpha", action.bookmarkRemoteId)
            assertEquals(PendingActionType.ARCHIVE, action.actionType)
        }
    }

    /** Highlight actions stored the bookmark's *local row id*, so they resolve through localId. */
    @Test
    fun aHighlightActionKeyedOnTheLocalIdIsRepointedAtTheStringId(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertBookmark(localId = 7, remoteId = "bk-alpha".hashCode(), originalRemoteId = "bk-alpha")
            connection.insertAction(
                id = 1,
                bookmarkRemoteId = 7,
                actionType = PendingActionType.CREATE_HIGHLIGHT,
                actionData = """{"bookmarkRemoteId":"bk-alpha","text":"quoted"}"""
            )
            connection.insertAction(
                id = 2,
                bookmarkRemoteId = 7,
                actionType = PendingActionType.DELETE_HIGHLIGHT,
                actionData = """{"highlightId":"hl-1"}"""
            )
        }

        openMigrated().use { db ->
            val actions = db.pendingActionDao().getPendingActionsList(SERVER_ID)
            assertEquals(2, actions.size)
            assertTrue(
                actions.all { it.bookmarkRemoteId == "bk-alpha" },
                "a local row id is not a hash — resolving it as one would have lost the highlight"
            )
        }
    }

    /**
     * A DELETE outlives the row it names: the bookmark is gone locally the moment it is
     * queued, so neither join can reach it and the id comes out of the JSON payload. Losing
     * this one would resurrect the bookmark on the next sync.
     */
    @Test
    fun aDeleteWhoseBookmarkIsAlreadyGoneRecoversItsIdFromTheActionData(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertAction(
                id = 1,
                bookmarkRemoteId = "bk-vanished".hashCode().toLong(),
                actionType = PendingActionType.DELETE,
                actionData = """{"originalRemoteId":"bk-vanished"}"""
            )
        }

        openMigrated().use { db ->
            val action = db.pendingActionDao().getPendingActionsList(SERVER_ID).single()
            assertEquals("bk-vanished", action.bookmarkRemoteId)
        }
    }

    /** Nothing resolves: the row is kept with an empty id rather than dropped silently. */
    @Test
    fun anUnresolvableActionIsKeptWithAnEmptyId(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertAction(
                id = 1,
                bookmarkRemoteId = 123456,
                actionType = PendingActionType.FAVOURITE,
                actionData = "{}"
            )
        }

        openMigrated().use { db ->
            val action = db.pendingActionDao().getPendingActionsList(SERVER_ID).single()
            assertEquals("", action.bookmarkRemoteId)
        }
    }

    @Test
    fun assetsAreRepointedAtTheirBookmarksStringId(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertBookmark(localId = 3, remoteId = "bk-alpha".hashCode(), originalRemoteId = "bk-alpha")
            connection.execSQL(
                "INSERT INTO assets (id, bookmarkRemoteId, serverId, assetType, fileName, contentType, localPath) " +
                    "VALUES ('asset-1', ${"bk-alpha".hashCode()}, '$SERVER_ID', 'bannerImage', NULL, NULL, '/tmp/a.png')"
            )
            connection.execSQL(
                "INSERT INTO assets (id, bookmarkRemoteId, serverId, assetType, fileName, contentType, localPath) " +
                    "VALUES ('asset-orphan', 999, '$SERVER_ID', 'screenshot', NULL, NULL, NULL)"
            )
        }

        openMigrated().use { db ->
            val assets = db.assetDao().getAssetsForBookmark("bk-alpha", SERVER_ID)
            assertEquals(1, assets.size)
            assertEquals("/tmp/a.png", assets.single().localPath, "the downloaded file stays reachable")
            assertTrue(
                db.assetDao().getAssetsForBookmark("", SERVER_ID).any { it.id == "asset-orphan" },
                "an asset whose bookmark is gone is kept, not dropped mid-migration"
            )
        }
    }

    /** Every column the rebuild copies has to arrive, not just the identity. */
    @Test
    fun theRebuildCarriesTheWholeRow(): Unit = runBlocking {
        writeV12 { connection ->
            connection.execSQL(
                """
                INSERT INTO bookmarks (
                    localId, remoteId, originalRemoteId, serverId, url, title, content, imageUrl,
                    bannerImageAssetId, screenshotAssetId, description, createdAt, isArchived,
                    isStarred, isRead, tags, listIds, readingTimeMinutes, readingProgress,
                    readingScrollIndex, readingScrollOffset, modifiedAt, progressSyncedAt,
                    crawlStatus, crawledAt, summary, summarizationStatus
                ) VALUES (
                    1, ${"bk-full".hashCode()}, 'bk-full', '$SERVER_ID', 'https://example.com/full',
                    'Full row', 'article body', 'https://example.com/i.png', 'banner-1', 'shot-1',
                    'meta description', 1700, 1, 1, 1, 'kotlin,room', 'list-a,list-b', 12, 0.75,
                    4, 96, 1800, 1900, 'success', 2000, 'the summary', 'success'
                )
                """
            )
        }

        openMigrated().use { db ->
            val row = assertNotNull(db.bookmarkDao().getBookmarkByRemoteId("bk-full", SERVER_ID))
            assertEquals("article body", row.content)
            assertEquals("kotlin,room", row.tags)
            assertEquals("list-a,list-b", row.listIds)
            assertEquals(0.75f, row.readingProgress)
            assertEquals(4, row.readingScrollIndex)
            assertEquals(96, row.readingScrollOffset)
            assertEquals(1800L, row.modifiedAt)
            assertEquals(1900L, row.progressSyncedAt)
            assertEquals("success", row.crawlStatus)
            assertEquals(2000L, row.crawledAt)
            assertEquals("the summary", row.summary)
            assertEquals("success", row.summarizationStatus)
            assertTrue(row.isArchived && row.isStarred && row.isRead)
        }
    }

    /**
     * Two v12 rows could carry the same string id — one per insert the colliding-hash REPLACE
     * happened to spare — and the new unique index does not allow that. The rebuild keeps the
     * most recently written of each, so the index can be created at all.
     */
    @Test
    fun duplicateStringIdsCollapseToTheMostRecentRow(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertBookmark(localId = 1, remoteId = 111, originalRemoteId = "bk-dup", title = "Stale")
            connection.insertBookmark(localId = 2, remoteId = 222, originalRemoteId = "bk-dup", title = "Fresh")
        }

        openMigrated().use { db ->
            val rows = db.bookmarkDao().getBookmarksForServer(SERVER_ID).first()
            assertEquals(1, rows.size)
            assertEquals("Fresh", rows.single().title)
            assertEquals(2L, rows.single().localId)
        }
    }

    /** Rows belonging to another server must not be joined across. */
    @Test
    fun resolutionIsScopedToTheActionsOwnServer(): Unit = runBlocking {
        writeV12 { connection ->
            connection.insertBookmark(
                localId = 1, remoteId = "bk-alpha".hashCode(), originalRemoteId = "bk-alpha", serverId = "server-other"
            )
            connection.insertAction(
                id = 1,
                bookmarkRemoteId = "bk-alpha".hashCode().toLong(),
                actionType = PendingActionType.ARCHIVE,
                actionData = "{}"
            )
        }

        openMigrated().use { db ->
            val action = db.pendingActionDao().getPendingActionsList(SERVER_ID).single()
            assertEquals("", action.bookmarkRemoteId, "another server's bookmark is not this action's bookmark")
            assertNull(db.bookmarkDao().getBookmarkByRemoteId("bk-alpha", SERVER_ID))
        }
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private fun writeV12(seed: (SQLiteConnection) -> Unit) {
        val connection = BundledSQLiteDriver().open(dbPath)
        try {
            V12_SCHEMA.forEach { connection.execSQL(it) }
            connection.execSQL(
                "INSERT INTO servers (id, url, apiKey, label) VALUES ('$SERVER_ID', 'https://karakeep.example', 'key', 'Test')"
            )
            seed(connection)
            connection.execSQL("PRAGMA user_version = 12")
        } finally {
            connection.close()
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = dbPath)
            .addMigrations(*ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private inline fun <T> AppDatabase.use(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }

    private fun SQLiteConnection.insertBookmark(
        localId: Long,
        remoteId: Int,
        originalRemoteId: String,
        serverId: String = SERVER_ID,
        title: String = "Title $originalRemoteId",
        orReplace: Boolean = false
    ) = execSQL(
        "INSERT ${if (orReplace) "OR REPLACE " else ""}INTO bookmarks " +
            "(localId, remoteId, originalRemoteId, serverId, url, title, createdAt, " +
            "isArchived, isStarred, isRead, tags, listIds, readingTimeMinutes, readingProgress, " +
            "readingScrollIndex, readingScrollOffset, progressSyncedAt) " +
            "VALUES ($localId, $remoteId, '$originalRemoteId', '$serverId', " +
            "'https://example.com/$originalRemoteId', '$title', 1000, 0, 0, 0, '', '', 0, 0.0, 0, 0, 0)"
    )

    private fun SQLiteConnection.insertAction(
        id: Long,
        bookmarkRemoteId: Long,
        actionType: String,
        actionData: String,
        serverId: String = SERVER_ID
    ) = execSQL(
        "INSERT INTO pending_actions (id, bookmarkRemoteId, serverId, actionType, actionData, " +
            "createdAt, retryCount, lastError, status, nextAttemptAt) " +
            "VALUES ($id, $bookmarkRemoteId, '$serverId', '$actionType', '$actionData', 1000, 0, NULL, 'pending', 0)"
    )

    private companion object {
        const val SERVER_ID = "server-1"

        // Two distinct strings with the same 32-bit hashCode: the classic "Aa"/"BB" pair,
        // which is what made the old key space collide at ~1.2% across 10k bookmarks.
        const val COLLIDING_A = "Aa"
        const val COLLIDING_B = "BB"
    }
}

/** `schemas/com.karakept.app.data.local.AppDatabase/12.json`, verbatim. */
private val V12_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `servers` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `apiKey` TEXT NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `bookmarks` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `remoteId` INTEGER NOT NULL, `originalRemoteId` TEXT NOT NULL, `serverId` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT, `imageUrl` TEXT, `bannerImageAssetId` TEXT, `screenshotAssetId` TEXT, `description` TEXT, `createdAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `isStarred` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, `tags` TEXT NOT NULL, `listIds` TEXT NOT NULL, `readingTimeMinutes` INTEGER NOT NULL, `readingProgress` REAL NOT NULL, `readingScrollIndex` INTEGER NOT NULL, `readingScrollOffset` INTEGER NOT NULL, `modifiedAt` INTEGER, `progressSyncedAt` INTEGER NOT NULL, `crawlStatus` TEXT, `crawledAt` INTEGER, `summary` TEXT, `summarizationStatus` TEXT)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_remoteId_serverId` ON `bookmarks` (`remoteId`, `serverId`)",
    "CREATE TABLE IF NOT EXISTS `assets` (`id` TEXT NOT NULL, `bookmarkRemoteId` INTEGER NOT NULL, `serverId` TEXT NOT NULL, `assetType` TEXT NOT NULL, `fileName` TEXT, `contentType` TEXT, `localPath` TEXT, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `pending_actions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookmarkRemoteId` INTEGER NOT NULL, `serverId` TEXT NOT NULL, `actionType` TEXT NOT NULL, `actionData` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `status` TEXT NOT NULL, `nextAttemptAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `highlights` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `remoteId` TEXT NOT NULL, `serverId` TEXT NOT NULL, `bookmarkRemoteId` TEXT NOT NULL, `text` TEXT NOT NULL, `startOffset` INTEGER NOT NULL, `endOffset` INTEGER NOT NULL, `note` TEXT, `color` TEXT, `createdAt` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_highlights_remoteId_serverId` ON `highlights` (`remoteId`, `serverId`)",
    "CREATE INDEX IF NOT EXISTS `index_highlights_bookmarkRemoteId` ON `highlights` (`bookmarkRemoteId`)",
    "CREATE TABLE IF NOT EXISTS `lists` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `remoteId` TEXT NOT NULL, `serverId` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT, `parentId` TEXT, `isPublic` INTEGER NOT NULL, `description` TEXT, `type` TEXT NOT NULL, `query` TEXT, `updatedAt` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_lists_remoteId_serverId` ON `lists` (`remoteId`, `serverId`)",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'aaf9dc92279fc0317b9e2b7dc295ffb4')",
)
