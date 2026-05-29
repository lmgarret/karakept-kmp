package com.karakept.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN type TEXT NOT NULL DEFAULT 'link'")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN sourceUrl TEXT")
    }
}
