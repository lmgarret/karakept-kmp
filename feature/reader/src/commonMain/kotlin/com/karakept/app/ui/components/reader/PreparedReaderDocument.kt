package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Everything the reader derives from an article's HTML before it can draw a line of it: the
 * parsed tree, the text-offset stream, the top-level blocks it renders and the page's gallery.
 *
 * Each of these is a walk over the whole document. On a long article they add up to the better
 * part of a second, so they are built together off the main thread by [prepareReaderDocument]
 * and handed to [NativeHtmlRenderer] finished, rather than `remember`ed one by one in composition.
 * Treat the tree as read-only once prepared: the offsets and spans point into it.
 */
class PreparedReaderDocument internal constructor(
    val html: String,
    val document: Document,
    val offsets: ReaderTextOffsets,
    val renderableChildren: List<ReaderTextSpan>,
    internal val galleryImages: List<GalleryImage>
) {
    val body: Element get() = document.body()
}

/** Parses [html] and runs every full-document walk the reader needs. Safe to call off the main thread. */
fun prepareReaderDocument(html: String): PreparedReaderDocument? {
    val document = try {
        Ksoup.parse(html)
    } catch (e: Exception) {
        return null
    }
    val body = document.body()
    val offsets = buildReaderTextOffsets(body)
    return PreparedReaderDocument(
        html = html,
        document = document,
        offsets = offsets,
        renderableChildren = computeReaderTextSpans(unwrapPlainContainers(body.childNodes()), offsets)
            .filter { it.isRenderable },
        galleryImages = collectGalleryImages(document)
    )
}

private val UNWRAPPABLE_TAGS = setOf("div", "section", "article", "main")

/**
 * [nodes] with every plain container replaced by its children, recursively.
 *
 * Readability wraps a whole article in one `<div>`, which made it a single top-level block: the
 * progressive reveal had nothing to split, and an 88-minute article was composed and measured in
 * one frame. A container is plain when it holds only blocks and blank text — then
 * `RenderDiv` draws it as a bare `Column` of those blocks, exactly what the reader's own `Column`
 * draws once it is unwrapped. One holding inline content is kept: `RenderChildren` groups that
 * content into a paragraph, which the top level does not.
 */
internal fun unwrapPlainContainers(nodes: List<Node>): List<Node> {
    val out = ArrayList<Node>(nodes.size)
    for (node in nodes) {
        if (node is Element && isPlainContainer(node)) {
            out += unwrapPlainContainers(node.childNodes())
        } else {
            out += node
        }
    }
    return out
}

private fun isPlainContainer(element: Element): Boolean =
    element.tagName().lowercase() in UNWRAPPABLE_TAGS &&
        element.childNodes().all { child ->
            when (child) {
                is Element -> isBlockElement(child)
                is TextNode -> child.getWholeText().isBlank()
                else -> true
            }
        }

/** How long one frame may spend composing and measuring newly revealed blocks. */
internal val REVEAL_FRAME_BUDGET = 8.milliseconds

/**
 * The next reveal batch, sized so its cost fits [budget] at the per-block cost the last batch
 * ([lastBatch] blocks, costing [lastCost]) measured. Grows at most fourfold a batch, so one cheap
 * batch of short paragraphs cannot commit the next frame to a run of images or tables.
 */
internal fun nextRevealBatchSize(
    lastBatch: Int,
    lastCost: Duration,
    budget: Duration = REVEAL_FRAME_BUDGET,
    minBatch: Int = 10,
    maxBatch: Int = 500
): Int {
    val blocks = lastBatch.coerceAtLeast(1)
    val perBlockNanos = (lastCost.inWholeNanoseconds / blocks).coerceAtLeast(1)
    val fitting = (budget.inWholeNanoseconds / perBlockNanos).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return fitting.coerceAtMost(blocks * 4).coerceIn(minBatch, maxBatch)
}
