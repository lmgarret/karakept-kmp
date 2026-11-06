package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UnwrapPlainContainersTest {

    private fun blockTags(html: String): List<String> =
        assertNotNull(prepareReaderDocument(html)).renderableChildren.map { (it.node as Element).tagName() }

    @Test
    fun readabilityWrapper_isSplitIntoItsBlocks() {
        assertEquals(
            listOf("h1", "p", "p"),
            blockTags("<div id=\"readability-page-1\"><h1>T</h1>\n<p>a</p>\n<p>b</p></div>")
        )
    }

    @Test
    fun nestedPlainWrappers_areAllUnwrapped() {
        assertEquals(
            listOf("p", "p", "p"),
            blockTags("<article><section><div><p>a</p><p>b</p></div></section><p>c</p></article>")
        )
    }

    @Test
    fun containerWithInlineContent_isKept() {
        // RenderChildren groups the stray text into a paragraph; the top level would drop it.
        assertEquals(listOf("div"), blockTags("<div>Loose text<p>a</p></div>"))
        assertEquals(listOf("div"), blockTags("<div><span>inline</span><p>a</p></div>"))
    }

    @Test
    fun containersThatDrawSomething_areKept() {
        assertEquals(listOf("blockquote"), blockTags("<blockquote><p>a</p><p>b</p></blockquote>"))
        assertEquals(listOf("a"), blockTags("<a href=\"https://x\"><p>a</p><p>b</p></a>"))
    }

    @Test
    fun unwrappedBlocks_keepTheirOffsetsInTheWholeDocumentStream() {
        val html = "<div><p>First paragraph.</p>\n<div><p>Second paragraph.</p></div><p>Third.</p></div>"
        val prepared = assertNotNull(prepareReaderDocument(html))

        val reference = buildReaderTextOffsets(Ksoup.parse(html).body())
        assertEquals(reference.text, prepared.offsets.text)
        for (span in prepared.renderableChildren) {
            assertEquals(prepared.offsets.startOf(span.node), span.startOffset)
        }
        val created = assertNotNull(findTextOffsets(html, "Second paragraph."))
        assertEquals(created.startOffset, prepared.renderableChildren[1].startOffset)
        assertEquals(3, prepared.renderableChildren.size)
    }
}
