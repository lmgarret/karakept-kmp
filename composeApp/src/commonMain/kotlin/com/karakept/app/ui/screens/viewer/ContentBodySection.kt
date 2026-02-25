package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.HtmlContent
import com.karakept.app.ui.components.WebModeBadge
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
    highlights: List<com.karakept.app.data.model.Highlight> = emptyList(),
    onLinkClick: (String) -> Unit,
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onDeleteHighlight: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onHighlightPosition: ((String, com.karakept.app.ui.components.HighlightPosition?) -> Unit)? = null,
    onContentReady: (() -> Unit)? = null
) {
    // Track when HTML content is truly ready (processed + rendered)
    var htmlContentReady by remember { mutableStateOf(false) }

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
                    onHighlightPosition = onHighlightPosition
                )
            }

            // Show skeleton on top until content ready (top layer)
            if (!htmlContentReady) {
                BookmarkContentLoader(
                    loadingState = loadingState,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}
