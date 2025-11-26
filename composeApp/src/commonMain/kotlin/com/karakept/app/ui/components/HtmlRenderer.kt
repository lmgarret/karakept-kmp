package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karakept.app.data.model.ViewerMode

/**
 * Platform-specific HTML renderer.
 *
 * Implementations:
 * - Android: Uses WebView with JavaScript disabled
 * - Desktop: Uses compose-webview-multiplatform with JCEF
 *
 * @param html Processed HTML content to render
 * @param viewerMode Viewer mode (READER or ARCHIVE)
 * @param modifier Modifier for layout
 * @param onLinkClick Callback when a link is clicked (receives the URL)
 */
@Composable
expect fun HtmlRenderer(
    html: String,
    viewerMode: ViewerMode,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onLoaded: (() -> Unit)? = null
)
