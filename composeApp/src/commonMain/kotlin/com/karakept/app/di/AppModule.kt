package com.karakept.app.di

import com.karakept.app.data.cache.CoilThumbnailCache
import com.karakept.app.data.cache.ThumbnailCache
import com.karakept.app.services.BackgroundSyncOrchestrator
import com.karakept.app.ui.screens.highlights.HighlightsScreenModel
import com.karakept.app.ui.screens.main.MainScreenModel
import com.karakept.app.ui.screens.onboarding.LoginScreenModel
import com.karakept.app.ui.screens.onboarding.OnboardingScreenModel
import com.karakept.app.ui.screens.settings.ReaderAppearanceScreenModel
import com.karakept.app.ui.screens.settings.SettingsScreenModel
import com.karakept.app.ui.screens.viewer.BookmarkViewerScreenModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    includes(dataModule)

    // OfflineCacheRepository reports and clears the image loader's disk cache, which the app owns.
    single<ThumbnailCache> { CoilThumbnailCache }

    // Background sync orchestrator — NotificationProvider is registered per-platform
    single { BackgroundSyncOrchestrator(get(), get(), get(), get()) }

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
    viewModel { MainScreenModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), serverVersionRepository = get()) }
    viewModel { BookmarkViewerScreenModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsScreenModel(get(), get(), get(), get()) }
    viewModel { HighlightsScreenModel(get(), get(), get(), get()) }
    viewModel { com.karakept.app.ui.screens.share.SaveErrorScreenModel(get()) }
    viewModel { com.karakept.app.ui.screens.settings.ListManagementScreenModel(get(), get()) }
    viewModel { params -> com.karakept.app.ui.screens.settings.PerListSettingsScreenModel(params.get(), get()) }
    viewModel { ReaderAppearanceScreenModel(get()) }
    viewModel { com.karakept.app.ui.screens.settings.EinkSettingsScreenModel(get(), get()) }
    viewModel { com.karakept.app.ui.screens.settings.BackupRestoreScreenModel(get(), get()) }
    viewModel { com.karakept.app.ui.screens.settings.LayoutsScreenModel(get()) }
    viewModel { com.karakept.app.ui.screens.settings.OfflineStorageScreenModel(get(), get()) }
    viewModel { com.karakept.app.ui.screens.settings.LayoutEditorScreenModel(get()) }
}
