package com.karakept.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        // Create lists table for offline access to bookmark lists
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS lists (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT NOT NULL,
                serverId TEXT NOT NULL,
                name TEXT NOT NULL,
                icon TEXT,
                parentId TEXT,
                isPublic INTEGER NOT NULL DEFAULT 0,
                description TEXT,
                type TEXT NOT NULL DEFAULT 'manual',
                query TEXT,
                updatedAt INTEGER NOT NULL
            )
        """)
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_lists_remoteId_serverId ON lists (remoteId, serverId)"
        )
    }
}
