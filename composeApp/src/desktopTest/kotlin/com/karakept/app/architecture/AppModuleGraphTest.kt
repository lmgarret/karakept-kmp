package com.karakept.app.architecture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import com.karakept.app.di.appModule
import com.karakept.app.di.inMemoryDatabaseModule
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.services.BackgroundSyncOrchestrator
import com.karakept.app.services.NotificationProvider
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.screens.viewer.BookmarkViewerScreenModel
import com.karakept.app.ui.screens.highlights.HighlightsScreenModel
import com.karakept.app.ui.screens.onboarding.LoginScreenModel
import com.karakept.app.ui.screens.main.MainScreenModel
import com.karakept.app.ui.screens.onboarding.OnboardingScreenModel
import com.karakept.app.ui.screens.settings.ReaderAppearanceScreenModel
import com.karakept.app.ui.screens.share.SaveErrorScreenModel
import com.karakept.app.ui.screens.settings.SettingsScreenModel
import com.karakept.app.ui.screens.settings.BackupRestoreScreenModel
import com.karakept.app.ui.screens.settings.EinkSettingsScreenModel
import com.karakept.app.ui.screens.settings.LayoutEditorScreenModel
import com.karakept.app.ui.screens.settings.LayoutsScreenModel
import com.karakept.app.ui.screens.settings.ListManagementScreenModel
import com.karakept.app.ui.screens.settings.OfflineStorageScreenModel
import com.karakept.app.ui.screens.settings.PerListSettingsScreenModel
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.TestAppDispatchers
import io.ktor.client.HttpClient
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.KoinApplication
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Resolves every ScreenModel and app-level single declared in [appModule] against stand-ins for
 * the platform and IO singles, so a constructor that gains a parameter nothing provides fails here
 * rather than when the screen is first opened. The data layer's own bindings are pinned by
 * DataModuleGraphTest in :core:data; here they run over an in-memory database, since the app
 * cannot name the database type to stub it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleGraphTest {

    private val testDispatcher = StandardTestDispatcher()
    private var app: KoinApplication? = null

    private val testOverrides = module {
        single<HttpClient> { mockk(relaxed = true) }
        single<DataStore<Preferences>> { FakeDataStore() }
        single<SecureCredentialStore> { mockk(relaxed = true) }
        single<NotificationProvider> { mockk(relaxed = true) }
        single<AppDispatchers> { TestAppDispatchers(testDispatcher) }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = koinApplication {
            allowOverride(true)
            modules(appModule, inMemoryDatabaseModule(), testOverrides)
        }
    }

    @AfterTest
    fun tearDown() {
        app?.close()
        Dispatchers.resetMain()
    }

    private val koin get() = app!!.koin

    @Test
    fun everyScreenModelResolves() {
        val parameterless: List<KClass<*>> = listOf(
            LoginScreenModel::class,
            OnboardingScreenModel::class,
            MainScreenModel::class,
            BookmarkViewerScreenModel::class,
            SettingsScreenModel::class,
            HighlightsScreenModel::class,
            SaveErrorScreenModel::class,
            ListManagementScreenModel::class,
            ReaderAppearanceScreenModel::class,
            EinkSettingsScreenModel::class,
            BackupRestoreScreenModel::class,
            LayoutsScreenModel::class,
            OfflineStorageScreenModel::class,
            LayoutEditorScreenModel::class,
        )
        parameterless.forEach { assertNotNull(koin.get<Any>(it), "${it.simpleName} did not resolve") }
        assertNotNull(koin.get<PerListSettingsScreenModel> { parametersOf("list-id") })

        val declared = Regex("""(?m)^\s*viewModel\s*\{""").findAll(appModuleSource()).count()
        assertEquals(
            parameterless.size + 1, declared,
            "AppModule declares $declared viewModel entries; add the new one to this test",
        )
    }

    @Test
    fun everyRepositoryAndServiceSingleResolves() {
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
            BookmarkActionController::class,
            BackgroundSyncOrchestrator::class,
            PageTurnDispatcher::class,
        ).forEach { assertNotNull(koin.get<Any>(it), "${it.simpleName} did not resolve") }
    }

    private fun appModuleSource(): String {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "tools/material-icons.txt").isFile }
        return File(root, "composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt").readText()
    }
}
