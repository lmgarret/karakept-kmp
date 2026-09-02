package com.karakept.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN summary TEXT")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN summarizationStatus TEXT")
    }
}
