package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drags the reader's scroll cursor over a real rendered article and checks, at every step, that
 * the section it names is the one the reader is actually looking at.
 *
 * `ReaderScrollCursorTest` pins the projection arithmetic against inputs it chooses; this checks
 * the inputs. The reader paints its top bar over the list, so "the section you are in" is the
 * last heading that has reached the top of the *readable* area — below the bar, not the top of
 * the viewport. That is measured here off the headings' own laid-out bounds, independently of
 * anything the cursor computes, so a cursor that is wrong about where the headings are cannot
 * also be wrong about what it is checked against.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderScrollCursorDragTest {

    private val sectionCount = 10
    private val obscuredTop = 120.dp

    // The last section runs on for several screens, so every heading — the last one included — can
    // be scrolled all the way to the top. Headings in an article's final screenful cannot be, and
    // are named by a different rule (the end of the article names the last section).
    private val html = buildString {
        append("<body><div>")
        repeat(sectionCount) { index ->
            append("<h2>Section $index</h2>")
            val paragraphs = if (index == sectionCount - 1) 40 else 6
            repeat(paragraphs) { append("<p>Body of section $index, paragraph $it, long enough to wrap.</p>") }
        }
        append("</div></body>")
    }

    private class Scene(
        val listTopInRoot: () -> Float,
        val obscuredTopPx: Float,
        /** The list's real height — the test window can be smaller than the box asked for. */
        val viewportPx: Float,
        val lookaheadPx: Float,
        /** How far the list has scrolled, in pixels from the very top. */
        val scrolledPx: () -> Float
    )

    private fun runScene(test: ComposeUiTest.(Scene) -> Unit) = runComposeUiTest {
        val document = assertNotNull(prepareReaderDocument(html))
        var listTop = 0f
        var obscuredPx = 0f
        var heroPx = 0f
        var viewportPx = 0
        lateinit var listState: LazyListState
        setContent {
            listState = remember { LazyListState() }
            heroPx = with(LocalDensity.current) { 300.dp.toPx() }
            val toc = remember { ReaderTocState() }
            obscuredPx = with(LocalDensity.current) { obscuredTop.toPx() }
            CompositionLocalProvider(LocalReaderToc provides toc) {
                Box(Modifier.size(400.dp, 800.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned {
                                listTop = it.positionInRoot().y
                                viewportPx = it.size.height
                            }
                    ) {
                        // The hero, as in the reader: the article is not the list's first item.
                        item { Spacer(Modifier.height(300.dp)) }
                        item { NativeHtmlRenderer(document = document) }
                    }
                    ReaderScrollCursor(
                        listState = listState,
                        toc = toc,
                        listTopInRoot = { listTop },
                        onScrollTo = { index, offset -> listState.scrollToItem(index, offset) },
                        obscuredTopPx = obscuredPx.toInt(),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                            .padding(top = obscuredTop)
                    )
                }
            }
        }
        waitForIdle()
        test(
            Scene(
                listTopInRoot = { listTop },
                obscuredTopPx = obscuredPx,
                viewportPx = viewportPx.toFloat(),
                lookaheadPx = sectionLookaheadPx(viewportPx, obscuredPx.toInt()).toFloat(),
                scrolledPx = {
                    val offset = listState.firstVisibleItemScrollOffset.toFloat()
                    if (listState.firstVisibleItemIndex == 0) offset else heroPx + offset
                }
            )
        )
    }

    private val inLabel = hasAnyAncestor(hasTestTag(ACTIVE_SECTION_TAG))

    /**
     * Where each heading of the article sits in root coordinates, read off its own layout. Not
     * `boundsInRoot`, which is clipped to the window and puts every off-screen heading at its edge.
     */
    private fun ComposeUiTest.headingTops(): List<Float> = List(sectionCount) { index ->
        onAllNodes(hasText("Section $index") and !inLabel, useUnmergedTree = true)
            .fetchSemanticsNodes().first().positionInRoot.y
    }

    /** The vertical centre of the cursor's bubble, or null when it is not up. */
    private fun ComposeUiTest.bubbleCenter(): Float? =
        onAllNodes(hasTestTag(ACTIVE_SECTION_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull()
            ?.let { it.positionInRoot.y + it.size.height / 2f }

    /** The section the cursor names, or null when it names none. */
    private fun ComposeUiTest.activeSection(): Int? = (0 until sectionCount).firstOrNull { index ->
        onAllNodes(hasText("Section $index") and inLabel, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    /** One step of a slow drag down the whole track. */
    private class Step(
        val fingerY: Float,
        val headingTops: List<Float>,
        val readableTop: Float,
        val named: Int?,
        val bubbleCenter: Float?,
        val scrolledPx: Float
    )

    private fun ComposeUiTest.dragDownTheTrack(scene: Scene, steps: Int = 80): List<Step> {
        val trackTop = scene.listTopInRoot() + scene.obscuredTopPx
        val trackBottom = scene.listTopInRoot() + scene.viewportPx
        val x = with(density) { 400.dp.toPx() } - 4f
        val result = mutableListOf<Step>()

        onRoot().performTouchInput { down(Offset(x, trackTop + 1f)) }
        waitForIdle()
        repeat(steps) { step ->
            val y = trackTop + 1f + (trackBottom - trackTop - 2f) * (step + 1) / steps
            onRoot().performTouchInput { moveTo(Offset(x, y)) }
            waitForIdle()
            result += Step(
                fingerY = y,
                headingTops = headingTops(),
                readableTop = scene.listTopInRoot() + scene.obscuredTopPx,
                named = activeSection(),
                bubbleCenter = bubbleCenter(),
                scrolledPx = runOnIdle { scene.scrolledPx() }
            )
        }
        onRoot().performTouchInput { up() }
        waitForIdle()
        return result
    }

    private fun Step.describe() = "names $named, heading tops ${headingTops.map { (it - readableTop).toInt() }} " +
        "relative to the readable top"

    // A pixel or two for rounding a scroll fraction to whole pixels and back.
    private val slack = 2f

    @Test
    fun theNamedSectionIsTheOneComingIntoViewThroughoutADrag() = runScene { scene ->
        val mismatches = dragDownTheTrack(scene).mapIndexedNotNull { index, step ->
            // The last heading to have come a lookahead into the readable area. A heading within a
            // pixel or two of that line could round either way, so both answers stand there.
            val line = step.readableTop + scene.lookaheadPx
            val surely = step.headingTops.indexOfLast { it <= line - slack }.takeIf { it >= 0 }
            val possibly = step.headingTops.indexOfLast { it <= line + slack }.takeIf { it >= 0 }
            if (step.named != surely && step.named != possibly) {
                "step $index: expected $surely or $possibly, cursor ${step.describe()}"
            } else {
                null
            }
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    @Test
    fun aSectionIsNamedWithItsHeadingOnScreenUnderTheTopBar() {
        // The regression (#277). Named only when its heading reached the readable top, a section's
        // first frame had the heading behind the bar already: a drag moves the article in jumps,
        // and the frame where it crossed was skipped. Named a lookahead early, it is on screen.
        runScene { scene ->
            val firstNamed = mutableMapOf<Int, Step>()
            for (step in dragDownTheTrack(scene)) {
                val named = step.named ?: continue
                firstNamed.getOrPut(named) { step }
            }

            assertEquals((0 until sectionCount).toList(), firstNamed.keys.sorted(), "every section is named on the way down")
            val hidden = firstNamed.filter { (section, step) ->
                val top = step.headingTops[section]
                top < step.readableTop - slack || top > step.readableTop + scene.lookaheadPx + slack
            }
            assertTrue(
                hidden.isEmpty(),
                hidden.entries.joinToString("\n") { (section, step) ->
                    "section $section first named with its heading " +
                        "${(step.headingTops[section] - step.readableTop).toInt()}px from the readable top"
                }
            )
        }
    }

    @Test
    fun theBubbleRidesTheThumb() = runScene { scene ->
        // As the bookmark list's does: centred on the finger, wherever there is room to be.
        // Away from the ends of the track, where the bubble is pinned inside it instead.
        val margin = with(density) { 24.dp.toPx() }
        val trackTop = scene.listTopInRoot() + scene.obscuredTopPx
        val trackBottom = scene.listTopInRoot() + scene.viewportPx
        val off = dragDownTheTrack(scene)
            .filter { it.bubbleCenter != null }
            .filter { it.fingerY - margin > trackTop && it.fingerY + margin < trackBottom }
            .filter { kotlin.math.abs(it.bubbleCenter!! - it.fingerY) > 1.5f }
        assertTrue(off.isEmpty(), off.joinToString("\n") { "finger at ${it.fingerY}, bubble at ${it.bubbleCenter}" })
    }

    @Test
    fun theArticleFollowsTheThumbAtOneSpeed() = runScene { scene ->
        // Linear, as the bookmark list's cursor is: equal moves of the finger, equal moves of the
        // article — no stretch where it holds still, and none where it races to catch up.
        val scrolled = dragDownTheTrack(scene).map { it.scrolledPx }
        // The first and last moves run into the ends of the track, where the thumb stops short of
        // the finger; everything between is the map itself.
        val deltas = scrolled.zipWithNext { a, b -> b - a }.filter { it > 0f }.drop(1).dropLast(1)
        val typical = deltas.sorted()[deltas.size / 2]
        val uneven = deltas.filter { kotlin.math.abs(it - typical) > 2f }
        assertTrue(uneven.isEmpty(), "steps of ${deltas.map { it.toInt() }}")
    }

    @Test
    fun aSwipeInFromTheEdgeDoesNotScroll() = runScene { scene ->
        // Android's back gesture starts on the same edge as the track, and the system cancels the
        // press once it recognises the swipe. Jumping on the down had already scrolled the article.
        val x = with(density) { 400.dp.toPx() } - 4f
        val y = scene.listTopInRoot() + scene.viewportPx * 0.75f
        onRoot().performTouchInput {
            down(Offset(x, y))
            moveTo(Offset(x - 40f, y))
            moveTo(Offset(x - 120f, y + 4f))
            cancel()
        }
        waitForIdle()
        assertEquals(0f, runOnIdle { scene.scrolledPx() })
        assertEquals(null, bubbleCenter())
    }

    @Test
    fun aCancelledPressOnTheTrackDoesNotScroll() = runScene { scene ->
        val x = with(density) { 400.dp.toPx() } - 4f
        val y = scene.listTopInRoot() + scene.viewportPx * 0.75f
        onRoot().performTouchInput {
            down(Offset(x, y))
            cancel()
        }
        waitForIdle()
        assertEquals(0f, runOnIdle { scene.scrolledPx() })
    }

    @Test
    fun aTapOnTheTrackStillJumps() = runScene { scene ->
        val x = with(density) { 400.dp.toPx() } - 4f
        val y = scene.listTopInRoot() + scene.viewportPx * 0.75f
        onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        waitForIdle()
        assertTrue(runOnIdle { scene.scrolledPx() } > 0f)
    }
}
