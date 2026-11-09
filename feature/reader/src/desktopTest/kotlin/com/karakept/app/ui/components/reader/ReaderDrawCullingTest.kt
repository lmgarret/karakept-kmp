package com.karakept.app.ui.components.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Showing a long article recorded the drawing of every block, on screen or not — ~90 ms on an
 * 88-minute read. Only the blocks near the viewport may draw; the rest stay laid out.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderDrawCullingTest {

    private val blockCount = 200

    private fun runCulled(
        test: androidx.compose.ui.test.ComposeUiTest.(ReaderDrawCuller, IntArray, ScrollState, CoroutineScope) -> Unit
    ) = runComposeUiTest {
        val culler = ReaderDrawCuller(blockCount)
        val draws = IntArray(blockCount)
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope
        setContent {
            scrollState = rememberScrollState()
            scope = rememberCoroutineScope()
            Column(Modifier.height(400.dp).verticalScroll(scrollState)) {
                Column(Modifier.onGloballyPositioned(culler::onArticlePositioned)) {
                    repeat(blockCount) { index ->
                        Box(
                            Modifier.fillMaxWidth().height(50.dp)
                                .graphicsLayer()
                                .culledBy(culler, index)
                                .drawBehind { draws[index]++ }
                        )
                    }
                }
            }
        }
        waitForIdle()
        test(culler, draws, scrollState, scope)
    }

    @Test
    fun offScreenBlocks_areLaidOutButNotDrawn() = runCulled { culler, draws, _, _ ->
        val drawn = culler.drawnIndices()
        assertEquals(0, drawn.first)
        // 400dp of viewport is 8 blocks; the margin adds half the window below it.
        assertTrue(drawn.last in 7 until blockCount / 4, "drew blocks $drawn")
        for (i in drawn.last + 1 until blockCount) {
            assertEquals(0, draws[i], "block $i is off screen and must not draw")
        }
        assertTrue(drawn.all { draws[it] > 0 }, "every block near the viewport draws")
    }

    @Test
    fun scrolling_drawsTheBlocksComingIntoView_andRedrawsNoOthers() = runCulled { culler, draws, state, scope ->
        val before = culler.drawnIndices()
        val countsBefore = draws.copyOf()

        runOnIdle { scope.launch { state.scrollBy(state.maxValue.toFloat()) } }
        waitForIdle()

        val after = culler.drawnIndices()
        assertEquals(blockCount - 1, after.last)
        assertFalse(0 in after, "the top of the article has left the window")
        for (i in after) assertTrue(draws[i] > 0, "block $i scrolled into view and must draw")
        for (i in before) {
            if (i !in after) assertEquals(countsBefore[i], draws[i], "block $i left the window and must not redraw")
        }

        // A small scroll that keeps the same blocks in the window redraws none of them.
        val settled = draws.copyOf()
        runOnIdle { scope.launch { state.scrollBy(-1f) } }
        waitForIdle()
        if (culler.drawnIndices() == after) {
            assertTrue(settled.contentEquals(draws), "a scroll within the window must not re-record any block")
        }
    }

    @Test
    fun theRenderedArticle_drawsItsEndOnceScrolledThere() = runComposeUiTest {
        val html = buildString {
            append("<body><div>")
            repeat(blockCount) { append("<p>Paragraph $it of a very long article.</p>") }
            append("</div></body>")
        }
        val document = assertNotNull(prepareReaderDocument(html))
        var loaded = false
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope
        setContent {
            scrollState = rememberScrollState()
            scope = rememberCoroutineScope()
            Column(Modifier.height(400.dp).verticalScroll(scrollState)) {
                NativeHtmlRenderer(document = document, onLoaded = { loaded = true })
            }
        }
        waitUntil(timeoutMillis = 10_000) { loaded }
        waitForIdle()
        assertFalse(onRoot().captureToImage().isBlank(), "the top of the article must draw")

        runOnIdle { scope.launch { scrollState.scrollBy(scrollState.maxValue.toFloat()) } }
        waitForIdle()
        assertFalse(onRoot().captureToImage().isBlank(), "the end of the article must draw once scrolled to")
    }

    private fun androidx.compose.ui.graphics.ImageBitmap.isBlank(): Boolean {
        val pixels = toPixelMap()
        val first = pixels[0, 0]
        return (0 until pixels.width step 2).all { x ->
            (0 until pixels.height step 2).all { y -> pixels[x, y] == first }
        }
    }
}
