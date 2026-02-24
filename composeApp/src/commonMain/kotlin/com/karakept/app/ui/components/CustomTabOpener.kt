package com.karakept.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Returns a platform-specific lambda for opening a URL in a Custom Tab
 * (or the closest equivalent on each platform).
 *
 * - Android: Chrome Custom Tabs (shares the browser session)
 * - Desktop: system default browser via java.awt.Desktop
 */
@Composable
expect fun rememberCustomTabOpener(): (String) -> Unit
