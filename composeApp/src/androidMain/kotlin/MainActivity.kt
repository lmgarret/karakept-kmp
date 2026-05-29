package com.karakept.app

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.karakept.app.services.SaveBookmarkWorker

class MainActivity : ComponentActivity() {
    private var openBookmarkId by mutableStateOf<String?>(null)
    private var saveErrorUrl by mutableStateOf<String?>(null)
    private var saveErrorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        // Enable edge-to-edge display for Android SDK 35+
        enableEdgeToEdge()

        // Initialize platform-specific utilities
        com.karakept.app.utils.HapticUtils.init(this)
        com.karakept.app.utils.ShareUtils.init(this)

        // Warm up WebView to reduce latency on first open
        try {
            android.webkit.WebView(applicationContext)
        } catch (e: Exception) {
            // Ignore if WebView is not available
        }

        // Handle initial intent
        intent?.getStringExtra("bookmark_id")?.let { id ->
            openBookmarkId = id
        }
        saveErrorUrl = intent?.getStringExtra(SaveBookmarkWorker.EXTRA_SAVE_ERROR_URL)
        saveErrorMessage = intent?.getStringExtra(SaveBookmarkWorker.EXTRA_SAVE_ERROR_MESSAGE)

        setContent {
            App(
                openBookmarkId = openBookmarkId,
                saveErrorUrl = saveErrorUrl,
                saveErrorMessage = saveErrorMessage
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val bookmarkId = intent.getStringExtra("bookmark_id")
        val errorUrl = intent.getStringExtra(SaveBookmarkWorker.EXTRA_SAVE_ERROR_URL)
        if (bookmarkId != null || errorUrl != null) {
            openBookmarkId = bookmarkId
            saveErrorUrl = errorUrl
            saveErrorMessage = intent.getStringExtra(SaveBookmarkWorker.EXTRA_SAVE_ERROR_MESSAGE)
            setContent {
                App(
                    openBookmarkId = openBookmarkId,
                    saveErrorUrl = saveErrorUrl,
                    saveErrorMessage = saveErrorMessage
                )
            }
        }
    }
}
