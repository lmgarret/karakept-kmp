package com.karakept.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        // Add bannerImageAssetId column for banner image fallback
        connection.execSQL(
            "ALTER TABLE bookmarks ADD COLUMN bannerImageAssetId TEXT DEFAULT NULL"
        )
    }
}
