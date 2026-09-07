package com.karakept.app.data.local.migrations

import androidx.room3.migration.Migration

/**
 * Every migration the database ships with, in order. Platform builders and the tests that open a
 * pre-existing file all add the same array — a migration reachable on one platform but not the
 * other is a silent destructive fallback for whoever is missing it.
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
)
