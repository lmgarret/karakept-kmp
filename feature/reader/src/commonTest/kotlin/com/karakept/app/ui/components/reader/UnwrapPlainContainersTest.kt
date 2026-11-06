package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UnwrapPlainContainersTest {

    /** Each top-level unit: an element's tag, or `inline:<text>` for a run of inline content. */
    private fun blockTags(html: String): List<String> =
        assertNotNull(prepareReaderDocument(html)).renderableChildren.map { span ->
            val inline = span.inlineNodes
            if (inline != null) "inline:" + inline.joinToString("") { node ->
                if (node is Element) node.wholeText() else (node as TextNode).getWholeText()
            }
            else (span.node as Element).tagName()
        }

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
    fun containerWithInlineContent_isUnwrappedIntoTheParagraphsRenderChildrenDraws() {
        assertEquals(listOf("inline:Loose text", "p"), blockTags("<div>Loose text<p>a</p></div>"))
        assertEquals(
            listOf("p", "inline:see note 1", "p"),
            blockTags("<div><p>a</p><span>see </span><a href=\"#n\">note 1</a><p>b</p></div>")
        )
    }

    @Test
    fun unwrappedUnits_matchWhatTheContainerItselfDraws() {
        val html = "<div><h2>T</h2>Loose <b>bold</b> text<div><p>a</p>tail</div><p>b</p></div>"
        val prepared = assertNotNull(prepareReaderDocument(html))
        val wrapper = prepared.body.child(0)

        // Flatten what RenderChildren would draw for the wrapper, unwrapping nested containers.
        fun drawn(element: Element): List<List<Node>> = groupRenderChildren(element).flatMap { unit ->
            when (unit) {
                is ChildRenderUnit.Block ->
                    if (unit.element.tagName() == "div") drawn(unit.element) else listOf(listOf(unit.element))
                is ChildRenderUnit.Inline -> listOf(unit.nodes)
            }
        }

        assertEquals(
            drawn(wrapper),
            prepared.renderableChildren.map { it.inlineNodes ?: listOf(it.node) }
        )
    }

    @Test
    fun containerWithOnlyInlineContent_isKeptAsOneBlock() {
        // RenderDiv draws that as a single text block, which the top level does already.
        assertEquals(listOf("div"), blockTags("<div>just <b>text</b></div>"))
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
