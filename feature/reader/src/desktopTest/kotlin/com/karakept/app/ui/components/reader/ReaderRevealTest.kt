package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The article is built over several frames, but the viewer must only ever be told it has loaded
 * once every block is in: scroll restoration, page snapping and progress tracking all read the
 * article's height at that moment, and a partial height would restore to the wrong place.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderRevealTest {

    private val blockCount = 400

    // One wrapper <div>, as Readability produces: without unwrapping this is a single block.
    private val html = buildString {
        append("<body><div>")
        repeat(blockCount) { append("<p>Paragraph $it of a very long article.</p>") }
        append("</div></body>")
    }

    @Test
    fun onLoaded_firesOnlyOnceEveryBlockIsComposed() = runComposeUiTest {
        val document = assertNotNull(prepareReaderDocument(html))
        assertEquals(blockCount, document.renderableChildren.size)

        var loadedCount = 0
        mainClock.autoAdvance = false
        setContent {
            Column(Modifier.height(800.dp).verticalScroll(rememberScrollState())) {
                NativeHtmlRenderer(document = document, onLoaded = { loadedCount++ })
            }
        }

        fun lastBlockComposed() =
            onAllNodesWithText("Paragraph ${blockCount - 1} ", substring = true)
                .fetchSemanticsNodes().isNotEmpty()

        var sawPartialReveal = false
        repeat(500) {
            if (loadedCount > 0) return@repeat
            mainClock.advanceTimeByFrame()
            if (!lastBlockComposed()) {
                if (!sawPartialReveal) {
                    // Drawing a partial article re-recorded all of it on every batch.
                    val pixels = onRoot().captureToImage().toPixelMap()
                    val first = pixels[0, 0]
                    val blank = (0 until pixels.width step 4).all { x ->
                        (0 until pixels.height step 4).all { y -> pixels[x, y] == first }
                    }
                    assertTrue(blank, "a partly built article must draw nothing")
                }
                sawPartialReveal = true
                assertEquals(0, loadedCount, "onLoaded fired before the last block was composed")
            }
        }

        assertTrue(sawPartialReveal, "the article should take more than one frame to build")
        assertEquals(1, loadedCount)
        assertTrue(lastBlockComposed())
        repeat(5) { mainClock.advanceTimeByFrame() }
        assertEquals(1, loadedCount, "onLoaded must fire once per document")
    }

    @Test
    fun onLoaded_firesForAnEmptyDocument() = runComposeUiTest {
        val document = assertNotNull(prepareReaderDocument(" "))
        var loaded = false
        setContent { NativeHtmlRenderer(document = document, onLoaded = { loaded = true }) }
        waitForIdle()
        assertTrue(loaded)
    }

    @Test
    fun nextRevealBatchSize_fitsTheBudgetAtTheMeasuredCost() {
        // 20 blocks at 1 ms each: 8 of them fit an 8 ms frame, raised to the floor of 10.
        assertEquals(10, nextRevealBatchSize(20, kotlin.time.Duration.parse("20ms")))
        // 20 blocks at 0.1 ms each: 80 would fit, exactly the fourfold cap.
        assertEquals(80, nextRevealBatchSize(20, kotlin.time.Duration.parse("2ms")))
        // Practically free: still capped at fourfold growth, then at the ceiling.
        assertEquals(80, nextRevealBatchSize(20, kotlin.time.Duration.parse("1us")))
        assertEquals(500, nextRevealBatchSize(400, kotlin.time.Duration.parse("1us")))
        assertFalse(nextRevealBatchSize(1, kotlin.time.Duration.ZERO) < 10)
    }
}
