package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.ui.components.HighlightPosition

/**
 * Native Compose Multiplatform HTML renderer for READER mode.
 *
 * Replaces the WebView-based renderer with pure Compose composables.
 * Parses sanitized HTML via ksoup and emits Text, Image, and layout composables.
 *
 * Highlight offsets are compatible with the WebView's JavaScript TreeWalker
 * approach: both walk text nodes in document order and count characters.
 */
@Composable
fun NativeHtmlRenderer(
    html: String,
    modifier: Modifier = Modifier,
    highlights: List<Highlight> = emptyList(),
    textColor: Color? = null,
    backgroundColor: Color? = null,
    fontSize: Int = 16,
    fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    onLinkClick: (String) -> Unit = {},
    onHighlightClick: (String) -> Unit = {},
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onHighlightPosition: ((String, HighlightPosition?) -> Unit)? = null,
    scrollToHighlightId: String? = null,
    onLoaded: (() -> Unit)? = null
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    val resolvedTextColor = textColor
        ?: if (surfaceColor.luminance() > 0.5f) Color.Black else Color.White

    val resolvedBackgroundColor = backgroundColor ?: surfaceColor

    val theme = remember(resolvedTextColor, resolvedBackgroundColor, fontSize, fontFamily, primaryColor) {
        ReaderThemeData(
            textColor = resolvedTextColor,
            backgroundColor = resolvedBackgroundColor,
            fontSize = fontSize.sp,
            fontFamily = fontFamily.composeFontFamily,
            linkColor = primaryColor,
            codeBackgroundColor = if (resolvedBackgroundColor.luminance() > 0.5f) {
                Color(0x1A7F7F7F) // rgba(127,127,127,0.1) on light
            } else {
                Color(0x337F7F7F) // slightly more visible on dark
            }
        )
    }

    // Parse HTML once and cache
    val document = remember(html) {
        try {
            Ksoup.parse(html)
        } catch (e: Exception) {
            null
        }
    }

    if (document == null) {
        onLoaded?.invoke()
        return
    }

    val body = document.body()
    val textOffset = remember(html) { TextOffsetTracker() }

    // Track whether we've reported the highlight position (only report once)
    var highlightPositionReported by remember(scrollToHighlightId) { mutableStateOf(false) }

    // Find the target highlight for scroll-to
    val targetHighlight = remember(scrollToHighlightId, highlights) {
        if (scrollToHighlightId != null) highlights.find { it.id == scrollToHighlightId } else null
    }

    // Custom text toolbar with "Highlight" action (Android only)
    val highlightToolbar = rememberHighlightTextToolbar { selectedText ->
        // Find offsets for the selected text in the HTML document
        val offsets = findTextOffsets(html, selectedText)
        if (offsets != null) {
            onCreateHighlight(offsets.matchedText, offsets.startOffset, offsets.endOffset, null, null)
        }
    }

    ReaderThemeProvider(theme = theme) {
        // Provide custom text toolbar if available (Android), otherwise use default
        val toolbarProvider: @Composable (@Composable () -> Unit) -> Unit = if (highlightToolbar != null) {
            { content ->
                CompositionLocalProvider(LocalTextToolbar provides highlightToolbar) {
                    content()
                }
            }
        } else {
            { content -> content() }
        }

        toolbarProvider {
            SelectionContainer {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 0.dp)
                        .padding(bottom = 28.dp)
                ) {
                // Reset offset at start of rendering
                textOffset.offset = 0

                // Render body children, wrapping blocks that might contain the scroll target
                val bodyChildren = body.childNodes()
                var i = 0
                while (i < bodyChildren.size) {
                    val child = bodyChildren[i]
                    if (child is com.fleeksoft.ksoup.nodes.Element && isBlockElement(child)) {
                        val blockStartOffset = textOffset.offset
                        val blockTextLength = child.text().length

                        // Check if this block contains the scroll-to highlight
                        val needsPositionTracking = targetHighlight != null &&
                            !highlightPositionReported &&
                            targetHighlight.startOffset >= blockStartOffset &&
                            targetHighlight.startOffset < blockStartOffset + blockTextLength

                        if (needsPositionTracking && onHighlightPosition != null) {
                            val highlightId = scrollToHighlightId!!
                            Box(
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    if (!highlightPositionReported) {
                                        highlightPositionReported = true
                                        val position = coords.positionInParent()
                                        onHighlightPosition(
                                            highlightId,
                                            HighlightPosition(
                                                x = position.x,
                                                y = position.y,
                                                width = coords.size.width.toFloat(),
                                                height = coords.size.height.toFloat(),
                                                scrollX = 0f,
                                                scrollY = 0f
                                            )
                                        )
                                    }
                                }
                            ) {
                                RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick)
                            }
                        } else {
                            RenderBlock(child, highlights, textOffset, onLinkClick, onHighlightClick)
                        }
                        i++
                    } else if (child is com.fleeksoft.ksoup.nodes.TextNode) {
                        // Bare text node at body level — skip if whitespace only
                        if (child.wholeText.isNotBlank()) {
                            val theme = LocalReaderTheme.current
                            val text = child.wholeText
                            textOffset.advance(text.length)
                            androidx.compose.material3.Text(
                                text = text,
                                color = theme.textColor,
                                fontSize = theme.fontSize,
                                fontFamily = theme.fontFamily,
                                lineHeight = (theme.fontSize.value * 1.6f).sp
                            )
                        } else {
                            textOffset.advance(child.wholeText.length)
                        }
                        i++
                    } else {
                        i++
                    }
                }

                // Bottom spacing
                Spacer(Modifier.height(16.dp))
            }
            }
        }
    }

    // Notify parent that content is ready
    LaunchedEffect(document) {
        onLoaded?.invoke()
    }
}
