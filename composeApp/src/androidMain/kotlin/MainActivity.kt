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

class MainActivity : ComponentActivity() {
    private var openBookmarkId by mutableStateOf<String?>(null)

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

        setContent {
            App(openBookmarkId = openBookmarkId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val bookmarkId = intent.getStringExtra("bookmark_id")
        if (bookmarkId != null) {
            openBookmarkId = bookmarkId
            setContent {
                App(openBookmarkId = openBookmarkId)
            }
        }
    }
}
