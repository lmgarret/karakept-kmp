package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE pending_actions ADD COLUMN status TEXT NOT NULL DEFAULT 'pending'")
        connection.execSQL("ALTER TABLE pending_actions ADD COLUMN nextAttemptAt INTEGER NOT NULL DEFAULT 0")
    }
}
