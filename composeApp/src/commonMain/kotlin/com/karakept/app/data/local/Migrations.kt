package com.karakept.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // Add imageUrl and description columns to bookmarks table
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN imageUrl TEXT")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN description TEXT")
    }
}
