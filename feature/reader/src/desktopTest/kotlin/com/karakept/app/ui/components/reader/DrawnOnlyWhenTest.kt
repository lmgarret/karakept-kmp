package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The article is built over many frames behind the hidden body. Hidden must mean not drawn at
 * all: drawing it transparently re-recorded the whole article on every one of those frames.
 */
@OptIn(ExperimentalTestApi::class)
class DrawnOnlyWhenTest {

    @Test
    fun hiddenContent_isLaidOutButNeverDrawn_untilShown() = runComposeUiTest {
        var visible by mutableStateOf(false)
        var draws = 0
        var placed = false
        setContent {
            Box(Modifier.drawnOnlyWhen(visible)) {
                Box(
                    Modifier.size(50.dp)
                        .onPlaced { placed = true }
                        .drawBehind { draws++ }
                )
            }
        }
        waitForIdle()
        assertTrue(placed, "hidden content must still be laid out")
        assertEquals(0, draws, "hidden content must not be drawn")

        visible = true
        waitForIdle()
        assertTrue(draws > 0, "content is drawn once shown")
    }

    @Test
    fun recomposingWithTheSameVisibility_doesNotRedraw() = runComposeUiTest {
        var tick by mutableStateOf(0)
        val draws = DrawCounter()
        setContent {
            // Read here so this scope recomposes, rebuilding the modifier with the same value.
            Box(Modifier.drawnOnlyWhen(visible = tick >= 0)) {
                CountedContent(draws)
            }
        }
        waitForIdle()
        val afterFirstDraw = draws.count
        assertTrue(afterFirstDraw > 0)

        repeat(3) {
            tick++
            waitForIdle()
        }
        assertEquals(afterFirstDraw, draws.count, "an unchanged visibility must not redraw the content")
    }
}

private class DrawCounter {
    var count = 0
}

// Its own composable, so it skips when the parent recomposes and only the modifier is in question.
@Composable
private fun CountedContent(counter: DrawCounter) {
    Box(Modifier.size(50.dp).drawBehind { counter.count++ })
}
