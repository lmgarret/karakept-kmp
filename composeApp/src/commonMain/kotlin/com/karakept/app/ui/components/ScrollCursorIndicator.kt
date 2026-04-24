package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.SortOption
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

@Composable
fun ScrollCursorIndicator(
    listState: LazyListState,
    bookmarks: List<BookmarkEntity>,
    sortOption: SortOption,
    modifier: Modifier = Modifier
) {
    val isScrollInProgress = listState.isScrollInProgress
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }

    val firstVisibleIndex = listState.firstVisibleItemIndex
    val bookmark = bookmarks.getOrNull(firstVisibleIndex)

    val label = when (sortOption) {
        SortOption.NEWEST, SortOption.OLDEST ->
            bookmark?.createdAt?.let { formatScrollCursorDate(it) } ?: ""
        SortOption.TITLE_AZ, SortOption.TITLE_ZA ->
            bookmark?.title?.firstOrNull()?.uppercaseChar()?.toString() ?: ""
        SortOption.READING_TIME_SHORT, SortOption.READING_TIME_LONG -> {
            val mins = bookmark?.readingTimeMinutes ?: 0
            if (mins > 0) "${mins}mn" else ""
        }
    }

    AnimatedVisibility(
        visible = visible && label.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun formatScrollCursorDate(epochMillis: Long): String {
    return try {
        val now = Clock.System.now().toEpochMilliseconds()
        val diffMs = now - epochMillis
        when {
            diffMs < 60_000L -> "now"
            diffMs < 3_600_000L -> "${diffMs / 60_000}m"
            diffMs < 86_400_000L -> "${diffMs / 3_600_000}h"
            diffMs < 7 * 86_400_000L -> "${diffMs / 86_400_000}d"
            diffMs < 30 * 86_400_000L -> "${diffMs / (7 * 86_400_000)}w"
            diffMs < 365 * 86_400_000L -> "${diffMs / (30 * 86_400_000)}mo"
            else -> "${diffMs / (365 * 86_400_000L)}y"
        }
    } catch (e: Exception) {
        ""
    }
}
