package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.reader.NativeHtmlRenderer

/**
 * Desktop (JVM) implementation of HtmlRenderer using NativeHtmlRenderer.
 * 
 * We use the pure Compose-based NativeHtmlRenderer for all modes on Desktop
 * to avoid JavaFX dependencies and provide a consistent experience.
 */
@Composable
actual fun HtmlRenderer(
    html: String,
    viewerMode: ViewerMode,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)?,
    onLoaded: (() -> Unit)?,
    customTextColor: Color?,
    customFontSize: Int,
    customFontFamily: ReaderFontFamily,
    localFilePath: String?,
    highlights: List<com.karakept.app.data.model.Highlight>,
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    scrollToHighlightId: String?
) {
    // For Desktop, we use the NativeHtmlRenderer for all modes for simplicity and performance,
    // as it doesn't require a heavy browser engine or JavaFX.
    NativeHtmlRenderer(
        html = html,
        modifier = modifier,
        highlights = highlights,
        textColor = customTextColor,
        fontSize = customFontSize,
        fontFamily = customFontFamily,
        onLinkClick = { onLinkClick?.invoke(it) },
        onHighlightClick = onHighlightClick,
        onCreateHighlight = onCreateHighlight,
        onHighlightPosition = onHighlightPosition,
        scrollToHighlightId = scrollToHighlightId,
        onLoaded = onLoaded
    )
}
