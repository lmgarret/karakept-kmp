package com.karakept.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        // Add screenshotAssetId column for screenshot fallback
        connection.execSQL(
            "ALTER TABLE bookmarks ADD COLUMN screenshotAssetId TEXT DEFAULT NULL"
        )
    }
}
