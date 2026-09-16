package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Gives the offline view a column it can be answered from (#273).
 *
 * The view selected on `length(content) > 0`, and `content` holds the whole article body. SQLite
 * has to load a value to compare it, and `length()` on TEXT decodes the UTF-8 to count characters
 * on top of that — so asking which bookmarks are available offline read every article in the
 * table. Measured on a real library it cost 523ms over 861 rows, while the 4280-row archive
 * counted in 15ms; the difference was the predicate, not the size.
 *
 * `hasContent` carries the same answer as one byte per row, and the index over it is what makes
 * the count an index scan rather than a table scan. A partial index on `content` would have
 * avoided the new column, but Room validates the schema it finds against the one its entities
 * declare, and `@Index` has no WHERE clause — an index Room did not declare fails the next
 * migration that runs.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE bookmarks ADD COLUMN hasContent INTEGER NOT NULL DEFAULT 0"
        )
        // The one pass over the bodies, so that nothing else has to make one.
        connection.execSQL(
            "UPDATE bookmarks SET hasContent = 1 WHERE content IS NOT NULL AND content <> ''"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bookmarks_serverId_hasContent_createdAt_localId " +
                "ON bookmarks(serverId, hasContent, createdAt, localId)"
        )
    }
}
