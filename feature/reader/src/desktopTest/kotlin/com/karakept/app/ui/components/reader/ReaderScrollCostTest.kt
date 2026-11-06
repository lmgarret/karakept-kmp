package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Scrolling a long article must cost nothing per text block. Each block used to write its root
 * position into snapshot state and push it into the snap registry on every scroll frame, which
 * recomposed the whole article each frame — on a 20-minute read, most of the frame budget.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderScrollCostTest {

    private val blockCount = 40

    private fun runScrolling(
        registry: ReaderSnapRegistry?,
        test: androidx.compose.ui.test.ComposeUiTest.(ScrollState, CoroutineScope) -> Unit
    ) = runComposeUiTest {
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope
        setContent {
            scrollState = rememberScrollState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalReaderSnapRegistry provides registry) {
                Column(Modifier.height(400.dp).verticalScroll(scrollState)) {
                    repeat(blockCount) { index ->
                        AnnotatedClickableText(
                            text = AnnotatedString("Paragraph $index of a very long article."),
                            onLinkClick = {},
                            onHighlightClick = {}
                        )
                    }
                }
            }
        }
        waitForIdle()
        test(scrollState, scope)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollAndSettle(
        state: ScrollState, scope: CoroutineScope, px: Float
    ) {
        runOnIdle { scope.launch { state.scrollBy(px) } }
        waitForIdle()
    }

    @Test
    fun scrollingWritesNoPerBlockState() = runScrolling(ReaderSnapRegistry()) { state, scope ->
        val changed = mutableSetOf<Any>()
        val handle = Snapshot.registerApplyObserver { objects, _ -> changed.addAll(objects) }
        try {
            repeat(5) { scrollAndSettle(state, scope, 30f) }
        } finally {
            handle.dispose()
        }
        // The scroll state's own fields change; one object per block must not.
        assertTrue(
            changed.size < blockCount / 4,
            "Scrolling changed ${changed.size} state objects across $blockCount blocks"
        )
    }

    @Test
    fun registryFollowsScrollWithoutReReports() {
        val registry = ReaderSnapRegistry()
        runScrolling(registry) { state, scope ->
            val before = assertNotNull(runOnIdle { registry.lineTopAt(10f) })
            scrollAndSettle(state, scope, 7f)
            // The line that sat at `before` has moved up by exactly the scroll, with nothing
            // reported again in between.
            val moved = assertNotNull(runOnIdle { registry.lineAt(before - 7f + 0.5f) })
            assertEquals(before - 7f, moved.top)
        }
    }
}
