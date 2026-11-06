package com.karakept.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val ICON_SIZE_DP = 24
private const val ICON_VIEWPORT = 24f

/**
 * Builds one of the vendored Material icons from its 24dp path data.
 *
 * Each source path stays a separate `addPath` call: merging them would let a subpath drawn
 * inside another one's hole cancel out under the non-zero winding rule.
 *
 * Returns a [Lazy] so the generated `val`s only pay for the icons a screen actually draws.
 */
internal fun materialIcon(
    name: String,
    vararg pathData: String,
    autoMirrored: Boolean = false,
): Lazy<ImageVector> = lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE_DP.dp,
        defaultHeight = ICON_SIZE_DP.dp,
        viewportWidth = ICON_VIEWPORT,
        viewportHeight = ICON_VIEWPORT,
        autoMirror = autoMirrored,
    ).apply {
        pathData.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()
}
