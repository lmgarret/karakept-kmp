
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

@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
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

    KoinApplication(application = {
        modules(appModule)
    }) {
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
                if (serverRepository.hasServers()) {
                    initialScreen = com.karakept.app.ui.screens.MainScreen()
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
}
