package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
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
        renderableChildren = unwrapPlainContainers(body.childNodes(), offsets).filter { it.isRenderable },
        galleryImages = collectGalleryImages(document)
    )
}

private val UNWRAPPABLE_TAGS = setOf("div", "section", "article", "main")

/**
 * The article's top-level spans, with every plain container replaced by what it draws, recursively.
 *
 * Readability wraps a whole article in one `<div>`, which made it a single top-level block: the
 * progressive reveal had nothing to split, and an 88-minute article was composed and measured in
 * one frame. A container holding blocks is drawn by `RenderDiv` as a bare `Column` of
 * [groupRenderChildren]'s units — each block, and each run of inline content as one paragraph —
 * so emitting those same units here draws exactly what the container did.
 */
internal fun unwrapPlainContainers(nodes: List<Node>, offsets: ReaderTextOffsets): List<ReaderTextSpan> {
    val out = ArrayList<ReaderTextSpan>(nodes.size)
    for (node in nodes) {
        if (node is Element && isUnwrappable(node)) {
            unwrapInto(node, offsets, out)
        } else {
            out += computeReaderTextSpans(listOf(node), offsets)
        }
    }
    return out
}

private fun unwrapInto(container: Element, offsets: ReaderTextOffsets, out: MutableList<ReaderTextSpan>) {
    for (unit in groupRenderChildren(container)) {
        when (unit) {
            is ChildRenderUnit.Block ->
                if (isUnwrappable(unit.element)) unwrapInto(unit.element, offsets, out)
                else out += ReaderTextSpan(unit.element, offsets.startOf(unit.element), isRenderable = true)
            is ChildRenderUnit.Inline -> {
                val first = unit.nodes.first()
                out += ReaderTextSpan(first, offsets.startOf(first), isRenderable = true, inlineNodes = unit.nodes)
            }
        }
    }
}

private fun isUnwrappable(element: Element): Boolean =
    element.tagName().lowercase() in UNWRAPPABLE_TAGS && hasBlockChildren(element)

/** One line describing the article's top-level shape, for the `ReaderPerf` log. */
internal fun describeTopLevel(document: PreparedReaderDocument): String {
    val units = document.renderableChildren
    val inline = units.count { it.inlineNodes != null }
    val head = units.take(3).joinToString { span ->
        val node = span.node
        when {
            span.inlineNodes != null -> "inline×${span.inlineNodes.size}"
            node is Element -> "<${node.tagName()}> ${node.childNodeSize()} children, " +
                "${node.children().count { isBlockElement(it) }} blocks"
            else -> "text"
        }
    }
    return "${units.size} units ($inline inline runs); first: $head"
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
