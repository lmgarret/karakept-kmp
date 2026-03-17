
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@Composable
@Preview
fun App(sharedUrl: String? = null, openBookmarkId: String? = null) {
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
                    println("🖼️ Coil loading image: $url")

                    // Only add auth header for asset URLs
                    if (url.contains("/api/v1/assets/")) {
                        val servers = runBlocking { serverRepository.servers.first() }
                        val server = servers.firstOrNull()
                        if (server != null) {
                            println("🔐 Adding auth header for asset URL")
                            request.header("Authorization", "Bearer ${server.apiKey}")
                        } else {
                            println("⚠️ No server found for authentication")
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

        com.karakept.app.ui.theme.AppTheme(
            themeMode = themeMode,
            accentColor = accentColor
        ) {
            com.karakept.app.ui.theme.SyncWindowTheme()
            val serverRepository = org.koin.compose.koinInject<com.karakept.app.data.repository.ServerRepository>()
            var initialScreens by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<cafe.adriel.voyager.core.screen.Screen>?>(null) }

            androidx.compose.runtime.LaunchedEffect(sharedUrl, openBookmarkId) {
                println("📱 App LaunchedEffect. sharedUrl=$sharedUrl, openBookmarkId=$openBookmarkId")
                if (sharedUrl != null) {
                    println("   Showing ShareBookmarkScreen")
                    initialScreens = listOf(com.karakept.app.ui.screens.ShareBookmarkScreen(sharedUrl))
                } else if (serverRepository.hasServers()) {
                    val screens = mutableListOf<cafe.adriel.voyager.core.screen.Screen>(com.karakept.app.ui.screens.MainScreen)
                    if (openBookmarkId != null) {
                        try {
                           val bookmarkIdLong = openBookmarkId.toLong()
                           println("   Parsing bookmark ID $bookmarkIdLong. Adding BookmarkViewerScreen.")
                           // BookmarkViewerScreen only needs bookmarkId (Long). It handles server resolution internally.
                           screens.add(com.karakept.app.ui.screens.BookmarkViewerScreen(bookmarkIdLong))
                        } catch (e: Exception) {
                            println("   Error parsing openBookmarkId: ${e.message}")
                        }
                    } else {
                        println("   No bookmark ID, showing only MainScreen")
                    }
                    initialScreens = screens
                } else {
                    val onboardingCompleted = settingsRepository.onboardingCompleted.first()
                    if (!onboardingCompleted) {
                        println("   First launch, showing OnboardingScreen")
                        initialScreens = listOf(com.karakept.app.ui.screens.OnboardingScreen())
                    } else {
                        println("   No servers, showing LoginScreen")
                        initialScreens = listOf(com.karakept.app.ui.screens.LoginScreen())
                    }
                }
            }

            if (initialScreens != null) {
                Navigator(initialScreens!!) { navigator ->
                    SlideTransition(navigator)
                }
            }
    }
}
