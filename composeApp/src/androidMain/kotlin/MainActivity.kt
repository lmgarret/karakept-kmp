package com.karakept.app

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.karakept.app.services.SaveBookmarkWorker
import com.karakept.app.ui.input.PageTurnDispatcher
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private var openBookmarkId by mutableStateOf<String?>(null)
    private var saveErrorUrl by mutableStateOf<String?>(null)
    private var saveErrorMessage by mutableStateOf<String?>(null)

    private val pageTurnDispatcher: PageTurnDispatcher by inject()

    /**
     * Key codes consumed on ACTION_DOWN, so the matching ACTION_UP can be swallowed too.
     *
     * Without this, a bound volume key turns the page *and* pops the system volume overlay: the
     * platform raises that on the up event. Interception has to happen in `dispatchKeyEvent`
     * rather than `onKeyDown` for the same reason — by the time `onKeyDown` runs, the window has
     * already had its say.
     */
    private val consumedKeyCodes = mutableSetOf<Int>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Ignore auto-repeat: holding a page button should not fly through the article.
                if (event.repeatCount > 0 && event.keyCode in consumedKeyCodes) return true
                if (pageTurnDispatcher.onKeyDown(event.keyCode)) {
                    consumedKeyCodes.add(event.keyCode)
                    return true
                }
            }
            KeyEvent.ACTION_UP -> {
                if (consumedKeyCodes.remove(event.keyCode)) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

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
