package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.Crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.reader.NativeHtmlRenderer
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
    precrawledAssetPath: String? = null,
    highlights: List<com.karakept.app.data.model.Highlight> = emptyList(),
    onLinkClick: (String) -> Unit,
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onDeleteHighlight: (String) -> Unit = {},
    removeFirstImage: Boolean = false,
    onReady: (() -> Unit)? = null,
    customTextColor: Color? = null,
    customBackgroundColor: Color? = null,
    customFontSize: Int = 16,
    customFontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    localFilePath: String? = null,
    onHighlightClick: ((String) -> Unit)? = null,
    onHighlightPosition: (String, com.karakept.app.ui.components.HighlightPosition) -> Unit = { _, _ -> },
    scrollToHighlightId: String? = null,
    selectedHighlightId: String? = null
) {
    // Process HTML based on viewer mode asynchronously
    val processedHtml by produceState<String?>(initialValue = null, html, viewerMode, removeFirstImage, localFilePath) {
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

        value = withContext(Dispatchers.Default) {
            try {
                val result = when (viewerMode) {
                    ViewerMode.READER -> HtmlSanitizer.sanitize(html, removeFirstImage = removeFirstImage)
                    ViewerMode.WEB -> HtmlArchiveProcessor.processForArchive(html)
                }
                println("HtmlContent: Processed HTML length=${result.length}, isBlank=${result.isBlank()}")
                result
            } catch (e: Exception) {
                println("HtmlContent: Processing failed: ${e.message}")
                null // Processing failed
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

    // Note: we intentionally do NOT call onReady() when html is null/blank.
    // Calling onReady() prematurely would set contentRendered = true in the
    // viewer screen, causing scroll restoration to fire before actual HTML
    // content is rendered — breaking resume-reading for on-demand bookmarks.

    Box(modifier = modifier) {
        if (html.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Bookmark content not synced",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            // Reset state when content changes
            androidx.compose.runtime.LaunchedEffect(processedHtml) {
                if (processedHtml == null) {
                    isContentLoaded = false
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                // Always render if content is processed OR we have a local file
                if (!processedHtml.isNullOrBlank() || localFilePath != null) {
                    // Apply background color directly to renderer modifier for READER mode
                    val rendererModifier = if (viewerMode == ViewerMode.READER && customBackgroundColor != null) {
                        Modifier.fillMaxWidth().background(customBackgroundColor)
                    } else {
                        Modifier.fillMaxWidth()
                    }

                    when (viewerMode) {
                        ViewerMode.READER -> {
                            // Native Compose renderer — no WebView needed
                            NativeHtmlRenderer(
                                html = processedHtml ?: "",
                                modifier = rendererModifier,
                                highlights = highlights,
                                textColor = customTextColor,
                                backgroundColor = customBackgroundColor,
                                fontSize = customFontSize,
                                fontFamily = customFontFamily,
                                onLinkClick = onLinkClick,
                                onHighlightClick = { highlightId ->
                                    onHighlightClick?.invoke(highlightId)
                                },
                                onCreateHighlight = onCreateHighlight,
                                onHighlightPosition = onHighlightPosition,
                                scrollToHighlightId = scrollToHighlightId,
                                selectedHighlightId = selectedHighlightId,
                                onLoaded = {
                                    isContentLoaded = true
                                }
                            )
                        }
                        ViewerMode.WEB -> {
                            // WebView-based renderer (Android only; Desktop overrides to READER)
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
                                localFilePath = localFilePath,
                                highlights = highlights,
                                onCreateHighlight = onCreateHighlight,
                                onDeleteHighlight = onDeleteHighlight,
                                onHighlightClick = { highlightId ->
                                    onHighlightClick?.invoke(highlightId)
                                },
                                onHighlightPosition = onHighlightPosition,
                                scrollToHighlightId = scrollToHighlightId
                            )
                        }
                    }
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
