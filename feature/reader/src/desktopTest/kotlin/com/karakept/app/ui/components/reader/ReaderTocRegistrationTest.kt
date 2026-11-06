package com.karakept.app.ui.components.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where the headings of a real rendered article end up, and what scrolling one costs.
 *
 * The projection arithmetic is pinned in `ReaderScrollCursorTest`; this is the other half — that
 * the headings of an article actually drawn by [NativeHtmlRenderer] register themselves under the
 * ids [extractReaderToc] gave them, and that they do it the way [ReaderSnapRegistry]'s blocks do:
 * live coordinates read on demand, so a scroll frame writes nothing per heading. Pushing a
 * position from `onGloballyPositioned` instead recomposes the whole article every frame — the cost
 * `ReaderScrollCostTest` was written for.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderTocRegistrationTest {

    private val sectionCount = 20

    // One wrapper <div>, as Readability produces: the table of contents has to see through it.
    private val html = buildString {
        append("<body><div>")
        repeat(sectionCount) { index ->
            append("<h2>Section $index</h2>")
            append("<p>Body text of section $index.</p>")
        }
        append("</div></body>")
    }

    private fun runArticle(
        toc: ReaderTocState?,
        test: androidx.compose.ui.test.ComposeUiTest.(PreparedReaderDocument, ScrollState, CoroutineScope) -> Unit
    ) = runComposeUiTest {
        val document = assertNotNull(prepareReaderDocument(html))
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope
        setContent {
            scrollState = rememberScrollState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalReaderToc provides toc) {
                Column(Modifier.height(400.dp).verticalScroll(scrollState)) {
                    NativeHtmlRenderer(document = document)
                }
            }
        }
        waitForIdle()
        test(document, scrollState, scope)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollAndSettle(
        state: ScrollState, scope: CoroutineScope, px: Float
    ) {
        runOnIdle { scope.launch { state.scrollBy(px) } }
        waitForIdle()
    }

    @Test
    fun everySectionOfTheArticleRegistersItself() {
        val toc = ReaderTocState()
        runArticle(toc) { document, _, _ ->
            assertEquals(sectionCount, document.toc.size)
            runOnIdle {
                val missing = document.toc.filter { toc.positionOf(it.id) == null }
                assertTrue(missing.isEmpty(), "No position registered for ${missing.map { it.title }}")
            }
        }
    }

    @Test
    fun theRendererPublishesTheDocumentsTableOfContents() {
        val toc = ReaderTocState()
        runArticle(toc) { document, _, _ ->
            runOnIdle { assertEquals(document.toc, toc.entries) }
        }
    }

    @Test
    fun sectionsAreRegisteredInDocumentOrderDownThePage() {
        val toc = ReaderTocState()
        runArticle(toc) { document, _, _ ->
            runOnIdle {
                val tops = document.toc.map { assertNotNull(toc.positionOf(it.id)) }
                assertEquals(tops.sorted(), tops, "Sections should run down the page in order")
            }
        }
    }

    @Test
    fun registeredPositionsFollowScrollWithoutReRegistering() {
        val toc = ReaderTocState()
        runArticle(toc) { document, state, scope ->
            val id = document.toc[1].id
            val before = assertNotNull(runOnIdle { toc.positionOf(id) })
            scrollAndSettle(state, scope, 7f)
            // Moved up by exactly the scroll, with nothing reported again in between.
            assertEquals(before - 7f, assertNotNull(runOnIdle { toc.positionOf(id) }))
        }
    }

    @Test
    fun scrollingWritesNoPerSectionState() {
        val toc = ReaderTocState()
        runArticle(toc) { _, state, scope ->
            val changed = mutableSetOf<Any>()
            val handle = Snapshot.registerApplyObserver { objects, _ -> changed.addAll(objects) }
            try {
                repeat(5) { scrollAndSettle(state, scope, 30f) }
            } finally {
                handle.dispose()
            }
            // The scroll state's own fields change; one object per section must not.
            assertTrue(
                changed.size < sectionCount / 2,
                "Scrolling changed ${changed.size} state objects across $sectionCount sections"
            )
        }
    }

    @Test
    fun nothingIsRegisteredWhenTheCursorIsOff() {
        // The composition local is null then, which is what makes the whole mechanism free.
        val toc = ReaderTocState()
        runArticle(null) { document, _, _ ->
            runOnIdle {
                assertTrue(document.toc.isNotEmpty())
                assertTrue(document.toc.all { toc.positionOf(it.id) == null })
                assertTrue(toc.entries.isEmpty())
            }
        }
    }
}
