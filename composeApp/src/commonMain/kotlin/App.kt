
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.karakept.app.ui.navigation.AppNavigator
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.appEntryProvider
import com.karakept.app.ui.navigation.navSavedStateConfiguration
import com.karakept.app.ui.navigation.sharedAxisZBackward
import com.karakept.app.ui.navigation.sharedAxisZForward
import com.karakept.app.di.appModule
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication

import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import com.karakept.app.utils.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@Composable
@Preview
fun App(
    sharedUrl: String? = null,
    openBookmarkId: String? = null,
    saveErrorUrl: String? = null,
    saveErrorMessage: String? = null
) {
    // Retain log lines in memory for the in-app log viewer (dev builds only).
    remember { AppLogger.captureEnabled = isDevBuild }

    // Get ServerRepository to access API keys for authentication
    val serverRepository = org.koin.compose.koinInject<com.karakept.app.data.repository.ServerRepository>()

        setSingletonImageLoaderFactory { context ->
            // Create Ktor client with authentication interceptor for asset URLs
            val httpClient = HttpClient {
                install(HttpSend) {
                    maxSendCount = 1
                }
            }.apply {
                plugin(HttpSend).intercept { request ->
                    val url = request.url.toString()

                    // Only add auth header for asset URLs
                    if (url.contains("/api/v1/assets/")) {
                        val servers = runBlocking { serverRepository.servers.first() }
                        val server = servers.firstOrNull()
                        if (server != null) {
                            request.header("Authorization", "Bearer ${server.apiKey}")
                        }
                    }
                    execute(request)
                }
            }

            ImageLoader.Builder(context)
                .components {
                    add(KtorNetworkFetcherFactory(httpClient = httpClient))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .diskCache {
                    getCacheDir(context)?.let { cacheDir ->
                        DiskCache.Builder()
                            .directory(cacheDir)
                            .maxSizePercent(0.02)
                            .build()
                    }
                }
                .crossfade(true)
                .build()
        }
        val settingsRepository = org.koin.compose.koinInject<com.karakept.app.data.repository.SettingsRepository>()
        val backupRepository = org.koin.compose.koinInject<com.karakept.app.data.repository.BackupRepository>()
        val themeMode by settingsRepository.themeMode.collectAsState(initial = com.karakept.app.data.model.ThemeMode.SYSTEM)
        val accentColor by settingsRepository.accentColor.collectAsState(initial = com.karakept.app.data.model.AccentColor.PURPLE)

        // Start reconnect detection: probes for connectivity while auto-offline and
        // flushes queued actions when the network returns.
        val connectivityRecoveryService =
            org.koin.compose.koinInject<com.karakept.app.services.ConnectivityRecoveryService>()
        val remoteDataSource =
            org.koin.compose.koinInject<com.karakept.app.data.remote.RemoteDataSource>()
        val bookmarkActionsRepository =
            org.koin.compose.koinInject<com.karakept.app.data.repository.BookmarkActionsRepository>()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            connectivityRecoveryService.start(remoteDataSource, bookmarkActionsRepository)
        }

        // Run scheduled auto-export check on startup (best-effort)
        androidx.compose.runtime.LaunchedEffect(Unit) {
            try {
                backupRepository.checkAndRunScheduledExport()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore – backup is best-effort
            }
        }

        // Migrate credentials from DB to SecureCredentialStore (best-effort, idempotent)
        androidx.compose.runtime.LaunchedEffect(Unit) {
            try {
                serverRepository.triggerMigration()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Migration is best-effort — existing creds work via DB fallback
            }
        }

        com.karakept.app.ui.theme.AppTheme(
            themeMode = themeMode,
            accentColor = accentColor
        ) {
            com.karakept.app.ui.theme.SyncWindowTheme()

            // Developer-owned Nav3 back stack. Persisted across config change / process death via
            // navSavedStateConfiguration (polymorphic NavKey serialization). Seeded once below.
            val backStack = rememberNavBackStack(navSavedStateConfiguration)
            val navigator = remember(backStack) { AppNavigator(backStack) }

            androidx.compose.runtime.LaunchedEffect(sharedUrl, openBookmarkId, saveErrorUrl, saveErrorMessage) {
                AppLogger.d("App", "LaunchedEffect. sharedUrl=$sharedUrl, openBookmarkId=$openBookmarkId, saveErrorUrl=$saveErrorUrl")
                // Only seed when empty: a non-empty stack means it was restored (process death) or
                // already initialized. New share/notification intents recreate App via an outer key().
                if (backStack.isNotEmpty()) return@LaunchedEffect

                val startKeys: List<NavKey> = if (sharedUrl != null) {
                    listOf(com.karakept.app.ui.screens.ShareBookmarkScreen(sharedUrl))
                } else if (serverRepository.hasServers()) {
                    buildList {
                        add(com.karakept.app.ui.screens.MainScreen)
                        if (saveErrorUrl != null) {
                            AppLogger.d("App", "Save error for $saveErrorUrl. Adding SaveErrorScreen.")
                            add(
                                com.karakept.app.ui.screens.SaveErrorScreen(
                                    url = saveErrorUrl,
                                    message = saveErrorMessage ?: "Unknown error"
                                )
                            )
                        }
                        if (openBookmarkId != null) {
                            try {
                                val bookmarkIdLong = openBookmarkId.toLong()
                                AppLogger.d("App", "Parsing bookmark ID $bookmarkIdLong. Adding BookmarkViewerScreen.")
                                // BookmarkViewerScreen only needs bookmarkId (Long). It resolves the server internally.
                                add(com.karakept.app.ui.screens.BookmarkViewerScreen(bookmarkIdLong))
                            } catch (e: Exception) {
                                AppLogger.w("App", "Error parsing openBookmarkId: ${e.message}")
                            }
                        } else {
                            AppLogger.d("App", "No bookmark ID, showing only MainScreen")
                        }
                    }
                } else {
                    val onboardingCompleted = settingsRepository.onboardingCompleted.first()
                    if (!onboardingCompleted) {
                        AppLogger.d("App", "First launch, showing OnboardingScreen")
                        listOf(com.karakept.app.ui.screens.OnboardingScreen())
                    } else {
                        AppLogger.d("App", "No servers, showing LoginScreen")
                        listOf(com.karakept.app.ui.screens.LoginScreen())
                    }
                }
                backStack.addAll(startKeys)
            }

            if (backStack.isNotEmpty()) {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { navigator.pop() },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        transitionSpec = { sharedAxisZForward() },
                        popTransitionSpec = { sharedAxisZBackward() },
                        predictivePopTransitionSpec = { sharedAxisZBackward() },
                        entryProvider = remember { appEntryProvider() },
                    )
                }
            }
    }
}
