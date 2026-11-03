package com.karakept.app.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.AppDatabase
import org.koin.dsl.module

/**
 * Replaces the on-disk database with an empty in-memory one. Load it after [dataModule], with
 * overrides allowed. For tests and tooling outside :core:data, which cannot name AppDatabase.
 */
fun inMemoryDatabaseModule() = module {
    single<AppDatabase> {
        Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}
