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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        // Add icon column with default clipboard emoji
        connection.execSQL("ALTER TABLE saved_filters ADD COLUMN icon TEXT NOT NULL DEFAULT '📋'")

        // Add displayOrder column
        connection.execSQL("ALTER TABLE saved_filters ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")

        // Add isVisibleInDrawer column (1 = true in SQLite)
        connection.execSQL("ALTER TABLE saved_filters ADD COLUMN isVisibleInDrawer INTEGER NOT NULL DEFAULT 1")

        // Initialize displayOrder based on existing id order to preserve sequence
        connection.execSQL("""
            UPDATE saved_filters
            SET displayOrder = (
                SELECT COUNT(*)
                FROM saved_filters AS s2
                WHERE s2.id <= saved_filters.id
            )
        """)
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        // Add isQuickFilter column (0 = false in SQLite)
        connection.execSQL("ALTER TABLE saved_filters ADD COLUMN isQuickFilter INTEGER NOT NULL DEFAULT 0")
    }
}
