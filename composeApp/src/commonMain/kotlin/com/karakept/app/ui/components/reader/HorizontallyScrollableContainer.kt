package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A container that supports horizontal scrolling.
 * On desktop, a visible scrollbar is shown below the content.
 * On Android, standard touch-based horizontal scrolling is used.
 *
 * @param scrollbarColor Color for the scrollbar thumb (desktop only).
 */
@Composable
expect fun HorizontallyScrollableContainer(
    modifier: Modifier = Modifier,
    scrollbarColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
)
