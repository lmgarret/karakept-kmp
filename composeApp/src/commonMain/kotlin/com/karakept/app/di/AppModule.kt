package com.karakept.app.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.AppDatabase
import com.karakept.app.data.local.createDataStore
import com.karakept.app.data.local.getDatabaseBuilder
import com.karakept.app.data.remote.createHttpClient
import io.ktor.client.HttpClient
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.DefaultAppDispatchers
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.api.infrastructure.ApiClient
import com.karakept.api.client.*
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.secure.SecureCredentialStore
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.services.BackgroundSyncOrchestrator
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
    single<AppDispatchers> { DefaultAppDispatchers() }

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

    single { com.karakept.app.utils.ImageCacheManager(get()) }

    single { createDataStore() }

    single { SecureCredentialStore() }
    single { ServerRepository(get(), get()) }
    single { SettingsRepository(get()) }
    single { ListRepository(get(), get(), get(), get()) }  // RemoteDataSource, ListDao, SettingsRepository, AppDispatchers

    // BookmarkActionsRepository depends on BookmarkDao, PendingActionDao, RemoteDataSource, ServerRepository, SettingsRepository, AppDispatchers
    single { BookmarkActionsRepository(get(), get(), get(), get(), get(), get()) }

    // HighlightRepository
    single { HighlightRepository(get(), get(), get()) }

    // BookmarkRepository depends on BookmarkActionsRepository and HighlightRepository
    single {
        BookmarkRepository(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()).also {
            // Wire up circular dependencies
            val actionsRepo = get<BookmarkActionsRepository>()
            actionsRepo.setBookmarkRepository(it)
            actionsRepo.setHighlightDao(get())
        }
    }

    // Action system - centralized action handling with undo support
    single { ActionSnackbarManager() }
    single { com.karakept.app.domain.action.TagFilterRequests() }
    single { BookmarkActionController(get(), get(), get(), get(), get(), get()) }

    // Background sync orchestrator — NotificationProvider is registered per-platform
    single { BackgroundSyncOrchestrator(get(), get(), get(), get()) }

    // Backup & Restore
    single { BackupRepository(get(), get()) }

    // Hardware page-turn buttons. A single so the platform key-dispatch entry point and the
    // screens that scroll share one instance.
    single { com.karakept.app.ui.input.PageTurnDispatcher(get(), get()) }

    viewModel { LoginScreenModel(get(), get(), get()) }
    viewModel { OnboardingScreenModel(get(), get(), get(), get()) }
    // viewModel (not single) so each Nav3 back-stack entry gets a fresh instance scoped
    // to that entry's ViewModelStore (provided by rememberViewModelStoreNavEntryDecorator).
    // A Koin single would share one instance across hosts/entries, but the store clears the
    // ViewModel (cancelling viewModelScope) when its entry leaves the back stack — a reused
    // singleton would then have dead coroutines and never load bookmarks (SAVE-02).
    viewModel { MainScreenModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { BookmarkViewerScreenModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsScreenModel(get(), get(), get(), get()) }
    viewModel { HighlightsScreenModel(get(), get(), get(), get()) }
    viewModel { com.karakept.app.ui.screens.SaveErrorScreenModel(get()) }
    viewModel { com.karakept.app.ui.screens.settings.ListManagementScreenModel(get(), get()) }
    viewModel { params -> com.karakept.app.ui.screens.settings.PerListSettingsScreenModel(params.get(), get()) }
    viewModel { ReaderAppearanceScreenModel(get()) }
    viewModel { com.karakept.app.ui.screens.settings.EinkSettingsScreenModel(get(), get()) }
    viewModel { com.karakept.app.ui.screens.settings.BackupRestoreScreenModel(get(), get()) }
    viewModel { com.karakept.app.ui.screens.settings.LayoutsScreenModel(get()) }
    viewModel { com.karakept.app.ui.screens.settings.LayoutEditorScreenModel(get()) }
}
