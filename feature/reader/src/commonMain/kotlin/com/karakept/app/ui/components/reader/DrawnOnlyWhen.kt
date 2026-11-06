package com.karakept.app.ui.components.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw

/**
 * Lays the content out but draws none of it until [visible]. An `alpha(0f)` hides it too, but
 * still records every block's drawing each frame — and while a long article is built a batch at a
 * time, that re-recorded the whole article on every frame. Skipping the draw records it once, when
 * it is shown.
 *
 * Under a `graphicsLayer` this has to sit *inside* the layer: Android re-records a changed layer
 * whether or not its parent draws it, so skipping the draw above the layer saves nothing.
 */
internal fun Modifier.drawnOnlyWhen(visible: Boolean): Modifier = this then DrawnOnlyWhenElement(visible)

// A node rather than drawWithContent: that takes a fresh lambda on every recomposition, which
// Compose treats as a changed modifier and redraws for — the whole article, each time the section
// recomposed, although nothing about its visibility had changed.
private data class DrawnOnlyWhenElement(val visible: Boolean) : ModifierNodeElement<DrawnOnlyWhenNode>() {
    override fun create() = DrawnOnlyWhenNode(visible)

    override fun update(node: DrawnOnlyWhenNode) {
        if (node.visible != visible) {
            node.visible = visible
            node.invalidateDraw()
        }
    }
}

private class DrawnOnlyWhenNode(var visible: Boolean) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        if (visible) drawContent()
    }
}
