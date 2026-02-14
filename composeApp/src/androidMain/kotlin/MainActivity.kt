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
        var openBookmarkId by androidx.compose.runtime.mutableStateOf<String?>(null)
        
        android.util.Log.d("DebuggingCtx", "🚀 MainActivity.onCreate called. Intent: $intent")
        
        // Handle initial intent
        intent?.let {
            it.getStringExtra("shared_url")?.let { url ->
                android.util.Log.d("DebuggingCtx", "   Found shared_url: $url")
                sharedUrl = url
            }
            it.getStringExtra("bookmark_id")?.let { id ->
                android.util.Log.d("DebuggingCtx", "   Found bookmark_id: $id")
                openBookmarkId = id
            }
             // Log all extras
            it.extras?.keySet()?.forEach { key ->
                android.util.Log.d("DebuggingCtx", "   Extra: $key = ${it.extras?.get(key)}")
            }
        }

        setContent {
            App(sharedUrl = sharedUrl, openBookmarkId = openBookmarkId)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        android.util.Log.d("DebuggingCtx", "🚀 MainActivity.onNewIntent called. Intent: $intent")
        
        // Handle subsequent intents while activity is running
        val url = intent.getStringExtra("shared_url")
        val bookmarkId = intent.getStringExtra("bookmark_id")
        android.util.Log.d("DebuggingCtx", "   New intent data. url=$url, bookmarkId=$bookmarkId")
        
        // Log all extras
        intent.extras?.keySet()?.forEach { key ->
            android.util.Log.d("DebuggingCtx", "   Extra: $key = ${intent.extras?.get(key)}")
        }
        
        if (url != null || bookmarkId != null) {
            setContent {
                App(sharedUrl = url, openBookmarkId = bookmarkId)
            }
        }
    }
}
