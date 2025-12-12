package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.Crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.utils.HtmlArchiveProcessor
import com.karakept.app.utils.HtmlCache
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
 * @param viewerMode Viewer mode (READER for sanitized, WEB for original styles)
 * @param modifier Modifier for layout
 * @param onLinkClick Callback when a link is clicked
 */
@Composable
fun HtmlContent(
    html: String?,
    viewerMode: ViewerMode,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    hideArticleThumbnails: Boolean = false,
    onReady: (() -> Unit)? = null,
    customTextColor: Color? = null,
    customBackgroundColor: Color? = null,
    customFontSize: Int = 16,
    customFontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    localFilePath: String? = null
) {
    // Debug output
    println("HtmlContent: Input HTML length=${html?.length}, isBlank=${html.isNullOrBlank()}, mode=$viewerMode, hideThumb=$hideArticleThumbnails")

    // Process HTML based on viewer mode asynchronously
    val processedHtml by produceState<String?>(initialValue = null, html, viewerMode, hideArticleThumbnails, customFontSize, customFontFamily, localFilePath) {
        if (localFilePath != null) {
            // If we have a local file, we don't need to process HTML string
            // Just return a placeholder to trigger rendering
            value = " " 
            return@produceState
        }

        if (html == null) {
            value = null
            return@produceState
        }

        val cacheKey = HtmlCache.generateKey(html, "${viewerMode.name}_hideThumb=${hideArticleThumbnails}_font=${customFontSize}-${customFontFamily.name}")
        val cached = HtmlCache.get(cacheKey)
        
        if (cached != null) {
            value = cached
        } else {
            value = withContext(Dispatchers.Default) {
                try {
                    val result = when (viewerMode) {
                        ViewerMode.READER -> HtmlSanitizer.sanitize(html, removeFirstImage = hideArticleThumbnails)
                        ViewerMode.WEB -> HtmlArchiveProcessor.processForArchive(html)
                    }
                    println("HtmlContent: Processed HTML length=${result.length}, isBlank=${result.isBlank()}")
                    HtmlCache.put(cacheKey, result)
                    result
                } catch (e: Exception) {
                    println("HtmlContent: Processing failed: ${e.message}")
                    null // Processing failed
                }
            }
        }
    }

    var isContentLoaded by remember { mutableStateOf(false) }
    
    // Notify parent when both processed and loaded
    androidx.compose.runtime.LaunchedEffect(processedHtml, isContentLoaded) {
        if (processedHtml != null && isContentLoaded && onReady != null) {
            onReady()
        }
    }

    Box(modifier = modifier) {
        if (html.isNullOrBlank()) {
            Text(
                text = "No content available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // State to track if WebView has finished rendering
            var isContentLoaded by remember { mutableStateOf(false) }

            // Reset state when content changes
            androidx.compose.runtime.LaunchedEffect(processedHtml) {
                if (processedHtml == null) {
                    isContentLoaded = false
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                // Always render HtmlRenderer if content is processed OR we have a local file
                if ((processedHtml != null && processedHtml!!.isNotBlank()) || localFilePath != null) {
                    // Apply background color directly to HtmlRenderer modifier for READER mode
                    val rendererModifier = if (viewerMode == ViewerMode.READER && customBackgroundColor != null) {
                        Modifier.fillMaxWidth().background(customBackgroundColor)
                    } else {
                        Modifier.fillMaxWidth()
                    }

                    HtmlRenderer(
                        html = processedHtml ?: "",
                        viewerMode = viewerMode,
                        modifier = rendererModifier,
                        onLinkClick = onLinkClick,
                        onLoaded = {
                            isContentLoaded = true
                        },
                        customTextColor = customTextColor,
                        customFontSize = customFontSize,
                        customFontFamily = customFontFamily,
                        localFilePath = localFilePath
                    )
                } else if (processedHtml != null && processedHtml!!.isBlank()) {
                     Text(
                        text = "Content could not be displayed safely",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Show SkeletonLoader until content is fully loaded
                // Use a crossfade for smoother transition
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isContentLoaded || (processedHtml == null && localFilePath == null),
                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                ) {
                    SkeletonLoader(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
