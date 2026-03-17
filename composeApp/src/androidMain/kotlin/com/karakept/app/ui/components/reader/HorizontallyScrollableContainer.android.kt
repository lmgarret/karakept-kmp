package com.karakept.app.ui.components.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun HorizontallyScrollableContainer(
    modifier: Modifier,
    scrollbarColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.horizontalScroll(rememberScrollState())) {
        content()
    }
}
