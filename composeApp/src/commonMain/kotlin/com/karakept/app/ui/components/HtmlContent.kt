package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.utils.HtmlArchiveProcessor
import com.karakept.app.utils.HtmlSanitizer

/**
 * Composable wrapper for rendering HTML content safely.
 *
 * Features:
 * - Sanitizes HTML before rendering (Reader mode)
 * - Processes HTML for Archive mode (original styles, no JS)
 * - Handles null/empty content gracefully
 * - Provides fallback for rendering errors
 * - Uses platform-specific renderer
 *
 * @param html Raw HTML content (will be processed based on viewerMode)
 * @param viewerMode Viewer mode (READER for sanitized, ARCHIVE for original styles)
 * @param modifier Modifier for layout
 * @param onLinkClick Callback when a link is clicked
 */
@Composable
fun HtmlContent(
    html: String?,
    viewerMode: ViewerMode,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    // Debug output
    println("HtmlContent: Input HTML length=${html?.length}, isBlank=${html.isNullOrBlank()}, mode=$viewerMode")

    // Process HTML based on viewer mode
    val processedHtml = remember(html, viewerMode) {
        try {
            val result = when (viewerMode) {
                ViewerMode.READER -> HtmlSanitizer.sanitize(html)
                ViewerMode.ARCHIVE -> HtmlArchiveProcessor.processForArchive(html)
            }
            println("HtmlContent: Processed HTML length=${result.length}, isBlank=${result.isBlank()}")
            result
        } catch (e: Exception) {
            println("HtmlContent: Processing failed: ${e.message}")
            null // Processing failed
        }
    }

    Box(modifier = modifier) {
        when {
            html.isNullOrBlank() -> {
                Text(
                    text = "No content available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            processedHtml == null -> {
                // Processing failed - show error
                Text(
                    text = "Content could not be processed safely",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            processedHtml.isBlank() -> {
                // Content was processed to nothing
                Text(
                    text = "Content could not be displayed safely",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                HtmlRenderer(
                    html = processedHtml,
                    viewerMode = viewerMode,
                    modifier = Modifier.fillMaxWidth(),
                    onLinkClick = onLinkClick
                )
            }
        }
    }
}

/**
 * Strips HTML tags from a string for fallback plain text display.
 */
private fun stripHtmlTags(html: String): String {
    return html
        .replace(Regex("<[^>]*>"), "") // Remove all HTML tags
        .replace(Regex("&nbsp;"), " ") // Replace non-breaking spaces
        .replace(Regex("&lt;"), "<")
        .replace(Regex("&gt;"), ">")
        .replace(Regex("&amp;"), "&")
        .replace(Regex("&quot;"), "\"")
        .trim()
}
