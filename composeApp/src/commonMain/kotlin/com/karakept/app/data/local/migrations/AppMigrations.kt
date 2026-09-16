package com.karakept.app.data.local.migrations

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import com.karakept.app.data.local.AppDatabase

/**
 * Every migration the database ships with, in order. Applied through [withAppSchema], so a
 * migration reachable on one platform but not the other is not a shape this can take — it used to
 * be, and the consequence was a silent destructive fallback for whoever was missing it.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
)

/**
 * Applies everything a builder needs beyond the entities.
 *
 * Every builder goes through this — both platform builders and any test that opens a real
 * database — so there is one list of migrations rather than a copy per call site to keep in step.
 */
fun RoomDatabase.Builder<AppDatabase>.withAppSchema(): RoomDatabase.Builder<AppDatabase> =
    addMigrations(*ALL_MIGRATIONS)
