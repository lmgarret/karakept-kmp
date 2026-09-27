package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * What the offline storage cap needs: when a bookmark was last opened, to evict the least
 * recently used copy first, and a marker for copies it evicted, so sync leaves them evicted.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN lastOpenedAt INTEGER")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN offlineEvictedAt INTEGER")
    }
}
