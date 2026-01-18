package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karakept.app.data.model.Highlight

/**
 * Floating card that displays highlighted text above the bottom panel.
 * Appears with scale + fade animation when a highlight is clicked.
 *
 * @param highlight The highlight to display
 * @param visible Whether the card is visible
 * @param fontFamily The font family to use for the text
 */
@Composable
fun HighlightFloatingCard(
    highlight: Highlight,
    visible: Boolean,
    fontFamily: com.karakept.app.data.model.ReaderFontFamily = com.karakept.app.data.model.ReaderFontFamily.SYSTEM,
    fontSize: Int = 16,
    overrideColor: String? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialScale = 0.9f
        ) + fadeIn(),
        exit = scaleOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            targetScale = 0.9f
        ) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 16.dp)
                .shadow(4.dp, RoundedCornerShape(4.dp))
                .background(
                    color = getColorForHighlight(overrideColor ?: highlight.color ?: "yellow"),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Text(
                text = highlight.text,
                fontSize = fontSize.sp,
                fontFamily = fontFamily.composeFontFamily,
                color = Color.Black,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/**
 * Position data for a highlight in the WebView
 */
data class HighlightPosition(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val scrollX: Float,
    val scrollY: Float
)

/**
 * Maps highlight color names to Material color values
 */
@Composable
private fun getColorForHighlight(colorName: String): Color {
    return when (colorName.lowercase()) {
        "yellow" -> Color(0xFFFFEB3B)
        "blue" -> Color(0xFF2196F3)
        "green" -> Color(0xFF4CAF50)
        "red" -> Color(0xFFF44336)
        else -> Color(0xFFFFEB3B)
    }
}
