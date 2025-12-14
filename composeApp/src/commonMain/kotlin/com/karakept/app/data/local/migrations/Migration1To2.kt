package com.karakept.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // Add readingTimeMinutes column with default value 0
        connection.execSQL(
            "ALTER TABLE bookmarks ADD COLUMN readingTimeMinutes INTEGER NOT NULL DEFAULT 0"
        )
    }
}
