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
import com.karakept.app.data.repository.SavedFilterRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.ui.screens.LoginScreenModel
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.app.ui.screens.BookmarkViewerScreenModel
import com.karakept.app.ui.screens.ReaderAppearanceScreenModel
import com.karakept.app.ui.screens.SettingsScreenModel
import com.karakept.app.ui.screens.FilterManagementScreenModel
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController

val appModule = module {
    single<AppDatabase> {
        getDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .build()
    }
    
    single<HttpClient> {
        createHttpClient()
    }
    
    single { RemoteDataSource(get()) }
    
    single { get<AppDatabase>().serverDao() }
    single { get<AppDatabase>().bookmarkDao() }
    single { get<AppDatabase>().savedFilterDao() }
    single { get<AppDatabase>().assetDao() }
    single { get<AppDatabase>().pendingActionDao() }
    
    single { createDataStore() }

    single { ServerRepository(get()) }
    single { SettingsRepository(get()) }
    single { SavedFilterRepository(get()) }
    single { ListRepository(get()) }

    // BookmarkActionsRepository depends on BookmarkDao, PendingActionDao, RemoteDataSource, ServerRepository, SettingsRepository
    single { BookmarkActionsRepository(get(), get(), get(), get(), get()) }

    // BookmarkRepository depends on BookmarkActionsRepository
    single {
        BookmarkRepository(get(), get(), get(), get(), get(), get()).also {
            // Wire up circular dependency: BookmarkActionsRepository needs BookmarkRepository
            get<BookmarkActionsRepository>().setBookmarkRepository(it)
        }
    }

    // Action system - centralized action handling with undo support
    single { ActionSnackbarManager() }
    single { BookmarkActionController(get(), get(), get(), get()) }

    factory { LoginScreenModel(get(), get(), get()) }
    single { MainScreenModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { BookmarkViewerScreenModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SettingsScreenModel(get(), get(), get(), get()) }
    factory { com.karakept.app.ui.screens.settings.ListManagementScreenModel(get(), get()) }
    factory { ReaderAppearanceScreenModel(get()) }
    factory { FilterManagementScreenModel(get(), get(), get(), get()) }
}
