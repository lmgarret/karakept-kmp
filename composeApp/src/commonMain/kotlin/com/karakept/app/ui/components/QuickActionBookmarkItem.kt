package com.karakept.app.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.SwipeAction

/**
 * Desktop replacement for [SwipeableBookmarkItem].
 * Overlays inline icon buttons on top of the bookmark card
 * on the leading or trailing edge.
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
    content: @Composable () -> Unit
) {
    val hasActions = leftAction != SwipeAction.NONE || rightAction != SwipeAction.NONE

    if (!hasActions) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            content()
        }
        return
    }

    // Overlay the action buttons on top of the card content, inside the card boundary.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        content()

        ActionButtons(
            leftAction = leftAction,
            rightAction = rightAction,
            leftIcon = leftIcon,
            rightIcon = rightIcon,
            leftIsApplied = leftIsApplied,
            rightIsApplied = rightIsApplied,
            onActionTriggered = onActionTriggered,
            modifier = Modifier
                .align(
                    if (position == QuickActionPosition.LEFT) Alignment.CenterStart
                    else Alignment.CenterEnd
                )
                .then(
                    if (position == QuickActionPosition.LEFT) Modifier.padding(start = 8.dp)
                    else Modifier.padding(end = 8.dp)
                )
        )
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
        modifier = modifier,
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
    // Neutral color by default, action color on hover or when applied
    val actionColor = action.getColor()
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color = when {
        isApplied -> actionColor.copy(alpha = 0.5f)
        isHovered -> actionColor
        else -> neutralColor.copy(alpha = 0.6f)
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
