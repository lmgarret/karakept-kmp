package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific HTML renderer.
 *
 * Implementations:
 * - Android: Uses WebView with JavaScript disabled
 * - Desktop: Uses JEditorPane with HTMLEditorKit
 *
 * @param html Sanitized HTML content to render
 * @param modifier Modifier for layout
 * @param onLinkClick Callback when a link is clicked (receives the URL)
 */
@Composable
expect fun HtmlRenderer(
    html: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
)
