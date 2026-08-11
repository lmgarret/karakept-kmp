package com.karakept.app.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regression coverage for the highlight mask stretching when the reader scrolls
 * (#295).
 *
 * Each text block reports its own piece of the mask in root coordinates and
 * re-reports whenever it moves. The pieces used to be merged blindly, so opening
 * the details panel — which scrolls the content — left the pre-scroll piece in
 * the mask next to the post-scroll one, and the cut-out grew by the distance
 * scrolled.
 *
 * Lives in the desktop source set because [Path] needs a real graphics backend;
 * Android unit tests have no `android.graphics`. The class itself is common and
 * carries no platform behaviour.
 */
class HighlightMaskAccumulatorTest {

    private fun positionOf(rect: Rect, sourceKey: Any?): HighlightPosition {
        val path = Path().apply { addRect(rect) }
        return HighlightPosition(
            x = rect.left,
            y = rect.top,
            width = rect.width,
            height = rect.height,
            scrollX = 0f,
            scrollY = 0f,
            path = path,
            sourceKey = sourceKey
        )
    }

    private fun boundsOf(position: HighlightPosition): Rect {
        val path = position.path
        assertNotNull(path)
        return path.getBounds()
    }

    @Test
    fun aBlockThatMovesReplacesItsOwnPiece() {
        val accumulator = HighlightMaskAccumulator()
        val block = Any()

        accumulator.accumulate(positionOf(Rect(0f, 100f, 200f, 130f), block), fallbackKey = "id")
        // The content scrolled up by 80px, so the same block reports again.
        val afterScroll = accumulator.accumulate(
            positionOf(Rect(0f, 20f, 200f, 50f), block),
            fallbackKey = "id"
        )

        val bounds = boundsOf(afterScroll)
        assertEquals(20f, bounds.top)
        assertEquals(50f, bounds.bottom)
        assertEquals(30f, bounds.height, "The mask must not grow by the distance scrolled")
    }

    @Test
    fun piecesFromDifferentBlocksAreUnioned() {
        val accumulator = HighlightMaskAccumulator()

        accumulator.accumulate(positionOf(Rect(0f, 10f, 200f, 40f), Any()), fallbackKey = "id")
        val merged = accumulator.accumulate(
            positionOf(Rect(0f, 50f, 200f, 80f), Any()),
            fallbackKey = "id"
        )

        val bounds = boundsOf(merged)
        assertEquals(10f, bounds.top)
        assertEquals(80f, bounds.bottom, "A highlight spanning two paragraphs covers both")
    }

    @Test
    fun aMultiBlockHighlightSurvivesAScroll() {
        val accumulator = HighlightMaskAccumulator()
        val first = Any()
        val second = Any()

        accumulator.accumulate(positionOf(Rect(0f, 100f, 200f, 130f), first), fallbackKey = "id")
        accumulator.accumulate(positionOf(Rect(0f, 140f, 200f, 170f), second), fallbackKey = "id")

        // Both blocks move up by 80px.
        accumulator.accumulate(positionOf(Rect(0f, 20f, 200f, 50f), first), fallbackKey = "id")
        val afterScroll = accumulator.accumulate(
            positionOf(Rect(0f, 60f, 200f, 90f), second),
            fallbackKey = "id"
        )

        val bounds = boundsOf(afterScroll)
        assertEquals(20f, bounds.top)
        assertEquals(90f, bounds.bottom)
    }

    @Test
    fun clearingDropsEveryPiece() {
        val accumulator = HighlightMaskAccumulator()
        val block = Any()

        accumulator.accumulate(positionOf(Rect(0f, 100f, 200f, 130f), block), fallbackKey = "id")
        accumulator.clear()
        val rebuilt = accumulator.accumulate(
            positionOf(Rect(0f, 20f, 200f, 50f), Any()),
            fallbackKey = "id"
        )

        val bounds = boundsOf(rebuilt)
        assertEquals(20f, bounds.top)
        assertEquals(50f, bounds.bottom)
    }

    @Test
    fun reportsWithoutASourceFallBackToTheHighlightId() {
        val accumulator = HighlightMaskAccumulator()

        accumulator.accumulate(positionOf(Rect(0f, 100f, 200f, 130f), sourceKey = null), "highlight-1")
        val afterScroll = accumulator.accumulate(
            positionOf(Rect(0f, 20f, 200f, 50f), sourceKey = null),
            "highlight-1"
        )

        val bounds = boundsOf(afterScroll)
        assertEquals(30f, bounds.height, "One unidentified reporter must not stack with itself")
    }

    @Test
    fun aPositionWithoutAPathResetsTheMask() {
        val accumulator = HighlightMaskAccumulator()
        accumulator.accumulate(positionOf(Rect(0f, 100f, 200f, 130f), Any()), fallbackKey = "id")

        val pathless = HighlightPosition(0f, 0f, 0f, 0f, 0f, 0f, path = null)
        assertNull(accumulator.accumulate(pathless, fallbackKey = "id").path)

        val rebuilt = accumulator.accumulate(
            positionOf(Rect(0f, 20f, 200f, 50f), Any()),
            fallbackKey = "id"
        )
        assertEquals(20f, boundsOf(rebuilt).top)
    }
}
