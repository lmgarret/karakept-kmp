package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.ui.theme.rememberFontFamily

@Composable
internal fun DescriptionCard(
    description: String,
    htmlBackgroundColor: Color?,
    htmlTextColor: Color?,
    htmlFontSize: Int,
    htmlFontFamily: ReaderFontFamily
) {
    // Mix reader background with accent color for subtle highlight (20% accent, 80% reader background)
    val readerBg = htmlBackgroundColor ?: MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primaryContainer
    val descriptionBgColor = lerp(readerBg, accent, 0.2f)

    // Mix reader text color with accent color for text (20% accent, 80% reader text)
    val readerTextColor = htmlTextColor ?: MaterialTheme.colorScheme.onBackground
    val accentTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val descriptionTextColor = lerp(readerTextColor, accentTextColor, 0.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(htmlBackgroundColor ?: MaterialTheme.colorScheme.background)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = descriptionBgColor
            )
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = TextUnit(
                        htmlFontSize.toFloat(),
                        TextUnitType.Sp
                    )
                ),
                color = descriptionTextColor,
                fontFamily = htmlFontFamily.rememberFontFamily(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
