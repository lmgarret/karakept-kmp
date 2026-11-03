package com.karakept.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.karakept.app.data.cache.ThumbnailCache
import com.karakept.app.data.local.AppDatabase
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.AssetRepository
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.FakeDataStore
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.OfflineCacheRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.ServerVersionRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.secure.SecureCredentialStore
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.TagFilterRequests
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.TestAppDispatchers
import io.ktor.client.HttpClient
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Resolves every single [dataModule] declares against stand-ins for the platform and IO ones, so
 * a constructor that gains a parameter nothing provides fails here rather than at startup. The
 * app's half of the graph is AppModuleGraphTest's.
 */
class DataModuleGraphTest {

    private val testDispatcher = StandardTestDispatcher()
    private var app: KoinApplication? = null

    private val testOverrides = module {
        single<AppDatabase> { mockk(relaxed = true) }
        single<HttpClient> { mockk(relaxed = true) }
        single<DataStore<Preferences>> { FakeDataStore() }
        single<SecureCredentialStore> { mockk(relaxed = true) }
        single<AppDispatchers> { TestAppDispatchers(testDispatcher) }
        // Provided by the app.
        single<ThumbnailCache> { mockk(relaxed = true) }
    }

    @BeforeTest
    fun setUp() {
        app = koinApplication {
            allowOverride(true)
            modules(dataModule, testOverrides)
        }
    }

    @AfterTest
    fun tearDown() {
        app?.close()
    }

    @Test
    fun everyDataSingleResolves() {
        val koin = app!!.koin
        listOf<KClass<*>>(
            ServerRepository::class,
            SettingsRepository::class,
            ListRepository::class,
            ServerVersionRepository::class,
            BookmarkActionsRepository::class,
            HighlightRepository::class,
            OfflineCacheRepository::class,
            BookmarkRepository::class,
            BackupRepository::class,
            AssetRepository::class,
            RemoteDataSource::class,
            ActionSnackbarManager::class,
            TagFilterRequests::class,
            BookmarkActionController::class,
        ).forEach { assertNotNull(koin.get<Any>(it), "${it.simpleName} did not resolve") }
    }
}
