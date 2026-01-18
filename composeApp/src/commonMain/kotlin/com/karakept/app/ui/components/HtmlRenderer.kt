package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode

/**
 * Platform-specific HTML renderer.
 *
 * Implementations:
 * - Android: Uses WebView with JavaScript disabled
 * - Desktop: Uses compose-webview-multiplatform with JCEF
 *
 * @param html Processed HTML content to render
 * @param viewerMode Viewer mode (READER or WEB)
 * @param modifier Modifier for layout
 * @param onLinkClick Callback when a link is clicked (receives the URL)
 * @param onLoaded Callback when content is loaded
 * @param customTextColor Optional custom text color for HTML content
 */
@Composable
expect fun HtmlRenderer(
    html: String,
    viewerMode: ViewerMode,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onLoaded: (() -> Unit)? = null,
    customTextColor: Color? = null,
    customFontSize: Int = 16,
    customFontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    localFilePath: String? = null,
    highlights: List<com.karakept.app.data.model.Highlight> = emptyList(),
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onDeleteHighlight: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onHighlightPosition: ((String, HighlightPosition?) -> Unit)? = null
)
