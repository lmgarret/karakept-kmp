package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.BookmarkContentLoader
import com.karakept.app.ui.components.HtmlContent
import com.karakept.app.ui.components.WebModeBadge
import com.fleeksoft.ksoup.nodes.Document
import com.karakept.app.ui.components.reader.SearchMatch
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
    selectedSource: ContentSource = ContentSource.EXTRACTED,
    sourceContentOverride: String? = null,
    loadingState: BookmarkLoadingState,
    contentFetchAttempted: Boolean = false,
    contentRevealed: Boolean = true,
    archiveAvailableOnServer: Boolean = false,
    isLoadingArchive: Boolean = false,
    onFetchArchive: () -> Unit = {},
    highlights: List<com.karakept.app.data.model.Highlight> = emptyList(),
    onLinkClick: (String) -> Unit,
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onDeleteHighlight: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onHighlightPosition: (String, com.karakept.app.ui.components.HighlightPosition) -> Unit = { _, _ -> },
    onContentReady: (() -> Unit)? = null,
    scrollToHighlightId: String? = null,
    selectedHighlightId: String? = null,
    parseDocument: ((String) -> Document?)? = null,
    searchQuery: String = "",
    activeSearchMatchIndex: Int = 0,
    onSearchMatchesFound: (List<SearchMatch>) -> Unit = {},
    onSearchMatchPosition: (Float) -> Unit = {}
) {
    // Resolve effective content and local file path based on source + mode
    val effectiveContent = when {
        selectedSource == ContentSource.FULL_PAGE_ARCHIVE && viewerMode == ViewerMode.READER ->
            sourceContentOverride ?: content
        else -> content
    }
    val effectiveLocalFilePath = when {
        selectedSource == ContentSource.FULL_PAGE_ARCHIVE && viewerMode == ViewerMode.WEB ->
            precrawledAssetPath
        else -> null
    }

    val hasRenderableBody = !effectiveContent.isNullOrBlank() || effectiveLocalFilePath != null

    // Track when HTML content is truly ready (processed + rendered)
    var htmlContentReady by remember { mutableStateOf(false) }

    // The real article is only revealed once it is both parsed and cleared for reveal by
    // the parent (scroll restoration / highlight scroll complete). Until then it stays
    // composed but invisible so it lays out for scroll restoration without flashing.
    val bodyRevealed = htmlContentReady && contentRevealed

    // Reset on new content; or mark ready immediately when fetch is done but no content exists,
    // so the skeleton is replaced by the content-unavailable message instead of spinning forever.
    LaunchedEffect(effectiveContent, contentFetchAttempted) {
        htmlContentReady = if (effectiveContent.isNullOrBlank() && contentFetchAttempted) true else false
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
            if (effectiveContent.isNullOrBlank() && contentFetchAttempted) {
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

                    if (archiveAvailableOnServer && selectedSource == ContentSource.EXTRACTED) {
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(
                            onClick = onFetchArchive,
                            enabled = !isLoadingArchive
                        ) {
                            if (isLoadingArchive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Load full page archive")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Downloads the complete page snapshot. Uses more storage and renders better in Web mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Always render HtmlContent when data arrives (bottom layer). Keep it composed
            // (so it lays out for scroll restoration) but hold it invisible until revealed.
            if (hasRenderableBody) {
                HtmlContent(
                    html = effectiveContent,
                    viewerMode = viewerMode,
                    modifier = Modifier.alpha(if (bodyRevealed) 1f else 0f),
                    removeFirstImage = removeFirstImage,
                    onLinkClick = onLinkClick,
                    onReady = { htmlContentReady = true },
                    customTextColor = htmlTextColor,
                    customBackgroundColor = htmlBackgroundColor,
                    customFontSize = htmlFontSize,
                    customFontFamily = htmlFontFamily,
                    localFilePath = effectiveLocalFilePath,
                    highlights = highlights,
                    onCreateHighlight = onCreateHighlight,
                    onDeleteHighlight = onDeleteHighlight,
                    onHighlightClick = onHighlightClick,
                    onHighlightPosition = onHighlightPosition,
                    scrollToHighlightId = scrollToHighlightId,
                    selectedHighlightId = selectedHighlightId,
                    parseDocument = parseDocument,
                    searchQuery = searchQuery,
                    activeSearchMatchIndex = activeSearchMatchIndex,
                    onSearchMatchesFound = onSearchMatchesFound,
                    onSearchMatchPosition = onSearchMatchPosition
                )
            }

            // Show skeleton on top until the body is ready AND revealed (top layer).
            // Always use Initial state so the shimmer skeleton is visible.
            // Using the actual loadingState would render nothing for FullyLoaded,
            // letting the CloudOff placeholder in HtmlContent flash briefly
            // before the HTML is processed. No banner skeleton here: the real hero
            // is already shown above this section.
            if (shouldShowBodySkeleton(htmlContentReady, hasRenderableBody, contentRevealed)) {
                BookmarkContentLoader(
                    loadingState = BookmarkLoadingState.Initial,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .background(MaterialTheme.colorScheme.background),
                    showBanner = false
                )
            }
        }
    }
}

/**
 * The article body is revealed only once both scroll restoration and highlight scrolling
 * are done, so the hero can appear immediately while the body waits.
 */
internal fun computeContentRevealed(needsScrollRestore: Boolean, needsHighlightScroll: Boolean): Boolean =
    !needsScrollRestore && !needsHighlightScroll

/**
 * Whether the shimmer skeleton should cover the body slot. It stays while the HTML is being
 * parsed, and (for a renderable body) also while the parent has not yet cleared it for reveal.
 * Blank/unavailable content is never held behind the skeleton, so its message surfaces at once.
 */
internal fun shouldShowBodySkeleton(
    htmlContentReady: Boolean,
    hasRenderableBody: Boolean,
    contentRevealed: Boolean
): Boolean = !htmlContentReady || (hasRenderableBody && !contentRevealed)
