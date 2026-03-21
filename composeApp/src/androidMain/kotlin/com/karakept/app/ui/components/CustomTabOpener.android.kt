package com.karakept.app.ui.components

import android.net.Uri
import com.karakept.app.utils.AppLogger
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberCustomTabOpener(): (String) -> Unit {
    val context = LocalContext.current
    return { url ->
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            AppLogger.e("CustomTabOpener", "Failed to open custom tab: ${e.message}", e)
        }
    }
}
