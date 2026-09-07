package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN crawlStatus TEXT")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN crawledAt INTEGER")
    }
}
