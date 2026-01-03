
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@Composable
@Preview
fun App(sharedUrl: String? = null) {
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
        val themeMode by settingsRepository.themeMode.collectAsState(initial = com.karakept.app.data.model.ThemeMode.SYSTEM)
        val accentColor by settingsRepository.accentColor.collectAsState(initial = com.karakept.app.data.model.AccentColor.PURPLE)

        com.karakept.app.ui.theme.AppTheme(
            themeMode = themeMode,
            accentColor = accentColor
        ) {
            val serverRepository = org.koin.compose.koinInject<com.karakept.app.data.repository.ServerRepository>()
            var initialScreen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<cafe.adriel.voyager.core.screen.Screen?>(null) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (sharedUrl != null) {
                    initialScreen = com.karakept.app.ui.screens.ShareBookmarkScreen(sharedUrl)
                } else if (serverRepository.hasServers()) {
                    initialScreen = com.karakept.app.ui.screens.MainScreen
                } else {
                    initialScreen = com.karakept.app.ui.screens.LoginScreen()
                }
            }

            if (initialScreen != null) {
                Navigator(initialScreen!!) { navigator ->
                    SlideTransition(navigator)
                }
            }
    }
}
