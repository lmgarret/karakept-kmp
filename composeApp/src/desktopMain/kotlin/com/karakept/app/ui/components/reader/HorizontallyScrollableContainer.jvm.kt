package com.karakept.app.ui.components.reader

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun HorizontallyScrollableContainer(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            content()
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
