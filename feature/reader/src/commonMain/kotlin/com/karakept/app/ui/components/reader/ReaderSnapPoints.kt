package com.karakept.app.ui.components.reader

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the reader is allowed to end a page: the top edge of every laid-out line of text, in root
 * coordinates.
 *
 * The reader renders a whole article as a *single* lazy item, so `LazyListState.layoutInfo` knows
 * nothing finer than "the body" and cannot say where a page turn would slice a line in half. The
 * blocks are all eagerly composed in a plain `Column` though, so every one of them is positioned —
 * including those scrolled past and those below the fold — and can report itself here.
 *
 * Deliberately *not* snapshot state: nothing observes the registry reactively; it is read once per
 * page turn. Blocks register their coordinates and line layout, not a position, so scrolling writes
 * nothing here at all.
 *
 * Non-text blocks — images, tables, rules — register nothing on purpose. A fold inside one finds
 * only the last line of the paragraph above it, which the caller's snap limit rejects, so the turn
 * is left alone. An image has no line to keep whole, and rewinding to clear it would cost a
 * screenful of content.
 */
class ReaderSnapRegistry {
    private class Entry(val topInRoot: () -> Float, val lineTops: FloatArray, val lineBottoms: FloatArray)

    private val blocks = mutableMapOf<Any, Entry>()

    /**
     * Records [key]'s line boundaries. [lineTops] and [lineBottoms] are relative to the block;
     * [topInRoot] is where the block itself sits, so the two add up to root coordinates.
     */
    fun report(key: Any, topInRoot: Float, lineTops: FloatArray, lineBottoms: FloatArray) {
        report(key, { topInRoot }, lineTops, lineBottoms)
    }

    /**
     * Like the other overload, but the block's position is asked for only when a page turn
     * queries the registry. A position pushed from `onGloballyPositioned` costs one callback per
     * block per scroll frame, which on a long article is most of the frame; read lazily it is
     * paid once per turn. [topInRoot] returns NaN for a block that is no longer attached, which
     * every query skips.
     */
    fun report(key: Any, topInRoot: () -> Float, lineTops: FloatArray, lineBottoms: FloatArray) {
        blocks[key] = Entry(topInRoot, lineTops, lineBottoms)
    }

    fun forget(key: Any) {
        blocks.remove(key)
    }

    private var contentEnd: (() -> Float)? = null

    /**
     * Where the last rendered block stops, in root coordinates — the end of the *article*, which is
     * not the end of the lazy item holding it: the renderer adds its own bottom margin below this.
     *
     * Null until the renderer reports it, and a caller with no answer should assume there is more
     * below rather than less, so a reader without a registry keeps behaving as it always did.
     */
    val contentEndInRoot: Float?
        get() = contentEnd?.invoke()?.takeUnless { it.isNaN() }

    fun reportContentEnd(yInRoot: Float) {
        reportContentEnd { yInRoot }
    }

    /** Lazily read, for the same reason as the lazy [report]. */
    fun reportContentEnd(yInRoot: () -> Float) {
        contentEnd = yInRoot
    }

    /** Whether any registered line is still, wholly or partly, below [yInRoot]. */
    fun hasLineBelow(yInRoot: Float): Boolean = blocks.values.any { entry ->
        val top = entry.topInRoot()
        entry.lineBottoms.any { bottom -> top + bottom > yInRoot }
    }

    /**
     * The top of the line [yInRoot] falls inside, or null when it falls between two blocks or
     * outside the text entirely.
     *
     * Null is a meaningful answer, not a failure: an edge landing in the whitespace between
     * paragraphs, or inside an image or a table, has no line to keep whole, so the caller leaves
     * that page turn exactly where it landed. Knowing the difference is why line *bottoms* are
     * stored — within one block the lines are contiguous, so tops alone cannot tell a fold inside
     * the last line from one in the padding beneath it.
     */
    fun lineTopAt(yInRoot: Float): Float? = lineAt(yInRoot)?.top

    /** The line [yInRoot] falls inside, top and bottom, with the same null as [lineTopAt]. */
    fun lineAt(yInRoot: Float): LineSpan? {
        for (entry in blocks.values) {
            val blockTop = entry.topInRoot()
            if (blockTop.isNaN()) continue
            for (line in entry.lineTops.indices) {
                val top = blockTop + entry.lineTops[line]
                val bottom = blockTop + entry.lineBottoms[line]
                if (yInRoot >= top && yInRoot < bottom) return LineSpan(top, bottom)
            }
        }
        return null
    }
}

class LineSpan(val top: Float, val bottom: Float)

/**
 * Null when page snapping is off, which is also what makes the reporting free: text blocks skip the
 * whole registration when there is no registry to report to.
 */
val LocalReaderSnapRegistry = staticCompositionLocalOf<ReaderSnapRegistry?> { null }
