package com.karakept.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [DataStore]<[Preferences]> implementation for tests.
 *
 * Reusable across test suites -- pass an [initial] snapshot to pre-seed values
 * (e.g. for corrupt-data or migration tests).
 */
class FakeDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences
    ): Preferences = mutex.withLock {
        state.updateAndGet { current -> transform(current) }
    }
}
