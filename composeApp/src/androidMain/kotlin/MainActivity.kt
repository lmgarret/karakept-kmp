package com.karakept.app

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.karakept.app.data.local.AndroidContext.context = applicationContext
        com.karakept.app.data.local.initializeDataStore(applicationContext)
        
        // Warm up WebView to reduce latency on first open
        try {
            android.webkit.WebView(applicationContext)
        } catch (e: Exception) {
            // Ignore if WebView is not available
        }
        
        setContent {
            App()
        }
    }
}
