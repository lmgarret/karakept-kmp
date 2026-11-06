package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The scroll cursor's table of contents, and the identity the renderer has to agree on.
 *
 * A section is identified by where its heading starts in the document's text stream, because that
 * is the one number both ends can produce: [extractReaderToc] reads it off the parsed document,
 * and the heading registers its laid-out coordinates under the same number as it is drawn. If the
 * two ever disagreed, every section would sit at the wrong scroll position.
 */
class ReaderTocTest {

    private fun tocOf(html: String): List<ReaderTocEntry> {
        val body = Ksoup.parse(html).body()
        return extractReaderToc(body, buildReaderTextOffsets(body))
    }

    @Test
    fun headingsAreCollectedInDocumentOrderWithTheirLevels() {
        val toc = tocOf(
            "<h1>Title</h1><p>Lede.</p><h2>First</h2><p>Body.</p><h3>Nested</h3><h2>Second</h2>"
        )

        assertEquals(
            listOf("Title" to 1, "First" to 2, "Nested" to 3, "Second" to 2),
            toc.map { it.title to it.level }
        )
    }

    @Test
    fun headingIdIsWhereItsTextStartsInTheStream() {
        val html = "<p>Lede.</p><h2>Section</h2><p>Body.</p>"
        val body = Ksoup.parse(html).body()
        val offsets = buildReaderTextOffsets(body)

        val toc = extractReaderToc(body, offsets)

        val heading = assertNotNull(body.select("h2").first())
        assertEquals(offsets.startOf(heading), toc.single().id)
    }

    @Test
    fun headingsNestedInsideContainersAreFound() {
        // Readability wraps a whole article in one <div>; unwrapPlainContainers flattens that for
        // rendering, and the table of contents has to see through it too.
        val toc = tocOf("<div><section><h2>Buried</h2></section></div>")

        assertEquals(listOf("Buried"), toc.map { it.title })
    }

    @Test
    fun aHeadingSplitAcrossInlineElementsReadsAsOneLine() {
        val toc = tocOf("<h2>\n  <a href=\"#x\">Part one</a>\n  <span>part two</span>\n</h2>")

        assertEquals("Part one part two", toc.single().title)
    }

    @Test
    fun headingsWithNoTextAreNotSections() {
        // RenderHeading draws nothing for these, so nothing could register a position for them.
        val toc = tocOf("<h2><img src=\"x.png\"></h2><h2>Real</h2>")

        assertEquals(listOf("Real"), toc.map { it.title })
    }

    @Test
    fun anArticleWithNoHeadingsHasNoTableOfContents() {
        assertTrue(tocOf("<p>One.</p><p>Two.</p>").isEmpty())
    }

    @Test
    fun everySectionHasADistinctId() {
        val toc = tocOf("<h2>Same</h2><p>a</p><h2>Same</h2><p>b</p><h2>Same</h2>")

        assertEquals(3, toc.size)
        assertEquals(3, toc.map { it.id }.toSet().size)
    }

    @Test
    fun preparingADocumentWalksTheTableOfContentsWithIt() {
        // The walk belongs to prepareReaderDocument, off the main thread, with the offsets and
        // spans it shares — not to a remember in the renderer.
        val document = assertNotNull(
            prepareReaderDocument("<body><div><h1>Intro</h1><p>Text.</p><h2>Detail</h2></div></body>")
        )

        assertEquals(listOf("Intro", "Detail"), document.toc.map { it.title })
        val heading = assertNotNull(document.body.select("h2").first())
        assertEquals(document.offsets.startOf(heading), document.toc[1].id)
    }
}
