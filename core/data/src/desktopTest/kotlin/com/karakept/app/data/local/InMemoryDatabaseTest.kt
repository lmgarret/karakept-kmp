package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before

/**
 * A fresh in-memory [AppDatabase] per test, opened before the subclass's own `@Before` runs and
 * closed after its `@After`.
 */
internal abstract class InMemoryDatabaseTest {

    protected lateinit var db: AppDatabase

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }
}
