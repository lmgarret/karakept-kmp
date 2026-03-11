package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Immutable
data class ReaderThemeData(
    val textColor: Color,
    val backgroundColor: Color,
    val fontSize: TextUnit,
    val fontFamily: FontFamily,
    val linkColor: Color,
    val codeBackgroundColor: Color,
    val highlightColors: Map<String, Color> = defaultHighlightColors
) {
    companion object {
        val defaultHighlightColors = mapOf(
            "yellow" to Color(0xFFFFEB3B),
            "blue" to Color(0xFF2196F3),
            "green" to Color(0xFF4CAF50),
            "red" to Color(0xFFF44336)
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
