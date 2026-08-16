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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.theme.rememberFontFamily

@Immutable
internal data class DescriptionCardColors(val container: Color, val content: Color)

/** How much accent is mixed into the reader's own colours off e-ink. */
private const val ACCENT_MIX = 0.2f

/**
 * Off e-ink the card tints the reader's own background with the accent, which keeps it legible
 * whatever colours the reader is set to.
 *
 * Under `highContrast` that tint resolves to the page colour twice over — `primaryContainer` is
 * flattened to the page, and mixing 20% of the page into the page changes nothing — so the summary
 * loses its container entirely. `secondaryContainer` is the scheme's one deliberate grey, the same
 * fill tag chips and e-ink highlights use, so the card reads as a block without an outline.
 */
internal fun descriptionCardColors(
    highContrast: Boolean,
    readerBackground: Color,
    readerText: Color,
    accentContainer: Color,
    onAccentContainer: Color,
    einkContainer: Color,
    einkContent: Color
): DescriptionCardColors = if (highContrast) {
    DescriptionCardColors(container = einkContainer, content = einkContent)
} else {
    DescriptionCardColors(
        container = lerp(readerBackground, accentContainer, ACCENT_MIX),
        content = lerp(readerText, onAccentContainer, ACCENT_MIX)
    )
}

@Composable
internal fun DescriptionCard(
    description: String,
    htmlBackgroundColor: Color?,
    htmlTextColor: Color?,
    htmlFontSize: Int,
    htmlFontFamily: ReaderFontFamily
) {
    val readerBg = htmlBackgroundColor ?: MaterialTheme.colorScheme.background
    val colors = descriptionCardColors(
        highContrast = LocalEinkMode.current.highContrast,
        readerBackground = readerBg,
        readerText = htmlTextColor ?: MaterialTheme.colorScheme.onBackground,
        accentContainer = MaterialTheme.colorScheme.primaryContainer,
        onAccentContainer = MaterialTheme.colorScheme.onPrimaryContainer,
        einkContainer = MaterialTheme.colorScheme.secondaryContainer,
        einkContent = MaterialTheme.colorScheme.onSecondaryContainer
    )

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
                containerColor = colors.container
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
                color = colors.content,
                fontFamily = htmlFontFamily.rememberFontFamily(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
