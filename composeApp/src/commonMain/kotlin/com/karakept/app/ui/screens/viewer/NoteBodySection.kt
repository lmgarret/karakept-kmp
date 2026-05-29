package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.ui.theme.rememberFontFamily

/**
 * Renders a text/note bookmark's body as plain selectable text, honoring the reader's
 * font and color settings. Notes are stored as plain text, so they intentionally bypass
 * the HTML [ContentBodySection] renderer.
 */
@Composable
internal fun NoteBodySection(
    content: String,
    htmlBackgroundColor: Color?,
    htmlTextColor: Color?,
    htmlFontSize: Int,
    htmlFontFamily: ReaderFontFamily
) {
    SelectionContainer(
        modifier = Modifier
            .fillMaxWidth()
            .background(htmlBackgroundColor ?: MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = TextUnit(htmlFontSize.toFloat(), TextUnitType.Sp)
            ),
            color = htmlTextColor ?: MaterialTheme.colorScheme.onBackground,
            fontFamily = htmlFontFamily.rememberFontFamily(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}
