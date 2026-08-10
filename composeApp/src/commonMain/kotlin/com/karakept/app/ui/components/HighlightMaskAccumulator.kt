package com.karakept.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Collects the pieces that make up one highlight's mask.
 *
 * A highlight that spans several paragraphs is drawn by several text blocks, and
 * each reports only its own piece, so the mask is the union of all of them. Each
 * block also re-reports whenever it moves, which is why the pieces are keyed by
 * their source: merging blindly kept the piece from before a scroll alongside
 * the one from after it, stretching the mask by the distance scrolled (#295).
 */
class HighlightMaskAccumulator {
    private val fragments = LinkedHashMap<Any, Path>()

    /** Drops every piece. Call when the mask no longer belongs to the same highlight. */
    fun clear() {
        fragments.clear()
    }

    /**
     * Records [position] as the current piece for its source and returns the
     * mask covering every piece collected so far.
     *
     * Positions carrying no path replace the mask outright — there is nothing to
     * union — and reset what came before.
     */
    fun accumulate(position: HighlightPosition, fallbackKey: Any): HighlightPosition {
        val path = position.path ?: run {
            clear()
            return position
        }

        fragments[position.sourceKey ?: fallbackKey] = path

        val merged = Path()
        for (fragment in fragments.values) {
            merged.addPath(fragment)
        }

        val bounds = merged.getBounds()
        return HighlightPosition(
            x = bounds.left,
            y = bounds.top,
            width = bounds.width,
            height = bounds.height,
            scrollX = 0f,
            scrollY = 0f,
            path = merged,
            rootOffset = Offset.Zero
        )
    }
}
