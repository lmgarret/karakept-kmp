package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.theme.LocalEinkMode

/**
 * Desktop replacement for [SwipeableBookmarkItem].
 * Overlays inline icon buttons on top of the bookmark card
 * on the leading or trailing edge, visible only when the card is hovered.
 * Matches the padding of [SwipeableBookmarkItem] so items don't stick together.
 */
@Composable
fun QuickActionBookmarkItem(
    leftAction: SwipeAction = SwipeAction.NONE,
    rightAction: SwipeAction = SwipeAction.NONE,
    leftIcon: ImageVector? = null,
    rightIcon: ImageVector? = null,
    leftIsApplied: Boolean = false,
    rightIsApplied: Boolean = false,
    position: QuickActionPosition = QuickActionPosition.RIGHT,
    onActionTriggered: (SwipeAction, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Full-bleed rows with no gutter, for [com.karakept.app.data.model.ItemContainerStyle.FLAT].
     * The row supplies its own text inset and a divider that must reach both edges.
     */
    flat: Boolean = false,
    /**
     * Show the buttons unconditionally instead of on hover. Required on touch, where a hover
     * event never arrives and the cluster would otherwise never appear.
     */
    alwaysVisible: Boolean = false,
    content: @Composable () -> Unit
) {
    val hasActions = leftAction != SwipeAction.NONE || rightAction != SwipeAction.NONE

    if (!hasActions) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(if (flat) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ) {
            content()
        }
        return
    }

    // Track hover on the entire item to show/hide action buttons
    val itemInteractionSource = remember { MutableInteractionSource() }
    val isItemHovered by itemInteractionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (flat) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            .hoverable(itemInteractionSource)
    ) {
        content()

        // Buttons overlay on top of card, only visible on hover
        AnimatedVisibilityOrPlain(
            visible = alwaysVisible || isItemHovered,
            animated = !LocalEinkMode.current.animationsDisabled,
            modifier = Modifier
                .align(
                    if (position == QuickActionPosition.LEFT) Alignment.CenterStart
                    else Alignment.CenterEnd
                )
                .then(
                    if (position == QuickActionPosition.LEFT) Modifier.padding(start = 8.dp)
                    else Modifier.padding(end = 8.dp)
                )
        ) {
            ActionButtons(
                leftAction = leftAction,
                rightAction = rightAction,
                leftIcon = leftIcon,
                rightIcon = rightIcon,
                leftIsApplied = leftIsApplied,
                rightIsApplied = rightIsApplied,
                onActionTriggered = onActionTriggered
            )
        }
    }
}

@Composable
private fun ActionButtons(
    leftAction: SwipeAction,
    rightAction: SwipeAction,
    leftIcon: ImageVector?,
    rightIcon: ImageVector?,
    leftIsApplied: Boolean,
    rightIsApplied: Boolean,
    onActionTriggered: (SwipeAction, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .then(
                // surfaceContainerHigh is the page colour in high contrast, so the cluster would
                // float over the row with no edge of its own.
                if (LocalEinkMode.current.highContrast) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                } else Modifier
            )
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "Right" swipe action = leading / primary action (shown first)
        if (rightAction != SwipeAction.NONE) {
            QuickActionButton(
                action = rightAction,
                iconOverride = rightIcon,
                isApplied = rightIsApplied,
                onClick = { onActionTriggered(rightAction, true) }
            )
        }
        // "Left" swipe action = trailing / secondary action
        if (leftAction != SwipeAction.NONE) {
            QuickActionButton(
                action = leftAction,
                iconOverride = leftIcon,
                isApplied = leftIsApplied,
                onClick = { onActionTriggered(leftAction, false) }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    action: SwipeAction,
    iconOverride: ImageVector?,
    isApplied: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val actionColor = action.getColor()
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color = when {
        isApplied -> actionColor.copy(alpha = 0.5f)
        isHovered -> actionColor
        else -> neutralColor.copy(alpha = 0.8f)
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp).hoverable(interactionSource),
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = color
        )
    ) {
        Icon(
            imageVector = iconOverride ?: action.getIcon(),
            contentDescription = action.displayName,
            modifier = Modifier.size(20.dp)
        )
    }
}
