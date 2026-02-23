package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific composable that loads and displays a URL in a native web view.
 *
 * Implementations:
 * - Android: Uses Android WebView with standard security settings
 * - Desktop: Uses JavaFX WebView
 *
 * @param url The URL to load
 * @param modifier Modifier for layout
 * @param onPageTitleChanged Callback when the page title changes (for the top bar)
 * @param onLinkClick Callback when a link inside the loaded page is clicked
 */
@Composable
expect fun UrlRenderer(
    url: String,
    modifier: Modifier = Modifier,
    onPageTitleChanged: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null
)
