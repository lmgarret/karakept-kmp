package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreparedReaderDocumentTest {

    private val html = """
        <body>
            <p>First paragraph.</p>
            <img src="https://x/1.jpg">
            <div><p>Second <b>bold</b> paragraph.</p><img src="https://x/2.jpg"></div>
            <ul><li>one</li><li>two</li></ul>
        </body>
    """.trimIndent()

    @Test
    fun prepare_matchesTheWalksRunOnAFreshParse() {
        val prepared = assertNotNull(prepareReaderDocument(html))

        val reference = Ksoup.parse(html)
        val referenceOffsets = buildReaderTextOffsets(reference.body())
        val referenceSpans = unwrapPlainContainers(reference.body().childNodes(), referenceOffsets)
            .filter { it.isRenderable }

        assertEquals(referenceOffsets.text, prepared.offsets.text)
        assertEquals(referenceSpans.map { it.startOffset }, prepared.renderableChildren.map { it.startOffset })
        assertEquals(
            collectGalleryImages(reference).map { it.urls },
            prepared.galleryImages.map { it.urls }
        )
    }

    @Test
    fun prepare_spansAndImagesPointIntoThePreparedTree() {
        val prepared = assertNotNull(prepareReaderDocument(html))

        assertTrue(prepared.renderableChildren.all { it.node.ownerDocument() === prepared.document })
        val firstImg = prepared.document.select("img").first()
        assertSame(firstImg, prepared.galleryImages.first().element)
    }

    @Test
    fun prepare_keepsTheHtmlItWasPreparedFrom() {
        assertEquals(html, prepareReaderDocument(html)?.html)
    }

    @Test
    fun prepare_blankHtml_yieldsAnEmptyDocument() {
        val prepared = assertNotNull(prepareReaderDocument(" "))
        assertTrue(prepared.renderableChildren.isEmpty())
        assertTrue(prepared.galleryImages.isEmpty())
    }
}
