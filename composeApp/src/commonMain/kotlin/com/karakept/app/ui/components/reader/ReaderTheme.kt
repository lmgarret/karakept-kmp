package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The single fill every highlight shares on a monochrome panel.
 *
 * Four saturated fills all land within a couple of greys of one another on e-ink, so the fill only
 * says *that* a run is highlighted; which colour it is comes from the pattern rule
 * [com.karakept.app.ui.components.drawHighlightRule] draws underneath.
 */
@Immutable
data class MonochromeHighlight(val fill: Color, val content: Color)

@Immutable
data class ReaderThemeData(
    val textColor: Color,
    val backgroundColor: Color,
    val fontSize: TextUnit,
    val fontFamily: FontFamily,
    val linkColor: Color,
    val codeBackgroundColor: Color,
    /** Non-null only on e-ink; off it, highlights keep their own colours. */
    val monochromeHighlight: MonochromeHighlight? = null,
    /**
     * Multiplies the per-block line-height ratios below. A scale rather than an absolute line
     * height, so body / heading / code stay proportional to one another.
     */
    val lineHeightScale: Float = 1.0f
) {
    /** Line height for body-sized text, in sp. */
    val bodyLineHeight: TextUnit get() = (fontSize.value * BODY_LINE_RATIO * lineHeightScale).sp

    /** Line height for a block whose font size is [fontSize] × [sizeRatio], in sp. */
    fun tightLineHeight(sizeRatio: Float = 1f): TextUnit =
        (fontSize.value * sizeRatio * TIGHT_LINE_RATIO * lineHeightScale).sp

    companion object {
        /** Body copy: generous leading for long-form reading. */
        const val BODY_LINE_RATIO = 1.6f

        /** Headings, code blocks, blockquotes and tables: tighter than body copy. */
        const val TIGHT_LINE_RATIO = 1.4f

        /**
         * Highlight text color — black for readability on the saturated colored backgrounds.
         *
         * E-ink does not use this: its highlights are filled with `secondaryContainer`, whose own
         * `onSecondaryContainer` follows the panel's light/dark inversion.
         */
        val highlightTextColor = Color.Black
    }
}

val LocalReaderTheme = staticCompositionLocalOf<ReaderThemeData> {
    error("No ReaderThemeData provided")
}

@Composable
fun ReaderThemeProvider(
    theme: ReaderThemeData,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalReaderTheme provides theme) {
        content()
    }
}
