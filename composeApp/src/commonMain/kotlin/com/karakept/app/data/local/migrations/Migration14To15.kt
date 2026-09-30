package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Records when a bookmark became read or archived, so its offline copy can be dropped a set
 * number of days later. Left null here: the first cleanup stamps every bookmark that is already
 * read or archived with that moment, so nothing is evicted on the day of the upgrade.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN readOrArchivedAt INTEGER")
    }
}
