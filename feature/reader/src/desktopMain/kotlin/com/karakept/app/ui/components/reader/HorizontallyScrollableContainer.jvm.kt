package com.karakept.app.ui.components.reader

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun HorizontallyScrollableContainer(
    modifier: Modifier,
    scrollbarColor: Color,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            content()
        }
        val style = if (scrollbarColor != Color.Unspecified) {
            LocalScrollbarStyle.current.copy(
                unhoverColor = scrollbarColor.copy(alpha = 0.4f),
                hoverColor = scrollbarColor.copy(alpha = 0.7f)
            )
        } else {
            LocalScrollbarStyle.current
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.fillMaxWidth(),
            style = style
        )
    }
}
