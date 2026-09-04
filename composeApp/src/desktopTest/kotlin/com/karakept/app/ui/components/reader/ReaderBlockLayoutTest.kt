package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Layout regression tests for [RenderBlock]'s container.
 *
 * Every block is wrapped in one container, and two of the `when` branches emit several
 * sibling composables into it: `<hr>` (spacer/divider/spacer) and the `else` fallback, which
 * calls [RenderChildren] once per child. A `Box` there stacks them all at the same top-left
 * corner instead of laying them out one under the next.
 *
 * The fallback is not an exotic path: [isBlockElement] deliberately treats an `<a>` wrapping
 * block content as a block (the lightbox pattern `<figure><a><picture>…`), and "a" has no
 * branch of its own. While reader images filled the column and painted over whatever was
 * stacked beneath them the overlap was invisible; sizing images to their own width uncovered it.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderBlockLayoutTest {

    private val theme = ReaderThemeData(
        textColor = Color.Black,
        backgroundColor = Color.White,
        fontSize = 16.sp,
        fontFamily = FontFamily.Default,
        linkColor = Color.Blue,
        codeBackgroundColor = Color.LightGray
    )

    private fun blocks(html: String): List<Element> =
        Ksoup.parse(html).body().children().toList()

    private fun offsetsOf(html: String) = buildReaderTextOffsets(Ksoup.parse(html).body())

    private fun runBlocks(html: String, assertions: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalReaderTheme provides theme) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val offsets = offsetsOf(html)
                        for (block in blocks(html)) {
                            RenderBlock(
                                element = block,
                                highlights = emptyList(),
                                offsets = offsets,
                                onLinkClick = {},
                                onHighlightClick = {},
                                onHighlightPosition = { _, _ -> }
                            )
                        }
                    }
                }
            }
            assertions()
        }

    @Test
    fun anchorWrappingBlocks_childrenStackVerticallyNotOnTopOfEachOther() = runBlocks(
        "<body><a href='https://example.com'><p>FIRST</p><p>SECOND</p></a></body>"
    ) {
        val first = onNodeWithText("FIRST").getBoundsInRoot()
        val second = onNodeWithText("SECOND").getBoundsInRoot()
        assertTrue(
            second.top >= first.bottom,
            "SECOND (top=${second.top}) must start below FIRST (bottom=${first.bottom}), not on top of it"
        )
    }

    @Test
    fun blockSequence_laterBlockStartsBelowEarlierOne() = runBlocks(
        "<body><p>ABOVE</p><a href='https://example.com'><p>INSIDE</p></a><p>BELOW</p></body>"
    ) {
        val above = onNodeWithText("ABOVE").getBoundsInRoot()
        val inside = onNodeWithText("INSIDE").getBoundsInRoot()
        val below = onNodeWithText("BELOW").getBoundsInRoot()
        assertTrue(inside.top >= above.bottom, "anchor content must start below the paragraph above it")
        assertTrue(below.top >= inside.bottom, "the paragraph after must start below the anchor content")
    }
}
