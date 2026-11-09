package com.karakept.app.ui.components.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw

/**
 * Draws only the article's top-level blocks near the viewport. Every block stays composed and
 * laid out — page snapping, scroll restoration, the end-of-article marker and the table of contents
 * all read laid-out blocks — but a block off screen records nothing, so showing a long article
 * records about a screenful of text rather than all of it.
 *
 * Each block has its own layer and only the blocks whose culling flips are invalidated, so a scroll
 * re-records the block coming into view and nothing else. The window is pushed from a single
 * `onGloballyPositioned` on the article, not one per block; a block's position is read from its
 * coordinates only when the window moves, by binary search, since blocks stack in index order.
 */
internal class ReaderDrawCuller(blockCount: Int) {
    private val blocks = arrayOfNulls<CulledBlockNode>(blockCount)

    // Inclusive range of drawn blocks. Without a window, every block draws.
    private var hasWindow = false
    private var first = 0
    private var last = -1

    fun isDrawn(index: Int): Boolean = !hasWindow || index in first..last

    internal fun drawnIndices(): IntRange = if (hasWindow) first..last else blocks.indices

    internal fun attach(index: Int, node: CulledBlockNode) {
        if (index in blocks.indices) blocks[index] = node
    }

    internal fun detach(index: Int, node: CulledBlockNode) {
        if (index in blocks.indices && blocks[index] === node) blocks[index] = null
    }

    /** [article] is any layout holding the blocks; what of it is visible bounds the window. */
    fun onArticlePositioned(article: LayoutCoordinates) {
        if (!article.isAttached) return
        val visible = article.boundsInRoot()
        val margin = article.findRootCoordinates().size.height / 2f
        update(visible.top - margin, visible.bottom + margin)
    }

    internal fun update(windowTop: Float, windowBottom: Float) {
        setRange(drawnRange(windowTop, windowBottom))
    }

    private fun drawnRange(windowTop: Float, windowBottom: Float): IntRange? {
        if (blocks.isEmpty()) return IntRange.EMPTY
        // While the article is still being built not every block exists; nothing draws then anyway.
        if (blocks.any { it?.coordinates?.isAttached != true }) return null
        // First block whose bottom is below the window's top.
        var lo = 0
        var hi = blocks.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (bottomOf(mid) <= windowTop) lo = mid + 1 else hi = mid
        }
        val start = lo
        // First block whose top is at or below the window's bottom.
        hi = blocks.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (topOf(mid) < windowBottom) lo = mid + 1 else hi = mid
        }
        return start until lo
    }

    private fun topOf(index: Int): Float = blocks[index]!!.coordinates!!.positionInRoot().y

    private fun bottomOf(index: Int): Float {
        val coords = blocks[index]!!.coordinates!!
        return coords.positionInRoot().y + coords.size.height
    }

    private fun setRange(range: IntRange?) {
        val wasDrawn = drawnIndices()
        hasWindow = range != null
        if (range != null) {
            first = range.first
            last = range.last
        }
        val nowDrawn = drawnIndices()
        if (wasDrawn == nowDrawn) return
        val from = minOf(wasDrawn.first, nowDrawn.first).coerceAtLeast(0)
        val to = maxOf(wasDrawn.last, nowDrawn.last).coerceAtMost(blocks.lastIndex)
        for (i in from..to) {
            if ((i in wasDrawn) != (i in nowDrawn)) blocks[i]?.invalidateDraw()
        }
    }
}

/**
 * The block at [index] of the article, drawn only while [culler] says so. Goes inside the block's
 * own layer: Android re-records a changed layer whether or not its parent draws it.
 */
internal fun Modifier.culledBy(culler: ReaderDrawCuller, index: Int): Modifier =
    this then CulledBlockElement(culler, index)

private data class CulledBlockElement(
    val culler: ReaderDrawCuller,
    val index: Int
) : ModifierNodeElement<CulledBlockNode>() {
    override fun create() = CulledBlockNode(culler, index)

    override fun update(node: CulledBlockNode) {
        if (node.culler === culler && node.index == index) return
        node.culler.detach(node.index, node)
        node.culler = culler
        node.index = index
        culler.attach(index, node)
        node.invalidateDraw()
    }
}

internal class CulledBlockNode(
    var culler: ReaderDrawCuller,
    var index: Int
) : Modifier.Node(), DrawModifierNode, LayoutAwareModifierNode {
    var coordinates: LayoutCoordinates? = null
        private set

    override fun onAttach() = culler.attach(index, this)

    override fun onDetach() {
        culler.detach(index, this)
        coordinates = null
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun ContentDrawScope.draw() {
        if (culler.isDrawn(index)) drawContent()
    }
}
