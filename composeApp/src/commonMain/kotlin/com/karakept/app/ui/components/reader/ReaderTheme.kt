package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.karakept.app.ui.theme.HighlightYellow
import com.karakept.app.ui.theme.HighlightBlue
import com.karakept.app.ui.theme.HighlightGreen
import com.karakept.app.ui.theme.HighlightRed

@Immutable
data class ReaderThemeData(
    val textColor: Color,
    val backgroundColor: Color,
    val fontSize: TextUnit,
    val fontFamily: FontFamily,
    val linkColor: Color,
    val codeBackgroundColor: Color,
    val highlightColors: Map<String, Color> = defaultHighlightColors,
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

        val defaultHighlightColors = mapOf(
            "yellow" to HighlightYellow,
            "blue" to HighlightBlue,
            "green" to HighlightGreen,
            "red" to HighlightRed
        )

        /** Highlight text color — always black for readability on colored backgrounds. */
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
