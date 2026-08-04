package com.karakept.app.data.local

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.karakept.app.data.local.migrations.MIGRATION_8_9
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies Migration 8→9 adds the status/nextAttemptAt columns to pending_actions
 * and back-fills existing rows with the retryable defaults.
 */
class Migration8To9Test {

    private val dbPath = "/tmp/karakept-migration-8-9-${System.nanoTime()}.db"

    @AfterTest
    fun cleanup() {
        java.io.File(dbPath).delete()
    }

    @Test
    fun migration8To9_addsStatusAndBackoffColumnsWithDefaults() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(dbPath)
        try {
            // v8 schema
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_actions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`bookmarkRemoteId` INTEGER NOT NULL, `serverId` TEXT NOT NULL, " +
                    "`actionType` TEXT NOT NULL, `actionData` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT)"
            )
            connection.execSQL(
                "INSERT INTO pending_actions (bookmarkRemoteId, serverId, actionType, actionData, createdAt, retryCount) " +
                    "VALUES (42, 'server-1', 'archive', '{}', 1000, 0)"
            )

            MIGRATION_8_9.migrate(connection)

            connection.prepare("SELECT status, nextAttemptAt FROM pending_actions WHERE bookmarkRemoteId = 42").use { stmt ->
                assertTrue(stmt.step(), "Row should survive the migration")
                assertEquals("pending", stmt.getText(0), "Existing rows default to pending")
                assertEquals(0L, stmt.getLong(1), "Existing rows default to immediate eligibility")
            }
        } finally {
            connection.close()
        }
    }
}
