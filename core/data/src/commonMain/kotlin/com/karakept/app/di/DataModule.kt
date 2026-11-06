package com.karakept.app.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.AppDatabase
import com.karakept.app.data.local.compact
import com.karakept.app.data.local.createDataStore
import com.karakept.app.data.local.getDatabaseBuilder
import com.karakept.app.data.local.reclaimableBytes
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.createHttpClient
import com.karakept.app.data.repository.AssetRepository
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.OfflineCacheRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.ServerVersionRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.resolveOfflineScope
import com.karakept.app.data.repository.toHierarchyNode
import com.karakept.app.data.secure.SecureCredentialStore
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.TagFilterRequests
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.DefaultAppDispatchers
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import org.koin.dsl.module

// The on-disk database, kept apart so a test can swap in inMemoryDatabaseModule alone. Declared
// above dataModule on purpose: top-level vals initialise in file order, and dataModule includes it.
internal val databaseModule = module {
    single<AppDatabase> {
        getDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}

/**
 * Everything the data and domain layers bind. Declared here, not in the app's module, because
 * the DAOs and RemoteDataSource they are built from are internal to :core:data.
 *
 * Expects the app to provide a [com.karakept.app.data.cache.ThumbnailCache]: the image loader
 * behind it is set up by the app.
 */
val dataModule = module {
    single<AppDispatchers> { DefaultAppDispatchers() }

    includes(databaseModule)

    single<HttpClient> { createHttpClient() }

    // RemoteDataSource with offline mode guard (blocks requests only when the user has
    // manually enabled offline mode).
    single {
        val settingsRepo = get<SettingsRepository>()
        RemoteDataSource(
            client = get(),
            offlineModeProvider = { settingsRepo.offlineMode.first() }
        )
    }

    single { get<AppDatabase>().serverDao() }
    single { get<AppDatabase>().bookmarkDao() }
    single { get<AppDatabase>().assetDao() }
    single { get<AppDatabase>().pendingActionDao() }
    single { get<AppDatabase>().highlightDao() }
    single { get<AppDatabase>().listDao() }

    single { ImageCacheManager(get()) }

    single { createDataStore() }

    single { SecureCredentialStore() }
    single { ServerRepository(get(), get(), get()) }
    single { SettingsRepository(get()) }
    single { ListRepository(get(), get(), get(), get()) }  // RemoteDataSource, ListDao, SettingsRepository, AppDispatchers
    single { ServerVersionRepository(get(), get()) }
    single { AssetRepository(get(), get(), get()) }  // AssetDao, RemoteDataSource, AppDispatchers

    // BookmarkActionsRepository depends on BookmarkDao, PendingActionDao, RemoteDataSource, ServerRepository, SettingsRepository, AppDispatchers
    single { BookmarkActionsRepository(get(), get(), get(), get(), get(), get()) }

    single { HighlightRepository(get(), get(), get()) }
    single {
        val database = get<AppDatabase>()
        val settings = get<SettingsRepository>()
        val listDao = get<com.karakept.app.data.local.dao.ListDao>()
        OfflineCacheRepository(
            bookmarkDao = get(),
            assetDao = get(),
            settingsRepository = settings,
            appDispatchers = get(),
            reclaimableDatabaseBytes = { database.reclaimableBytes() },
            compactDatabase = { database.compact() },
            thumbnailCache = get(),
            offlineScope = {
                settings.resolveOfflineScope(listDao.getAllListsOnce().map { it.toHierarchyNode() })
            }
        )
    }

    // BookmarkRepository depends on BookmarkActionsRepository and HighlightRepository
    single {
        BookmarkRepository(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()).also {
            // Wire up circular dependencies
            val actionsRepo = get<BookmarkActionsRepository>()
            actionsRepo.setBookmarkRepository(it)
            actionsRepo.setHighlightDao(get())
        }
    }

    // Action system - centralized action handling with undo support
    single { ActionSnackbarManager() }
    single { TagFilterRequests() }
    single { BookmarkActionController(get(), get(), get(), get(), get(), get()) }

    single { BackupRepository(get(), get()) }
}
