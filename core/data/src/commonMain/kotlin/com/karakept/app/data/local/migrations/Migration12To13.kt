package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Retires the 32-bit hash that used to stand in for a bookmark's identity (#281).
 *
 * `bookmarks.remoteId` held `serverId.hashCode().toLong()`, and that hash was the local
 * identity key, the value `pending_actions` and `assets` pointed at, and the list's item key.
 * A 32-bit space collides at roughly 1.2% across 10k bookmarks and 25% across 50k, and a
 * collision means two distinct bookmarks share a key: the unique index's REPLACE drops one,
 * and a queued action lands on whichever of them is left. This migration makes the column the
 * server's own string id, which is what the API was being called with all along — the old
 * `originalRemoteId` column, now folded into `remoteId`.
 *
 * SQLite cannot change a column's type, so all three tables are rebuilt. The rewrite is
 * per-row rather than a single join because the old `bookmarkRemoteId` did not mean the same
 * thing in every row:
 *
 *  - the highlight actions stored the bookmark's **local row id**, so they join on `localId`;
 *  - every other action stored the 32-bit **hash**, so they join on the old `remoteId`;
 *  - a DELETE outlives the row it names — the bookmark is gone locally the moment it is
 *    queued — so its id is recovered from the `originalRemoteId` its JSON payload carries.
 *
 * A row none of the three resolves gets an empty id: nothing to route it to, and the queue
 * would rather carry an inert action than lose one silently. `executeAction` drops those on
 * the next pass, except for the two highlight actions, which name a highlight and never look
 * at the bookmark id.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // pending_actions and assets are rewritten first: both resolve their new id against
        // the still-hash-keyed bookmarks table.
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_actions_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bookmarkRemoteId TEXT NOT NULL,
                serverId TEXT NOT NULL,
                actionType TEXT NOT NULL,
                actionData TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                retryCount INTEGER NOT NULL,
                lastError TEXT,
                status TEXT NOT NULL,
                nextAttemptAt INTEGER NOT NULL
            )
            """
        )
        connection.execSQL(
            """
            INSERT INTO pending_actions_new (
                id, bookmarkRemoteId, serverId, actionType, actionData,
                createdAt, retryCount, lastError, status, nextAttemptAt
            )
            SELECT
                p.id,
                COALESCE(
                    CASE
                        WHEN p.actionType IN ('create_highlight', 'update_highlight', 'delete_highlight')
                            THEN (
                                SELECT b.originalRemoteId FROM bookmarks b
                                WHERE b.localId = p.bookmarkRemoteId AND b.serverId = p.serverId
                            )
                        ELSE (
                            SELECT b.originalRemoteId FROM bookmarks b
                            WHERE b.remoteId = p.bookmarkRemoteId AND b.serverId = p.serverId
                        )
                    END,
                    $JSON_ORIGINAL_REMOTE_ID,
                    ''
                ),
                p.serverId, p.actionType, p.actionData,
                p.createdAt, p.retryCount, p.lastError, p.status, p.nextAttemptAt
            FROM pending_actions p
            """
        )
        connection.execSQL("DROP TABLE pending_actions")
        connection.execSQL("ALTER TABLE pending_actions_new RENAME TO pending_actions")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS assets_new (
                id TEXT NOT NULL,
                bookmarkRemoteId TEXT NOT NULL,
                serverId TEXT NOT NULL,
                assetType TEXT NOT NULL,
                fileName TEXT,
                contentType TEXT,
                localPath TEXT,
                PRIMARY KEY(id)
            )
            """
        )
        connection.execSQL(
            """
            INSERT INTO assets_new (id, bookmarkRemoteId, serverId, assetType, fileName, contentType, localPath)
            SELECT
                a.id,
                COALESCE((
                    SELECT b.originalRemoteId FROM bookmarks b
                    WHERE b.remoteId = a.bookmarkRemoteId AND b.serverId = a.serverId
                ), ''),
                a.serverId, a.assetType, a.fileName, a.contentType, a.localPath
            FROM assets a
            """
        )
        connection.execSQL("DROP TABLE assets")
        connection.execSQL("ALTER TABLE assets_new RENAME TO assets")

        // bookmarks last, so the joins above still had the hash to resolve against.
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks_new (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT NOT NULL,
                serverId TEXT NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT,
                imageUrl TEXT,
                bannerImageAssetId TEXT,
                screenshotAssetId TEXT,
                description TEXT,
                createdAt INTEGER NOT NULL,
                isArchived INTEGER NOT NULL,
                isStarred INTEGER NOT NULL,
                isRead INTEGER NOT NULL,
                tags TEXT NOT NULL,
                listIds TEXT NOT NULL,
                readingTimeMinutes INTEGER NOT NULL,
                readingProgress REAL NOT NULL,
                readingScrollIndex INTEGER NOT NULL,
                readingScrollOffset INTEGER NOT NULL,
                modifiedAt INTEGER,
                progressSyncedAt INTEGER NOT NULL,
                crawlStatus TEXT,
                crawledAt INTEGER,
                summary TEXT,
                summarizationStatus TEXT
            )
            """
        )
        // The old unique index was on the hash, so two bookmarks whose ids collided could
        // never both be here — but two *rows* for the same string id could, one per colliding
        // insert that REPLACE happened to spare. GROUP BY keeps the highest localId of each,
        // which is the most recently written, so the new unique index can be created.
        connection.execSQL(
            """
            INSERT INTO bookmarks_new (
                localId, remoteId, serverId, url, title, content, imageUrl,
                bannerImageAssetId, screenshotAssetId, description, createdAt,
                isArchived, isStarred, isRead, tags, listIds, readingTimeMinutes,
                readingProgress, readingScrollIndex, readingScrollOffset, modifiedAt,
                progressSyncedAt, crawlStatus, crawledAt, summary, summarizationStatus
            )
            SELECT
                MAX(localId), originalRemoteId, serverId, url, title, content, imageUrl,
                bannerImageAssetId, screenshotAssetId, description, createdAt,
                isArchived, isStarred, isRead, tags, listIds, readingTimeMinutes,
                readingProgress, readingScrollIndex, readingScrollOffset, modifiedAt,
                progressSyncedAt, crawlStatus, crawledAt, summary, summarizationStatus
            FROM bookmarks
            GROUP BY originalRemoteId, serverId
            """
        )
        connection.execSQL("DROP TABLE bookmarks")
        connection.execSQL("ALTER TABLE bookmarks_new RENAME TO bookmarks")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_remoteId_serverId ON bookmarks (remoteId, serverId)"
        )
    }
}

/**
 * Pulls `originalRemoteId` out of a DELETE action's JSON payload with string functions rather
 * than `json_extract`: the JSON1 extension is not something an arbitrary Android build's
 * SQLite can be assumed to carry, and this migration runs on whatever the device ships.
 *
 * The payload is `{"originalRemoteId":"<id>"}` written by kotlinx-serialization, and Karakeep
 * ids are alphanumeric, so the value never contains an escape. Yields NULL when the key is
 * absent, which is what lets it sit inside a COALESCE.
 */
private const val JSON_ORIGINAL_REMOTE_ID = """
    CASE WHEN instr(p.actionData, '"originalRemoteId":"') > 0 THEN
        substr(
            p.actionData,
            instr(p.actionData, '"originalRemoteId":"') + 20,
            instr(substr(p.actionData, instr(p.actionData, '"originalRemoteId":"') + 20), '"') - 1
        )
    END
"""
