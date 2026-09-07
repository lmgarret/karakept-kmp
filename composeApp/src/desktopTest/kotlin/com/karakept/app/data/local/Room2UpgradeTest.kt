package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.karakept.app.data.local.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Room 3 must open a database file written by the Room 2.8 build unchanged. If the schema identity
 * hash or the `room_master_table` layout differed across the major version, the builders'
 * `fallbackToDestructiveMigration(true)` would not surface it as a crash — it would silently drop
 * every table, taking queued pending actions, downloaded content and unsynced reading progress
 * with it.
 *
 * The v12 DDL below is `schemas/…/12.json` as the Room 2.8 compiler emitted it, identity hash
 * included, so this is byte-for-byte what an upgrading install has on disk. Opening it also
 * runs it forward through [MIGRATION_12_13][com.karakept.app.data.local.migrations.MIGRATION_12_13],
 * which is why the bookmark comes back keyed on the server's own id rather than the old hash —
 * what that migration does with the row is covered in `Migration12To13Test`.
 */
class Room2UpgradeTest {

    private val dbPath = "/tmp/karakept-room2-upgrade-${System.nanoTime()}.db"

    @AfterTest
    fun cleanup() {
        java.io.File(dbPath).delete()
    }

    @Test
    fun room2Database_opensUnderRoom3_withoutDestructiveFallback() = runBlocking {
        writeRoom2Database()

        val db = Room.databaseBuilder<AppDatabase>(name = dbPath)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(true)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        try {
            val server = db.serverDao().getServerById("server-1")
            assertNotNull(server, "Row written by Room 2.8 survived the upgrade")
            assertEquals("https://karakeep.example", server.url)

            val bookmark = db.bookmarkDao().getBookmarkByRemoteId("remote-42", "server-1")
            assertNotNull(bookmark, "Bookmark written by Room 2.8 survived the upgrade")
            assertEquals("Kept across the major version", bookmark.title)
        } finally {
            db.close()
        }
    }

    private fun writeRoom2Database() {
        val connection = BundledSQLiteDriver().open(dbPath)
        try {
            ROOM_2_V12_SCHEMA.forEach { connection.execSQL(it) }
            connection.execSQL(
                "INSERT INTO servers (id, url, apiKey, label) " +
                    "VALUES ('server-1', 'https://karakeep.example', 'key', 'Test')"
            )
            connection.execSQL(
                "INSERT INTO bookmarks (remoteId, originalRemoteId, serverId, url, title, createdAt, " +
                    "isArchived, isStarred, isRead, tags, listIds, readingTimeMinutes, readingProgress, " +
                    "readingScrollIndex, readingScrollOffset, progressSyncedAt) " +
                    "VALUES (42, 'remote-42', 'server-1', 'https://example.com', " +
                    "'Kept across the major version', 1000, 0, 0, 0, '', '', 0, 0.0, 0, 0, 0)"
            )
            connection.execSQL("PRAGMA user_version = 12")
        } finally {
            connection.close()
        }
    }
}

/** `schemas/com.karakept.app.data.local.AppDatabase/12.json`, as written by the Room 2.8 compiler. */
private val ROOM_2_V12_SCHEMA = listOf(
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
