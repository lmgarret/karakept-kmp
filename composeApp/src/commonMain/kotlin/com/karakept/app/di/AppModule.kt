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
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.ui.screens.LoginScreenModel
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.app.ui.screens.BookmarkViewerScreenModel
import com.karakept.app.ui.screens.SettingsScreenModel

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
    
    single { createDataStore() }

    single { ServerRepository(get()) }
    single { BookmarkRepository(get(), get()) }
    single { SettingsRepository(get()) }

    factory { LoginScreenModel(get(), get()) }
    factory { MainScreenModel(get(), get(), get()) }
    factory { BookmarkViewerScreenModel(get()) }
    factory { SettingsScreenModel(get(), get()) }
}
