package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.SwipeAction

/**
 * Desktop replacement for [SwipeableBookmarkItem].
 * Shows inline icon buttons for the configured quick actions
 * on the leading or trailing side of the bookmark item.
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
        // No actions configured — just render the content with matching padding
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            content()
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (position == QuickActionPosition.LEFT) {
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

        Row(modifier = Modifier.weight(1f)) {
            content()
        }

        if (position == QuickActionPosition.RIGHT) {
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
    onActionTriggered: (SwipeAction, Boolean) -> Unit
) {
    Column(
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
    val color = action.getColor()
    val alpha = if (isApplied) 0.4f else 1f

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = color.copy(alpha = alpha)
        )
    ) {
        Icon(
            imageVector = iconOverride ?: action.getIcon(),
            contentDescription = action.displayName,
            modifier = Modifier.size(20.dp)
        )
    }
}
