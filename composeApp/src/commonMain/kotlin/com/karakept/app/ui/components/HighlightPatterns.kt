package com.karakept.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.karakept.app.ui.theme.HighlightPattern
import com.karakept.app.ui.theme.HighlightStyle
import com.karakept.app.ui.theme.LocalEinkMode

/**
 * Draws a highlight's pattern as a horizontal rule spanning [left]..[right], growing upward from
 * [bottom] so every pattern stays inside the highlighted band whatever its height.
 *
 * Used under a highlighted text run in the reader, where the four colours are all rendered with the
 * same grey fill on e-ink and the pattern is the only thing telling them apart.
 */
fun DrawScope.drawHighlightRule(
    pattern: HighlightPattern,
    color: Color,
    left: Float,
    right: Float,
    bottom: Float,
    strokeWidth: Float
) {
    if (right <= left) return

    fun rule(y: Float, effect: PathEffect? = null, cap: StrokeCap = StrokeCap.Butt) {
        drawLine(
            color = color,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = strokeWidth,
            pathEffect = effect,
            cap = cap
        )
    }

    val baseline = bottom - strokeWidth / 2f
    when (pattern) {
        HighlightPattern.SOLID -> rule(baseline)
        HighlightPattern.DOUBLE -> {
            rule(baseline)
            rule(baseline - strokeWidth * 2f)
        }
        HighlightPattern.DASHED ->
            rule(baseline, PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 4f, strokeWidth * 3f)))
        HighlightPattern.DOTTED ->
            rule(
                baseline,
                PathEffect.dashPathEffect(floatArrayOf(strokeWidth, strokeWidth * 2f)),
                StrokeCap.Round
            )
    }
}

/**
 * The colour accent on a highlight row — a plain fill normally, the colour's pattern on e-ink.
 *
 * [modifier] must give the bar its size; the caller owns the width and height as it did when this
 * was a plain `Box().background()`.
 */
@Composable
fun HighlightPatternBar(
    style: HighlightStyle,
    modifier: Modifier = Modifier
) {
    if (!LocalEinkMode.current.highContrast) {
        Box(modifier.background(style.color))
        return
    }

    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val width = size.width
        when (style.pattern) {
            HighlightPattern.SOLID -> drawRect(ink)
            HighlightPattern.DOUBLE -> {
                val rule = width / 3f
                drawRect(ink, size = Size(rule, size.height))
                drawRect(ink, topLeft = Offset(width - rule, 0f), size = Size(rule, size.height))
            }
            HighlightPattern.DASHED -> drawVerticalDashes(ink, width, width * 2.5f, width * 1.5f)
            HighlightPattern.DOTTED -> drawVerticalDashes(ink, width, width, width * 1.2f)
        }
    }
}

private fun DrawScope.drawVerticalDashes(color: Color, width: Float, dash: Float, gap: Float) {
    drawLine(
        color = color,
        start = Offset(width / 2f, 0f),
        end = Offset(width / 2f, size.height),
        strokeWidth = width,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))
    )
}
