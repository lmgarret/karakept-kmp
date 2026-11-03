package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN modifiedAt INTEGER")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN progressSyncedAt INTEGER NOT NULL DEFAULT 0")
    }
}
