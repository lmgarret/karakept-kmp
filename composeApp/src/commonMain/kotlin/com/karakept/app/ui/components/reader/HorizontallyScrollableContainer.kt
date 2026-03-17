package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A container that supports horizontal scrolling.
 * On desktop, a visible scrollbar is shown below the content.
 * On Android, standard touch-based horizontal scrolling is used.
 */
@Composable
expect fun HorizontallyScrollableContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
