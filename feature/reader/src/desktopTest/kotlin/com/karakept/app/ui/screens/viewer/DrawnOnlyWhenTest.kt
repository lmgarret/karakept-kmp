package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
}
