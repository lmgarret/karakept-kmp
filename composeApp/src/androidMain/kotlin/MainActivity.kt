package com.karakept.app

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
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
        
        var sharedUrl by androidx.compose.runtime.mutableStateOf<String?>(null)
        
        // Handle initial intent
        intent?.getStringExtra("shared_url")?.let {
            sharedUrl = it
        }

        setContent {
            App(sharedUrl = sharedUrl)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle subsequent intents while activity is running
        intent.getStringExtra("shared_url")?.let {
            // Need a way to trigger refresh in App
            // For now, re-set content or use a shared state
            setContent {
                App(sharedUrl = it)
            }
        }
    }
}
