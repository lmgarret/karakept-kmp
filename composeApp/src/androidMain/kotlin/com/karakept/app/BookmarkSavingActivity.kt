package com.karakept.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.ui.screens.ShareBookmarkScreen
import com.karakept.app.ui.screens.ShareMultipleBookmarksScreen
import com.karakept.app.ui.theme.AppTheme
import org.koin.compose.koinInject

class BookmarkSavingActivity : ComponentActivity() {
    private var sharedUrls by mutableStateOf<List<String>>(emptyList())
    private var intentKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrls = extractUrlsFromIntent(intent)

        setContent {
            key(intentKey) {
                val urls = sharedUrls
                if (urls.isNotEmpty()) {
                    BookmarkSavingContent(urls = urls, onClose = { finish() })
                } else {
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrls = extractUrlsFromIntent(intent)
        intentKey++
    }

    private fun extractUrlsFromIntent(intent: Intent): List<String> {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return emptyList()
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return emptyList()
        val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/[^\\s]*)?)".toRegex()
        val matches = urlRegex.findAll(sharedText).map { it.value }.toList()
        return matches.ifEmpty { listOf(sharedText) }
    }
}

@Composable
private fun BookmarkSavingContent(urls: List<String>, onClose: () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val accentColor by settingsRepository.accentColor.collectAsState(initial = AccentColor.PURPLE)

    val initialScreen = if (urls.size == 1) {
        ShareBookmarkScreen(url = urls[0], onClose = onClose)
    } else {
        ShareMultipleBookmarksScreen(urls = urls, onClose = onClose)
    }

    AppTheme(themeMode = themeMode, accentColor = accentColor) {
        Navigator(initialScreen) { navigator ->
            SlideTransition(navigator)
        }
    }
}
