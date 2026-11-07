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

    private class Scene(val listTopInRoot: () -> Float, val obscuredTopPx: Float)

    private fun runScene(test: ComposeUiTest.(Scene) -> Unit) = runComposeUiTest {
        val document = assertNotNull(prepareReaderDocument(html))
        var listTop = 0f
        var obscuredPx = 0f
        setContent {
            val listState = remember { LazyListState() }
            val toc = remember { ReaderTocState() }
            obscuredPx = with(LocalDensity.current) { obscuredTop.toPx() }
            CompositionLocalProvider(LocalReaderToc provides toc) {
                Box(Modifier.size(400.dp, 800.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned { listTop = it.positionInRoot().y }
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
        test(Scene({ listTop }, obscuredPx))
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

    /** The section the cursor names, or null when it names none. */
    private fun ComposeUiTest.activeSection(): Int? = (0 until sectionCount).firstOrNull { index ->
        onAllNodes(hasText("Section $index") and inLabel, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    /** One step of a slow drag down the whole track: where the headings were, and what was named. */
    private class Step(val headingTops: List<Float>, val readableTop: Float, val named: Int?)

    private fun ComposeUiTest.dragDownTheTrack(scene: Scene, steps: Int = 80): List<Step> {
        val trackTop = scene.obscuredTopPx
        val trackBottom = with(density) { 800.dp.toPx() }
        val x = with(density) { 400.dp.toPx() } - 4f
        val result = mutableListOf<Step>()

        onRoot().performTouchInput { down(Offset(x, trackTop + 1f)) }
        waitForIdle()
        repeat(steps) { step ->
            val y = trackTop + 1f + (trackBottom - trackTop - 2f) * (step + 1) / steps
            onRoot().performTouchInput { moveTo(Offset(x, y)) }
            waitForIdle()
            result += Step(headingTops(), scene.listTopInRoot() + scene.obscuredTopPx, activeSection())
        }
        onRoot().performTouchInput { up() }
        waitForIdle()
        return result
    }

    private fun Step.describe() = "names $named, heading tops ${headingTops.map { (it - readableTop).toInt() }} " +
        "relative to the readable top"

    @Test
    fun theNamedSectionIsTheOneBeingReadThroughoutADrag() = runScene { scene ->
        // A pixel or two for rounding a scroll fraction to whole pixels and back.
        val slack = 2f
        val mismatches = dragDownTheTrack(scene).mapIndexedNotNull { index, step ->
            // The section being read: the last heading at or above the readable top.
            val truth = step.headingTops.indexOfLast { it <= step.readableTop + slack }.takeIf { it >= 0 }
            if (step.named != truth) "step $index: reader is in $truth, cursor ${step.describe()}" else null
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    @Test
    fun aSectionIsNamedWithItsHeadingInViewUnderTheTopBar() {
        // The regression (#277), twice over. Linearly, a drag moved the article tens of pixels per
        // pixel of thumb, so the frame in which a heading crossed the readable top was skipped and
        // its section was first named with the heading already behind the bar. Each heading now has
        // a dwell, and the first frame a section is named, its heading is sitting right at the top.
        runScene { scene ->
            val firstNamed = mutableMapOf<Int, Step>()
            for (step in dragDownTheTrack(scene)) {
                val named = step.named ?: continue
                firstNamed.getOrPut(named) { step }
            }

            assertEquals((0 until sectionCount).toList(), firstNamed.keys.sorted(), "every section is named on the way down")
            val late = firstNamed.filter { (section, step) ->
                kotlin.math.abs(step.headingTops[section] - step.readableTop) > 1.5f
            }
            assertTrue(
                late.isEmpty(),
                late.entries.joinToString("\n") { (section, step) ->
                    "section $section first named ${(step.headingTops[section] - step.readableTop).toInt()}px " +
                        "from the readable top"
                }
            )
        }
    }
}
