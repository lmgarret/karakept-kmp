package com.karakept.app.ui.components.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry tests for [highlightLineRects], the per-line boxes both the selection mask and the
 * e-ink pattern rule are built from.
 *
 * `getPathForRange()` is the obvious alternative and the reason this exists: it extends every
 * wrapped line to the full column width, so a two-word highlight at the end of a paragraph would
 * get a rule running the whole way across the page.
 */
@OptIn(ExperimentalTestApi::class)
class HighlightLineRectsTest {

    private val text = "one two three four five six seven eight nine ten eleven twelve"

    /** Lays [text] out at [widthPx] and hands the result to [assertions]. */
    private fun layout(widthPx: Int, assertions: (TextLayoutResult) -> Unit) = runComposeUiTest {
        val captured = mutableStateOf<TextLayoutResult?>(null)
        setContent {
            val measurer = rememberTextMeasurer()
            captured.value = measurer.measure(
                text = buildAnnotatedString { append(text) },
                style = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.Default),
                constraints = Constraints(maxWidth = widthPx)
            )
        }
        waitForIdle()
        assertions(requireNotNull(captured.value) { "text was never measured" })
    }

    @Test
    fun singleLineRangeProducesOneRect() = layout(widthPx = 4000) { result ->
        assertEquals(1, result.lineCount, "test needs the text on one line")

        val rects = highlightLineRects(result, 0, 7)

        assertEquals(1, rects.size)
        assertTrue(rects.single().width > 0f)
    }

    @Test
    fun wrappedRangeProducesOneRectPerCoveredLine() = layout(widthPx = 120) { result ->
        assertTrue(result.lineCount >= 2, "test needs the text to wrap")

        val rects = highlightLineRects(result, 0, text.length)

        assertEquals(result.lineCount, rects.size)
        // Each rect belongs to a later line than the one before it.
        rects.zipWithNext { above, below -> assertTrue(below.top >= above.bottom - 1f) }
    }

    @Test
    fun rectStopsAtTheEndOfTheRangeNotTheEndOfTheLine() = layout(widthPx = 4000) { result ->
        val partial = highlightLineRects(result, 0, 3).single()
        val whole = highlightLineRects(result, 0, text.length).single()

        // This is exactly what getPathForRange() would get wrong.
        assertTrue(
            partial.right < whole.right,
            "a partial range spanned the full line: ${partial.right} vs ${whole.right}"
        )
    }

    @Test
    fun emptyAndInvertedRangesDrawNothing() = layout(widthPx = 4000) { result ->
        assertTrue(highlightLineRects(result, 5, 5).isEmpty())
        assertTrue(highlightLineRects(result, 9, 4).isEmpty())
    }
}
