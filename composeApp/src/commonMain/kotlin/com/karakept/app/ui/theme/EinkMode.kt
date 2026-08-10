package com.karakept.app.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.InteractionSource

/**
 * Whether the current composition is rendering for an e-ink panel.
 *
 * Read this instead of threading parameters: anything that animates, shimmers, or relies on a
 * tonal fill to be visible needs to behave differently on e-ink, and those sites are spread across
 * the whole tree.
 *
 * Both flags already fold in the master "E-ink mode" switch, so a consumer only checks the one it
 * cares about.
 */
@Immutable
data class EinkMode(
    val animationsDisabled: Boolean = false,
    val highContrast: Boolean = false,
    /** Page turns and scroll-to-top jump instead of animating. */
    val instantScroll: Boolean = false
)

val LocalEinkMode = staticCompositionLocalOf { EinkMode() }

/**
 * Ripple is a multi-frame animation that leaves a visible smear on e-ink, and the tonal overlay it
 * draws is invisible against the monochrome scheme anyway.
 */
object NoIndication : Indication, IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

private class NoIndicationNode : Modifier.Node()
