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
import com.karakept.app.ui.theme.AppTheme
import org.koin.compose.koinInject

class BookmarkSavingActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)
    private var intentKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractUrlFromIntent(intent)

        setContent {
            key(intentKey) {
                val url = sharedUrl
                if (url != null) {
                    BookmarkSavingContent(url = url, onClose = { finish() })
                } else {
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl = extractUrlFromIntent(intent)
        intentKey++
    }

    private fun extractUrlFromIntent(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/[^\\s]*)?)".toRegex()
        return urlRegex.find(sharedText)?.value ?: sharedText
    }
}

@Composable
private fun BookmarkSavingContent(url: String, onClose: () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val accentColor by settingsRepository.accentColor.collectAsState(initial = AccentColor.PURPLE)

    AppTheme(themeMode = themeMode, accentColor = accentColor) {
        Navigator(ShareBookmarkScreen(url = url, onClose = onClose)) { navigator ->
            SlideTransition(navigator)
        }
    }
}
