package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.HtmlContent
import com.karakept.app.ui.components.WebModeBadge
import com.fleeksoft.ksoup.nodes.Document
import com.karakept.app.ui.screens.BookmarkLoadingState

@Composable
internal fun ContentBodySection(
    content: String?,
    viewerMode: ViewerMode,
    removeFirstImage: Boolean,
    htmlTextColor: Color?,
    htmlBackgroundColor: Color?,
    htmlFontSize: Int,
    htmlFontFamily: ReaderFontFamily,
    precrawledAssetPath: String? = null,
    loadingState: BookmarkLoadingState,
    contentFetchAttempted: Boolean = false,
    highlights: List<com.karakept.app.data.model.Highlight> = emptyList(),
    onLinkClick: (String) -> Unit,
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onDeleteHighlight: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onHighlightPosition: (String, com.karakept.app.ui.components.HighlightPosition) -> Unit = { _, _ -> },
    onContentReady: (() -> Unit)? = null,
    scrollToHighlightId: String? = null,
    selectedHighlightId: String? = null,
    parseDocument: ((String) -> Document?)? = null
) {
    // Track when HTML content is truly ready (processed + rendered)
    var htmlContentReady by remember { mutableStateOf(false) }

    // Reset on new content; or mark ready immediately when fetch is done but no content exists,
    // so the skeleton is replaced by the content-unavailable message instead of spinning forever.
    LaunchedEffect(content, contentFetchAttempted) {
        htmlContentReady = if (content == null && contentFetchAttempted) true else false
    }

    // Notify parent when content is fully rendered
    LaunchedEffect(htmlContentReady) {
        if (htmlContentReady) {
            onContentReady?.invoke()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(htmlBackgroundColor ?: MaterialTheme.colorScheme.background)
    ) {
        // Web mode badge
        if (htmlContentReady && viewerMode == ViewerMode.WEB) {
            WebModeBadge(
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Render content area with overlay approach
        Box(modifier = Modifier.fillMaxWidth()) {
            // Content unavailable: fetch was attempted but server returned no content
            if (content == null && contentFetchAttempted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
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
                        text = "Content unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This article's content could not be retrieved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Always render HtmlContent when data arrives (bottom layer)
            if (content != null) {
                HtmlContent(
                    html = content,
                    viewerMode = viewerMode,
                    removeFirstImage = removeFirstImage,
                    onLinkClick = onLinkClick,
                    onReady = { htmlContentReady = true },
                    customTextColor = htmlTextColor,
                    customBackgroundColor = htmlBackgroundColor,
                    customFontSize = htmlFontSize,
                    customFontFamily = htmlFontFamily,
                    localFilePath = if (viewerMode == ViewerMode.WEB) precrawledAssetPath else null,
                    highlights = highlights,
                    onCreateHighlight = onCreateHighlight,
                    onDeleteHighlight = onDeleteHighlight,
                    onHighlightClick = onHighlightClick,
                    onHighlightPosition = onHighlightPosition,
                    scrollToHighlightId = scrollToHighlightId,
                    selectedHighlightId = selectedHighlightId,
                    parseDocument = parseDocument
                )
            }

            // Show skeleton on top until content ready (top layer).
            // Always use Initial state so the shimmer skeleton is visible.
            // Using the actual loadingState would render nothing for FullyLoaded,
            // letting the CloudOff placeholder in HtmlContent flash briefly
            // before the HTML is processed.
            if (!htmlContentReady) {
                BookmarkContentLoader(
                    loadingState = BookmarkLoadingState.Initial,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}
