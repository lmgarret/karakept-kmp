package com.karakept.app.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.AppDatabase
import com.karakept.app.data.local.createDataStore
import com.karakept.app.data.local.getDatabaseBuilder
import com.karakept.app.data.remote.createHttpClient
import io.ktor.client.HttpClient
import org.koin.dsl.module
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.api.infrastructure.ApiClient
import com.karakept.api.client.*
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.ui.screens.LoginScreenModel
import com.karakept.app.ui.screens.OnboardingScreenModel
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.app.ui.screens.BookmarkViewerScreenModel
import com.karakept.app.ui.screens.ReaderAppearanceScreenModel
import com.karakept.app.ui.screens.SettingsScreenModel
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.ui.screens.HighlightsScreenModel
import kotlinx.coroutines.flow.first

val appModule = module {
    single<AppDatabase> {
        getDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    single<HttpClient> {
        createHttpClient()
    }

    // Generated API Client and Services
    val defaultBaseUrl = "https://try.karakept.app/api/v1"
    single<ApiClient> {
        ApiClient(
            baseUrl = defaultBaseUrl,
            httpClient = get()
        )
    }
    single { BookmarksApi(baseUrl = defaultBaseUrl, httpClient = get<HttpClient>()) }
    single { ListsApi(baseUrl = defaultBaseUrl, httpClient = get<HttpClient>()) }
    single { TagsApi(baseUrl = defaultBaseUrl, httpClient = get<HttpClient>()) }
    single { HighlightsApi(baseUrl = defaultBaseUrl, httpClient = get<HttpClient>()) }
    single { UsersApi(baseUrl = defaultBaseUrl, httpClient = get<HttpClient>()) }

    // RemoteDataSource with offline mode guard
    single {
        val settingsRepo = get<SettingsRepository>()
        RemoteDataSource(
            client = get(),
            offlineModeProvider = { settingsRepo.effectiveOfflineMode.first() }
        )
    }

    single { get<AppDatabase>().serverDao() }
    single { get<AppDatabase>().bookmarkDao() }
    single { get<AppDatabase>().assetDao() }
    single { get<AppDatabase>().pendingActionDao() }
    single { get<AppDatabase>().highlightDao() }
    single { get<AppDatabase>().listDao() }

    single { com.karakept.app.utils.ImageCacheManager(get()) }

    single { createDataStore() }

    single { ServerRepository(get()) }
    single { SettingsRepository(get()) }
    single { ListRepository(get(), get(), get()) }  // RemoteDataSource, ListDao, SettingsRepository

    // BookmarkActionsRepository depends on BookmarkDao, PendingActionDao, RemoteDataSource, ServerRepository, SettingsRepository
    single { BookmarkActionsRepository(get(), get(), get(), get(), get()) }

    // HighlightRepository
    single { HighlightRepository(get(), get(), get()) }

    // BookmarkRepository depends on BookmarkActionsRepository and HighlightRepository
    single {
        BookmarkRepository(get(), get(), get(), get(), get(), get(), get(), get()).also {
            // Wire up circular dependencies
            val actionsRepo = get<BookmarkActionsRepository>()
            actionsRepo.setBookmarkRepository(it)
            actionsRepo.setHighlightDao(get())
        }
    }

    // Action system - centralized action handling with undo support
    single { ActionSnackbarManager() }
    single { BookmarkActionController(get(), get(), get(), get(), get()) }

    factory { LoginScreenModel(get(), get(), get()) }
    factory { OnboardingScreenModel(get(), get(), get()) }
    single { MainScreenModel(get(), get(), get(), get(), get(), get(), get()) }
    factory { BookmarkViewerScreenModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SettingsScreenModel(get(), get(), get(), get()) }
    factory { HighlightsScreenModel(get(), get(), get(), get()) }
    factory { com.karakept.app.ui.screens.settings.ListManagementScreenModel(get(), get()) }
    factory { ReaderAppearanceScreenModel(get()) }
}
