package com.karakept.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN readingProgress REAL NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN readingScrollIndex INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN readingScrollOffset INTEGER NOT NULL DEFAULT 0")
    }
}
